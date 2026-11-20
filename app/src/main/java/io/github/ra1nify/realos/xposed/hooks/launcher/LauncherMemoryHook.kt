package io.github.ra1nify.realos.xposed.hooks.launcher

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import io.github.ra1nify.realos.launcher.LauncherConfig

/**
 * Recent task page "running memory info" correction and real memory display.
 *
 * Measured on ColorOS 17 / launcher 17.3.12:
 * - with `ro.product.first_api_level > 34` the host permanently disables the memory display
 *   switch and stores the result in a final field that reflection cannot flip;
 * - the derived `isNeedMemoryDetail()` stays false, so the host never renders memory text;
 * - the settings page also calls `setVisible(false)` on the switch and removes the whole category.
 *
 * Three hooks cover this:
 * 1. forces the two no-arg boolean gates of the memory manager to true;
 * 2. takes over the memory panel refresh entry; the module chooses official figures or kernel data;
 * 3. injects a secondary "real memory info" switch below the memory switch in the launcher
 *    settings page; it appears only while the official switch is on.
 */
object LauncherMemoryHook {

    private const val TAG = "RealOS-LauncherMemory"

    /** Preference key of the official "show running memory info" switch */
    private const val OFFICIAL_MEMORY_PREF_KEY = "display_memory_information_recent_task"

    /** Key of the secondary switch injected by this module */
    private const val REAL_MEMORY_PREF_KEY = "realos_launcher_real_memory"

    /** Order offset of the injected switch relative to the official switch */
    private const val INJECTED_ORDER_OFFSET = 1

