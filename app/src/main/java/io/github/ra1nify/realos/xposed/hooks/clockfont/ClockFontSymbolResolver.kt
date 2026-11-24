package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.util.AttributeSet
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.ConstructorDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Resolves jadx-verified clock-plugin and SystemUI symbols with cache support. */
internal object ClockFontSymbolResolver {

    private const val TAG = "RealOS-ClockFontSymbols"

    /** Cache rule version. */
    const val PLUGIN_RULE_VERSION = 1
    const val SYSTEM_RULE_VERSION = 1

    private const val PLUGIN_CACHE_PREFS = "realos_clockfont_plugin_symbols"
    private const val SYSTEM_CACHE_PREFS = "realos_clockfont_system_symbols"

    /** Personality clock plugin package. */
    const val TARGET_PLUGIN = "com.oplus.keyguard.personality.clocks"

    // Plugin classes.
    private const val PANEL_CLASS =
        "com.oplus.keyguard.clock.common.view.font.OplusKeyguardStyleFontSettingPanel"
    private const val FONT_LISTENER_INTERFACE =
        "com.oplus.keyguard.clock.common.callback.IOplusKeyguardStyleFontListener"
    private const val FONT_TYPEFACE_CACHE = "com.oplus.keyguard.clock.digital.util.FontTypefaceCache"
    private const val COMMON_UTILS = "com.oplus.keyguard.clock.common.util.CommonUtils"
    private const val KSTYLE_UTIL = "com.oplus.keyguard.clock.common.util.KStyleUtil"
    private const val CLOCK_CONTAINER = "com.oplus.keyguard.clock.base.ui.view.ClockContainer"
    private const val DIGITAL_COMPAT =
        "com.oplus.keyguard.clock.digital.util.ext.DigitalClockStyleConfigCompatEx"
    private const val CLOCK_LAYOUT_PANEL =
        "com.oplus.keyguard.clock.base.widget.stylepanel.ClockLayoutSelectionPanel"
    private const val TEXT_UTILS = "com.oplus.keyguard.clock.text.util.Utils"
    private const val TEXT_TIME_UTILS =
        "com.oplus.keyguard.clock.text.ui.magazine.TextTimeUtils"
    private const val GALLERY_TYPEFACE_CACHE =
        "com.oplus.keyguard.clock.gallery.utils.TypefaceVariantCache"
    private const val GALLERY_STATE =
        "com.oplus.keyguard.clock.gallery.domain.state.SingleClockViewState"
    private const val GALLERY_DEFAULT_VIEW =
        "com.oplus.keyguard.clock.gallery.view.DefaultSingleClockView"
    private const val GALLERY_OLD_VIEW =
        "com.oplus.keyguard.clock.gallery.view.OldSingleClockView"

    // SystemUI classes.
    private const val SYSTEM_CLOCK = "com.android.systemui.statusbar.policy.Clock"
    private const val STAT_CLOCK = "com.oplus.systemui.statusbar.widget.StatClock"
    private const val SIMPLE_QS_CLOCK = "com.oplus.systemui.qs.widget.SimpleQsClock"
    private const val KEYGUARD_STAT_CLOCK =
        "com.oplus.systemui.statusbar.widget.OplusKeyguardStatusBarClock"
    private const val STYLE_RECORD =
        "com.oplus.systemui.keyguard.clockstyle.KeyguardStyleClockConfigRecord"

    private val STYLE_CHANGE_METHOD_NAMES = setOf(
        "recordKeyguardClockStyleData",
        "recordPersonalityData"
    )

    // Plugin symbols.

