package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Applies the clock font to SystemUI text clocks. */
internal object SystemClockFontHook {

    private const val TAG = "RealOS-SystemClockFont"
    private const val WEIGHT = 600
    private const val HEIGHT = 50

    private val installed = AtomicBoolean(false)
    private val refreshScheduled = AtomicBoolean(false)
    private val warned = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var symbols: ClockFontSystemSymbols? = null

    /** Target clock class names. */
    @Volatile
    private var targetClockNames: Set<String> = emptySet()

    @Volatile
    private var styleRecord: Any? = null

    @Volatile
    private var getClockStyleConfig: Method? = null

    @Volatile
    private var getTimeFamilyName: Method? = null

    @Volatile
    private var fileCachedValue: String? = null

    @Volatile
    private var fileCachedMtime: Long = -1L

    @Volatile
    private var fileCachedAt: Long = 0L

    /** Fallback typefaces by clock. */
    private val clocks = Collections.synchronizedMap(WeakHashMap<TextView, Typeface?>())

    /** Applied clock-font typefaces. */
    private val applied = Collections.synchronizedMap(WeakHashMap<TextView, Typeface>())

    private val handler = Handler(Looper.getMainLooper())

    fun install(
        module: XposedModule,
        loader: ClassLoader,
        context: Context?,
        apkPath: String?
    ) {
        if (!installed.compareAndSet(false, true)) return
        this.module = module

        val resolved = runCatching {
            ClockFontSymbolResolver.resolveSystem(module, context, loader, apkPath)
        }.onFailure {
            logStatus("status_clock_symbols", "ERROR", it.message ?: it.javaClass.simpleName)
            installed.set(false)
        }.getOrNull() ?: return
        symbols = resolved
        targetClockNames = resolved.targetClockClassNames

        if (resolved.clockClass?.restore(loader) == null) {
            logStatus("status_bar_clock", "MISSING", "Clock not found in SystemUI")
            installed.set(false)
            return
        }

        resolveStyleRecord(loader)
        hookSetTypeface(module, loader)
        hookConstructor(module, loader)
        hookUpdateClock(module, loader)
        hookStyleChanges(module, loader)

        ClockFontHost.setSystemClockChangeListener { refreshAsync() }
        Log.i(TAG, "system clock hook installed")
    }

    fun reset() {
        installed.set(false)
        module = null
        symbols = null
        targetClockNames = emptySet()
        styleRecord = null
        getClockStyleConfig = null
        getTimeFamilyName = null
        clocks.clear()
        applied.clear()
        refreshScheduled.set(false)
        warned.clear()
        ClockFontHost.setSystemClockChangeListener(null)
    }

    private fun resolveStyleRecord(loader: ClassLoader) {
        val resolved = symbols
        val instanceField = resolved?.styleRecordInstance?.restore(loader)
        val configMethod = resolved?.styleConfigGetter?.restore(loader)
        val familyMethod = resolved?.timeFamilyNameGetter?.restore(loader)
            // Fall back to the getter return type.
            ?: configMethod?.returnType?.let {
                runCatching { it.getMethod("getTimeFamilyName") }.getOrNull()
            }
        if (resolved?.styleRecordClass?.restore(loader) == null ||
            instanceField == null ||
            configMethod == null ||
            familyMethod == null
        ) {
            logStatus("status_clock_style", "MISSING", "style config descriptors unavailable")
            return
        }

        styleRecord = runCatching { instanceField.get(null) }.getOrNull()
        getClockStyleConfig = configMethod
        getTimeFamilyName = familyMethod
        if (styleRecord == null) {
            logStatus("status_clock_style", "MISSING", "style record instance unavailable")
        } else {
            logStatus("status_clock_style", "FOUND", null)
        }
    }

    private fun hookSetTypeface(module: XposedModule, loader: ClassLoader) {
        val method = symbols?.clockSetTypeface?.restore(loader)
        if (method == null) {
            logStatus("status_bar_clock_set_typeface", "MISSING", "Clock#setTypeface")
            return
        }
        safeHook(module, method, "status_bar_clock_set_typeface") { chain ->
            onSetTypeface(chain)
        }
    }

    private fun hookConstructor(module: XposedModule, loader: ClassLoader) {
        val constructor = symbols?.clockConstructor?.restore(loader)
        if (constructor == null) {
            logStatus("status_bar_clock_ctor", "MISSING", "Clock ctor")
            return
        }
        safeHook(module, constructor, "status_bar_clock_ctor") { chain ->
            val result = chain.proceed()
            val view = chain.thisObject as? TextView
            if (view != null) applyToClock(view)
            result
        }
    }

    private fun hookUpdateClock(module: XposedModule, loader: ClassLoader) {
        val method = symbols?.clockUpdateClock?.restore(loader)
        if (method == null) {
            logStatus("status_bar_clock_update", "MISSING", "Clock#updateClock")
            return
        }
        safeHook(module, method, "status_bar_clock_update") { chain ->
            val view = chain.thisObject as? TextView
            if (view != null && isTargetClock(view)) {
                register(view)
                val target = targetTypeface(view)
                if (target != null) {
                    if (applied[view] !== target) applyToClock(view)
                } else {
                    restoreIfNeeded(view)
                }
            }
            chain.proceed()
        }
    }

    private fun hookStyleChanges(module: XposedModule, loader: ClassLoader) {
        val methods = symbols?.styleChangeMethods.orEmpty().mapNotNull { descriptor ->
            descriptor.restore(loader)?.let { descriptor to it }
        }
        if (methods.isEmpty()) {
            logStatus("status_clock_style_change", "MISSING", "style change methods")
            return
        }
        methods.forEachIndexed { index, (descriptor, method) ->
            safeHook(
                module,
                method,
                "status_clock_style_${descriptor.methodName}_$index"
            ) { chain ->
                val result = chain.proceed()
                refreshAsync()
                result
            }
        }
    }

