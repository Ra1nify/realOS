package io.github.ra1nify.realos.xposed.hooks.clockseconds

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.clockseconds.ClockSecondsContract
import io.github.ra1nify.realos.util.ModuleResources
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Relabels the native five-minute clock-seconds entry and stores a persistent deadline when
 * the entry is selected.
 */
internal object ClockSecondsSettingsHook {

    private const val TAG = "RealOS-ClockSeconds"

    private const val KEY_MODE = "oplus_status_bar_clock_seconds_mode"
    private const val KEY_DEADLINE = "oplus_status_bar_clock_seconds_deadline"
    private const val KEY_LEGACY = "clock_seconds"
    private const val KEY_LONGPRESS_TIP_DONE = "oplus_status_bar_clock_seconds_longpress_tip_done"

    private const val MODE_FIVE_MINUTES = 1
    private const val ALWAYS_DEADLINE = Long.MAX_VALUE
    private const val FIVE_MINUTES_MS = 300000L

    private val installed = AtomicBoolean(false)
    private val warned = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var context: Context? = null

    @Volatile
    private var symbols: ClockSecondsSymbols? = null

    @Volatile
    private var persistentTitle: String? = null

    /** Native five-minute entry was relabeled in this process. */
    @Volatile
    private var labelReady = false

    @Volatile
    private var labelLogged = false

    @Volatile
    private var hooksHealthy = false

    fun install(module: XposedModule, app: Application) {
        if (!installed.compareAndSet(false, true)) return
        this.module = module
        val appContext = app.applicationContext ?: app
        context = appContext

        val resolved = runCatching {
            ClockSecondsSymbolResolver.resolve(app.classLoader)
        }.onFailure {
            logStatus("clock_seconds_symbols", "MISSING", it.message ?: it.javaClass.simpleName)
            installed.set(false)
        }.getOrNull()
        if (resolved == null) {
            downgradePersistentIfNeeded()
            return
        }
        symbols = resolved

        val title = runCatching {
            ModuleResources.string(appContext, R.string.clock_seconds_always)
        }.getOrDefault("")
        if (title.isBlank()) {
            logStatus("clock_seconds_title", "MISSING", "module string unavailable")
            downgradePersistentIfNeeded()
            installed.set(false)
            return
        }
        persistentTitle = title

        val itemsHooked = safeHook(module, resolved.getItems, "clock_seconds_items") { chain ->
            onGetItems(chain)
        }
        val applyHooked = safeHook(module, resolved.applyMode, "clock_seconds_apply_mode") { chain ->
            onApplyMode(chain)
        }

        hooksHealthy = itemsHooked && applyHooked

        // Clear a persistent state when the switch is off.
        val prefs = remoteConfig()
        if (prefs == null) {
            warnOnce("remote-prefs", "remote clock-seconds config unavailable")
        } else if (!isFeatureEnabled()) {
            downgradePersistentIfNeeded()
        }
        if (!hooksHealthy) {
            // Disable conversion when a hook failed.
            downgradePersistentIfNeeded()
        }

        if (hooksHealthy) {
            val detail = when {
                prefs == null -> "remote config unavailable"
                isFeatureEnabled() -> "enabled"
                else -> "disabled by realOS switch"
            }
            logStatus("clock_seconds_feature", "FOUND", detail)
        } else {
            logStatus("clock_seconds_feature", "ERROR", "incomplete hook set")
        }
    }