    fun resolvePlugin(
        module: XposedModule,
        context: Context?,
        includePanel: Boolean,
        pluginContext: Context,
        pluginLoader: ClassLoader
    ): ClockFontPluginSymbols {
        val fingerprint = pluginFingerprint(pluginContext)
        val cache = AntiTearingCache(
            context,
            PLUGIN_CACHE_PREFS,
            PLUGIN_RULE_VERSION,
            tag = TAG
        )

        if (fingerprint.isNotBlank()) {
            val payload = cache.read(fingerprint)
            val cached = payload?.let {
                runCatching { ClockFontPluginSymbols.fromCachePayload(it) }.getOrNull()
            }
            if (cached != null && verifyPluginCached(cached, pluginLoader, includePanel)) {
                module.log(
                    Log.INFO,
                    TAG,
                    "HookPoint[clockfont_plugin_symbols] state=FOUND " +
                        "source=cache panel=$includePanel"
                )
                return cached
            }
            if (payload != null) {
                module.log(Log.WARN, TAG, "clockfont plugin symbol cache verification failed; rescanning")
            }
        }

        val symbols = scanPlugin(pluginLoader, includePanel, fingerprint)
        logStates(module, symbols.statusMap, symbols.scanErrors)

        if (fingerprint.isNotBlank() && symbols.canRender && (!includePanel || symbols.canPanel)) {
            if (cache.write(symbols.toCachePayload(fingerprint))) {
                module.log(
                    Log.INFO,
                    TAG,
                    "persisted clockfont plugin symbols (ruleVersion=$PLUGIN_RULE_VERSION, panel=$includePanel)"
                )
            }
        }
        return symbols
    }

    private class ScanContext(val loader: ClassLoader) {
        val states = mutableMapOf<String, HookPointState>()
        val errors = mutableListOf<String>()

        fun loadClass(name: String): Class<*>? =
            runCatching { loader.loadClass(name) }.getOrNull()

        fun report(key: String, found: Boolean, detail: String) {
            states[key] = if (found) HookPointState.FOUND else HookPointState.MISSING
            if (!found) errors += "$key: $detail"
        }
    }

    private fun scanMethod(
        scan: ScanContext,
        key: String,
        clazz: Class<*>?,
        name: String,
        returnType: Class<*>?,
        vararg params: Class<*>
    ): MethodDescriptor? {
        if (clazz == null) {
            scan.report(key, false, "class missing")
            return null
        }
        val method = findDeclaredMethod(clazz, name, returnType, *params)
        scan.report(key, method != null, "$name signature missing")
        return method?.let { MethodDescriptor.of(it) }
    }

