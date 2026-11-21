package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 1 observe-only probe.
 *
 * Hooks only `ContextImpl.createPackageContext(...)`, waits for the original to
 * return, and then records which class loader the clock plugin context exposes
 * and which known symbols resolve against it.
 *
 * It installs **no business hooks**, never changes arguments, and never changes
 * return values.
 *
 * The result is written to three places because the ColorOS main logcat buffer is
 * only 256 KiB and rotates within seconds:
 *  1. android.util.Log (tag RealOS-ClockFontProbe)
 *  2. module.log (persistent LSPosed module log)
 *  3. <host data dir>/app_realos_clockfont_probe/probe.log (durable per-host file)
 *
 * Active only when BuildConfig.CLOCKFONT_STAGE == 1.
 */
internal object ClockFontLoaderProbe {

    private const val TAG = "RealOS-ClockFontProbe"
    private const val PROBE_DIR = "realos_clockfont_probe"
    private const val PROBE_FILE = "probe.log"

    private val PLUGIN_PACKAGES = setOf(
        "com.oplus.keyguard.clock.base",
        "com.oplus.keyguard.personality.clocks"
    )

    private val SYMBOLS = listOf(
        Symbol(
            "com.oplus.keyguard.clock.digital.util.FontTypefaceCache",
            "buildTypeFace",
            Context::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        ),
        Symbol(
            "com.oplus.keyguard.clock.common.util.CommonUtils",
            "buildTypeFace",
            Context::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        ),
        Symbol(
            "com.oplus.keyguard.clock.common.util.CommonUtils",
            "isTunableFont",
            String::class.java
        ),
        Symbol(
            "com.oplus.keyguard.clock.common.util.KStyleUtil",
            "getClockNumFont",
            Context::class.java,
            String::class.java
        ),
        Symbol(
            "com.oplus.keyguard.clock.base.ui.view.ClockContainer",
            "buildTimeTypeface",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        ),
        Symbol(
            "com.oplus.keyguard.clock.digital.util.ext.DigitalClockStyleConfigCompatEx",
            "isTunableFont",
            String::class.java
        ),
        Symbol(
            "com.oplus.keyguard.clock.common.view.font.OplusKeyguardStyleFontSettingPanel",
            "setFontData",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Array<String>::class.java,
            Boolean::class.javaObjectType
        ),
        Symbol(
            "com.oplus.keyguard.clock.common.view.font.OplusKeyguardStyleFontSettingPanel",
            "setFontSelected",
            String::class.java,
            Array<String>::class.java
        )
    )

    private val installed = AtomicBoolean(false)
    private val seen = ConcurrentHashMap.newKeySet<String>()

    /**
     * Safe to call from onPackageReady: it resolves only framework classes
     * (ContextImpl / UserHandle) and installs one framework method hook. It does
     * not load host or plugin classes.
     */
    fun install(module: XposedModule) {
        if (!installed.compareAndSet(false, true)) return
        Log.i(TAG, "install requested")
        module.log(Log.INFO, TAG, "install requested")

        val contextImpl = runCatching { Class.forName("android.app.ContextImpl") }.getOrNull()
        if (contextImpl == null) {
            Log.w(TAG, "ContextImpl not found; probe disabled")
            module.log(Log.WARN, TAG, "ContextImpl not found; probe disabled")
            return
        }

        val main = hookContextMethod(
            module,
            contextImpl,
            "createPackageContext",
            String::class.java,
            Int::class.javaPrimitiveType!!
        )
        val asUser = hookContextMethod(
            module,
            contextImpl,
            "createPackageContextAsUser",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            UserHandle::class.java
        )
        if (!main && !asUser) {
            Log.w(TAG, "no ContextImpl.createPackageContext hook installed; probe disabled")
            module.log(Log.WARN, TAG, "no ContextImpl.createPackageContext hook installed; probe disabled")
        }
    }

    fun reset() {
        installed.set(false)
        seen.clear()
    }

    private fun hookContextMethod(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        vararg params: Class<*>
    ): Boolean {
        val method = runCatching { clazz.getDeclaredMethod(name, *params) }.getOrNull()
        if (method == null) {
            Log.w(TAG, "ContextImpl.$name not found; probe path disabled")
            module.log(Log.WARN, TAG, "ContextImpl.$name not found; probe path disabled")
            return false
        }
        return runCatching {
            method.isAccessible = true
            module.hook(method).intercept { chain -> onContext(module, chain) }
            Log.i(TAG, "probe hook installed: ContextImpl.$name")
            module.log(Log.INFO, TAG, "probe hook installed: ContextImpl.$name")
            true
        }.onFailure {
            Log.w(TAG, "failed to hook ContextImpl.$name", it)
            module.log(Log.WARN, TAG, "failed to hook ContextImpl.$name: ${it.message}")
        }.getOrDefault(false)
    }

    private fun onContext(module: XposedModule, chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val pluginPackage = chain.args.getOrNull(0) as? String ?: return result
        if (pluginPackage !in PLUGIN_PACKAGES) return result

        val context = result as? Context ?: return result
        val loader = context.classLoader
        val hostContext = chain.thisObject as? Context
        val hostPackage = hostContext?.packageName ?: "?"
        val key = "${Process.myPid()}|$hostPackage|$pluginPackage|${System.identityHashCode(loader)}"
        if (!seen.add(key)) return result

        Thread(
            { probe(module, pluginPackage, hostPackage, loader, hostContext) },
            "RealOS-ClockFontProbe"
        ).start()
        return result
    }

    private fun probe(
        module: XposedModule,
        pluginPackage: String,
        hostPackage: String,
        loader: ClassLoader,
        hostContext: Context?
    ) {
        val found = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (symbol in SYMBOLS) {
            val clazz = runCatching { Class.forName(symbol.className, false, loader) }.getOrNull()
            if (clazz == null) {
                missing += symbol.className.substringAfterLast('.')
                continue
            }
            val method = runCatching {
                clazz.getDeclaredMethod(symbol.methodName, *symbol.params)
            }.getOrNull()
            if (method == null) {
                missing += "${symbol.className.substringAfterLast('.')}#${symbol.methodName}"
            } else {
                found += "${symbol.className.substringAfterLast('.')}#${symbol.methodName}"
            }
        }

        val message =
            "probe pid=${Process.myPid()} host=$hostPackage plugin=$pluginPackage " +
                "loader=${loader.javaClass.name}@${Integer.toHexString(System.identityHashCode(loader))} " +
                "found=${found.size} missing=${missing.size}" +
                if (missing.isEmpty()) "" else " missingSymbols=${missing.joinToString()}"

        Log.i(TAG, message)
        module.log(Log.INFO, TAG, message)
        writeProbeFile(hostContext, message)
    }

    private fun writeProbeFile(hostContext: Context?, message: String) {
        val ctx = hostContext ?: return
        runCatching {
            val dir = ctx.getDir(PROBE_DIR, Context.MODE_PRIVATE)
            File(dir, PROBE_FILE).appendText(message + "\n")
        }
    }

    private class Symbol(
        val className: String,
        val methodName: String,
        vararg val params: Class<*>
    )
}
