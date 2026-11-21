package io.github.ra1nify.realos.xposed.hooks.navigation

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Live reflection handles restored from [SystemUiSymbols].
 *
 * Separated from descriptors so descriptors can be safely cached on disk
 * while handles remain bound to the host ClassLoader.
 */
data class SystemUiHandles(
    // ── Side Gesture Back ──
    val gestureNavViewClass: Class<*>? = null,
    val gestureViewManagerClass: Class<*>? = null,
    val bezierCalculatorClass: Class<*>? = null,

    val setBackIconMethod: Method? = null,
    val iconPaintField: Field? = null,
    val bezierPaintField: Field? = null,
    val borderPaintField: Field? = null,
    val positionField: Field? = null,
    val hasDownEventField: Field? = null,
    val lastGestureStateField: Field? = null,
    val gestureStateField: Field? = null,

    val managerCreateViewMethod: Method? = null,
    val managerCreateIconMethod: Method? = null,
    val navViewOnDrawMethod: Method? = null,
    val managerViewFields: List<Field> = emptyList(),
    val managerOnDownEventMethod: Method? = null,
    val managerOnUpEventMethod: Method? = null,

    val onUpdateGestureViewMethod: Method? = null,
    val containerStateMethod: Method? = null,
    val containerPointMethod: Method? = null,

    val managerOnUpdateGestureViewMethod: Method? = null,
    val updateBackIconMethod: Method? = null,
    val calculateMatrixMethod: Method? = null,
    val backIconMatrixMethod: Method? = null,
    val bezierCalculatorField: Field? = null,
    val getCurrentPointMethod: Method? = null,
    val currentPointField: Field? = null,
    val getScreenWidthMethod: Method? = null,

    val getBezierCalculatorMethod: Method? = null,
    val getPathMethod: Method? = null,

    val vibrateObserverClass: Class<*>? = null,
    val vibrateObserverOnChangeMethod: Method? = null,

    val gestureUiThreadManagerClass: Class<*>? = null,
    val getGestureUiHandlerMethod: Method? = null,

    // ── Navigation Bar Buttons ──
    val navBarViewClass: Class<*>? = null,
    val keyButtonDrawableClass: Class<*>? = null,
    val shadowDrawableStateClass: Class<*>? = null,
    val buttonDispatcherClass: Class<*>? = null,

    val keyButtonDrawableCtor: Constructor<*>? = null,
    val onAttachedToWindowMethod: Method? = null,
    val onDetachedFromWindowMethod: Method? = null,
    val updateMainIconsMethod: Method? = null,
    val updateNavButtonIconsMethod: Method? = null,
    val updateIconsMethod: Method? = null,
    val orientBackButtonMethod: Method? = null,
    val getBackButtonMethod: Method? = null,
    val getHomeButtonMethod: Method? = null,
    val getRecentsButtonMethod: Method? = null,
    val dispatcherSetImageDrawableMethod: Method? = null,

    val homeDefaultIconField: Field? = null,
    val backIconField: Field? = null,
    val recentIconField: Field? = null,
    val isVerticalField: Field? = null,
    val barTransitionsField: Field? = null,

    val setDarkIntensityMethod: Method? = null,
    val getDarkIntensityMethod: Method? = null,
    val setRotationMethod: Method? = null,
    val setColorFilterMethod: Method? = null,

    // ── Gesture Handle ──
    val handleViewClass: Class<*>? = null,
    val handleConstructors: List<Constructor<*>> = emptyList(),
    val handleSetVerticalMethod: Method? = null,
    val handleOnDrawMethod: Method? = null,

    val failedKeys: Set<String> = emptySet()
) {

    val createIconMethod: Method? get() = managerCreateIconMethod
    val onDrawMethod: Method? get() = navViewOnDrawMethod

    val canHookGestureBack: Boolean
        get() = gestureNavViewClass != null &&
            setBackIconMethod != null &&
            iconPaintField != null &&
            !failedKeys.contains(KEY_GESTURE_NAV_VIEW) &&
            !failedKeys.contains(KEY_SET_BACK_ICON)

    val canHookGestureAppearance: Boolean
        get() = canHookGestureBack &&
            managerCreateIconMethod != null &&
            managerViewFields.isNotEmpty() &&
            !failedKeys.contains(KEY_MANAGER_CREATE_ICON)

    val canHookGestureVibrate: Boolean
        get() = onUpdateGestureViewMethod != null &&
            containerStateMethod != null &&
            containerPointMethod != null &&
            hasDownEventField != null &&
            !failedKeys.contains(KEY_ON_UPDATE_GESTURE_VIEW)

    val canHookNavBarButtons: Boolean
        get() = navBarViewClass != null &&
            keyButtonDrawableClass != null &&
            shadowDrawableStateClass != null &&
            keyButtonDrawableCtor != null &&
            dispatcherSetImageDrawableMethod != null &&
            !failedKeys.contains(KEY_NAV_BAR_VIEW) &&
            !failedKeys.contains(KEY_KEY_BUTTON_DRAWABLE)

    val canHookGestureHandle: Boolean
        get() = handleViewClass != null &&
            handleConstructors.isNotEmpty() &&
            !failedKeys.contains(KEY_HANDLE_VIEW)

    companion object {
        fun restore(symbols: SystemUiSymbols, classLoader: ClassLoader): SystemUiHandles {
            val failed = mutableSetOf<String>()

            fun <D : Any, T : Any> track(key: String, descriptor: D?, restore: (D) -> T?): T? {
                if (descriptor == null) return null
                val value = runCatching { restore(descriptor) }.getOrNull()
                if (value == null) failed += key
                return value
            }

            // Side Gesture Back
            val gestureNavViewClass = track(KEY_GESTURE_NAV_VIEW, symbols.gestureNavViewClass) {
                it.restore(classLoader)
            }
            val gestureViewManagerClass = track(KEY_GESTURE_VIEW_MANAGER, symbols.gestureViewManagerClass) {
                it.restore(classLoader)
            }
            val bezierCalculatorClass = symbols.bezierCalculatorClass?.restore(classLoader)

            val setBackIconMethod = track(KEY_SET_BACK_ICON, symbols.setBackIconMethod) {
                it.restore(classLoader)
            }
            val iconPaintField = track(KEY_ICON_PAINT, symbols.iconPaintField) {
                it.restore(classLoader)
            }
            val bezierPaintField = symbols.bezierPaintField?.restore(classLoader)
            val borderPaintField = symbols.borderPaintField?.restore(classLoader)
            val positionField = symbols.positionField?.restore(classLoader)
            val hasDownEventField = symbols.hasDownEventField?.restore(classLoader)
            val lastGestureStateField = symbols.lastGestureStateField?.restore(classLoader)
            val gestureStateField = symbols.gestureStateField?.restore(classLoader)

            val managerCreateViewMethod = symbols.managerCreateViewMethod?.restore(classLoader)
            val managerCreateIconMethod = track(KEY_MANAGER_CREATE_ICON, symbols.managerCreateIconMethod) {
                it.restore(classLoader)
            }
            val navViewOnDrawMethod = symbols.navViewOnDrawMethod?.restore(classLoader)

            val managerViewFields = symbols.managerViewFields.mapNotNull { it.restore(classLoader) }
            val managerOnDownEventMethod = track(KEY_MANAGER_ON_DOWN, symbols.managerOnDownEventMethod) {
                it.restore(classLoader)
            }
            val managerOnUpEventMethod = symbols.managerOnUpEventMethod?.restore(classLoader)

            val onUpdateGestureViewMethod = track(KEY_ON_UPDATE_GESTURE_VIEW, symbols.onUpdateGestureViewMethod) {
                it.restore(classLoader)
            }
            val containerStateMethod = symbols.containerStateMethod?.restore(classLoader)
            val containerPointMethod = symbols.containerPointMethod?.restore(classLoader)
            val managerOnUpdateGestureViewMethod = symbols.managerOnUpdateGestureViewMethod?.restore(classLoader)
            val updateBackIconMethod = symbols.updateBackIconMethod?.restore(classLoader)
            val calculateMatrixMethod = symbols.calculateMatrixMethod?.restore(classLoader)
            val backIconMatrixMethod = symbols.backIconMatrixMethod?.restore(classLoader)
            val bezierCalculatorField = symbols.bezierCalculatorField?.restore(classLoader)
            val getCurrentPointMethod = symbols.getCurrentPointMethod?.restore(classLoader)
            val currentPointField = symbols.currentPointField?.restore(classLoader)
            val getScreenWidthMethod = symbols.getScreenWidthMethod?.restore(classLoader)
            val getBezierCalculatorMethod = symbols.getBezierCalculatorMethod?.restore(classLoader)
            val getPathMethod = symbols.getPathMethod?.restore(classLoader)

            val vibrateObserverClass = track(KEY_VIBRATE_OBSERVER, symbols.vibrateObserverClass) {
                it.restore(classLoader)
            }
            val vibrateObserverOnChangeMethod = symbols.vibrateObserverOnChangeMethod?.restore(classLoader)

            val gestureUiThreadManagerClass = symbols.gestureUiThreadManagerClass?.restore(classLoader)
            val getGestureUiHandlerMethod = symbols.getGestureUiHandlerMethod?.restore(classLoader)

            // Navigation Bar Buttons
            val navBarViewClass = track(KEY_NAV_BAR_VIEW, symbols.navBarViewClass) {
                it.restore(classLoader)
            }
            val keyButtonDrawableClass = track(KEY_KEY_BUTTON_DRAWABLE, symbols.keyButtonDrawableClass) {
                it.restore(classLoader)
            }
            val shadowDrawableStateClass = track(KEY_SHADOW_DRAWABLE_STATE, symbols.shadowDrawableStateClass) {
                it.restore(classLoader)
            }
            val buttonDispatcherClass = track(KEY_BUTTON_DISPATCHER, symbols.buttonDispatcherClass) {
                it.restore(classLoader)
            }

            val keyButtonDrawableCtor = track(KEY_KEY_BUTTON_DRAWABLE, symbols.keyButtonDrawableCtor) {
                it.restore(classLoader)
            }
            val onAttachedToWindowMethod = symbols.onAttachedToWindowMethod?.restore(classLoader)
            val onDetachedFromWindowMethod = symbols.onDetachedFromWindowMethod?.restore(classLoader)
            val updateMainIconsMethod = symbols.updateMainIconsMethod?.restore(classLoader)
            val updateNavButtonIconsMethod = track(KEY_NAV_BAR_UPDATE_ICONS, symbols.updateNavButtonIconsMethod) {
                it.restore(classLoader)
            }
            val updateIconsMethod = symbols.updateIconsMethod?.restore(classLoader)
            val orientBackButtonMethod = symbols.orientBackButtonMethod?.restore(classLoader)
            val getBackButtonMethod = symbols.getBackButtonMethod?.restore(classLoader)
            val getHomeButtonMethod = symbols.getHomeButtonMethod?.restore(classLoader)
            val getRecentsButtonMethod = symbols.getRecentsButtonMethod?.restore(classLoader)
            val dispatcherSetImageDrawableMethod = symbols.dispatcherSetImageDrawableMethod?.restore(classLoader)

            val homeDefaultIconField = symbols.homeDefaultIconField?.restore(classLoader)
            val backIconField = symbols.backIconField?.restore(classLoader)
            val recentIconField = symbols.recentIconField?.restore(classLoader)
            val isVerticalField = symbols.isVerticalField?.restore(classLoader)
            val barTransitionsField = symbols.barTransitionsField?.restore(classLoader)

            val setDarkIntensityMethod = symbols.setDarkIntensityMethod?.restore(classLoader)
            val getDarkIntensityMethod = symbols.getDarkIntensityMethod?.restore(classLoader)
            val setRotationMethod = symbols.setRotationMethod?.restore(classLoader)
            val setColorFilterMethod = symbols.setColorFilterMethod?.restore(classLoader)

            // Gesture Handle
            val handleViewClass = track(KEY_HANDLE_VIEW, symbols.handleViewClass) {
                it.restore(classLoader)
            }
            val handleConstructors = symbols.handleConstructors.mapNotNull { it.restore(classLoader) }
            val handleSetVerticalMethod = track(KEY_HANDLE_SET_VERTICAL, symbols.handleSetVerticalMethod) {
                it.restore(classLoader)
            }
            val handleOnDrawMethod = symbols.handleOnDrawMethod?.restore(classLoader)

            return SystemUiHandles(
                gestureNavViewClass = gestureNavViewClass,
                gestureViewManagerClass = gestureViewManagerClass,
                bezierCalculatorClass = bezierCalculatorClass,
                setBackIconMethod = setBackIconMethod,
                iconPaintField = iconPaintField,
                bezierPaintField = bezierPaintField,
                borderPaintField = borderPaintField,
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
                onUpdateGestureViewMethod = onUpdateGestureViewMethod,
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
                getBezierCalculatorMethod = getBezierCalculatorMethod,
                getPathMethod = getPathMethod,
                vibrateObserverClass = vibrateObserverClass,
                vibrateObserverOnChangeMethod = vibrateObserverOnChangeMethod,
                gestureUiThreadManagerClass = gestureUiThreadManagerClass,
                getGestureUiHandlerMethod = getGestureUiHandlerMethod,
                navBarViewClass = navBarViewClass,
                keyButtonDrawableClass = keyButtonDrawableClass,
                shadowDrawableStateClass = shadowDrawableStateClass,
                buttonDispatcherClass = buttonDispatcherClass,
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
                handleViewClass = handleViewClass,
                handleConstructors = handleConstructors,
                handleSetVerticalMethod = handleSetVerticalMethod,
                handleOnDrawMethod = handleOnDrawMethod,
                failedKeys = failed
            )
        }
    }
}