    private fun scanPlugin(
        loader: ClassLoader,
        includePanel: Boolean,
        fingerprint: String
    ): ClockFontPluginSymbols {
        val scan = ScanContext(loader)

        fun loadClass(name: String): Class<*>? = scan.loadClass(name)

        fun report(key: String, found: Boolean, detail: String) =
            scan.report(key, found, detail)

        fun methodPoint(
            key: String,
            clazz: Class<*>?,
            name: String,
            returnType: Class<*>?,
            vararg params: Class<*>
        ): MethodDescriptor? = scanMethod(scan, key, clazz, name, returnType, *params)

        // Render symbols.
        val typefaceCacheClass = loadClass(FONT_TYPEFACE_CACHE)
        val fontTypefaceCacheBuild = methodPoint(
            ClockFontPluginSymbols.KEY_FONT_TYPEFACE_CACHE_BUILD,
            typefaceCacheClass,
            "buildTypeFace",
            Typeface::class.java,
            Context::class.java,
            String::class.java,
            Integer.TYPE,
            Integer.TYPE
        )

        val commonUtilsClass = loadClass(COMMON_UTILS)
        val commonUtilsIsTunable = methodPoint(
            ClockFontPluginSymbols.KEY_COMMON_UTILS_IS_TUNABLE,
            commonUtilsClass,
            "isTunableFont",
            java.lang.Boolean.TYPE,
            String::class.java
        )

        val clockContainerClass = loadClass(CLOCK_CONTAINER)
        val clockContainerBuild = methodPoint(
            ClockFontPluginSymbols.KEY_CLOCK_CONTAINER_BUILD_TYPEFACE,
            clockContainerClass,
            "buildTimeTypeface",
            Typeface::class.java,
            String::class.java,
            Integer.TYPE,
            java.lang.Boolean.TYPE
        )

        val digitalCompatClass = loadClass(DIGITAL_COMPAT)
        val digitalIsTunable = methodPoint(
            ClockFontPluginSymbols.KEY_DIGITAL_COMPAT_IS_TUNABLE,
            digitalCompatClass,
            "isTunableFont",
            java.lang.Boolean.TYPE,
            String::class.java
        )
        val digitalIsVariable = methodPoint(
            ClockFontPluginSymbols.KEY_DIGITAL_COMPAT_IS_VARIABLE,
            digitalCompatClass,
            "isVariableFont",
            java.lang.Boolean.TYPE,
            String::class.java
        )

        val textUtilsBuild = methodPoint(
            ClockFontPluginSymbols.KEY_TEXT_UTILS_BUILD_TYPEFACE,
            loadClass(TEXT_UTILS),
            "buildTypeFace",
            Typeface::class.java,
            Context::class.java,
            String::class.java,
            Integer.TYPE,
            Integer.TYPE
        )

        val textTimeGet = methodPoint(
            ClockFontPluginSymbols.KEY_TEXT_TIME_GET_TYPEFACE,
            loadClass(TEXT_TIME_UTILS),
            "getTextTypeFace",
            Typeface::class.java,
            AssetManager::class.java,
            Integer.TYPE,
            String::class.java
        )

        val galleryCreate = methodPoint(
            ClockFontPluginSymbols.KEY_GALLERY_CREATE_TYPEFACE,
            loadClass(GALLERY_TYPEFACE_CACHE),
            "createNewTypeFace",
            Typeface::class.java,
            Context::class.java,
            String::class.java
        )

        // Text and gallery symbols.
        val textUtilsGet = methodPoint(
            ClockFontPluginSymbols.KEY_TEXT_UTILS_GET_TYPEFACE,
            loadClass(TEXT_UTILS),
            "getTextTypeFace",
            Typeface::class.java,
            AssetManager::class.java,
            Integer.TYPE,
            String::class.java
        )

        val galleryOldView = loadClass(GALLERY_OLD_VIEW)?.let { ClassDescriptor(it.name) }
        report(
            ClockFontPluginSymbols.KEY_GALLERY_OLD_VIEW_CLASS,
            galleryOldView != null,
            "class $GALLERY_OLD_VIEW missing"
        )

        val galleryState = loadClass(GALLERY_STATE)?.let { ClassDescriptor(it.name) }
        report(
            ClockFontPluginSymbols.KEY_GALLERY_STATE_CLASS,
            galleryState != null,
            "class $GALLERY_STATE missing"
        )
        val galleryStateClass = galleryState?.restore(loader)

        val galleryStyleType = methodPoint(
            ClockFontPluginSymbols.KEY_GALLERY_STYLE_TYPE,
            galleryStateClass,
            "getGalleryStyleType",
            Integer.TYPE
        )
        val galleryConfig = methodPoint(
            ClockFontPluginSymbols.KEY_GALLERY_CLOCK_STYLE_CONFIG,
            galleryStateClass,
            "getClockStyleConfig",
            null
        )
        val galleryConfigClass = galleryConfig?.restore(loader)?.returnType
        val galleryFamily = methodPoint(
            ClockFontPluginSymbols.KEY_GALLERY_TIME_FAMILY,
            galleryConfigClass,
            "getTimeFamilyName",
            String::class.java
        )
        val galleryWeight = methodPoint(
            ClockFontPluginSymbols.KEY_GALLERY_TIME_FONT_WEIGHT,
            galleryConfigClass,
            "getTimeFontWeight",
            Integer.TYPE
        )

        val gallerySetFontStyle = if (galleryStateClass != null) {
            listOf(GALLERY_DEFAULT_VIEW, GALLERY_OLD_VIEW)
                .mapNotNull { loadClass(it) }
                .mapNotNull { clazz ->
                    findDeclaredMethod(
                        clazz,
                        "setFontStyle",
                        java.lang.Void.TYPE,
                        galleryStateClass,
                        Typeface::class.java,
                        Typeface::class.java
                    )
                }
                .map { MethodDescriptor.of(it) }
        } else {
            emptyList()
        }
        report(
            ClockFontPluginSymbols.KEY_GALLERY_SET_FONT_STYLE,
            gallerySetFontStyle.isNotEmpty(),
            "setFontStyle missing"
        )

        // Panel symbols (Wallpapers only).
        var panelClass: ClassDescriptor? = null
        var panelSetFontData: MethodDescriptor? = null
        var panelSetFontSelected: MethodDescriptor? = null
        var panelSetSeekBarEnable: MethodDescriptor? = null
        var panelSetOnFontListListener: MethodDescriptor? = null
        var panelListenerInterface: ClassDescriptor? = null
        var panelOnItemSelect: MethodDescriptor? = null
        var commonUtilsBuildTypeFace: MethodDescriptor? = null
        var kStyleGetClockNumFont: MethodDescriptor? = null
        var clockLayoutPanelClass: ClassDescriptor? = null
        var clockLayoutGetTargetTypeFace: MethodDescriptor? = null
        var clockLayoutFontFamily: FieldDescriptor? = null

        if (includePanel) {
            panelClass = loadClass(PANEL_CLASS)?.let { ClassDescriptor(it.name) }
            report(
                ClockFontPluginSymbols.KEY_PANEL_CLASS,
                panelClass != null,
                "class $PANEL_CLASS missing"
            )
            val panel = panelClass?.restore(loader)

            panelSetFontData = methodPoint(
                ClockFontPluginSymbols.KEY_PANEL_SET_FONT_DATA,
                panel,
                "setFontData",
                java.lang.Void.TYPE,
                String::class.java,
                Integer.TYPE,
                Array<String>::class.java,
                java.lang.Boolean.TYPE
            )
            panelSetFontSelected = methodPoint(
                ClockFontPluginSymbols.KEY_PANEL_SET_FONT_SELECTED,
                panel,
                "setFontSelected",
                java.lang.Void.TYPE,
                String::class.java,
                Array<String>::class.java
            )
            panelSetSeekBarEnable = methodPoint(
                ClockFontPluginSymbols.KEY_PANEL_SET_SEEK_BAR_ENABLE,
                panel,
                "setSeekBarEnable",
                java.lang.Void.TYPE,
                java.lang.Boolean.TYPE
            )

            val listenerClass = loadClass(FONT_LISTENER_INTERFACE)
            panelListenerInterface = listenerClass?.let { ClassDescriptor(it.name) }
            report(
                ClockFontPluginSymbols.KEY_PANEL_FONT_LISTENER,
                listenerClass != null,
                "class $FONT_LISTENER_INTERFACE missing"
            )
            if (listenerClass != null) {
                panelSetOnFontListListener = methodPoint(
                    ClockFontPluginSymbols.KEY_PANEL_SET_FONT_LISTENER,
                    panel,
                    "setOnFontListListener",
                    java.lang.Void.TYPE,
                    listenerClass
                )
            }
            // onItemSelect(String, int): match by name and first parameter.
            panelOnItemSelect = listenerClass?.let { clazz ->
                runCatching { clazz.declaredMethods }.getOrNull().orEmpty()
                    .firstOrNull {
                        it.name == "onItemSelect" &&
                            it.parameterTypes.firstOrNull() == String::class.java
                    }
            }?.let { MethodDescriptor.of(it) }
            report(
                ClockFontPluginSymbols.KEY_PANEL_ON_ITEM_SELECT,
                panelOnItemSelect != null,
                "onItemSelect missing"
            )

            commonUtilsBuildTypeFace = methodPoint(
                ClockFontPluginSymbols.KEY_COMMON_UTILS_BUILD_TYPEFACE,
                commonUtilsClass,
                "buildTypeFace",
                Typeface::class.java,
                Context::class.java,
                String::class.java,
                Integer.TYPE,
                Integer.TYPE
            )

            val kStyleClass = loadClass(KSTYLE_UTIL)
            kStyleGetClockNumFont = methodPoint(
                ClockFontPluginSymbols.KEY_KSTYLE_GET_CLOCK_NUM_FONT,
                kStyleClass,
                "getClockNumFont",
                Typeface::class.java,
                Context::class.java,
                String::class.java
            )

            clockLayoutPanelClass = loadClass(CLOCK_LAYOUT_PANEL)?.let { ClassDescriptor(it.name) }
            report(
                ClockFontPluginSymbols.KEY_CLOCK_LAYOUT_PANEL_CLASS,
                clockLayoutPanelClass != null,
                "class $CLOCK_LAYOUT_PANEL missing"
            )
            val layoutPanel = clockLayoutPanelClass?.restore(loader)
            clockLayoutGetTargetTypeFace = methodPoint(
                ClockFontPluginSymbols.KEY_CLOCK_LAYOUT_GET_TYPEFACE,
                layoutPanel,
                "getTargetTypeFace",
                Typeface::class.java
            )
            clockLayoutFontFamily = findField(layoutPanel, "fontFamily", String::class.java)
                ?.let { FieldDescriptor.of(it) }
            report(
                ClockFontPluginSymbols.KEY_CLOCK_LAYOUT_FONT_FAMILY,
                clockLayoutFontFamily != null,
                "field fontFamily missing"
            )
        }

        return ClockFontPluginSymbols(
            ruleVersion = PLUGIN_RULE_VERSION,
            hostFingerprint = fingerprint,
            panelClass = panelClass,
            panelSetFontData = panelSetFontData,
            panelSetFontSelected = panelSetFontSelected,
            panelSetSeekBarEnable = panelSetSeekBarEnable,
            panelSetOnFontListListener = panelSetOnFontListListener,
            panelFontListenerInterface = panelListenerInterface,
            panelFontListenerOnItemSelect = panelOnItemSelect,
            clockLayoutPanelClass = clockLayoutPanelClass,
            clockLayoutPanelGetTargetTypeFace = clockLayoutGetTargetTypeFace,
            clockLayoutPanelFontFamily = clockLayoutFontFamily,
            commonUtilsBuildTypeFace = commonUtilsBuildTypeFace,
            kStyleGetClockNumFont = kStyleGetClockNumFont,
            fontTypefaceCacheBuildTypeFace = fontTypefaceCacheBuild,
            commonUtilsIsTunableFont = commonUtilsIsTunable,
            clockContainerBuildTimeTypeface = clockContainerBuild,
            digitalCompatIsTunableFont = digitalIsTunable,
            digitalCompatIsVariableFont = digitalIsVariable,
            textUtilsBuildTypeFace = textUtilsBuild,
            textUtilsGetTextTypeFace = textUtilsGet,
            textTimeGetTextTypeFace = textTimeGet,
            galleryCreateNewTypeFace = galleryCreate,
            galleryOldViewClass = galleryOldView,
            galleryStateClass = galleryState,
            galleryGetStyleType = galleryStyleType,
            galleryGetClockStyleConfig = galleryConfig,
            galleryGetTimeFamilyName = galleryFamily,
            galleryGetTimeFontWeight = galleryWeight,
            gallerySetFontStyleMethods = gallerySetFontStyle,
            statusMap = scan.states,
            scanErrors = scan.errors
        )
    }