    private fun onSetTypeface(chain: XposedInterface.Chain): Any? {
        val view = chain.thisObject as? TextView ?: return chain.proceed()
        if (!isTargetClock(view)) return chain.proceed()
        register(view)

        val incoming = chain.args.getOrNull(0) as? Typeface
        val target = targetTypeface(view)
        if (target == null) {
            // Restore the fallback when no target typeface is available.
            val previous = applied.remove(view)
            if (incoming != null && incoming !== previous) {
                clocks[view] = incoming
            }
            return chain.proceed()
        }

        if (incoming === target) {
            applied[view] = target
            return chain.proceed()
        }

        val previous = applied[view]
        if (incoming != null && incoming !== previous) {
            clocks[view] = incoming
        } else if (!clocks.containsKey(view)) {
            clocks[view] = view.typeface
        }

        val result = chain.proceed(arrayOf<Any?>(target))
        applied[view] = target
        return result
    }

    private fun applyToClock(view: TextView) {
        if (!isTargetClock(view)) return
        register(view)
        val target = targetTypeface(view)
        if (target == null) {
            restoreIfNeeded(view)
            return
        }
        if (view.typeface === target) {
            applied[view] = target
            return
        }

        val previous = applied[view]
        val before = view.typeface
        if (before != null && before !== previous) {
            clocks[view] = before
        }
        view.setTypeface(target)
        applied[view] = target
    }

    private fun restoreIfNeeded(view: TextView) {
        if (applied.remove(view) == null) return
        if (!clocks.containsKey(view)) return
        // A null fallback restores the default typeface.
        view.setTypeface(clocks[view])
    }

    private fun refreshAsync() {
        if (!refreshScheduled.compareAndSet(false, true)) return
        handler.post {
            try {
                refreshClocks()
            } finally {
                refreshScheduled.set(false)
            }
        }
    }

    private fun refreshClocks() {
        val enabled = ClockFontHost.isStatusBarClockEnabled()
        val snapshot = synchronized(clocks) { clocks.keys.toList() }
        for (view in snapshot) {
            if (!isTargetClock(view)) continue
            if (enabled) {
                applyToClock(view)
            } else {
                restoreIfNeeded(view)
            }
        }
    }

    private fun targetTypeface(view: TextView): Typeface? {
        if (!ClockFontHost.isAttached() || !ClockFontHost.isStatusBarClockEnabled()) return null
        val value = appliedClockValue(view.context) ?: return null
        if (!ClockFontHost.isCustom(value)) return null
        return ClockFontHost.buildTypeface(
            view.context,
            value,
            WEIGHT,
            HEIGHT,
            originFor(view),
            normalizeMetrics = true
        )
    }

    private fun appliedClockValue(context: Context): String? {
        val fromRecord = readAppliedValueFromRecord()
        if (fromRecord != null) return fromRecord
        return readAppliedValueFromFile(context)
    }

    private fun readAppliedValueFromRecord(): String? {
        val record = styleRecord ?: return null
        val configMethod = getClockStyleConfig ?: return null
        val familyMethod = getTimeFamilyName ?: return null
        return runCatching {
            val config = configMethod.invoke(record) ?: return null
            familyMethod.invoke(config) as? String
        }.onFailure {
            warnOnce("style", "failed to read applied clock style: ${it.message}")
        }.getOrNull()
    }

    /** Reads the applied style from SystemUI's DE file. */
    private fun readAppliedValueFromFile(context: Context): String? {
        val now = SystemClock.elapsedRealtime()
        if (now - fileCachedAt < 1_000L) return fileCachedValue
        fileCachedAt = now

        val file = File(
            context.createDeviceProtectedStorageContext().filesDir,
            ClockFontHostSlots.STYLE_FILE
        )
        if (!file.isFile) {
            fileCachedMtime = -1L
            fileCachedValue = null
            return null
        }
        val mtime = file.lastModified()
        if (mtime == fileCachedMtime) return fileCachedValue

        val value = runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val styleData = root.optString(ClockFontHostSlots.STYLE_DATA, "")
            if (styleData.isBlank()) return@runCatching null
            val style = JSONObject(styleData)
            val clockStyle = style.optString(ClockFontHostSlots.CLOCK_STYLE_CONFIG, "")
            if (clockStyle.isBlank()) return@runCatching null
            val config = JSONObject(clockStyle)
            config.optString(ClockFontHostSlots.TIME_FAMILY_NAME, "")
                .takeIf { it.isNotBlank() }
        }.onFailure {
            warnOnce("style-file", "failed to read applied clock style file: ${it.message}")
        }.getOrNull()

        fileCachedMtime = mtime
        fileCachedValue = value
        return value
    }

    private fun register(view: TextView) {
        if (!clocks.containsKey(view)) {
            clocks[view] = view.typeface
        }
    }

    private fun isTargetClock(view: TextView): Boolean =
        view.javaClass.name in targetClockNames

    private fun originFor(view: TextView): String {
        val resolved = symbols ?: return "SystemClock"
        return when (view.javaClass.name) {
            resolved.statClockClass?.className -> "StatusBarClock"
            resolved.simpleQsClockClass?.className -> "NotificationClock"
            resolved.keyguardStatusBarClockClass?.className -> "KeyguardStatusBarClock"
            else -> "SystemClock"
        }
    }

    private fun safeHook(
        module: XposedModule,
        method: Executable,
        key: String,
        handler: (XposedInterface.Chain) -> Any?
    ) {
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept(handler)
            logStatus(key, "FOUND", null)
        }.onFailure {
            logStatus(key, "ERROR", it.message ?: it.javaClass.simpleName)
        }
    }

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
