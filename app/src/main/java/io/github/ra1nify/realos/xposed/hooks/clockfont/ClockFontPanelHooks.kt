package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.clockfont.ClockFontContract
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/** Wallpapers clock-font panel hooks. */
internal object ClockFontPanelHooks {

    private const val TAG = "RealOS-ClockFontPlugin"

    private val selectedFonts = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val panelFontLists = Collections.synchronizedMap(WeakHashMap<Any, List<String>>())
    private val panelLayoutTypes = Collections.synchronizedMap(WeakHashMap<Any, Int>())

    @Volatile
    private var seekBarEnableMethod: Method? = null

    fun install(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        hookFontPanelData(module, loader, symbols)
        hookFontPanelSelected(module, loader, symbols)
        hookFontPanelSeekBarEnable(module, loader, symbols)
        hookFontPanelListListener(module, loader, symbols)
        hookCommonUtilsTypeface(module, loader, symbols)
        hookKStyleUtil(module, loader, symbols)
        hookClockLayoutSelectionPanel(module, loader, symbols)
    }

    fun reset() {
        selectedFonts.clear()
        panelFontLists.clear()
        panelLayoutTypes.clear()
        seekBarEnableMethod = null
    }

    private fun hookFontPanelData(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val panelClass = symbols.panelClass?.restore(loader)
        val method = symbols.panelSetFontData?.restore(loader)
        if (panelClass == null || method == null) {
            return ClockFontHookSupport.logMissing(module, "panelSetFontData")
        }

        ClockFontHookSupport.safeHook(
            module,
            method,
            "OplusKeyguardStyleFontSettingPanel.setFontData"
        ) { chain ->
            val panel = chain.thisObject
            val layoutType = (chain.args.getOrNull(1) as? Number)?.toInt() ?: -1
            if (panel != null) {
                panelLayoutTypes[panel] = layoutType
                if (layoutType != 0) panelFontLists.remove(panel)
            }
            // 0 = time font, 1 = week/date font.
            if (layoutType != 0) return@safeHook chain.proceed()

            val args = chain.args.toTypedArray()
            val original = (args.getOrNull(2) as? Array<*>)?.filterIsInstance<String>()
                ?: emptyList()
            val showLast = (args.getOrNull(3) as? Boolean) ?: true
            val custom = ClockFontHost.listValues()
            val augmented = ClockFontListAugmenter.augment(original, custom, showLast)
            if (panel != null) {
                // Mirror the visible list for setFontSelected.
                panelFontLists[panel] = if (showLast) augmented else augmented.dropLast(1)
            }
            if (custom.isEmpty()) return@safeHook chain.proceed()

            args[2] = augmented.toTypedArray()
            chain.proceed(args)
        }
    }

    private fun hookFontPanelSelected(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val panelClass = symbols.panelClass?.restore(loader)
        val method = symbols.panelSetFontSelected?.restore(loader)
        if (panelClass == null || method == null) {
            return ClockFontHookSupport.logMissing(module, "panelSetFontSelected")
        }

        ClockFontHookSupport.safeHook(
            module,
            method,
            "OplusKeyguardStyleFontSettingPanel.setFontSelected"
        ) { chain ->
            val panel = chain.thisObject
            val initialSelected = chain.args.getOrNull(0) as? String
            if (panel != null && initialSelected != null) {
                selectedFonts[panel] = initialSelected
            }

            val layout = panel?.let { panelLayoutTypes[it] }
            if (layout != null && layout != 0) return@safeHook chain.proceed()

            // Normalize variable:/fixed: prefixes.
            val normalized = normalizeCustomValue(initialSelected)
            val args = chain.args.toTypedArray()
            if (normalized != null) args[0] = normalized

            // Reuse the visible list from setFontData.
            val stored = panel?.let { panelFontLists[it] }
            if (stored != null) {
                args[1] = stored.toTypedArray()
                val result = chain.proceed(args)
                applyCustomFontSeekBarIfApplicable(panel, normalized ?: initialSelected)
                return@safeHook result
            }

            val custom = ClockFontHost.listValues()
            if (custom.isEmpty()) {
                val result = chain.proceed(args)
                applyCustomFontSeekBarIfApplicable(panel, normalized ?: initialSelected)
                return@safeHook result
            }

            val original = (args.getOrNull(1) as? Array<*>)?.filterIsInstance<String>()
                ?: emptyList()
            val additions = custom.distinct().filterNot { it in original }
            if (additions.isNotEmpty()) {
                args[1] = (original + additions).toTypedArray()
            }
            val result = chain.proceed(args)
            applyCustomFontSeekBarIfApplicable(panel, normalized ?: initialSelected)
            result
        }
    }