    private fun verifyPluginCached(
        symbols: ClockFontPluginSymbols,
        loader: ClassLoader,
        includePanel: Boolean
    ): Boolean {
        if (!symbols.canRender) return false
        if (includePanel && !symbols.canPanel) return false
        if (symbols.fontTypefaceCacheBuildTypeFace?.restore(loader) == null) return false
        if (includePanel) {
            if (symbols.panelClass?.restore(loader) == null) return false
            if (symbols.panelSetFontData?.restore(loader) == null) return false
            if (symbols.panelSetFontSelected?.restore(loader) == null) return false
        }
        return true
    }

    // SystemUI symbols.

    fun resolveSystem(
        module: XposedModule,
        context: Context?,
        loader: ClassLoader,
        apkPath: String?
    ): ClockFontSystemSymbols {
        val fingerprint = fingerprintOf(apkPath)
        val cache = AntiTearingCache(
            context,
            SYSTEM_CACHE_PREFS,
            SYSTEM_RULE_VERSION,
            tag = TAG
        )

        if (fingerprint.isNotBlank()) {
            val payload = cache.read(fingerprint)
            val cached = payload?.let {
                runCatching { ClockFontSystemSymbols.fromCachePayload(it) }.getOrNull()
            }
            if (cached != null && verifySystemCached(cached, loader)) {
                module.log(
                    Log.INFO,
                    TAG,
                    "HookPoint[clockfont_system_symbols] state=FOUND source=cache"
                )
                return cached
            }
            if (payload != null) {
                module.log(Log.WARN, TAG, "clockfont system symbol cache verification failed; rescanning")
            }
        }

        val symbols = scanSystem(loader, fingerprint)
        logStates(module, symbols.statusMap, symbols.scanErrors)

        if (fingerprint.isNotBlank() && symbols.canApplyClocks) {
            if (cache.write(symbols.toCachePayload(fingerprint))) {
                module.log(
                    Log.INFO,
                    TAG,
                    "persisted clockfont system symbols (ruleVersion=$SYSTEM_RULE_VERSION)"
                )
            }
        }
        return symbols
    }

