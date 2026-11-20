package io.github.ra1nify.realos.xposed.hooks.navigation

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.ConstructorDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * SystemUI symbol resolver (Layer 1).
 *
 * Scans and caches method, field, and class descriptors for SystemUI hooks.
 * Employs [AntiTearingCache] for fast, non-blocking startup.
 */
object SystemUiSymbolResolver {

    private const val TAG = "RealOS-SysUiResolver"
    const val RULE_VERSION = 2
    private const val CACHE_PREFS = "realos_systemui_symbols"

    // ── Known Host Class Names ──
    private const val CLASS_SIDE_GESTURE_NAV_VIEW =
        "com.oplus.systemui.navigationbar.gesture.sidegesture.view.SideGestureNavView"
    private const val CLASS_SIDE_GESTURE_VIEW_MANAGER =
        "com.oplus.systemui.navigationbar.gesture.sidegesture.view.SideGestureViewManager"
    private const val CLASS_BASE_BEZIER_CALCULATOR =
        "com.oplus.systemui.navigationbar.gesture.sidegesture.view.animcalculator.BaseBezierCalculator"
    private const val CLASS_VIBRATE_OBSERVER =
        "com.oplus.systemui.navigationbar.observer.SwipeSideGestureBackVibrateObserver"
    private const val CLASS_GESTURE_UI_THREAD_MANAGER =
        "com.oplus.systemui.navigationbar.gesture.manager.GestureUIThreadManager"

    private const val CLASS_NAV_BAR_VIEW =
        "com.android.systemui.navigationbar.views.NavigationBarView"
    private const val CLASS_KEY_BUTTON_DRAWABLE =
        "com.android.systemui.navigationbar.views.buttons.KeyButtonDrawable"
    private const val CLASS_SHADOW_DRAWABLE_STATE =
        "com.android.systemui.navigationbar.views.buttons.KeyButtonDrawable\$ShadowDrawableState"
    private const val CLASS_BUTTON_DISPATCHER =
        "com.android.systemui.navigationbar.views.buttons.ButtonDispatcher"

    private val HANDLE_CLASS_CANDIDATES = listOf(
        "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle",
        "com.android.systemui.navigationbar.gestural.NavigationHandle"
    )

    fun resolve(module: XposedModule, context: Context?, classLoader: ClassLoader, apkPath: String?): SystemUiSymbols {
        val fingerprint = buildFingerprint(apkPath)
        val cache = AntiTearingCache(context, CACHE_PREFS, RULE_VERSION, tag = TAG)

        if (fingerprint.isNotBlank()) {
            val cachedPayload = cache.read(fingerprint)
            if (cachedPayload != null) {
                runCatching {
                    val cachedSymbols = SystemUiSymbols.fromCachePayload(cachedPayload)
                    module.log(Log.INFO, TAG, "Symbol cache hit (SystemUI, fingerprint=$fingerprint)")
                    return cachedSymbols
                }.onFailure {
                    module.log(Log.WARN, TAG, "Failed to deserialize cached SystemUI symbols, rescanning", it)
                }
            }
        }

        module.log(Log.INFO, TAG, "Cache miss; performing structural symbol scan for SystemUI")
        val symbols = scan(module, classLoader, fingerprint)

        if (fingerprint.isNotBlank() && symbols.statusMap.values.any { it == HookPointState.FOUND }) {
            val written = cache.write(symbols.toCachePayload(fingerprint))
            if (written) {
                module.log(Log.INFO, TAG, "Persisted SystemUI symbols to cache (ruleVersion=$RULE_VERSION)")
            }
        }

        return symbols
    }