    fun reset() {
        installed.set(false)
        module = null
        context = null
        symbols = null
        persistentTitle = null
        labelReady = false
        labelLogged = false
        hooksHealthy = false
        warned.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun onGetItems(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        if (!hooksHealthy) return result
        if (!isFeatureEnabled()) {
            if (remoteConfig() != null) downgradePersistentIfNeeded()
            labelReady = false
            return result
        }

        val list = result as? MutableList<Any?> ?: run {
            labelReady = false
            return result
        }
        val resolved = symbols ?: run {
            labelReady = false
            return result
        }
        // Skip a native three-entry menu.
        if (list.count { resolved.statusIconClass.isInstance(it) } >= 3) {
            labelReady = false
            warnOnce(
                "native-third-item",
                "native clock-seconds menu already has three entries; leaving it untouched"
            )
            return result
        }

        val fiveIndex = list.indexOfFirst { candidate ->
            resolved.statusIconClass.isInstance(candidate) &&
                settingsValueOf(candidate) == MODE_FIVE_MINUTES
        }
        if (fiveIndex < 0) {
            labelReady = false
            warnOnce("five-minutes-item", "native five-minute clock-seconds item not found")
            return result
        }

        val title = persistentTitle ?: run {
            labelReady = false
            return result
        }
        val collision = list.indices.any { index ->
            index != fiveIndex &&
                resolved.statusIconClass.isInstance(list[index]) &&
                titleOf(list[index]) == title
        }
        if (collision) {
            labelReady = false
            warnOnce("title-collision", "native menu already uses the persistent clock-seconds title")
            return result
        }

        val fiveMinutesItem = list[fiveIndex]
        val dialogItem = runCatching {
            resolved.statusIconDialogItemField.get(fiveMinutesItem)
        }.onFailure {
            warnOnce("dialog-item", "failed to read dialog item: ${it.message}")
        }.getOrNull() ?: run {
            labelReady = false
            return result
        }

        val replacement = runCatching {
            resolved.statusIconConstructor.newInstance(title, dialogItem)
        }.onFailure {
            warnOnce("replacement-item", "failed to build the persistent label: ${it.message}")
        }.getOrNull() ?: run {
            labelReady = false
            return result
        }

        val replaced = runCatching {
            list[fiveIndex] = replacement
        }.isSuccess
        if (!replaced) {
            labelReady = false
            warnOnce("replace-item", "failed to relabel the five-minute entry")
            return result
        }

        labelReady = true
        // Upgrade an active five-minute state after relabeling.
        upgradeActiveFiveMinutesIfNeeded()
        if (!labelLogged) {
            labelLogged = true
            Log.i(TAG, "HookPoint[clock_seconds_label] state=FOUND index=$fiveIndex")
        }
        return result
    }

    private fun onApplyMode(chain: XposedInterface.Chain): Any? {
        val mode = chain.args.getOrNull(1) as? Int ?: return chain.proceed()
        if (mode != MODE_FIVE_MINUTES || !hooksHealthy || !labelReady || !isFeatureEnabled()) {
            return chain.proceed()
        }
        val settingsContext = chain.args.getOrNull(0) as? Context ?: return chain.proceed()
        return if (applyPersistent(settingsContext)) null else chain.proceed()
    }

    private fun titleOf(candidate: Any?): String? = runCatching {
        symbols?.statusIconTitleField?.get(candidate) as? String
    }.getOrNull()

    private fun settingsValueOf(statusIcon: Any?): Int? = runCatching {
        val resolved = symbols ?: return@runCatching null
        val dialogItem = resolved.statusIconDialogItemField.get(statusIcon)
        resolved.dialogItemSettingsValue.invoke(dialogItem) as? Int
    }.getOrNull()

    private fun remoteConfig(): SharedPreferences? = runCatching {
        module?.getRemotePreferences(ClockSecondsContract.REMOTE_PREFS)
    }.getOrNull()

    private fun isFeatureEnabled(): Boolean {
        val prefs = remoteConfig() ?: return false
        return runCatching {
            prefs.getBoolean(
                ClockSecondsContract.REMOTE_KEY_ENABLED,
                ClockSecondsContract.DEFAULT_ENABLED
            )
        }.getOrDefault(ClockSecondsContract.DEFAULT_ENABLED)
    }

    /** Downgrades a persistent state to the native five-minute deadline. */
    private fun downgradePersistentIfNeeded() {
        val appContext = context ?: return
        val resolver = appContext.contentResolver

        val mode = runCatching {
            Settings.Secure.getInt(resolver, KEY_MODE, 0)
        }.getOrDefault(0)
        if (mode != MODE_FIVE_MINUTES) return

        val deadline = runCatching {
            Settings.Secure.getLong(resolver, KEY_DEADLINE, 0L)
        }.getOrDefault(0L)
        if (deadline != ALWAYS_DEADLINE) return

        val nextDeadline = System.currentTimeMillis() + FIVE_MINUTES_MS
        writeSecondsState(appContext, nextDeadline, "downgrade")
        Log.i(TAG, "persistent clock seconds disabled; downgraded to the native five-minute mode")
    }

    /** Upgrades an active five-minute state to the persistent deadline. */
    private fun upgradeActiveFiveMinutesIfNeeded() {
        val appContext = context ?: return
        val resolver = appContext.contentResolver

        val mode = runCatching {
            Settings.Secure.getInt(resolver, KEY_MODE, 0)
        }.getOrDefault(0)
        if (mode != MODE_FIVE_MINUTES) return

        val deadline = runCatching {
            Settings.Secure.getLong(resolver, KEY_DEADLINE, 0L)
        }.getOrDefault(0L)
        if (deadline == ALWAYS_DEADLINE || deadline <= System.currentTimeMillis()) return

        writeSecondsState(appContext, ALWAYS_DEADLINE, "upgrade")
        Log.i(TAG, "active five-minute clock seconds upgraded to persistent")
    }

    private fun writeSecondsState(ctx: Context, deadline: Long, action: String) {
        val resolver = ctx.contentResolver
        runCatching {
            Settings.Secure.putLong(resolver, KEY_DEADLINE, deadline)
        }.onFailure {
            warnOnce("$action-deadline", "failed to write $KEY_DEADLINE: ${it.message}")
        }
        runCatching {
            Settings.Secure.putInt(resolver, KEY_MODE, MODE_FIVE_MINUTES)
        }.onFailure {
            warnOnce("$action-mode", "failed to write $KEY_MODE: ${it.message}")
        }
        runCatching {
            Settings.Secure.putInt(resolver, KEY_LEGACY, 1)
        }.onFailure {
            warnOnce("$action-legacy", "failed to write $KEY_LEGACY: ${it.message}")
        }
    }

    private fun isAlwaysActive(appContext: Context? = context): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            Settings.Secure.getInt(ctx.contentResolver, KEY_MODE, 0) == MODE_FIVE_MINUTES &&
                Settings.Secure.getLong(ctx.contentResolver, KEY_DEADLINE, 0L) == ALWAYS_DEADLINE
        }.getOrDefault(false)
    }

    private fun applyPersistent(settingsContext: Context): Boolean {
        val resolver = settingsContext.contentResolver

        // Mark the native five-minute tip as seen.
        val tipMarked = runCatching {
            Settings.Secure.putInt(resolver, KEY_LONGPRESS_TIP_DONE, 1)
        }.getOrDefault(false)
        if (!tipMarked) {
            warnOnce("write-tip-done", "failed to mark the clock-seconds tip as seen")
        }

        writeSecondsState(settingsContext, ALWAYS_DEADLINE, "persistent")

        // Verify the final mode/deadline pair.
        val applied = isAlwaysActive(settingsContext)
        if (applied) {
            Log.i(
                TAG,
                "clock seconds set to persistent " +
                    "(mode=$MODE_FIVE_MINUTES, deadline=$ALWAYS_DEADLINE)"
            )
        } else {
            warnOnce("verify-persistent", "persistent clock seconds did not stick")
        }
        return applied
    }

    private fun safeHook(
        hostModule: XposedModule,
        method: Method,
        key: String,
        handler: (XposedInterface.Chain) -> Any?,
    ): Boolean = runCatching {
        method.isAccessible = true
        hostModule.hook(method).intercept(handler)
        logStatus(key, "FOUND", null)
        true
    }.onFailure {
        logStatus(key, "ERROR", it.message ?: it.javaClass.simpleName)
    }.getOrDefault(false)

    private fun logStatus(key: String, state: String, detail: String?) {
        val message = "HookPoint[$key] state=$state" +
            if (detail.isNullOrBlank()) "" else " ($detail)"
        if (state == "FOUND") {
            Log.i(TAG, message)
        } else {
            Log.w(TAG, message)
        }
        module?.let { module ->
            runCatching {
                module.log(
                    if (state == "FOUND") Log.INFO else Log.WARN,
                    TAG,
                    message
                )
            }
        }
    }

    private fun warnOnce(key: String, message: String) {
        if (warned.add(key)) Log.w(TAG, message)
    }
}