    private fun hookFontPanelSeekBarEnable(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val panelClass = symbols.panelClass?.restore(loader)
        val method = symbols.panelSetSeekBarEnable?.restore(loader)
        if (panelClass == null || method == null) {
            return ClockFontHookSupport.logMissing(module, "panelSetSeekBarEnable")
        }
        seekBarEnableMethod = method

        ClockFontHookSupport.safeHook(
            module,
            method,
            "OplusKeyguardStyleFontSettingPanel.setSeekBarEnable"
        ) { chain ->
            val panel = chain.thisObject
            val currentFont = panel?.let { selectedFonts[it] }
            if (ClockFontHost.isCustom(currentFont)) {
                val dynamic = ClockFontHost.isDynamicWeight(currentFont)
                val args = chain.args.toTypedArray()
                args[0] = dynamic
                return@safeHook chain.proceed(args)
            }
            // Leave stock fonts unchanged.
            chain.proceed()
        }
    }

    private fun hookFontPanelListListener(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val panelClass = symbols.panelClass?.restore(loader)
        val listenerInterface = symbols.panelFontListenerInterface?.restore(loader)
        val method = symbols.panelSetOnFontListListener?.restore(loader)
        if (panelClass == null || listenerInterface == null || method == null) {
            return ClockFontHookSupport.logMissing(module, "panelSetOnFontListListener")
        }
        val onItemSelectName = symbols.panelFontListenerOnItemSelect?.methodName
        if (onItemSelectName == null) {
            Log.w(TAG, "onItemSelect descriptor missing; font selection tracking disabled")
        }

        ClockFontHookSupport.safeHook(
            module,
            method,
            "OplusKeyguardStyleFontSettingPanel.setOnFontListListener"
        ) { chain ->
            val panel = chain.thisObject ?: return@safeHook chain.proceed()
            val originalListener = chain.args.getOrNull(0) ?: return@safeHook chain.proceed()

            val proxy = Proxy.newProxyInstance(
                loader,
                arrayOf(listenerInterface)
            ) { _, invokedMethod, args ->
                fun invokeOriginal(): Any? =
                    if (args == null) {
                        invokedMethod.invoke(originalListener)
                    } else {
                        invokedMethod.invoke(originalListener, *args)
                    }

                if (onItemSelectName != null && invokedMethod.name == onItemSelectName) {
                    val selected = args?.getOrNull(0) as? String
                    if (selected != null) {
                        selectedFonts[panel] = selected
                    }
                    val result = runCatching { invokeOriginal() }.getOrNull()
                    applyCustomFontSeekBarIfApplicable(panel, selected)
                    result
                } else {
                    invokeOriginal()
                }
            }

            val newArgs = chain.args.toTypedArray()
            newArgs[0] = proxy
            chain.proceed(newArgs)
        }
    }

    private fun normalizeCustomValue(value: String?): String? {
        val id = ClockFontContract.idFromValue(value) ?: return value
        return ClockFontContract.valueFor(id)
    }

    private fun applyCustomFontSeekBarIfApplicable(panel: Any?, font: String?) {
        if (panel == null || font == null || !ClockFontHost.isCustom(font)) return
        val dynamic = ClockFontHost.isDynamicWeight(font)
        val method = seekBarEnableMethod ?: return
        runCatching {
            method.isAccessible = true
            method.invoke(panel, dynamic)
        }
    }

    private fun hookCommonUtilsTypeface(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.commonUtilsBuildTypeFace?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "commonUtilsBuildTypeFace")

        ClockFontHookSupport.safeHook(module, method, "CommonUtils.buildTypeFace") { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            val weight = (chain.args.getOrNull(2) as? Number)?.toInt() ?: 600
            val height = (chain.args.getOrNull(3) as? Number)?.toInt() ?: 50
            ClockFontHost.buildTypeface(context, value, weight, height, "CommonUtils")
                ?: chain.proceed()
        }
    }

    private fun hookKStyleUtil(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val method = symbols.kStyleGetClockNumFont?.restore(loader)
            ?: return ClockFontHookSupport.logMissing(module, "kStyleGetClockNumFont")

        ClockFontHookSupport.safeHook(module, method, "KStyleUtil.getClockNumFont") { chain ->
            val context = chain.args.getOrNull(0) as? Context
            val value = chain.args.getOrNull(1) as? String
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.buildTypeface(context, value, 600, 50, "KStyleUtil")
                ?: chain.proceed()
        }
    }

    private fun hookClockLayoutSelectionPanel(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols
    ) {
        val panelClass = symbols.clockLayoutPanelClass?.restore(loader)
        val method = symbols.clockLayoutPanelGetTargetTypeFace?.restore(loader)
        val fontFamilyField = symbols.clockLayoutPanelFontFamily?.restore(loader)
        if (panelClass == null || method == null || fontFamilyField == null) {
            return ClockFontHookSupport.logMissing(module, "clockLayoutPanel")
        }

        ClockFontHookSupport.safeHook(
            module,
            method,
            "ClockLayoutSelectionPanel.getTargetTypeFace"
        ) { chain ->
            val panel = chain.thisObject as? View ?: return@safeHook chain.proceed()
            val value = runCatching { fontFamilyField.get(panel) as? String }.getOrNull()
            if (!ClockFontHost.isCustom(value)) return@safeHook chain.proceed()
            ClockFontHost.buildTypeface(
                panel.context,
                value,
                500,
                50,
                "ClockLayoutSelectionPanel"
            ) ?: chain.proceed()
        }
    }
}
