package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.os.UserHandle
import android.util.Log as AndroidLog
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Business hooks for ColorOS clock hosts (`com.oplus.wallpapers`, `com.android.systemui`,
 * `com.android.launcher`, `com.oplus.aod`).
 *
 * Intercepts `ContextImpl.createPackageContext(...)` for `com.oplus.keyguard.personality.clocks`.
 * Once the plugin Context is obtained, it:
 * 1. Attaches the host-process [ClockFontHost] client for non-blocking font resolution.
 * 2. Idempotently hooks the core symbols to inject custom fonts into the setting panel (Wallpapers)
 *    and render their typefaces in previews and lockscreen clocks (Wallpapers, SystemUI, etc.).
 */
internal object ClockFontPluginHook {

    private const val TAG = "RealOS-ClockFontPlugin"
    private const val TARGET_PLUGIN = "com.oplus.keyguard.personality.clocks"

    private val contextHookInstalled = AtomicBoolean(false)
    private val pluginHooksInstalled = AtomicBoolean(false)
    private val installedHookKeys = ConcurrentHashMap.newKeySet<String>()
    private val selectedFonts = Collections.synchronizedMap(WeakHashMap<Any, String>())

    fun install(module: XposedModule) {
        if (!contextHookInstalled.compareAndSet(false, true)) return
        Log.i(TAG, "clockfont plugin hook install requested")
        module.log(AndroidLog.INFO, TAG, "clockfont plugin hook install requested")

        val contextImpl = runCatching { Class.forName("android.app.ContextImpl") }.getOrNull()
        if (contextImpl == null) {
            Log.w(TAG, "ContextImpl not found; clockfont plugin hook disabled")
            module.log(AndroidLog.WARN, TAG, "ContextImpl not found; clockfont plugin hook disabled")
            return
        }

        hookContextMethod(
            module,
            contextImpl,
            "createPackageContext",
            String::class.java,
            Int::class.javaPrimitiveType!!
        )
        hookContextMethod(
            module,
            contextImpl,
            "createPackageContextAsUser",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            UserHandle::class.java
        )
    }

    fun reset() {
        contextHookInstalled.set(false)
        pluginHooksInstalled.set(false)
        installedHookKeys.clear()
        selectedFonts.clear()
        ClockFontHost.invalidate()
    }

