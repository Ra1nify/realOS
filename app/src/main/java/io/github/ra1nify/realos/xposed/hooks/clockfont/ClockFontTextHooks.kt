package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/** Text clock hooks. */
internal object ClockFontTextHooks {

    fun install(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        hookTextUtils(module, loader, symbols)
        hookTextUtilsGetTypeFace(module, loader, symbols)
        hookTextTimeUtils(module, loader, symbols)
    }

    private fun hookTextUtils(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.textUtilsBuildTypeFace?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "textUtilsBuildTypeFace")

        ClockFontHookSupport.safeHook(module, method, "TextUtils.buildTypeFace") { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(2) as? Number)?.toInt() ?: 600
            val height = (chain.args.getOrNull(3) as? Number)?.toInt() ?: 50
            ClockFontHost.buildTypeface(context, value, weight, height, "TextUtils")
                ?: chain.proceed()
        }
    }

    private fun hookTextUtilsGetTypeFace(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.textUtilsGetTextTypeFace?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "textUtilsGetTextTypeFace")
        hookAssetManagerTypeface(
            module,
            method,
            "TextUtils.getTextTypeFace",
            "TextUtilsPreview"
        )
    }

    private fun hookTextTimeUtils(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.textTimeGetTextTypeFace?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "textTimeGetTextTypeFace")
        hookAssetManagerTypeface(
            module,
            method,
            "TextTimeUtils.getTextTypeFace",
            "TextTimeUtils"
        )
    }

    /** Handles AssetManager + weight + family helpers. */
    private fun hookAssetManagerTypeface(
        module: XposedModule,
        method: Method,
        hookKey: String,
        origin: String
    ) {
        ClockFontHookSupport.safeHook(module, method, hookKey) { chain ->
            val value = chain.args.getOrNull(2) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(1) as? Number)?.toInt() ?: 600
            ClockFontHost.buildTypeface(
                ClockFontHost.context(),
                value,
                weight,
                50,
                origin
            ) ?: chain.proceed()
        }
    }
}