    private fun scan(module: XposedModule, classLoader: ClassLoader, fingerprint: String): SystemUiSymbols {
        val states = mutableMapOf<String, HookPointState>()
        val scanErrors = mutableListOf<String>()

        fun logState(key: String, state: HookPointState, detail: String = "") {
            states[key] = state
            val msg = "HookPoint[$key] state=$state${if (detail.isNotEmpty()) " detail=$detail" else ""}"
            if (state == HookPointState.FOUND) {
                module.log(Log.INFO, TAG, msg)
            } else {
                module.log(Log.WARN, TAG, msg)
            }
        }

        fun safeLoadClass(name: String): Class<*>? = runCatching { classLoader.loadClass(name) }.getOrNull()

        /** Required class; a load failure is recorded in scanErrors with the class name. */
        fun requiredClass(name: String): Class<*>? {
            val c = runCatching { classLoader.loadClass(name) }.getOrNull()
            if (c == null) scanErrors.add("Required class not loaded: $name")
            return c
        }

        // ── 1. Side Gesture Back ──
        val navViewClass = requiredClass(CLASS_SIDE_GESTURE_NAV_VIEW)
        val navViewDesc = navViewClass?.let { ClassDescriptor(it.name) }
        logState(KEY_GESTURE_NAV_VIEW, if (navViewClass != null && View::class.java.isAssignableFrom(navViewClass)) HookPointState.FOUND else HookPointState.MISSING)

        val setBackIconMethod = navViewClass?.declaredMethods?.firstOrNull { m ->
            m.name == "setBackIcon" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Bitmap::class.java
        }?.let { MethodDescriptor.of(it) }
        logState(KEY_SET_BACK_ICON, if (setBackIconMethod != null) HookPointState.FOUND else HookPointState.MISSING)

        val paintFields = navViewClass?.declaredFields?.filter {
            it.type == Paint::class.java && !Modifier.isStatic(it.modifiers)
        } ?: emptyList()

        // Icon Paint: matches by name first, otherwise by the structural rule "unique
        // after excluding bezier/border"; the match must remain **unique**.
        val remainingPaints = paintFields.filter {
            !it.name.contains("bezier", true) && !it.name.contains("border", true)
        }
        val iconPaintField = paintFields.filter { it.name.contains("icon", ignoreCase = true) }
            .singleOrNull()
            ?: remainingPaints.singleOrNull()
        if (iconPaintField == null) {
            scanErrors.add(
                "iconPaint candidates not unique or missing: paints=${paintFields.size} remaining=${remainingPaints.size}"
            )
        }
        val iconPaintDesc = iconPaintField?.let { FieldDescriptor.of(it) }
        logState(KEY_ICON_PAINT, if (iconPaintDesc != null) HookPointState.FOUND else HookPointState.MISSING)

        val bezierPaintDesc = paintFields.firstOrNull { it.name.contains("bezier", true) }?.let { FieldDescriptor.of(it) }
        val borderPaintDesc = paintFields.firstOrNull { it.name.contains("border", true) }?.let { FieldDescriptor.of(it) }

        val positionField = navViewClass?.declaredFields?.firstOrNull {
            it.type == Integer.TYPE && it.name.contains("position", true)
        }?.let { FieldDescriptor.of(it) }

        val hasDownEventField = navViewClass?.declaredFields?.firstOrNull {
            it.type == java.lang.Boolean.TYPE && it.name.contains("hasDownEvent", true)
        }?.let { FieldDescriptor.of(it) }

        val lastGestureStateField = navViewClass?.declaredFields?.firstOrNull {
            it.type == Integer.TYPE && it.name.contains("lastGestureState", true)
        }?.let { FieldDescriptor.of(it) }

        val gestureStateField = navViewClass?.declaredFields?.firstOrNull {
            it.type == Integer.TYPE && it.name.equals("gestureState", true)
        }?.let { FieldDescriptor.of(it) }

        val navViewOnDrawMethod = navViewClass?.declaredMethods?.firstOrNull {
            it.name == "onDraw" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Canvas::class.java
        }?.let { MethodDescriptor.of(it) }

        val onUpdateGestureViewMethod = navViewClass?.declaredMethods?.firstOrNull {
            it.name == "onUpdateGestureView" && it.parameterTypes.size == 1
        }
        val onUpdateGestureViewDesc = onUpdateGestureViewMethod?.let { MethodDescriptor.of(it) }
        logState(KEY_ON_UPDATE_GESTURE_VIEW, if (onUpdateGestureViewDesc != null) HookPointState.FOUND else HookPointState.MISSING)

        val containerClass = onUpdateGestureViewMethod?.parameterTypes?.get(0)
        val containerStateMethod = containerClass?.methods?.firstOrNull {
            it.returnType == Integer.TYPE && it.parameterTypes.isEmpty() &&
                (it.name.contains("state", true) || it.name.contains("getState", true))
        }?.let { MethodDescriptor.of(it) }

        val containerPointMethod = containerClass?.methods?.firstOrNull {
            (it.returnType.name.contains("PointF") || it.returnType.name.contains("Point")) &&
                it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val getBezierCalcMethod = navViewClass?.declaredMethods?.firstOrNull {
            it.name == "getBezierCalculator" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        // ── Side Gesture View Manager ──
        val managerClass = requiredClass(CLASS_SIDE_GESTURE_VIEW_MANAGER)
        val managerDesc = managerClass?.let { ClassDescriptor(it.name) }
        logState(KEY_GESTURE_VIEW_MANAGER, if (managerClass != null) HookPointState.FOUND else HookPointState.MISSING)

        val managerCreateViewMethod = managerClass?.declaredMethods?.firstOrNull {
            it.name == "createView" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val managerCreateIconMethod = managerClass?.declaredMethods?.firstOrNull {
            it.name == "createIcon" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }
        logState(KEY_MANAGER_CREATE_ICON, if (managerCreateIconMethod != null) HookPointState.FOUND else HookPointState.MISSING)

        val managerViewFields = managerClass?.declaredFields?.filter {
            navViewClass != null && navViewClass.isAssignableFrom(it.type)
        }?.map { FieldDescriptor.of(it) } ?: emptyList()

        val managerOnDownEventMethod = managerClass?.declaredMethods?.firstOrNull {
            it.name == "onDownEvent" && it.parameterTypes.any { pt -> pt == MotionEvent::class.java }
        }?.let { MethodDescriptor.of(it) }
        logState(KEY_MANAGER_ON_DOWN, if (managerOnDownEventMethod != null) HookPointState.FOUND else HookPointState.MISSING)

        val managerOnUpEventMethod = managerClass?.declaredMethods?.firstOrNull {
            it.name == "onUpEvent"
        }?.let { MethodDescriptor.of(it) }

        val managerOnUpdateGestureViewMethod = managerClass?.declaredMethods?.firstOrNull {
            it.name == "onUpdateGestureView" && it.parameterTypes.size == 1
        }?.let { MethodDescriptor.of(it) }

        val updateBackIconMethod = managerClass?.declaredMethods?.firstOrNull {
            it.name == "updateBackIcon" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val bezierCalcClass = safeLoadClass(CLASS_BASE_BEZIER_CALCULATOR)
        val bezierCalcDesc = bezierCalcClass?.let { ClassDescriptor(it.name) }

        val calculateMatrixMethod = bezierCalcClass?.declaredMethods?.firstOrNull {
            it.name == "calculateMatrix" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val backIconMatrixMethod = bezierCalcClass?.declaredMethods?.firstOrNull {
            it.name == "getBackIconMatrix" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val getCurrentPointMethod = bezierCalcClass?.declaredMethods?.firstOrNull {
            it.name == "getCurrentPoint" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val currentPointField = bezierCalcClass?.declaredFields?.firstOrNull {
            it.name == "currentPoint"
        }?.let { FieldDescriptor.of(it) }

        val getScreenWidthMethod = bezierCalcClass?.declaredMethods?.firstOrNull {
            it.name == "getScreenWidth" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val bezierCalculatorField = navViewClass?.declaredFields?.firstOrNull {
            it.name == "bezierCalculator" || (bezierCalcClass != null && bezierCalcClass.isAssignableFrom(it.type))
        }?.let { FieldDescriptor.of(it) }

        val getPathMethod = bezierCalcClass?.declaredMethods?.firstOrNull {
            it.name == "getPath" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val vibrateObserverClass = safeLoadClass(CLASS_VIBRATE_OBSERVER)
        val vibrateObserverDesc = vibrateObserverClass?.let { ClassDescriptor(it.name) }
        val vibrateObserverOnChangeMethod = vibrateObserverClass?.declaredMethods?.firstOrNull {
            it.name == "onChange" && it.parameterTypes.size == 1 && it.parameterTypes[0] == java.lang.Boolean.TYPE
        }?.let { MethodDescriptor.of(it) }
        logState(KEY_VIBRATE_OBSERVER, if (vibrateObserverOnChangeMethod != null) HookPointState.FOUND else HookPointState.MISSING)

        val gestureUiThreadManagerClass = safeLoadClass(CLASS_GESTURE_UI_THREAD_MANAGER)
        val gestureUiThreadManagerDesc = gestureUiThreadManagerClass?.let { ClassDescriptor(it.name) }
        val getGestureUiHandlerMethod = gestureUiThreadManagerClass?.declaredMethods?.firstOrNull {
            it.name == "getGestureUIHandler" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        // ── 2. Navigation Bar Buttons ──
        val navBarViewClass = requiredClass(CLASS_NAV_BAR_VIEW)
        val navBarViewDesc = navBarViewClass?.let { ClassDescriptor(it.name) }
        logState(KEY_NAV_BAR_VIEW, if (navBarViewClass != null) HookPointState.FOUND else HookPointState.MISSING)

        val keyButtonDrawableClass = requiredClass(CLASS_KEY_BUTTON_DRAWABLE)
        val keyButtonDrawableDesc = keyButtonDrawableClass?.let { ClassDescriptor(it.name) }
        logState(KEY_KEY_BUTTON_DRAWABLE, if (keyButtonDrawableClass != null) HookPointState.FOUND else HookPointState.MISSING)

        val shadowDrawableStateClass = safeLoadClass(CLASS_SHADOW_DRAWABLE_STATE)
        val shadowDrawableStateDesc = shadowDrawableStateClass?.let { ClassDescriptor(it.name) }
        logState(KEY_SHADOW_DRAWABLE_STATE, if (shadowDrawableStateClass != null) HookPointState.FOUND else HookPointState.MISSING)

        val buttonDispatcherClass = requiredClass(CLASS_BUTTON_DISPATCHER)
        val buttonDispatcherDesc = buttonDispatcherClass?.let { ClassDescriptor(it.name) }
        logState(KEY_BUTTON_DISPATCHER, if (buttonDispatcherClass != null) HookPointState.FOUND else HookPointState.MISSING)

        val keyButtonDrawableCtor = keyButtonDrawableClass?.declaredConstructors?.firstOrNull { c ->
            c.parameterTypes.size >= 2 &&
                Drawable::class.java.isAssignableFrom(c.parameterTypes[0]) &&
                Drawable::class.java.isAssignableFrom(c.parameterTypes[1])
        }?.let { ConstructorDescriptor.of(it) }

        val onAttachedToWindowMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "onAttachedToWindow" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val onDetachedFromWindowMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "onDetachedFromWindow" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val updateMainIconsMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "updateMainIcons" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val updateNavButtonIconsMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "updateNavButtonIcons" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }
        logState(KEY_NAV_BAR_UPDATE_ICONS, if (updateNavButtonIconsMethod != null) HookPointState.FOUND else HookPointState.MISSING)

        val updateIconsMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "updateIcons" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Configuration::class.java
        }?.let { MethodDescriptor.of(it) }

        val orientBackButtonMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "orientBackButton" && it.parameterTypes.size == 1
        }?.let { MethodDescriptor.of(it) }

        val getBackButtonMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "getBackButton" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val getHomeButtonMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "getHomeButton" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val getRecentsButtonMethod = navBarViewClass?.declaredMethods?.firstOrNull {
            it.name == "getRecentsButton" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val dispatcherSetImageDrawableMethod = buttonDispatcherClass?.declaredMethods?.firstOrNull {
            it.name == "setImageDrawable" && it.parameterTypes.size == 1
        }?.let { MethodDescriptor.of(it) }

        val homeDefaultIconField = navBarViewClass?.declaredFields?.firstOrNull {
            it.name.contains("homeDefaultIcon", true) || (it.name.contains("home", true) && it.type.name.contains("KeyButtonDrawable"))
        }?.let { FieldDescriptor.of(it) }

        val backIconField = navBarViewClass?.declaredFields?.firstOrNull {
            it.name.contains("backIcon", true) || (it.name.contains("back", true) && it.type.name.contains("KeyButtonDrawable"))
        }?.let { FieldDescriptor.of(it) }

        val recentIconField = navBarViewClass?.declaredFields?.firstOrNull {
            it.name.contains("recentIcon", true) || (it.name.contains("recent", true) && it.type.name.contains("KeyButtonDrawable"))
        }?.let { FieldDescriptor.of(it) }

        val isVerticalField = navBarViewClass?.declaredFields?.firstOrNull {
            it.name.contains("isVertical", true) && it.type == java.lang.Boolean.TYPE
        }?.let { FieldDescriptor.of(it) }

        val barTransitionsField = navBarViewClass?.declaredFields?.firstOrNull {
            it.name.contains("barTransitions", true) || it.type.name.contains("BarTransitions")
        }?.let { FieldDescriptor.of(it) }

        val setDarkIntensityMethod = keyButtonDrawableClass?.declaredMethods?.firstOrNull {
            it.name == "setDarkIntensity" && it.parameterTypes.size == 1 && it.parameterTypes[0] == java.lang.Float.TYPE
        }?.let { MethodDescriptor.of(it) }

        val getDarkIntensityMethod = keyButtonDrawableClass?.declaredMethods?.firstOrNull {
            it.name == "getDarkIntensity" && it.parameterTypes.isEmpty()
        }?.let { MethodDescriptor.of(it) }

        val setRotationMethod = keyButtonDrawableClass?.declaredMethods?.firstOrNull {
            it.name == "setRotation" && it.parameterTypes.size == 1 && it.parameterTypes[0] == java.lang.Float.TYPE
        }?.let { MethodDescriptor.of(it) }

        val setColorFilterMethod = keyButtonDrawableClass?.declaredMethods?.firstOrNull {
            it.name == "setColorFilter" && it.parameterTypes.size == 1 && android.graphics.ColorFilter::class.java.isAssignableFrom(it.parameterTypes[0])
        }?.let { MethodDescriptor.of(it) }

        // ── 3. Gesture Handle (Pill) ──
        val handleClass = HANDLE_CLASS_CANDIDATES.asSequence()
            .mapNotNull { safeLoadClass(it) }
            .firstOrNull { View::class.java.isAssignableFrom(it) }
        val handleClassDesc = handleClass?.let { ClassDescriptor(it.name) }
        logState(KEY_HANDLE_VIEW, if (handleClass != null) HookPointState.FOUND else HookPointState.MISSING, handleClass?.name ?: "not found")

        val handleConstructors = handleClass?.declaredConstructors?.map { ConstructorDescriptor.of(it) } ?: emptyList()

        val handleSetVerticalMethod = handleClass?.declaredMethods?.firstOrNull {
            it.name == "setVertical" && it.parameterTypes.size == 1 && it.parameterTypes[0] == java.lang.Boolean.TYPE
        }?.let { MethodDescriptor.of(it) }
        logState(KEY_HANDLE_SET_VERTICAL, if (handleSetVerticalMethod != null) HookPointState.FOUND else HookPointState.MISSING)

        val handleOnDrawMethod = handleClass?.declaredMethods?.firstOrNull {
            it.name == "onDraw" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Canvas::class.java
        }?.let { MethodDescriptor.of(it) }

        return SystemUiSymbols(
            ruleVersion = RULE_VERSION,
            hostFingerprint = fingerprint,
            gestureNavViewClass = navViewDesc,
            gestureViewManagerClass = managerDesc,
            bezierCalculatorClass = bezierCalcDesc,
            setBackIconMethod = setBackIconMethod,
            iconPaintField = iconPaintDesc,
            bezierPaintField = bezierPaintDesc,
            borderPaintField = borderPaintDesc,
            positionField = positionField,
            hasDownEventField = hasDownEventField,
            lastGestureStateField = lastGestureStateField,
            gestureStateField = gestureStateField,
            managerCreateViewMethod = managerCreateViewMethod,
            managerCreateIconMethod = managerCreateIconMethod,
            navViewOnDrawMethod = navViewOnDrawMethod,
            managerViewFields = managerViewFields,
            managerOnDownEventMethod = managerOnDownEventMethod,
            managerOnUpEventMethod = managerOnUpEventMethod,
            onUpdateGestureViewMethod = onUpdateGestureViewDesc,
            containerStateMethod = containerStateMethod,
            containerPointMethod = containerPointMethod,
            managerOnUpdateGestureViewMethod = managerOnUpdateGestureViewMethod,
            updateBackIconMethod = updateBackIconMethod,
            calculateMatrixMethod = calculateMatrixMethod,
            backIconMatrixMethod = backIconMatrixMethod,
            bezierCalculatorField = bezierCalculatorField,
            getCurrentPointMethod = getCurrentPointMethod,
            currentPointField = currentPointField,
            getScreenWidthMethod = getScreenWidthMethod,
            getBezierCalculatorMethod = getBezierCalcMethod,
            getPathMethod = getPathMethod,
            vibrateObserverClass = vibrateObserverDesc,
            vibrateObserverOnChangeMethod = vibrateObserverOnChangeMethod,
            gestureUiThreadManagerClass = gestureUiThreadManagerDesc,
            getGestureUiHandlerMethod = getGestureUiHandlerMethod,
            navBarViewClass = navBarViewDesc,
            keyButtonDrawableClass = keyButtonDrawableDesc,
            shadowDrawableStateClass = shadowDrawableStateDesc,
            buttonDispatcherClass = buttonDispatcherDesc,
            keyButtonDrawableCtor = keyButtonDrawableCtor,
            onAttachedToWindowMethod = onAttachedToWindowMethod,
            onDetachedFromWindowMethod = onDetachedFromWindowMethod,
            updateMainIconsMethod = updateMainIconsMethod,
            updateNavButtonIconsMethod = updateNavButtonIconsMethod,
            updateIconsMethod = updateIconsMethod,
            orientBackButtonMethod = orientBackButtonMethod,
            getBackButtonMethod = getBackButtonMethod,
            getHomeButtonMethod = getHomeButtonMethod,
            getRecentsButtonMethod = getRecentsButtonMethod,
            dispatcherSetImageDrawableMethod = dispatcherSetImageDrawableMethod,
            homeDefaultIconField = homeDefaultIconField,
            backIconField = backIconField,
            recentIconField = recentIconField,
            isVerticalField = isVerticalField,
            barTransitionsField = barTransitionsField,
            setDarkIntensityMethod = setDarkIntensityMethod,
            getDarkIntensityMethod = getDarkIntensityMethod,
            setRotationMethod = setRotationMethod,
            setColorFilterMethod = setColorFilterMethod,
            handleViewClass = handleClassDesc,
            handleConstructors = handleConstructors,
            handleSetVerticalMethod = handleSetVerticalMethod,
            handleOnDrawMethod = handleOnDrawMethod,
            statusMap = states,
            scanErrors = scanErrors
        )
    }

    private fun buildFingerprint(apkPath: String?): String {
        if (apkPath.isNullOrBlank()) return ""
        val file = File(apkPath)
        if (!file.exists()) return ""
        return "${file.absolutePath}:${file.lastModified()}:${file.length()}"
    }
}