    /** Upper bound of memory node reads in milliseconds */
    private const val RENDER_THROTTLE_MS = 800L

    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "realOS-launcher-mem").apply { isDaemon = true }
    }

    private val lastRenderAt = AtomicLong(0L)

    @Volatile
    private var installed = false

    @Volatile
    private var textViewField: java.lang.reflect.Field? = null

    @Volatile
    private var animatorField: java.lang.reflect.Field? = null

    /** PreferenceGroup add/find (the host obfuscates the names; the resolver locates them by structure) */
    @Volatile
    private var groupAddMethod: java.lang.reflect.Method? = null

    @Volatile
    private var groupFindMethod: java.lang.reflect.Method? = null

    /** Injected secondary switch; the weak reference does not keep a destroyed settings page alive */
    @Volatile
    private var injectedSwitchRef: WeakReference<Any>? = null

    fun install(module: XposedModule, symbols: LauncherHandles) {
        if (installed) {
            module.log(Log.INFO, TAG, "already installed, skip")
            return
        }
        installed = true

        groupAddMethod = symbols.preferenceGroupAddMethod
        groupFindMethod = symbols.preferenceGroupFindMethod

        // Gates run before the panel hook: the host calls g() to decide whether to call the
        // memory refresh entry B(); h() is used only by B(), which this module takes over.
        val panelHooked = installMemoryPanelHook(module, symbols)
        installMemoryGateHooks(module, symbols)
        if (!panelHooked) {
            module.log(Log.WARN, TAG, "Memory refresh entry not intercepted; falls back to the host own rendering (gated by h())")
        }

        installPreferenceVisibleHook(module, symbols)
        installMemorySwitchStateHook(module, symbols)
        installPreferenceChangePersistHook(module, symbols)
        installTwoStateCheckedHook(module, symbols)
        installSettingsInjectHook(module, symbols)
    }

    /** Pre-hot-reload cleanup: the framework unloads old generation handles; this resets the install flag and caches */
    fun reset() {
        installed = false
        textViewField = null
        animatorField = null
        groupAddMethod = null
        groupFindMethod = null
        injectedSwitchRef = null
        lastRenderAt.set(0L)
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Version gate removal: forces the two no-arg boolean methods of the memory manager to true
    // ─────────────────────────────────────────────────────────────

    private fun installMemoryGateHooks(module: XposedModule, symbols: LauncherHandles) {
        if (symbols.memoryGateMethods.isEmpty()) {
            module.log(Log.WARN, TAG, "fail closed: memory gate symbol missing -> the official switch stays reported as unavailable")
            return
        }
        for (method in symbols.memoryGateMethods) {
            runCatching {
                module.hook(method).intercept { chain ->
                    true
                }
                module.log(Log.INFO, TAG, "Unlocked the memory gate ${method.name}()")
            }.onFailure {
                module.log(Log.WARN, TAG, "Memory gate ${method.name}() hook install failed", it)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Takes over the memory text refresh entry
    // ─────────────────────────────────────────────────────────────

    /** @return true when the host memory text rendering is taken over */
    private fun installMemoryPanelHook(module: XposedModule, symbols: LauncherHandles): Boolean {
        val method = symbols.refreshMemoryMethod
        if (method == null || symbols.memoryTextViewField == null) {
            module.log(
                Log.WARN, TAG,
                "fail closed: memory panel symbol missing -> not installing the memory display hook " +
                    "(refresh=${method?.name}, tv=${symbols.memoryTextViewField?.name})"
            )
            return false
        }

        textViewField = symbols.memoryTextViewField
        animatorField = symbols.memoryAnimatorField

        return runCatching {
            module.hook(method).intercept { chain ->
                val panel = chain.thisObject as? View ?: return@intercept chain.proceed()
                val context = panel.context ?: return@intercept chain.proceed()

                renderMemory(module, panel, context)
                // Full takeover: the host disable logic does not run
                null
            }
            module.log(Log.INFO, TAG, "Intercepted ${symbols.clearAllPanelClass?.simpleName}#${method.name}(Z)V")
            true
        }.getOrElse {
            module.log(Log.ERROR, TAG, "fail closed: memory panel hook install failed", it)
            false
        }
    }

    private fun renderMemory(module: XposedModule, panel: View, context: Context) {
        // Throttle check first; no preference read precedes it on this path.
        // The hideMemoryText branch also runs behind the same throttle.
        val now = System.currentTimeMillis()
        val last = lastRenderAt.get()
        if (now - last < RENDER_THROTTLE_MS) return
        if (!lastRenderAt.compareAndSet(last, now)) return

        val showReal = LauncherConfig.isRealMemoryEnabled(context)
        val showOfficial = LauncherConfig.isOfficialMemorySwitchOn(context)

        if (!showReal && !showOfficial) {
            hideMemoryText(panel)
            return
        }

        ioExecutor.execute {
            val snapshot = LauncherMemoryReader.read()
            // zram data comes from the realOS root read through the provider; the swap metric is the fallback
            val rootStats = if (showReal) LauncherConfig.readRootStats(context) else null
            val text = when {
                showReal && snapshot != null -> LauncherMemoryReader.formatReal(snapshot, rootStats)
                else -> LauncherMemoryReader.formatOfficial(context, snapshot)
            }
            if (text.isBlank()) {
                module.log(Log.WARN, TAG, "Memory text came out empty -> falling back to hidden")
                panel.post { hideMemoryText(panel) }
                return@execute
            }
            panel.post { applyMemoryText(panel, text) }
        }
    }

    private fun applyMemoryText(panel: View, text: String) {
        runCatching {
            cancelMemoryAnimator(panel)
            val tv = memoryTextView(panel) ?: return
            tv.text = text
            tv.visibility = View.VISIBLE
            panel.contentDescription = text
        }.onFailure { Log.w(TAG, "Failed to apply the memory text", it) }
    }

    private fun hideMemoryText(panel: View) {
        runCatching {
            cancelMemoryAnimator(panel)
            val tv = memoryTextView(panel) ?: return
            tv.text = ""
            tv.visibility = View.GONE
        }.onFailure { Log.w(TAG, "Failed to hide the memory text", it) }
    }

    private fun memoryTextView(panel: View): TextView? {
        val field = textViewField ?: return null
        return runCatching { field.get(panel) as? TextView }.getOrNull()
    }

    private fun cancelMemoryAnimator(panel: View) {
        val field = animatorField ?: return
        val animator = runCatching { field.get(panel) as? android.animation.ValueAnimator }.getOrNull()
        if (animator != null && animator.isRunning) {
            runCatching { animator.cancel() }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Corrects visibility of the official switch
    // ─────────────────────────────────────────────────────────────

    private fun installPreferenceVisibleHook(module: XposedModule, symbols: LauncherHandles) {
        val method = symbols.preferenceSetVisibleMethod
        if (method == null) {
            module.log(Log.WARN, TAG, "fail closed: Preference#setVisible not resolved -> the official switch stays hidden")
            return
        }

        runCatching {
            module.hook(method).intercept { chain ->
                val preference = chain.thisObject ?: return@intercept chain.proceed()
                if (preferenceKey(preference) == OFFICIAL_MEMORY_PREF_KEY) {
                    // Forces visibility so the host cannot hide the switch and drop the whole category.
                    // Chain.args is immutable; argument changes go through proceed(newArgs).
                    return@intercept chain.proceed(arrayOf<Any?>(true))
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "Installed Preference#setVisible (fixes the official memory switch visibility)")
        }.onFailure {
            module.log(Log.ERROR, TAG, "fail closed: Preference#setVisible hook install failed", it)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Official switch state change -> syncs secondary switch visibility
    // ─────────────────────────────────────────────────────────────

    private fun installTwoStateCheckedHook(module: XposedModule, symbols: LauncherHandles) {
        val method = symbols.twoStateSetCheckedMethod ?: run {
            module.log(Log.WARN, TAG, "TwoStatePreference#setChecked not resolved -> the sub switch cannot follow the master switch")
            return
        }

        runCatching {
            module.hook(method).intercept { chain ->
                val preference = chain.thisObject
                val isOfficial = preference != null &&
                    preferenceKey(preference) == OFFICIAL_MEMORY_PREF_KEY

                val result = chain.proceed()

                if (isOfficial) {
                    val checked = runCatching {
                        preference.javaClass.getMethod("isChecked").invoke(preference) as? Boolean
                    }.getOrNull() ?: false
                    syncSecondaryVisibility(checked)
                }
                result
            }
            module.log(Log.INFO, TAG, "Installed TwoStatePreference#setChecked (the sub switch follows the master switch)")
        }.onFailure {
            module.log(Log.ERROR, TAG, "TwoStatePreference#setChecked hook install failed", it)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Corrects the switch checked state
    // ─────────────────────────────────────────────────────────────

    /**
     * The host calls `updateMemoryInfoSwitch(lp.f.a(ctx).mNeedMemoryDetail)` from onCreateView
     * and onResume, and that field stays false. onResume runs after the onCreateView after-hook,
     * so the argument is replaced with the persisted value.
     */
    private fun installMemorySwitchStateHook(module: XposedModule, symbols: LauncherHandles) {
        val method = symbols.lockSettingUpdateMemorySwitchMethod ?: run {
            module.log(Log.WARN, TAG, "updateMemoryInfoSwitch not resolved -> the checked state is still overwritten by the always-false field")
            return
        }

        runCatching {
            module.hook(method).intercept { chain ->
                // Fragment and Preference both expose getContext(); one reflection helper serves both
                val context = chain.thisObject?.let { preferenceContext(it) }
                    ?: return@intercept chain.proceed()

                val real = LauncherConfig.isOfficialMemorySwitchOn(context)
                chain.proceed(arrayOf<Any?>(real))
            }
            module.log(Log.INFO, TAG, "Installed updateMemoryInfoSwitch (corrects the checked state after entering the page)")
        }.onFailure {
            module.log(Log.WARN, TAG, "updateMemoryInfoSwitch hook install failed", it)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Switch persistence fallback layer
    // ─────────────────────────────────────────────────────────────

    /**
     * Takes over the host `onPreferenceChange` handling for the memory switch.
     *
     * Fallback layer: [attachOfficialMemoryListener] usually replaces the host listener, so the
     * host onPreferenceChange is not called. When that replacement fails (binding timing change),
     * the host path returns early on disable and never persists; both layers write the same value.
     */
    private fun installPreferenceChangePersistHook(module: XposedModule, symbols: LauncherHandles) {
        val method = symbols.lockSettingOnPreferenceChangeMethod ?: run {
            module.log(Log.WARN, TAG, "LockSettingFragment#onPreferenceChange not resolved -> persistence relies on the listener swap only")
            return
        }

        runCatching {
            module.hook(method).intercept { chain ->
                val preference = chain.args.getOrNull(0) ?: return@intercept chain.proceed()
                if (preferenceKey(preference) != OFFICIAL_MEMORY_PREF_KEY) {
                    return@intercept chain.proceed()
                }
                val newValue = chain.args.getOrNull(1) as? Boolean ?: return@intercept chain.proceed()
                val context = preferenceContext(preference) ?: return@intercept chain.proceed()

                // Settings.Secure is the single source of truth, bypassing the host early return on disable
                LauncherConfig.setOfficialMemorySwitch(context, newValue)
                syncSecondaryVisibility(newValue)
                true
            }
            module.log(Log.INFO, TAG, "Installed LockSettingFragment#onPreferenceChange (persistence fallback)")
        }.onFailure {
            module.log(Log.WARN, TAG, "onPreferenceChange persistence fallback hook install failed", it)
        }
    }

    private fun syncSecondaryVisibility(visible: Boolean) {
        val preference = injectedSwitchRef?.get() ?: return
        runCatching {
            preference.javaClass.getMethod("setVisible", Boolean::class.javaPrimitiveType)
                .invoke(preference, visible)
        }.onFailure { Log.w(TAG, "Failed to sync the sub switch visibility", it) }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Settings page: injects the secondary switch
    // ─────────────────────────────────────────────────────────────

    private fun installSettingsInjectHook(module: XposedModule, symbols: LauncherHandles) {
        val method = symbols.lockSettingOnCreateViewMethod
        if (method == null || !symbols.canInjectSettings) {
            module.log(
                Log.WARN, TAG,
                "fail closed: settings page symbols missing -> not injecting the switch failedKeys=${symbols.failedKeys}"
            )
            return
        }

        runCatching {
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject
                if (fragment != null) {
                    runCatching { injectSwitch(module, symbols, fragment) }
                        .onFailure { module.log(Log.ERROR, TAG, "Failed to inject the real memory switch", it) }
                }
                result
            }
            module.log(Log.INFO, TAG, "Installed LockSettingFragment#onCreateView (injects the real memory switch)")
        }.onFailure {
            module.log(Log.ERROR, TAG, "fail closed: settings page hook install failed", it)
        }
    }

    private fun injectSwitch(module: XposedModule, symbols: LauncherHandles, fragment: Any) {
        val context = runCatching {
            fragment.javaClass.getMethod("getContext").invoke(fragment) as? Context
        }.getOrNull() ?: return

        if (symbols.preferenceClass == null) return

        LauncherConfig.initHost(context)

        val official = findPreferenceDeep(fragment, OFFICIAL_MEMORY_PREF_KEY) ?: run {
            module.log(Log.WARN, TAG, "Official memory switch not found; skipping the injection")
            return
        }

        // The official switch stays visible
        runCatching { symbols.preferenceSetVisibleMethod?.invoke(official, true) }

        // The host refreshes the checked state from a permanently disabled field; the persisted value wins
        val officialOn = LauncherConfig.isOfficialMemorySwitchOn(context)
        setPreferenceChecked(official, officialOn)

        // Takes over persistence of the official switch: the host returns early on disable while
        // the field d stays false, so the disable action never reaches Settings.Secure.
        attachOfficialMemoryListener(official, context)

        val parent = runCatching {
            official.javaClass.getMethod("getParent").invoke(official)
        }.getOrNull() ?: run {
            module.log(Log.WARN, TAG, "Official memory switch has no parent container; skipping the injection")
            return
        }

        val existing = findInGroup(parent, REAL_MEMORY_PREF_KEY)
        if (existing != null) {
            injectedSwitchRef = WeakReference(existing)
            runCatching { symbols.preferenceSetVisibleMethod?.invoke(existing, officialOn) }
            module.log(Log.INFO, TAG, "The real memory switch already exists; syncing visibility only")
            return
        }

        val switchClass = symbols.couiSwitchPreferenceClass ?: return
        val preference = runCatching {
            switchClass.getConstructor(Context::class.java).newInstance(context)
        }.getOrNull() ?: run {
            module.log(Log.WARN, TAG, "COUISwitchPreference construction failed; skipping the injection")
            return
        }

        val officialOrder = runCatching {
            official.javaClass.getMethod("getOrder").invoke(official) as? Int
        }.getOrNull() ?: 0

        setPreferenceKey(preference, REAL_MEMORY_PREF_KEY)
        setPreferenceTitle(preference, LauncherUiText.realMemoryTitle(context))
        setPreferenceSummary(preference, LauncherUiText.realMemorySummary(context))
        setPreferenceOrder(preference, officialOrder + INJECTED_ORDER_OFFSET)
        // The module persists this switch; the host default SharedPreferences copy is disabled
        setPreferencePersistent(preference, false)
        setPreferenceChecked(preference, LauncherConfig.isRealMemoryEnabled(context))
        // Secondary switch: shown only while the official memory display is on
        runCatching {
            symbols.preferenceSetVisibleMethod?.invoke(preference, officialOn)
        }
        attachChangeListener(preference, context, fragment.javaClass.classLoader)

        val adder = groupAddMethod ?: run {
            module.log(Log.WARN, TAG, "fail closed: PreferenceGroup#addPreference not resolved; skipping the injection")
            return
        }
        runCatching {
            adder.invoke(parent, preference)
        }.onFailure {
            module.log(Log.WARN, TAG, "addPreference call failed", it)
            return
        }

        if (findInGroup(parent, REAL_MEMORY_PREF_KEY) != null) {
            injectedSwitchRef = WeakReference(preference)
            module.log(
                Log.INFO, TAG,
                "Injected the Real memory info switch below the official memory switch (visible=$officialOn)"
            )
        } else {
            module.log(Log.WARN, TAG, "The switch is still absent from the parent container after injection; the injection had no effect")
        }
    }

    /**
     * Takes over the change handling of the official "show running memory info" switch.
     *
     * Host implementation (`LockSettingFragment.onPreferenceChange`):
     * ```
     * if (newValue == lp.f.mNeedMemoryDetail) return true;   // permanently disabled field, always false
     * return Settings.Secure.putInt(cr, MEMORY_INFO_KEY, newValue ? 1 : 0);
     * ```
     * The host returns early when the switch is turned off, so Settings.Secure never receives the
     * value. The listener below writes Settings.Secure directly.
     */
    private fun attachOfficialMemoryListener(official: Any, context: Context) {
        val cl = official.javaClass.classLoader ?: return
        val iface = runCatching {
            cl.loadClass("androidx.preference.Preference\$OnPreferenceChangeListener")
        }.getOrNull() ?: return

        val proxy = Proxy.newProxyInstance(iface.classLoader ?: cl, arrayOf(iface)) { _, method, args ->
            when (method.name) {
                "equals" -> false
                "hashCode" -> 0
                "toString" -> "realOS-LauncherOfficialMemoryListener"
                else -> {
                    val newValue = args?.getOrNull(1) as? Boolean ?: false
                    LauncherConfig.setOfficialMemorySwitch(context, newValue)
                    // Secondary switch follows the main switch visibility
                    syncSecondaryVisibility(newValue)
                    true
                }
            }
        }

        val setter = official.javaClass.methods.firstOrNull {
            it.name == "setOnPreferenceChangeListener" && it.parameterCount == 1
        } ?: return

        runCatching { setter.invoke(official, proxy) }
    }

    private fun attachChangeListener(preference: Any, context: Context, classLoader: ClassLoader?) {
        val cl = classLoader ?: LauncherMemoryHook::class.java.classLoader ?: return
        val iface = runCatching {
            cl.loadClass("androidx.preference.Preference\$OnPreferenceChangeListener")
        }.getOrNull() ?: return

        val proxy = Proxy.newProxyInstance(iface.classLoader ?: cl, arrayOf(iface)) { _, method, args ->
            when (method.name) {
                "equals" -> false
                "hashCode" -> 0
                "toString" -> "realOS-LauncherRealMemoryListener"
                else -> {
                    val newValue = args?.getOrNull(1) as? Boolean ?: false
                    LauncherConfig.setRealMemoryEnabled(context, newValue)
                    // Enabling real memory also enables the official switch when it is off
                    if (newValue && !LauncherConfig.isOfficialMemorySwitchOn(context)) {
                        LauncherConfig.setOfficialMemorySwitch(context, true)
                    }
                    true
                }
            }
        }

        val setter = preference.javaClass.methods.firstOrNull {
            it.name == "setOnPreferenceChangeListener" && it.parameterCount == 1
        } ?: return

        runCatching { setter.invoke(preference, proxy) }
    }

    // ─────────────────────────────────────────────────────────────
    // Reflection helpers (androidx.preference / COUI are public classes inside the host)
    // ─────────────────────────────────────────────────────────────

    private fun findPreference(fragment: Any, key: String): Any? = runCatching {
        fragment.javaClass.getMethod("findPreference", CharSequence::class.java)
            .invoke(fragment, key)
    }.getOrNull()

    private fun findInGroup(group: Any, key: String): Any? {
        // The host obfuscates PreferenceGroup#findPreference to a single letter; the resolved method comes first
        groupFindMethod?.let { m ->
            runCatching { m.invoke(group, key) }.getOrNull()?.let { return it }
        }
        return runCatching {
            group.javaClass.getMethod("findPreference", CharSequence::class.java)
                .invoke(group, key)
        }.getOrNull()
    }

    /** Searches the fragment first, then the PreferenceScreen group */
    private fun findPreferenceDeep(fragment: Any, key: String): Any? {
        findPreference(fragment, key)?.let { return it }
        val screen = runCatching {
            fragment.javaClass.getMethod("getPreferenceScreen").invoke(fragment)
        }.getOrNull() ?: return null
        return findInGroup(screen, key)
    }

    private fun preferenceKey(preference: Any): String? = runCatching {
        preference.javaClass.getMethod("getKey").invoke(preference) as? String
    }.getOrNull()

    private fun preferenceContext(preference: Any): Context? = runCatching {
        preference.javaClass.getMethod("getContext").invoke(preference) as? Context
    }.getOrNull()

    private fun setPreferenceKey(preference: Any, key: String) {
        runCatching { preference.javaClass.getMethod("setKey", String::class.java).invoke(preference, key) }
    }

    private fun setPreferenceTitle(preference: Any, title: String) {
        runCatching {
            preference.javaClass.getMethod("setTitle", CharSequence::class.java).invoke(preference, title)
        }
    }

    private fun setPreferenceSummary(preference: Any, summary: String) {
        runCatching {
            preference.javaClass.getMethod("setSummary", CharSequence::class.java).invoke(preference, summary)
        }
    }

    private fun setPreferenceOrder(preference: Any, order: Int) {
        runCatching {
            preference.javaClass.getMethod("setOrder", Int::class.javaPrimitiveType).invoke(preference, order)
        }
    }

    private fun setPreferencePersistent(preference: Any, persistent: Boolean) {
        runCatching {
            preference.javaClass.getMethod("setPersistent", Boolean::class.javaPrimitiveType)
                .invoke(preference, persistent)
        }
    }

    private fun setPreferenceChecked(preference: Any, checked: Boolean) {
        var clazz: Class<*>? = preference.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val m = runCatching {
                clazz.getDeclaredMethod("setChecked", Boolean::class.javaPrimitiveType)
            }.getOrNull()
            if (m != null) {
                m.isAccessible = true
                runCatching { m.invoke(preference, checked) }
                return
            }
            clazz = clazz.superclass
        }
    }
}