    private fun hookContextMethod(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        vararg params: Class<*>
    ) {
        val method = runCatching { clazz.getDeclaredMethod(name, *params) }.getOrNull() ?: return
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept { chain -> onContext(module, chain) }
            Log.i(TAG, "context hook installed: ContextImpl.$name")
            module.log(AndroidLog.INFO, TAG, "context hook installed: ContextImpl.$name")
        }.onFailure {
            Log.w(TAG, "failed to hook ContextImpl.$name: ${it.message}")
            module.log(AndroidLog.WARN, TAG, "failed to hook ContextImpl.$name: ${it.message}")
        }
    }

    private fun onContext(module: XposedModule, chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val pluginPackage = chain.args.getOrNull(0) as? String ?: return result
        if (pluginPackage != TARGET_PLUGIN) return result

        val pluginContext = result as? Context ?: return result
        val hostContext = (chain.thisObject as? Context) ?: pluginContext
        ClockFontHost.attach(hostContext)

        val loader = pluginContext.classLoader
        if (pluginHooksInstalled.compareAndSet(false, true)) {
            installPluginHooks(module, loader)
        }
        return result
    }

    private fun installPluginHooks(module: XposedModule, loader: ClassLoader) {
        Log.i(TAG, "installing plugin hooks for $TARGET_PLUGIN")
        module.log(AndroidLog.INFO, TAG, "installing plugin hooks for $TARGET_PLUGIN")

        hookFontPanelData(module, loader)
        hookFontPanelSelected(module, loader)
        hookFontPanelSeekBarEnable(module, loader)
        hookFontPanelListListener(module, loader)
        hookFontTypefaceCache(module, loader)
        hookCommonUtilsTypeface(module, loader)
        hookCommonUtilsTunable(module, loader)
        hookKStyleUtil(module, loader)
        hookClockContainer(module, loader)
        hookDigitalClockCompatTunable(module, loader)
        hookDigitalClockCompatVariable(module, loader)
        hookClockLayoutSelectionPanel(module, loader)
    }

    private fun hookFontPanelData(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.common.view.font.OplusKeyguardStyleFontSettingPanel"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(
            clazz,
            "setFontData",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Array<String>::class.java,
            Boolean::class.javaObjectType
        ) ?: return logMissingMethod(module, className, "setFontData")

        safeHook(module, method, "OplusKeyguardStyleFontSettingPanel.setFontData") { chain ->
            val layoutType = (chain.args.getOrNull(1) as? Number)?.toInt() ?: -1
            // 0 = LAYOUT_TYPE_TIME (time font), 1 = LAYOUT_TYPE_WEEKLY (week/date font)
            if (layoutType != 0) return@safeHook chain.proceed()

            val custom = ClockFontHost.listValues()
            if (custom.isEmpty()) return@safeHook chain.proceed()

            val args = chain.args.toTypedArray()
            val original = (args.getOrNull(2) as? Array<*>)?.filterIsInstance<String>() ?: emptyList()
            val showLast = (args.getOrNull(3) as? Boolean) ?: true
            val augmented = ClockFontListAugmenter.augment(original, custom, showLast)

            args[2] = augmented.toTypedArray()
            chain.proceed(args)
        }
    }

    private fun hookFontPanelSelected(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.common.view.font.OplusKeyguardStyleFontSettingPanel"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(
            clazz,
            "setFontSelected",
            String::class.java,
            Array<String>::class.java
        ) ?: return logMissingMethod(module, className, "setFontSelected")

        safeHook(module, method, "OplusKeyguardStyleFontSettingPanel.setFontSelected") { chain ->
            val panel = chain.thisObject
            val initialSelected = chain.args.getOrNull(0) as? String
            if (panel != null && initialSelected != null) {
                selectedFonts[panel] = initialSelected
            }

            val custom = ClockFontHost.listValues()
            if (custom.isEmpty()) {
                val result = chain.proceed()
                applyCustomFontSeekBarIfApplicable(panel, initialSelected)
                return@safeHook result
            }

            val args = chain.args.toTypedArray()
            val original = (args.getOrNull(1) as? Array<*>)?.filterIsInstance<String>() ?: emptyList()
            val additions = custom.distinct().filterNot { it in original }
            if (additions.isNotEmpty()) {
                args[1] = (original + additions).toTypedArray()
            }
            val result = chain.proceed(args)
            applyCustomFontSeekBarIfApplicable(panel, initialSelected)
            result
        }
    }

    private fun hookFontPanelSeekBarEnable(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.common.view.font.OplusKeyguardStyleFontSettingPanel"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(clazz, "setSeekBarEnable", Boolean::class.javaPrimitiveType!!)
            ?: return logMissingMethod(module, className, "setSeekBarEnable")

        safeHook(module, method, "OplusKeyguardStyleFontSettingPanel.setSeekBarEnable") { chain ->
            val panel = chain.thisObject
            val currentFont = panel?.let { selectedFonts[it] }
            if (ClockFontHost.isCustom(currentFont)) {
                val dynamic = ClockFontHost.isDynamicWeight(currentFont)
                val args = chain.args.toTypedArray()
                args[0] = dynamic
                return@safeHook chain.proceed(args)
            }
            // For stock fonts: do not intercept or modify; let stock ColorOS logic run unchanged.
            chain.proceed()
        }
    }

    private fun hookFontPanelListListener(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.common.view.font.OplusKeyguardStyleFontSettingPanel"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val listenerInterface = resolveClass(loader, "com.oplus.keyguard.clock.common.callback.IOplusKeyguardStyleFontListener")
            ?: return logMissingClass(module, "IOplusKeyguardStyleFontListener")
        val method = resolveMethod(clazz, "setOnFontListListener", listenerInterface)
            ?: return logMissingMethod(module, className, "setOnFontListListener")

        safeHook(module, method, "OplusKeyguardStyleFontSettingPanel.setOnFontListListener") { chain ->
            val panel = chain.thisObject ?: return@safeHook chain.proceed()
            val originalListener = chain.args.getOrNull(0) ?: return@safeHook chain.proceed()

            val proxy = Proxy.newProxyInstance(
                loader,
                arrayOf(listenerInterface)
            ) { _, invokedMethod, args ->
                if (invokedMethod.name == "onItemSelect") {
                    val selected = args?.getOrNull(0) as? String
                    if (selected != null) {
                        selectedFonts[panel] = selected
                    }
                    val res = runCatching {
                        if (args == null) {
                            invokedMethod.invoke(originalListener)
                        } else {
                            invokedMethod.invoke(originalListener, *args)
                        }
                    }.getOrNull()
                    applyCustomFontSeekBarIfApplicable(panel, selected)
                    res
                } else {
                    if (args == null) {
                        invokedMethod.invoke(originalListener)
                    } else {
                        invokedMethod.invoke(originalListener, *args)
                    }
                }
            }

            val newArgs = chain.args.toTypedArray()
            newArgs[0] = proxy
            chain.proceed(newArgs)
        }
    }

    private fun applyCustomFontSeekBarIfApplicable(panel: Any?, font: String?) {
        if (panel == null || font == null || !ClockFontHost.isCustom(font)) return
        val dynamic = ClockFontHost.isDynamicWeight(font)
        runCatching {
            val setEnableMethod = panel.javaClass.getDeclaredMethod("setSeekBarEnable", Boolean::class.javaPrimitiveType)
            setEnableMethod.isAccessible = true
            setEnableMethod.invoke(panel, dynamic)
        }
    }

    private fun hookFontTypefaceCache(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.digital.util.FontTypefaceCache"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(
            clazz,
            "buildTypeFace",
            Context::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        ) ?: return logMissingMethod(module, className, "buildTypeFace")

        safeHook(module, method, "FontTypefaceCache.buildTypeFace") { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(2) as? Number)?.toInt() ?: 600
            val height = (chain.args.getOrNull(3) as? Number)?.toInt() ?: 50
            ClockFontHost.buildTypeface(context, value, weight, height) ?: chain.proceed()
        }
    }

    private fun hookCommonUtilsTypeface(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.common.util.CommonUtils"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(
            clazz,
            "buildTypeFace",
            Context::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        ) ?: return logMissingMethod(module, className, "buildTypeFace")

        safeHook(module, method, "CommonUtils.buildTypeFace") { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(2) as? Number)?.toInt() ?: 600
            val height = (chain.args.getOrNull(3) as? Number)?.toInt() ?: 50
            ClockFontHost.buildTypeface(context, value, weight, height) ?: chain.proceed()
        }
    }

    private fun hookCommonUtilsTunable(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.common.util.CommonUtils"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(clazz, "isTunableFont", String::class.java)
            ?: return logMissingMethod(module, className, "isTunableFont")

        safeHook(module, method, "CommonUtils.isTunableFont") { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.isFlex(value)
        }
    }

    private fun hookKStyleUtil(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.common.util.KStyleUtil"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(clazz, "getClockNumFont", Context::class.java, String::class.java)
            ?: return logMissingMethod(module, className, "getClockNumFont")

        safeHook(module, method, "KStyleUtil.getClockNumFont") { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.buildTypeface(context, value, 600, 50) ?: chain.proceed()
        }
    }

    private fun hookClockContainer(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.base.ui.view.ClockContainer"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(
            clazz,
            "buildTimeTypeface",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        ) ?: return logMissingMethod(module, className, "buildTimeTypeface")

        safeHook(module, method, "ClockContainer.buildTimeTypeface") { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(1) as? Number)?.toInt() ?: 600
            val view = chain.thisObject as? View
            ClockFontHost.buildTypeface(view?.context, value, weight, 50) ?: chain.proceed()
        }
    }

    private fun hookDigitalClockCompatTunable(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.digital.util.ext.DigitalClockStyleConfigCompatEx"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(clazz, "isTunableFont", String::class.java)
            ?: return logMissingMethod(module, className, "isTunableFont")

        safeHook(module, method, "DigitalClockStyleConfigCompatEx.isTunableFont") { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.isFlex(value)
        }
    }

    private fun hookDigitalClockCompatVariable(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.digital.util.ext.DigitalClockStyleConfigCompatEx"
        val clazz = resolveClass(loader, className) ?: return logMissingClass(module, className)
        val method = resolveMethod(clazz, "isVariableFont", String::class.java)
            ?: return logMissingMethod(module, className, "isVariableFont")

        safeHook(module, method, "DigitalClockStyleConfigCompatEx.isVariableFont") { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.isFlex(value)
        }
    }

    private fun hookClockLayoutSelectionPanel(module: XposedModule, loader: ClassLoader) {
        val className = "com.oplus.keyguard.clock.base.widget.stylepanel.ClockLayoutSelectionPanel"
        val clazz = resolveClass(loader, className) ?: return
        val method = resolveMethod(clazz, "getTargetTypeFace") ?: return

        safeHook(module, method, "ClockLayoutSelectionPanel.getTargetTypeFace") { chain ->
            val panel = chain.thisObject as? View ?: return@safeHook chain.proceed()
            val field = runCatching { clazz.getDeclaredField("fontFamily").apply { isAccessible = true } }.getOrNull()
            val value = field?.get(panel) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.buildTypeface(panel.context, value, 500, 50) ?: chain.proceed()
        }
    }

    private fun logMissingClass(module: XposedModule, className: String) {
        Log.w(TAG, "class not found in $TARGET_PLUGIN: $className")
        module.log(AndroidLog.WARN, TAG, "class not found in $TARGET_PLUGIN: $className")
    }

    private fun logMissingMethod(module: XposedModule, className: String, methodName: String) {
        Log.w(TAG, "method not found: $className.$methodName")
        module.log(AndroidLog.WARN, TAG, "method not found: $className.$methodName")
    }

    private fun safeHook(
        module: XposedModule,
        method: Method,
        key: String,
        handler: (XposedInterface.Chain) -> Any?
    ) {
        if (!installedHookKeys.add(key)) return
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept(handler)
            Log.i(TAG, "hook installed: $key")
            module.log(AndroidLog.INFO, TAG, "hook installed: $key")
        }.onFailure {
            installedHookKeys.remove(key)
            Log.w(TAG, "failed to hook $key: ${it.message}")
            module.log(AndroidLog.WARN, TAG, "failed to hook $key: ${it.message}")
        }
    }

    private fun resolveClass(loader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    private fun resolveMethod(clazz: Class<*>, name: String, vararg params: Class<*>): Method? =
        runCatching { clazz.getDeclaredMethod(name, *params) }.getOrNull()
}
