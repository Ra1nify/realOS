package io.github.ra1nify.realos.xposed.hooks.navigation

import org.json.JSONArray
import org.json.JSONObject
import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.ConstructorDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.JsonSymbol
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor

/**
 * Data model for resolved SystemUI symbols (Layer 2).
 *
 * Holds serializable descriptors for persistent cache, decoupling symbol
 * resolution from reflection handles.
 */
data class SystemUiSymbols(
    val ruleVersion: Int = 1,
    val hostFingerprint: String = "",

    // ── Side Gesture Back: View & Manager ──
    val gestureNavViewClass: ClassDescriptor? = null,
    val gestureViewManagerClass: ClassDescriptor? = null,
    val bezierCalculatorClass: ClassDescriptor? = null,

    val setBackIconMethod: MethodDescriptor? = null,
    val iconPaintField: FieldDescriptor? = null,
    val bezierPaintField: FieldDescriptor? = null,
    val borderPaintField: FieldDescriptor? = null,
    val positionField: FieldDescriptor? = null,
    val hasDownEventField: FieldDescriptor? = null,
    val lastGestureStateField: FieldDescriptor? = null,
    val gestureStateField: FieldDescriptor? = null,

    val managerCreateViewMethod: MethodDescriptor? = null,
    val managerCreateIconMethod: MethodDescriptor? = null,
    val navViewOnDrawMethod: MethodDescriptor? = null,
    val managerViewFields: List<FieldDescriptor> = emptyList(),
    val managerOnDownEventMethod: MethodDescriptor? = null,
    val managerOnUpEventMethod: MethodDescriptor? = null,

    val onUpdateGestureViewMethod: MethodDescriptor? = null,
    val containerStateMethod: MethodDescriptor? = null,
    val containerPointMethod: MethodDescriptor? = null,

    val managerOnUpdateGestureViewMethod: MethodDescriptor? = null,
    val updateBackIconMethod: MethodDescriptor? = null,
    val calculateMatrixMethod: MethodDescriptor? = null,
    val backIconMatrixMethod: MethodDescriptor? = null,
    val bezierCalculatorField: FieldDescriptor? = null,
    val getCurrentPointMethod: MethodDescriptor? = null,
    val currentPointField: FieldDescriptor? = null,
    val getScreenWidthMethod: MethodDescriptor? = null,

    val getBezierCalculatorMethod: MethodDescriptor? = null,
    val getPathMethod: MethodDescriptor? = null,

    val vibrateObserverClass: ClassDescriptor? = null,
    val vibrateObserverOnChangeMethod: MethodDescriptor? = null,

    val gestureUiThreadManagerClass: ClassDescriptor? = null,
    val getGestureUiHandlerMethod: MethodDescriptor? = null,

    // ── Navigation Bar Buttons ──
    val navBarViewClass: ClassDescriptor? = null,
    val keyButtonDrawableClass: ClassDescriptor? = null,
    val shadowDrawableStateClass: ClassDescriptor? = null,
    val buttonDispatcherClass: ClassDescriptor? = null,

    val keyButtonDrawableCtor: ConstructorDescriptor? = null,
    val onAttachedToWindowMethod: MethodDescriptor? = null,
    val onDetachedFromWindowMethod: MethodDescriptor? = null,
    val updateMainIconsMethod: MethodDescriptor? = null,
    val updateNavButtonIconsMethod: MethodDescriptor? = null,
    val updateIconsMethod: MethodDescriptor? = null,
    val orientBackButtonMethod: MethodDescriptor? = null,
    val getBackButtonMethod: MethodDescriptor? = null,
    val getHomeButtonMethod: MethodDescriptor? = null,
    val getRecentsButtonMethod: MethodDescriptor? = null,
    val dispatcherSetImageDrawableMethod: MethodDescriptor? = null,

    val homeDefaultIconField: FieldDescriptor? = null,
    val backIconField: FieldDescriptor? = null,
    val recentIconField: FieldDescriptor? = null,
    val isVerticalField: FieldDescriptor? = null,
    val barTransitionsField: FieldDescriptor? = null,

    val setDarkIntensityMethod: MethodDescriptor? = null,
    val getDarkIntensityMethod: MethodDescriptor? = null,
    val setRotationMethod: MethodDescriptor? = null,
    val setColorFilterMethod: MethodDescriptor? = null,

    // ── Gesture Handle (Navigation Bar Pill) ──
    val handleViewClass: ClassDescriptor? = null,
    val handleConstructors: List<ConstructorDescriptor> = emptyList(),
    val handleSetVerticalMethod: MethodDescriptor? = null,
    val handleOnDrawMethod: MethodDescriptor? = null,

    // ── Status and Diagnostics ──
    val statusMap: Map<String, HookPointState> = emptyMap(),
    val scanErrors: List<String> = emptyList()
) {

    val isReadyForGestureBack: Boolean
        get() = gestureNavViewClass != null &&
            setBackIconMethod != null &&
            iconPaintField != null &&
            statusMap[KEY_GESTURE_NAV_VIEW] == HookPointState.FOUND &&
            statusMap[KEY_SET_BACK_ICON] == HookPointState.FOUND

    val isReadyForGestureAppearance: Boolean
        get() = isReadyForGestureBack &&
            managerCreateIconMethod != null &&
            managerViewFields.isNotEmpty() &&
            statusMap[KEY_MANAGER_CREATE_ICON] == HookPointState.FOUND

    val isReadyForGestureVibrate: Boolean
        get() = onUpdateGestureViewMethod != null &&
            containerStateMethod != null &&
            containerPointMethod != null &&
            hasDownEventField != null &&
            statusMap[KEY_ON_UPDATE_GESTURE_VIEW] == HookPointState.FOUND

    val isReadyForNavBarButtons: Boolean
        get() = navBarViewClass != null &&
            keyButtonDrawableClass != null &&
            shadowDrawableStateClass != null &&
            keyButtonDrawableCtor != null &&
            dispatcherSetImageDrawableMethod != null &&
            statusMap[KEY_NAV_BAR_VIEW] == HookPointState.FOUND &&
            statusMap[KEY_KEY_BUTTON_DRAWABLE] == HookPointState.FOUND

    val isReadyForGestureHandle: Boolean
        get() = handleViewClass != null &&
            handleConstructors.isNotEmpty() &&
            statusMap[KEY_HANDLE_VIEW] == HookPointState.FOUND

    fun toJson(): JSONObject = JSONObject().apply {
        put("ruleVersion", ruleVersion)
        put("hostFingerprint", hostFingerprint)

        // Side Gesture Back
        gestureNavViewClass?.let { put("gestureNavViewClass", it.toJson()) }
        gestureViewManagerClass?.let { put("gestureViewManagerClass", it.toJson()) }
        bezierCalculatorClass?.let { put("bezierCalculatorClass", it.toJson()) }
        setBackIconMethod?.let { put("setBackIconMethod", it.toJson()) }
        iconPaintField?.let { put("iconPaintField", it.toJson()) }
        bezierPaintField?.let { put("bezierPaintField", it.toJson()) }
        borderPaintField?.let { put("borderPaintField", it.toJson()) }
        positionField?.let { put("positionField", it.toJson()) }
        hasDownEventField?.let { put("hasDownEventField", it.toJson()) }
        lastGestureStateField?.let { put("lastGestureStateField", it.toJson()) }
        gestureStateField?.let { put("gestureStateField", it.toJson()) }
        managerCreateViewMethod?.let { put("managerCreateViewMethod", it.toJson()) }
        managerCreateIconMethod?.let { put("managerCreateIconMethod", it.toJson()) }
        navViewOnDrawMethod?.let { put("navViewOnDrawMethod", it.toJson()) }
        if (managerViewFields.isNotEmpty()) {
            put("managerViewFields", JSONArray().apply { managerViewFields.forEach { put(it.toJson()) } })
        }
        managerOnDownEventMethod?.let { put("managerOnDownEventMethod", it.toJson()) }
        managerOnUpEventMethod?.let { put("managerOnUpEventMethod", it.toJson()) }
        onUpdateGestureViewMethod?.let { put("onUpdateGestureViewMethod", it.toJson()) }
        containerStateMethod?.let { put("containerStateMethod", it.toJson()) }
        containerPointMethod?.let { put("containerPointMethod", it.toJson()) }
        managerOnUpdateGestureViewMethod?.let { put("managerOnUpdateGestureViewMethod", it.toJson()) }
        updateBackIconMethod?.let { put("updateBackIconMethod", it.toJson()) }
        calculateMatrixMethod?.let { put("calculateMatrixMethod", it.toJson()) }
        backIconMatrixMethod?.let { put("backIconMatrixMethod", it.toJson()) }
        bezierCalculatorField?.let { put("bezierCalculatorField", it.toJson()) }
        getCurrentPointMethod?.let { put("getCurrentPointMethod", it.toJson()) }
        currentPointField?.let { put("currentPointField", it.toJson()) }
        getScreenWidthMethod?.let { put("getScreenWidthMethod", it.toJson()) }
        getBezierCalculatorMethod?.let { put("getBezierCalculatorMethod", it.toJson()) }
        getPathMethod?.let { put("getPathMethod", it.toJson()) }
        vibrateObserverClass?.let { put("vibrateObserverClass", it.toJson()) }
        vibrateObserverOnChangeMethod?.let { put("vibrateObserverOnChangeMethod", it.toJson()) }
        gestureUiThreadManagerClass?.let { put("gestureUiThreadManagerClass", it.toJson()) }
        getGestureUiHandlerMethod?.let { put("getGestureUiHandlerMethod", it.toJson()) }

        // Navigation Bar Buttons
        navBarViewClass?.let { put("navBarViewClass", it.toJson()) }
        keyButtonDrawableClass?.let { put("keyButtonDrawableClass", it.toJson()) }
        shadowDrawableStateClass?.let { put("shadowDrawableStateClass", it.toJson()) }
        buttonDispatcherClass?.let { put("buttonDispatcherClass", it.toJson()) }
        keyButtonDrawableCtor?.let { put("keyButtonDrawableCtor", it.toJson()) }
        onAttachedToWindowMethod?.let { put("onAttachedToWindowMethod", it.toJson()) }
        onDetachedFromWindowMethod?.let { put("onDetachedFromWindowMethod", it.toJson()) }
        updateMainIconsMethod?.let { put("updateMainIconsMethod", it.toJson()) }
        updateNavButtonIconsMethod?.let { put("updateNavButtonIconsMethod", it.toJson()) }
        updateIconsMethod?.let { put("updateIconsMethod", it.toJson()) }
        orientBackButtonMethod?.let { put("orientBackButtonMethod", it.toJson()) }
        getBackButtonMethod?.let { put("getBackButtonMethod", it.toJson()) }
        getHomeButtonMethod?.let { put("getHomeButtonMethod", it.toJson()) }
        getRecentsButtonMethod?.let { put("getRecentsButtonMethod", it.toJson()) }
        dispatcherSetImageDrawableMethod?.let { put("dispatcherSetImageDrawableMethod", it.toJson()) }
        homeDefaultIconField?.let { put("homeDefaultIconField", it.toJson()) }
        backIconField?.let { put("backIconField", it.toJson()) }
        recentIconField?.let { put("recentIconField", it.toJson()) }
        isVerticalField?.let { put("isVerticalField", it.toJson()) }
        barTransitionsField?.let { put("barTransitionsField", it.toJson()) }
        setDarkIntensityMethod?.let { put("setDarkIntensityMethod", it.toJson()) }
        getDarkIntensityMethod?.let { put("getDarkIntensityMethod", it.toJson()) }
        setRotationMethod?.let { put("setRotationMethod", it.toJson()) }
        setColorFilterMethod?.let { put("setColorFilterMethod", it.toJson()) }

        // Gesture Handle
        handleViewClass?.let { put("handleViewClass", it.toJson()) }
        if (handleConstructors.isNotEmpty()) {
            put("handleConstructors", JSONArray().apply { handleConstructors.forEach { put(it.toJson()) } })
        }
        handleSetVerticalMethod?.let { put("handleSetVerticalMethod", it.toJson()) }
        handleOnDrawMethod?.let { put("handleOnDrawMethod", it.toJson()) }

        // Status map
        val statusObj = JSONObject()
        statusMap.forEach { (k, v) -> statusObj.put(k, v.name) }
        put("statusMap", statusObj)

        // Errors
        if (scanErrors.isNotEmpty()) {
            put("scanErrors", JSONArray().apply { scanErrors.forEach { put(it) } })
        }
    }

    /**
     * Writes the complete [toJson] snapshot into the cache payload.
     */
    fun toCachePayload(fingerprint: String): AntiTearingCache.CachePayload {
        val snapshot = JsonSymbol(toJson().toString())
        return AntiTearingCache.CachePayload(fingerprint, ruleVersion, mapOf(CK_SNAPSHOT to snapshot))
    }

    companion object {
        /** Key of the snapshot inside the cache payload */
        private const val CK_SNAPSHOT = "snapshot"

        /**
         * Restores from the cache.
         *
         * Accepts the snapshot format only; any other format throws for the caller to
         * catch and rescan.
         */
        fun fromCachePayload(payload: AntiTearingCache.CachePayload): SystemUiSymbols {
            val raw = (payload.symbols[CK_SNAPSHOT] as? JsonSymbol)?.raw
                ?: error("SystemUI symbol cache has no snapshot (old format); a rescan is required")
            return fromJson(JSONObject(raw))
        }

        fun fromJson(json: JSONObject): SystemUiSymbols {
            val statusMap = mutableMapOf<String, HookPointState>()
            json.optJSONObject("statusMap")?.let { obj ->
                obj.keys().forEach { k ->
                    statusMap[k] = runCatching { HookPointState.valueOf(obj.getString(k)) }.getOrDefault(HookPointState.MISSING)
                }
            }

            val scanErrors = mutableListOf<String>()
            json.optJSONArray("scanErrors")?.let { arr ->
                for (i in 0 until arr.length()) scanErrors.add(arr.getString(i))
            }

            val managerFields = mutableListOf<FieldDescriptor>()
            json.optJSONArray("managerViewFields")?.let { arr ->
                for (i in 0 until arr.length()) managerFields.add(FieldDescriptor.fromJson(arr.getJSONObject(i)))
            }

            val handleCtors = mutableListOf<ConstructorDescriptor>()
            json.optJSONArray("handleConstructors")?.let { arr ->
                for (i in 0 until arr.length()) handleCtors.add(ConstructorDescriptor.fromJson(arr.getJSONObject(i)))
            }

            fun optClass(key: String) = json.optJSONObject(key)?.let { ClassDescriptor.fromJson(it) }
            fun optMethod(key: String) = json.optJSONObject(key)?.let { MethodDescriptor.fromJson(it) }
            fun optField(key: String) = json.optJSONObject(key)?.let { FieldDescriptor.fromJson(it) }
            fun optCtor(key: String) = json.optJSONObject(key)?.let { ConstructorDescriptor.fromJson(it) }

            return SystemUiSymbols(
                ruleVersion = json.optInt("ruleVersion", 1),
                hostFingerprint = json.optString("hostFingerprint", ""),
                gestureNavViewClass = optClass("gestureNavViewClass"),
                gestureViewManagerClass = optClass("gestureViewManagerClass"),
                bezierCalculatorClass = optClass("bezierCalculatorClass"),
                setBackIconMethod = optMethod("setBackIconMethod"),
                iconPaintField = optField("iconPaintField"),
                bezierPaintField = optField("bezierPaintField"),
                borderPaintField = optField("borderPaintField"),
                positionField = optField("positionField"),
                hasDownEventField = optField("hasDownEventField"),
                lastGestureStateField = optField("lastGestureStateField"),
                gestureStateField = optField("gestureStateField"),
                managerCreateViewMethod = optMethod("managerCreateViewMethod"),
                managerCreateIconMethod = optMethod("managerCreateIconMethod"),
                navViewOnDrawMethod = optMethod("navViewOnDrawMethod"),
                managerViewFields = managerFields,
                managerOnDownEventMethod = optMethod("managerOnDownEventMethod"),
                managerOnUpEventMethod = optMethod("managerOnUpEventMethod"),
                onUpdateGestureViewMethod = optMethod("onUpdateGestureViewMethod"),
                containerStateMethod = optMethod("containerStateMethod"),
                containerPointMethod = optMethod("containerPointMethod"),
                managerOnUpdateGestureViewMethod = optMethod("managerOnUpdateGestureViewMethod"),
                updateBackIconMethod = optMethod("updateBackIconMethod"),
                calculateMatrixMethod = optMethod("calculateMatrixMethod"),
                backIconMatrixMethod = optMethod("backIconMatrixMethod"),
                bezierCalculatorField = optField("bezierCalculatorField"),
                getCurrentPointMethod = optMethod("getCurrentPointMethod"),
                currentPointField = optField("currentPointField"),
                getScreenWidthMethod = optMethod("getScreenWidthMethod"),
                getBezierCalculatorMethod = optMethod("getBezierCalculatorMethod"),
                getPathMethod = optMethod("getPathMethod"),
                vibrateObserverClass = optClass("vibrateObserverClass"),
                vibrateObserverOnChangeMethod = optMethod("vibrateObserverOnChangeMethod"),
                gestureUiThreadManagerClass = optClass("gestureUiThreadManagerClass"),
                getGestureUiHandlerMethod = optMethod("getGestureUiHandlerMethod"),
                navBarViewClass = optClass("navBarViewClass"),
                keyButtonDrawableClass = optClass("keyButtonDrawableClass"),
                shadowDrawableStateClass = optClass("shadowDrawableStateClass"),
                buttonDispatcherClass = optClass("buttonDispatcherClass"),
                keyButtonDrawableCtor = optCtor("keyButtonDrawableCtor"),
                onAttachedToWindowMethod = optMethod("onAttachedToWindowMethod"),
                onDetachedFromWindowMethod = optMethod("onDetachedFromWindowMethod"),
                updateMainIconsMethod = optMethod("updateMainIconsMethod"),
                updateNavButtonIconsMethod = optMethod("updateNavButtonIconsMethod"),
                updateIconsMethod = optMethod("updateIconsMethod"),
                orientBackButtonMethod = optMethod("orientBackButtonMethod"),
                getBackButtonMethod = optMethod("getBackButtonMethod"),
                getHomeButtonMethod = optMethod("getHomeButtonMethod"),
                getRecentsButtonMethod = optMethod("getRecentsButtonMethod"),
                dispatcherSetImageDrawableMethod = optMethod("dispatcherSetImageDrawableMethod"),
                homeDefaultIconField = optField("homeDefaultIconField"),
                backIconField = optField("backIconField"),
                recentIconField = optField("recentIconField"),
                isVerticalField = optField("isVerticalField"),
                barTransitionsField = optField("barTransitionsField"),
                setDarkIntensityMethod = optMethod("setDarkIntensityMethod"),
                getDarkIntensityMethod = optMethod("getDarkIntensityMethod"),
                setRotationMethod = optMethod("setRotationMethod"),
                setColorFilterMethod = optMethod("setColorFilterMethod"),
                handleViewClass = optClass("handleViewClass"),
                handleConstructors = handleCtors,
                handleSetVerticalMethod = optMethod("handleSetVerticalMethod"),
                handleOnDrawMethod = optMethod("handleOnDrawMethod"),
                statusMap = statusMap,
                scanErrors = scanErrors
            )
        }
    }
}

