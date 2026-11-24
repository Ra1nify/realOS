package io.github.ra1nify.realos.xposed.hooks.clockfont

import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.ClassSymbol
import io.github.ra1nify.realos.xposed.symbol.ConstructorDescriptor
import io.github.ra1nify.realos.xposed.symbol.ConstructorSymbol
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldSymbol
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import io.github.ra1nify.realos.xposed.symbol.MethodSymbol
import io.github.ra1nify.realos.xposed.symbol.SymbolDescriptor

/** Resolved symbols for the personality clock plugin. */
data class ClockFontPluginSymbols(
    val ruleVersion: Int = 0,
    val hostFingerprint: String = "",

    // Panel symbols (Wallpapers only).
    val panelClass: ClassDescriptor? = null,
    val panelSetFontData: MethodDescriptor? = null,
    val panelSetFontSelected: MethodDescriptor? = null,
    val panelSetSeekBarEnable: MethodDescriptor? = null,
    val panelSetOnFontListListener: MethodDescriptor? = null,
    val panelFontListenerInterface: ClassDescriptor? = null,
    val panelFontListenerOnItemSelect: MethodDescriptor? = null,
    val clockLayoutPanelClass: ClassDescriptor? = null,
    val clockLayoutPanelGetTargetTypeFace: MethodDescriptor? = null,
    val clockLayoutPanelFontFamily: FieldDescriptor? = null,
    val commonUtilsBuildTypeFace: MethodDescriptor? = null,
    val kStyleGetClockNumFont: MethodDescriptor? = null,

    // Render symbols (all hosts).
    val fontTypefaceCacheBuildTypeFace: MethodDescriptor? = null,
    val commonUtilsIsTunableFont: MethodDescriptor? = null,
    val clockContainerBuildTimeTypeface: MethodDescriptor? = null,
    val digitalCompatIsTunableFont: MethodDescriptor? = null,
    val digitalCompatIsVariableFont: MethodDescriptor? = null,

    // Text and gallery symbols.
    val textUtilsBuildTypeFace: MethodDescriptor? = null,
    val textUtilsGetTextTypeFace: MethodDescriptor? = null,
    val textTimeGetTextTypeFace: MethodDescriptor? = null,
    val galleryCreateNewTypeFace: MethodDescriptor? = null,

    // Gallery font assignment.
    val galleryOldViewClass: ClassDescriptor? = null,
    val galleryStateClass: ClassDescriptor? = null,
    val galleryGetStyleType: MethodDescriptor? = null,
    val galleryGetClockStyleConfig: MethodDescriptor? = null,
    val galleryGetTimeFamilyName: MethodDescriptor? = null,
    val galleryGetTimeFontWeight: MethodDescriptor? = null,
    val gallerySetFontStyleMethods: List<MethodDescriptor> = emptyList(),

    val statusMap: Map<String, HookPointState> = emptyMap(),
    val scanErrors: List<String> = emptyList()
) {

    /** Render contract. */
    val canRender: Boolean
        get() = fontTypefaceCacheBuildTypeFace != null

    /** Panel contract. */
    val canPanel: Boolean
        get() = panelClass != null &&
            panelSetFontData != null &&
            panelSetFontSelected != null

    fun toCachePayload(fingerprint: String): AntiTearingCache.CachePayload =
        AntiTearingCache.CachePayload(
            fingerprint = fingerprint,
            ruleVersion = ruleVersion,
            symbols = buildMap<String, SymbolDescriptor> {
                panelClass?.let { put(KEY_PANEL_CLASS, ClassSymbol(it)) }
                panelSetFontData?.let { put(KEY_PANEL_SET_FONT_DATA, MethodSymbol(it)) }
                panelSetFontSelected?.let { put(KEY_PANEL_SET_FONT_SELECTED, MethodSymbol(it)) }
                panelSetSeekBarEnable?.let { put(KEY_PANEL_SET_SEEK_BAR_ENABLE, MethodSymbol(it)) }
                panelSetOnFontListListener?.let { put(KEY_PANEL_SET_FONT_LISTENER, MethodSymbol(it)) }
                panelFontListenerInterface?.let { put(KEY_PANEL_FONT_LISTENER, ClassSymbol(it)) }
                panelFontListenerOnItemSelect?.let { put(KEY_PANEL_ON_ITEM_SELECT, MethodSymbol(it)) }
                clockLayoutPanelClass?.let { put(KEY_CLOCK_LAYOUT_PANEL_CLASS, ClassSymbol(it)) }
                clockLayoutPanelGetTargetTypeFace?.let {
                    put(KEY_CLOCK_LAYOUT_GET_TYPEFACE, MethodSymbol(it))
                }
                clockLayoutPanelFontFamily?.let { put(KEY_CLOCK_LAYOUT_FONT_FAMILY, FieldSymbol(it)) }
                commonUtilsBuildTypeFace?.let { put(KEY_COMMON_UTILS_BUILD_TYPEFACE, MethodSymbol(it)) }
                kStyleGetClockNumFont?.let { put(KEY_KSTYLE_GET_CLOCK_NUM_FONT, MethodSymbol(it)) }
                fontTypefaceCacheBuildTypeFace?.let {
                    put(KEY_FONT_TYPEFACE_CACHE_BUILD, MethodSymbol(it))
                }
                commonUtilsIsTunableFont?.let { put(KEY_COMMON_UTILS_IS_TUNABLE, MethodSymbol(it)) }
                clockContainerBuildTimeTypeface?.let {
                    put(KEY_CLOCK_CONTAINER_BUILD_TYPEFACE, MethodSymbol(it))
                }
                digitalCompatIsTunableFont?.let {
                    put(KEY_DIGITAL_COMPAT_IS_TUNABLE, MethodSymbol(it))
                }
                digitalCompatIsVariableFont?.let {
                    put(KEY_DIGITAL_COMPAT_IS_VARIABLE, MethodSymbol(it))
                }
                textUtilsBuildTypeFace?.let {
                    put(KEY_TEXT_UTILS_BUILD_TYPEFACE, MethodSymbol(it))
                }
                textUtilsGetTextTypeFace?.let {
                    put(KEY_TEXT_UTILS_GET_TYPEFACE, MethodSymbol(it))
                }
                textTimeGetTextTypeFace?.let {
                    put(KEY_TEXT_TIME_GET_TYPEFACE, MethodSymbol(it))
                }
                galleryCreateNewTypeFace?.let {
                    put(KEY_GALLERY_CREATE_TYPEFACE, MethodSymbol(it))
                }
                galleryOldViewClass?.let { put(KEY_GALLERY_OLD_VIEW_CLASS, ClassSymbol(it)) }
                galleryStateClass?.let { put(KEY_GALLERY_STATE_CLASS, ClassSymbol(it)) }
                galleryGetStyleType?.let { put(KEY_GALLERY_STYLE_TYPE, MethodSymbol(it)) }
                galleryGetClockStyleConfig?.let {
                    put(KEY_GALLERY_CLOCK_STYLE_CONFIG, MethodSymbol(it))
                }
                galleryGetTimeFamilyName?.let {
                    put(KEY_GALLERY_TIME_FAMILY, MethodSymbol(it))
                }
                galleryGetTimeFontWeight?.let {
                    put(KEY_GALLERY_TIME_FONT_WEIGHT, MethodSymbol(it))
                }
                gallerySetFontStyleMethods.forEachIndexed { index, method ->
                    put("$GALLERY_SET_FONT_STYLE_PREFIX$index", MethodSymbol(method))
                }
            }
        )

    companion object {
        const val KEY_PANEL_CLASS = "clockfont_plugin_panel_class"
        const val KEY_PANEL_SET_FONT_DATA = "clockfont_plugin_panel_set_font_data"
        const val KEY_PANEL_SET_FONT_SELECTED = "clockfont_plugin_panel_set_font_selected"
        const val KEY_PANEL_SET_SEEK_BAR_ENABLE = "clockfont_plugin_panel_set_seek_bar_enable"
        const val KEY_PANEL_SET_FONT_LISTENER = "clockfont_plugin_panel_set_font_listener"
        const val KEY_PANEL_FONT_LISTENER = "clockfont_plugin_panel_font_listener"
        const val KEY_PANEL_ON_ITEM_SELECT = "clockfont_plugin_panel_on_item_select"
        const val KEY_CLOCK_LAYOUT_PANEL_CLASS = "clockfont_plugin_clock_layout_panel_class"
        const val KEY_CLOCK_LAYOUT_GET_TYPEFACE = "clockfont_plugin_clock_layout_get_typeface"
        const val KEY_CLOCK_LAYOUT_FONT_FAMILY = "clockfont_plugin_clock_layout_font_family"
        const val KEY_COMMON_UTILS_BUILD_TYPEFACE = "clockfont_plugin_common_utils_build_typeface"
        const val KEY_KSTYLE_GET_CLOCK_NUM_FONT = "clockfont_plugin_kstyle_get_clock_num_font"
        const val KEY_FONT_TYPEFACE_CACHE_BUILD = "clockfont_plugin_font_typeface_cache_build"
        const val KEY_COMMON_UTILS_IS_TUNABLE = "clockfont_plugin_common_utils_is_tunable"
        const val KEY_CLOCK_CONTAINER_BUILD_TYPEFACE =
            "clockfont_plugin_clock_container_build_typeface"
        const val KEY_DIGITAL_COMPAT_IS_TUNABLE = "clockfont_plugin_digital_compat_is_tunable"
        const val KEY_DIGITAL_COMPAT_IS_VARIABLE = "clockfont_plugin_digital_compat_is_variable"
        const val KEY_TEXT_UTILS_BUILD_TYPEFACE = "clockfont_plugin_text_utils_build_typeface"
        const val KEY_TEXT_UTILS_GET_TYPEFACE = "clockfont_plugin_text_utils_get_typeface"
        const val KEY_TEXT_TIME_GET_TYPEFACE = "clockfont_plugin_text_time_get_typeface"
        const val KEY_GALLERY_CREATE_TYPEFACE = "clockfont_plugin_gallery_create_typeface"
        const val KEY_GALLERY_OLD_VIEW_CLASS = "clockfont_plugin_gallery_old_view_class"
        const val KEY_GALLERY_STATE_CLASS = "clockfont_plugin_gallery_state_class"
        const val KEY_GALLERY_STYLE_TYPE = "clockfont_plugin_gallery_style_type"
        const val KEY_GALLERY_CLOCK_STYLE_CONFIG = "clockfont_plugin_gallery_clock_style_config"
        const val KEY_GALLERY_TIME_FAMILY = "clockfont_plugin_gallery_time_family"
        const val KEY_GALLERY_TIME_FONT_WEIGHT = "clockfont_plugin_gallery_time_font_weight"
        const val KEY_GALLERY_SET_FONT_STYLE = "clockfont_plugin_gallery_set_font_style"
        const val GALLERY_SET_FONT_STYLE_PREFIX = "clockfont_plugin_gallery_set_font_style_"

        fun fromCachePayload(payload: AntiTearingCache.CachePayload): ClockFontPluginSymbols {
            val symbols = payload.symbols
            fun cls(key: String) = (symbols[key] as? ClassSymbol)?.descriptor
            fun method(key: String) = (symbols[key] as? MethodSymbol)?.descriptor
            fun field(key: String) = (symbols[key] as? FieldSymbol)?.descriptor

            val galleryFontStyleMethods = symbols.entries
                .filter { it.key.startsWith(GALLERY_SET_FONT_STYLE_PREFIX) }
                .sortedBy {
                    it.key.removePrefix(GALLERY_SET_FONT_STYLE_PREFIX).toIntOrNull()
                        ?: Int.MAX_VALUE
                }
                .mapNotNull { (it.value as? MethodSymbol)?.descriptor }

            val result = ClockFontPluginSymbols(
                ruleVersion = payload.ruleVersion,
                hostFingerprint = payload.fingerprint,
                panelClass = cls(KEY_PANEL_CLASS),
                panelSetFontData = method(KEY_PANEL_SET_FONT_DATA),
                panelSetFontSelected = method(KEY_PANEL_SET_FONT_SELECTED),
                panelSetSeekBarEnable = method(KEY_PANEL_SET_SEEK_BAR_ENABLE),
                panelSetOnFontListListener = method(KEY_PANEL_SET_FONT_LISTENER),
                panelFontListenerInterface = cls(KEY_PANEL_FONT_LISTENER),
                panelFontListenerOnItemSelect = method(KEY_PANEL_ON_ITEM_SELECT),
                clockLayoutPanelClass = cls(KEY_CLOCK_LAYOUT_PANEL_CLASS),
                clockLayoutPanelGetTargetTypeFace = method(KEY_CLOCK_LAYOUT_GET_TYPEFACE),
                clockLayoutPanelFontFamily = field(KEY_CLOCK_LAYOUT_FONT_FAMILY),
                commonUtilsBuildTypeFace = method(KEY_COMMON_UTILS_BUILD_TYPEFACE),
                kStyleGetClockNumFont = method(KEY_KSTYLE_GET_CLOCK_NUM_FONT),
                fontTypefaceCacheBuildTypeFace = method(KEY_FONT_TYPEFACE_CACHE_BUILD),
                commonUtilsIsTunableFont = method(KEY_COMMON_UTILS_IS_TUNABLE),
                clockContainerBuildTimeTypeface = method(KEY_CLOCK_CONTAINER_BUILD_TYPEFACE),
                digitalCompatIsTunableFont = method(KEY_DIGITAL_COMPAT_IS_TUNABLE),
                digitalCompatIsVariableFont = method(KEY_DIGITAL_COMPAT_IS_VARIABLE),
                textUtilsBuildTypeFace = method(KEY_TEXT_UTILS_BUILD_TYPEFACE),
                textUtilsGetTextTypeFace = method(KEY_TEXT_UTILS_GET_TYPEFACE),
                textTimeGetTextTypeFace = method(KEY_TEXT_TIME_GET_TYPEFACE),
                galleryCreateNewTypeFace = method(KEY_GALLERY_CREATE_TYPEFACE),
                galleryOldViewClass = cls(KEY_GALLERY_OLD_VIEW_CLASS),
                galleryStateClass = cls(KEY_GALLERY_STATE_CLASS),
                galleryGetStyleType = method(KEY_GALLERY_STYLE_TYPE),
                galleryGetClockStyleConfig = method(KEY_GALLERY_CLOCK_STYLE_CONFIG),
                galleryGetTimeFamilyName = method(KEY_GALLERY_TIME_FAMILY),
                galleryGetTimeFontWeight = method(KEY_GALLERY_TIME_FONT_WEIGHT),
                gallerySetFontStyleMethods = galleryFontStyleMethods
            )
            return result.copy(statusMap = result.derivedStatusMap())
        }
    }

    private fun derivedStatusMap(): Map<String, HookPointState> = buildMap {
        put(KEY_PANEL_CLASS, stateOf(panelClass != null))
        put(KEY_PANEL_SET_FONT_DATA, stateOf(panelSetFontData != null))
        put(KEY_PANEL_SET_FONT_SELECTED, stateOf(panelSetFontSelected != null))
        put(KEY_PANEL_SET_SEEK_BAR_ENABLE, stateOf(panelSetSeekBarEnable != null))
        put(KEY_PANEL_SET_FONT_LISTENER, stateOf(panelSetOnFontListListener != null))
        put(KEY_PANEL_FONT_LISTENER, stateOf(panelFontListenerInterface != null))
        put(KEY_PANEL_ON_ITEM_SELECT, stateOf(panelFontListenerOnItemSelect != null))
        put(KEY_CLOCK_LAYOUT_PANEL_CLASS, stateOf(clockLayoutPanelClass != null))
        put(KEY_CLOCK_LAYOUT_GET_TYPEFACE, stateOf(clockLayoutPanelGetTargetTypeFace != null))
        put(KEY_CLOCK_LAYOUT_FONT_FAMILY, stateOf(clockLayoutPanelFontFamily != null))
        put(KEY_COMMON_UTILS_BUILD_TYPEFACE, stateOf(commonUtilsBuildTypeFace != null))
        put(KEY_KSTYLE_GET_CLOCK_NUM_FONT, stateOf(kStyleGetClockNumFont != null))
        put(KEY_FONT_TYPEFACE_CACHE_BUILD, stateOf(fontTypefaceCacheBuildTypeFace != null))
        put(KEY_COMMON_UTILS_IS_TUNABLE, stateOf(commonUtilsIsTunableFont != null))
        put(KEY_CLOCK_CONTAINER_BUILD_TYPEFACE, stateOf(clockContainerBuildTimeTypeface != null))
        put(KEY_DIGITAL_COMPAT_IS_TUNABLE, stateOf(digitalCompatIsTunableFont != null))
        put(KEY_DIGITAL_COMPAT_IS_VARIABLE, stateOf(digitalCompatIsVariableFont != null))
        put(KEY_TEXT_UTILS_BUILD_TYPEFACE, stateOf(textUtilsBuildTypeFace != null))
        put(KEY_TEXT_UTILS_GET_TYPEFACE, stateOf(textUtilsGetTextTypeFace != null))
        put(KEY_TEXT_TIME_GET_TYPEFACE, stateOf(textTimeGetTextTypeFace != null))
        put(KEY_GALLERY_CREATE_TYPEFACE, stateOf(galleryCreateNewTypeFace != null))
        put(KEY_GALLERY_OLD_VIEW_CLASS, stateOf(galleryOldViewClass != null))
        put(KEY_GALLERY_STATE_CLASS, stateOf(galleryStateClass != null))
        put(KEY_GALLERY_STYLE_TYPE, stateOf(galleryGetStyleType != null))
        put(KEY_GALLERY_CLOCK_STYLE_CONFIG, stateOf(galleryGetClockStyleConfig != null))
        put(KEY_GALLERY_TIME_FAMILY, stateOf(galleryGetTimeFamilyName != null))
        put(KEY_GALLERY_TIME_FONT_WEIGHT, stateOf(galleryGetTimeFontWeight != null))
        put(KEY_GALLERY_SET_FONT_STYLE, stateOf(gallerySetFontStyleMethods.isNotEmpty()))
    }

    private fun stateOf(found: Boolean): HookPointState =
        if (found) HookPointState.FOUND else HookPointState.MISSING
}

