package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.view.View
import io.github.libxposed.api.XposedModule

/** Gallery clock hooks. */
internal object ClockFontGalleryHooks {

    fun install(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        hookGalleryTypeface(module, loader, symbols)
        hookGallerySetFontStyle(module, loader, symbols)
    }

    private fun hookGalleryTypeface(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.galleryCreateNewTypeFace?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "galleryCreateNewTypeFace")

        ClockFontHookSupport.safeHook(
            module,
            method,
            "TypefaceVariantCache.createNewTypeFace"
        ) { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.buildTypeface(
                context,
                value,
                500,
                50,
                "GalleryTypefaceVariantCache"
            ) ?: chain.proceed()
        }
    }

    private fun hookGallerySetFontStyle(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val stateClass = symbols.galleryStateClass?.restore(loader)
        val styleTypeMethod = symbols.galleryGetStyleType?.restore(loader)
        val configMethod = symbols.galleryGetClockStyleConfig?.restore(loader)
        val familyMethod = symbols.galleryGetTimeFamilyName?.restore(loader)
            ?: configMethod?.returnType?.let {
                runCatching { it.getMethod("getTimeFamilyName") }.getOrNull()
            }
        val weightMethod = symbols.galleryGetTimeFontWeight?.restore(loader)
            ?: configMethod?.returnType?.let {
                runCatching { it.getMethod("getTimeFontWeight") }.getOrNull()
            }

        val oldViewName = symbols.galleryOldViewClass?.className
        val hasDefaultView = symbols.gallerySetFontStyleMethods.any { it.className != oldViewName }

        if (stateClass == null ||
            configMethod == null ||
            familyMethod == null ||
            weightMethod == null ||
            symbols.gallerySetFontStyleMethods.isEmpty() ||
            (hasDefaultView && styleTypeMethod == null)
        ) {
            return ClockFontHookSupport.logMissing(module, "gallerySetFontStyle")
        }
        symbols.gallerySetFontStyleMethods.forEachIndexed { index, descriptor ->
            val method = descriptor.restore(loader) ?: return@forEachIndexed
            val isOldView = descriptor.className == oldViewName
            val hookKey = "GalleryClockView.setFontStyle#" +
                "${descriptor.className.substringAfterLast('.')}#$index"

            ClockFontHookSupport.safeHook(module, method, hookKey) { chain ->
                val state = chain.args.getOrNull(0) ?: return@safeHook chain.proceed()
                val config = runCatching { configMethod.invoke(state) }.getOrNull()
                val family = runCatching {
                    config?.let { familyMethod.invoke(it) } as? String
                }.getOrNull()
                if (!ClockFontHost.isCustom(family)) return@safeHook chain.proceed()

                val weight = runCatching {
                    config?.let { weightMethod.invoke(it) } as? Int
                }.getOrNull() ?: 600
                val view = chain.thisObject as? View
                val custom = ClockFontHost.buildTypeface(
                    view?.context ?: ClockFontHost.context(),
                    family,
                    weight,
                    50,
                    "GalleryClockView"
                ) ?: return@safeHook chain.proceed()

                val args = chain.args.toTypedArray()
                if (isOldView) {
                    // Old view: arg 1 applies to time/date/week.
                    args[1] = custom
                } else {
                    // Default view: style 0 uses arg 1, otherwise arg 2.
                    val style = styleTypeMethod?.let {
                        runCatching { it.invoke(state) as? Int }.getOrNull()
                    } ?: return@safeHook chain.proceed()
                    if (style == 0) args[1] = custom else args[2] = custom
                }
                chain.proceed(args)
            }
        }
    }
}