// ── Hook Point Key Constants ──
const val KEY_GESTURE_NAV_VIEW = "gesture_nav_view"
const val KEY_GESTURE_VIEW_MANAGER = "gesture_view_manager"
const val KEY_SET_BACK_ICON = "gesture_set_back_icon"
const val KEY_ICON_PAINT = "gesture_icon_paint"
const val KEY_MANAGER_CREATE_ICON = "gesture_manager_create_icon"
const val KEY_MANAGER_ON_DOWN = "gesture_manager_on_down"
const val KEY_ON_UPDATE_GESTURE_VIEW = "gesture_on_update_gesture_view"
const val KEY_VIBRATE_OBSERVER = "gesture_vibrate_observer"
const val KEY_NAV_BAR_VIEW = "nav_bar_view"
const val KEY_KEY_BUTTON_DRAWABLE = "nav_bar_key_button_drawable"
const val KEY_SHADOW_DRAWABLE_STATE = "nav_bar_shadow_drawable_state"
const val KEY_BUTTON_DISPATCHER = "nav_bar_button_dispatcher"
const val KEY_NAV_BAR_UPDATE_ICONS = "nav_bar_update_icons"
const val KEY_HANDLE_VIEW = "gesture_handle_view"
const val KEY_HANDLE_SET_VERTICAL = "gesture_handle_set_vertical"

val ALL_SYSTEMUI_HOOK_KEYS: List<String> = listOf(
    KEY_GESTURE_NAV_VIEW,
    KEY_GESTURE_VIEW_MANAGER,
    KEY_SET_BACK_ICON,
    KEY_ICON_PAINT,
    KEY_MANAGER_CREATE_ICON,
    KEY_MANAGER_ON_DOWN,
    KEY_ON_UPDATE_GESTURE_VIEW,
    KEY_VIBRATE_OBSERVER,
    KEY_NAV_BAR_VIEW,
    KEY_KEY_BUTTON_DRAWABLE,
    KEY_SHADOW_DRAWABLE_STATE,
    KEY_BUTTON_DISPATCHER,
    KEY_NAV_BAR_UPDATE_ICONS,
    KEY_HANDLE_VIEW,
    KEY_HANDLE_SET_VERTICAL
)