    private fun scanSystem(loader: ClassLoader, fingerprint: String): ClockFontSystemSymbols {
        val scan = ScanContext(loader)

        fun loadClass(name: String): Class<*>? = scan.loadClass(name)

        fun report(key: String, found: Boolean, detail: String) =
            scan.report(key, found, detail)

        fun methodPoint(
            key: String,
            clazz: Class<*>?,
            name: String,
            returnType: Class<*>?,
            vararg params: Class<*>
        ): MethodDescriptor? = scanMethod(scan, key, clazz, name, returnType, *params)

        val clockClass = loadClass(SYSTEM_CLOCK)
        val clockDesc = clockClass?.let { ClassDescriptor(it.name) }
        report(ClockFontSystemSymbols.KEY_CLOCK_CLASS, clockClass != null, "class $SYSTEM_CLOCK missing")

        val setTypeface = methodPoint(
            ClockFontSystemSymbols.KEY_CLOCK_SET_TYPEFACE,
            clockClass,
            "setTypeface",
            java.lang.Void.TYPE,
            Typeface::class.java
        )
        val updateClock = methodPoint(
            ClockFontSystemSymbols.KEY_CLOCK_UPDATE,
            clockClass,
            "updateClock",
            java.lang.Void.TYPE
        )

        val ctor = clockClass?.let {
            findConstructor(it, Context::class.java, AttributeSet::class.java, Integer.TYPE)
        }?.let { ConstructorDescriptor.of(it) }
        report(ClockFontSystemSymbols.KEY_CLOCK_CTOR, ctor != null, "constructor signature missing")

        val statClock = loadClass(STAT_CLOCK)
        val statClockDesc = statClock?.let { ClassDescriptor(it.name) }
        report(ClockFontSystemSymbols.KEY_STAT_CLOCK_CLASS, statClock != null, "class $STAT_CLOCK missing")

        val simpleQsClock = loadClass(SIMPLE_QS_CLOCK)
        val simpleQsDesc = simpleQsClock?.let { ClassDescriptor(it.name) }
        report(
            ClockFontSystemSymbols.KEY_SIMPLE_QS_CLOCK_CLASS,
            simpleQsClock != null,
            "class $SIMPLE_QS_CLOCK missing"
        )

        val keyguardStatClock = loadClass(KEYGUARD_STAT_CLOCK)
        val keyguardStatDesc = keyguardStatClock?.let { ClassDescriptor(it.name) }
        report(
            ClockFontSystemSymbols.KEY_KEYGUARD_STAT_CLOCK_CLASS,
            keyguardStatClock != null,
            "class $KEYGUARD_STAT_CLOCK missing"
        )

        val styleRecordClass = loadClass(STYLE_RECORD)
        val styleRecordDesc = styleRecordClass?.let { ClassDescriptor(it.name) }
        report(
            ClockFontSystemSymbols.KEY_STYLE_RECORD_CLASS,
            styleRecordClass != null,
            "class $STYLE_RECORD missing"
        )

        val instanceField = runCatching { styleRecordClass?.declaredFields }
            .getOrNull()
            .orEmpty()
            .firstOrNull { it.name == "INSTANCE" && it.type == styleRecordClass }
        val instanceDesc = instanceField?.let { FieldDescriptor.of(it) }
        report(
            ClockFontSystemSymbols.KEY_STYLE_RECORD_INSTANCE,
            instanceField != null,
            "field INSTANCE missing"
        )

        val configGetter = findMethodIncludingInherited(styleRecordClass, "getClockStyleConfig", null)
        val configGetterDesc = configGetter?.let { MethodDescriptor.of(it) }
        report(
            ClockFontSystemSymbols.KEY_STYLE_CONFIG_GETTER,
            configGetter != null,
            "getClockStyleConfig missing"
        )

        val familyGetter = findMethodIncludingInherited(
            configGetter?.returnType,
            "getTimeFamilyName",
            String::class.java
        )
        val familyGetterDesc = familyGetter?.let { MethodDescriptor.of(it) }
        report(
            ClockFontSystemSymbols.KEY_TIME_FAMILY_GETTER,
            familyGetter != null,
            "getTimeFamilyName missing"
        )

        val styleChangeMethods = runCatching { styleRecordClass?.declaredMethods }
            .getOrNull()
            .orEmpty()
            .filter { it.name in STYLE_CHANGE_METHOD_NAMES }
            .map { MethodDescriptor.of(it) }
        report(
            ClockFontSystemSymbols.KEY_STYLE_CHANGE_METHODS,
            styleChangeMethods.isNotEmpty(),
            "style change methods missing"
        )

        return ClockFontSystemSymbols(
            ruleVersion = SYSTEM_RULE_VERSION,
            hostFingerprint = fingerprint,
            clockClass = clockDesc,
            clockSetTypeface = setTypeface,
            clockConstructor = ctor,
            clockUpdateClock = updateClock,
            statClockClass = statClockDesc,
            simpleQsClockClass = simpleQsDesc,
            keyguardStatusBarClockClass = keyguardStatDesc,
            styleRecordClass = styleRecordDesc,
            styleRecordInstance = instanceDesc,
            styleConfigGetter = configGetterDesc,
            timeFamilyNameGetter = familyGetterDesc,
            styleChangeMethods = styleChangeMethods,
            statusMap = scan.states,
            scanErrors = scan.errors
        )
    }

