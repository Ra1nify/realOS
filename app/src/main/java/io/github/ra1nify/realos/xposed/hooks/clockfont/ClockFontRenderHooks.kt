package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.view.View
import io.github.libxposed.api.XposedModule

/** Clock render hooks. */
internal object ClockFontRenderHooks {

    fun install(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        hookFontTypefaceCache(module, loader, symbols)
        hookCommonUtilsTunable(module, loader, symbols)
        hookClockContainer(module, loader, symbols)
        hookDigitalClockCompatTunable(module, loader, symbols)
        hookDigitalClockCompatVariable(module, loader, symbols)
    }

    private fun hookFontTypefaceCache(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.fontTypefaceCacheBuildTypeFace?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "fontTypefaceCacheBuildTypeFace")

        ClockFontHookSupport.safeHook(module, method, "FontTypefaceCache.buildTypeFace") { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(2) as? Number)?.toInt() ?: 600
            val height = (chain.args.getOrNull(3) as? Number)?.toInt() ?: 50
            ClockFontHost.buildTypeface(context, value, weight, height, "FontTypefaceCache")
                ?: chain.proceed()
        }
    }

    private fun hookCommonUtilsTunable(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.commonUtilsIsTunableFont?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "commonUtilsIsTunableFont")

        ClockFontHookSupport.safeHook(module, method, "CommonUtils.isTunableFont") { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.isFlex(value)
        }
    }

    private fun hookClockContainer(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.clockContainerBuildTimeTypeface?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "clockContainerBuildTimeTypeface")

        ClockFontHookSupport.safeHook(module, method, "ClockContainer.buildTimeTypeface") { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(1) as? Number)?.toInt() ?: 600
            val view = chain.thisObject as? View
            ClockFontHost.buildTypeface(view?.context, value, weight, 50, "ClockContainer")
                ?: chain.proceed()
        }
    }

    private fun hookDigitalClockCompatTunable(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.digitalCompatIsTunableFont?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "digitalCompatIsTunableFont")

        ClockFontHookSupport.safeHook(
            module,
            method,
            "DigitalClockStyleConfigCompatEx.isTunableFont"
        ) { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.isFlex(value)
        }
    }

    private fun hookDigitalClockCompatVariable(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.digitalCompatIsVariableFont?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "digitalCompatIsVariableFont")

        ClockFontHookSupport.safeHook(
            module,
            method,
            "DigitalClockStyleConfigCompatEx.isVariableFont"
        ) { chain ->
            val value = chain.args.getOrNull(0) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            // Report the font type, not the weight preference.
            ClockFontHost.isFlex(value)
        }
    }
}