/** Resolved symbols for SystemUI clock hooks. */
data class ClockFontSystemSymbols(
    val ruleVersion: Int = 0,
    val hostFingerprint: String = "",

    val clockClass: ClassDescriptor? = null,
    val clockSetTypeface: MethodDescriptor? = null,
    val clockConstructor: ConstructorDescriptor? = null,
    val clockUpdateClock: MethodDescriptor? = null,

    val statClockClass: ClassDescriptor? = null,
    val simpleQsClockClass: ClassDescriptor? = null,
    val keyguardStatusBarClockClass: ClassDescriptor? = null,

    val styleRecordClass: ClassDescriptor? = null,
    val styleRecordInstance: FieldDescriptor? = null,
    val styleConfigGetter: MethodDescriptor? = null,
    val timeFamilyNameGetter: MethodDescriptor? = null,
    val styleChangeMethods: List<MethodDescriptor> = emptyList(),

    val statusMap: Map<String, HookPointState> = emptyMap(),
    val scanErrors: List<String> = emptyList()
) {

    /** Clock application contract. */
    val canApplyClocks: Boolean
        get() = clockClass != null && clockSetTypeface != null

    /** Target clock class names. */
    val targetClockClassNames: Set<String>
        get() = setOfNotNull(
            statClockClass?.className,
            simpleQsClockClass?.className,
            keyguardStatusBarClockClass?.className
        )

    fun toCachePayload(fingerprint: String): AntiTearingCache.CachePayload =
        AntiTearingCache.CachePayload(
            fingerprint = fingerprint,
            ruleVersion = ruleVersion,
            symbols = buildMap<String, SymbolDescriptor> {
                clockClass?.let { put(KEY_CLOCK_CLASS, ClassSymbol(it)) }
                clockSetTypeface?.let { put(KEY_CLOCK_SET_TYPEFACE, MethodSymbol(it)) }
                clockConstructor?.let { put(KEY_CLOCK_CTOR, ConstructorSymbol(it)) }
                clockUpdateClock?.let { put(KEY_CLOCK_UPDATE, MethodSymbol(it)) }
                statClockClass?.let { put(KEY_STAT_CLOCK_CLASS, ClassSymbol(it)) }
                simpleQsClockClass?.let { put(KEY_SIMPLE_QS_CLOCK_CLASS, ClassSymbol(it)) }
                keyguardStatusBarClockClass?.let {
                    put(KEY_KEYGUARD_STAT_CLOCK_CLASS, ClassSymbol(it))
                }
                styleRecordClass?.let { put(KEY_STYLE_RECORD_CLASS, ClassSymbol(it)) }
                styleRecordInstance?.let { put(KEY_STYLE_RECORD_INSTANCE, FieldSymbol(it)) }
                styleConfigGetter?.let { put(KEY_STYLE_CONFIG_GETTER, MethodSymbol(it)) }
                timeFamilyNameGetter?.let { put(KEY_TIME_FAMILY_GETTER, MethodSymbol(it)) }
                styleChangeMethods.forEachIndexed { index, method ->
                    put("$STYLE_CHANGE_METHOD_KEY_PREFIX$index", MethodSymbol(method))
                }
            }
        )

    companion object {
        const val KEY_CLOCK_CLASS = "clockfont_system_clock_class"
        const val KEY_CLOCK_SET_TYPEFACE = "clockfont_system_clock_set_typeface"
        const val KEY_CLOCK_CTOR = "clockfont_system_clock_ctor"
        const val KEY_CLOCK_UPDATE = "clockfont_system_clock_update"
        const val KEY_STAT_CLOCK_CLASS = "clockfont_system_stat_clock_class"
        const val KEY_SIMPLE_QS_CLOCK_CLASS = "clockfont_system_simple_qs_clock_class"
        const val KEY_KEYGUARD_STAT_CLOCK_CLASS = "clockfont_system_keyguard_stat_clock_class"
        const val KEY_STYLE_RECORD_CLASS = "clockfont_system_style_record_class"
        const val KEY_STYLE_RECORD_INSTANCE = "clockfont_system_style_record_instance"
        const val KEY_STYLE_CONFIG_GETTER = "clockfont_system_style_config_getter"
        const val KEY_TIME_FAMILY_GETTER = "clockfont_system_time_family_getter"
        const val KEY_STYLE_CHANGE_METHODS = "clockfont_system_style_change_methods"
        const val STYLE_CHANGE_METHOD_KEY_PREFIX = "clockfont_system_style_change_"

        fun fromCachePayload(payload: AntiTearingCache.CachePayload): ClockFontSystemSymbols {
            val symbols = payload.symbols
            fun cls(key: String) = (symbols[key] as? ClassSymbol)?.descriptor
            fun method(key: String) = (symbols[key] as? MethodSymbol)?.descriptor
            fun ctor(key: String) = (symbols[key] as? ConstructorSymbol)?.descriptor
            fun field(key: String) = (symbols[key] as? FieldSymbol)?.descriptor

            val styleMethods = symbols.entries
                .filter { it.key.startsWith(STYLE_CHANGE_METHOD_KEY_PREFIX) }
                .sortedBy {
                    it.key.removePrefix(STYLE_CHANGE_METHOD_KEY_PREFIX).toIntOrNull()
                        ?: Int.MAX_VALUE
                }
                .mapNotNull { (it.value as? MethodSymbol)?.descriptor }

            val result = ClockFontSystemSymbols(
                ruleVersion = payload.ruleVersion,
                hostFingerprint = payload.fingerprint,
                clockClass = cls(KEY_CLOCK_CLASS),
                clockSetTypeface = method(KEY_CLOCK_SET_TYPEFACE),
                clockConstructor = ctor(KEY_CLOCK_CTOR),
                clockUpdateClock = method(KEY_CLOCK_UPDATE),
                statClockClass = cls(KEY_STAT_CLOCK_CLASS),
                simpleQsClockClass = cls(KEY_SIMPLE_QS_CLOCK_CLASS),
                keyguardStatusBarClockClass = cls(KEY_KEYGUARD_STAT_CLOCK_CLASS),
                styleRecordClass = cls(KEY_STYLE_RECORD_CLASS),
                styleRecordInstance = field(KEY_STYLE_RECORD_INSTANCE),
                styleConfigGetter = method(KEY_STYLE_CONFIG_GETTER),
                timeFamilyNameGetter = method(KEY_TIME_FAMILY_GETTER),
                styleChangeMethods = styleMethods
            )
            return result.copy(statusMap = result.derivedStatusMap())
        }
    }

    private fun derivedStatusMap(): Map<String, HookPointState> = buildMap {
        put(KEY_CLOCK_CLASS, stateOf(clockClass != null))
        put(KEY_CLOCK_SET_TYPEFACE, stateOf(clockSetTypeface != null))
        put(KEY_CLOCK_CTOR, stateOf(clockConstructor != null))
        put(KEY_CLOCK_UPDATE, stateOf(clockUpdateClock != null))
        put(KEY_STAT_CLOCK_CLASS, stateOf(statClockClass != null))
        put(KEY_SIMPLE_QS_CLOCK_CLASS, stateOf(simpleQsClockClass != null))
        put(KEY_KEYGUARD_STAT_CLOCK_CLASS, stateOf(keyguardStatusBarClockClass != null))
        put(KEY_STYLE_RECORD_CLASS, stateOf(styleRecordClass != null))
        put(KEY_STYLE_RECORD_INSTANCE, stateOf(styleRecordInstance != null))
        put(KEY_STYLE_CONFIG_GETTER, stateOf(styleConfigGetter != null))
        put(KEY_TIME_FAMILY_GETTER, stateOf(timeFamilyNameGetter != null))
        put(KEY_STYLE_CHANGE_METHODS, stateOf(styleChangeMethods.isNotEmpty()))
    }

    private fun stateOf(found: Boolean): HookPointState =
        if (found) HookPointState.FOUND else HookPointState.MISSING
}