    private fun verifySystemCached(
        symbols: ClockFontSystemSymbols,
        loader: ClassLoader
    ): Boolean {
        if (!symbols.canApplyClocks) return false
        if (symbols.clockClass?.restore(loader) == null) return false
        if (symbols.clockSetTypeface?.restore(loader) == null) return false
        return true
    }

    // Reflection helpers.

    /** Finds a declared method with an exact signature. */
    private fun findDeclaredMethod(
        clazz: Class<*>?,
        name: String,
        returnType: Class<*>?,
        vararg params: Class<*>
    ): Method? {
        if (clazz == null) return null
        return runCatching { clazz.declaredMethods }.getOrNull().orEmpty()
            .firstOrNull {
                it.name == name &&
                    it.parameterTypes.size == params.size &&
                    params.indices.all { index -> sameType(it.parameterTypes[index], params[index]) } &&
                    returnMatches(it.returnType, returnType)
            }
    }

    /** Finds a declared or inherited public method. */
    private fun findMethodIncludingInherited(
        clazz: Class<*>?,
        name: String,
        returnType: Class<*>?,
        vararg params: Class<*>
    ): Method? {
        if (clazz == null) return null
        findDeclaredMethod(clazz, name, returnType, *params)?.let { return it }
        return runCatching { clazz.getMethod(name, *params) }.getOrNull()
            ?.takeIf { returnMatches(it.returnType, returnType) }
    }

    private fun findConstructor(
        clazz: Class<*>,
        vararg params: Class<*>
    ): java.lang.reflect.Constructor<*>? =
        runCatching { clazz.declaredConstructors }.getOrNull().orEmpty().firstOrNull {
            it.parameterTypes.size == params.size &&
                params.indices.all { index -> sameType(it.parameterTypes[index], params[index]) }
        }

    private fun findField(clazz: Class<*>?, name: String, type: Class<*>): Field? {
        var current = clazz
        while (current != null && current != Any::class.java) {
            runCatching { current.declaredFields }.getOrNull().orEmpty()
                .firstOrNull { it.name == name && it.type == type }
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun returnMatches(actual: Class<*>, expected: Class<*>?): Boolean =
        expected == null || sameType(actual, expected)

    /** Compares primitive and wrapper types. */
    private fun sameType(actual: Class<*>, expected: Class<*>): Boolean {
        if (actual == expected) return true
        return wrapperOf(actual) == wrapperOf(expected)
    }

    private fun wrapperOf(type: Class<*>): Class<*>? = when (type) {
        Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
        Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
        Char::class.javaPrimitiveType -> Char::class.javaObjectType
        Short::class.javaPrimitiveType -> Short::class.javaObjectType
        Int::class.javaPrimitiveType -> Int::class.javaObjectType
        Long::class.javaPrimitiveType -> Long::class.javaObjectType
        Float::class.javaPrimitiveType -> Float::class.javaObjectType
        Double::class.javaPrimitiveType -> Double::class.javaObjectType
        else -> type
    }

    // Fingerprint and logging.

    private fun pluginFingerprint(pluginContext: Context): String {
        val path = runCatching { pluginContext.applicationInfo.sourceDir }.getOrNull()
        return fingerprintOf(path)
    }

    private fun fingerprintOf(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val file = File(path)
        return if (file.exists()) {
            AntiTearingCache.fingerprintOf(file.absolutePath, file.lastModified(), file.length())
        } else {
            path
        }
    }

    private fun logStates(
        module: XposedModule,
        states: Map<String, HookPointState>,
        errors: List<String>
    ) {
        states.forEach { (key, state) ->
            val detail = errors.firstOrNull { it.startsWith("$key:") }
                ?.removePrefix("$key:")
                ?.trim()
            val message = buildString {
                append("HookPoint[$key] state=$state")
                if (!detail.isNullOrBlank()) append(" ($detail)")
            }
            module.log(if (state == HookPointState.FOUND) Log.INFO else Log.WARN, TAG, message)
        }
        if (errors.isNotEmpty()) {
            module.log(Log.WARN, TAG, "clockfont symbol scan errors: ${errors.joinToString("; ")}")
        }
    }
}
