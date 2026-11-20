package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor

/**
 * Symbols model for OPPO Keyboard (Xiaobu Input Method) hook.
 */
data class OplusInputSymbols(
    val ruleVersion: Int = 18,
    val hostFingerprint: String = "",
    val moduleVersionCode: Int = 0,
    val lastAdaptedVersion: String = "",
    val whitelistField: FieldDescriptor? = null,
    val blurDecisionMethod: MethodDescriptor? = null,
    val blurApplyMethod: MethodDescriptor? = null,
    val foldCheckMethod: MethodDescriptor? = null,
    val keyboardTouchMethod: MethodDescriptor? = null,
    val keyboardDrawMethod: MethodDescriptor? = null,
    val dayNightCheckMethod: MethodDescriptor? = null,
    val blurParamCreateMethod: MethodDescriptor? = null,
    val keyBackgroundMethod: MethodDescriptor? = null,
    /**
     * Sources of the key background alpha value.
     *
     * The host calls a no-arg `Float` getter on the theme worker object inside every key draw
     * path and applies `drawable.setAlpha(clamp(t * 255))`; the facade `com.oplus.keyboard.base.theme.b#t()`
     * only forwards to that worker and is not on the draw path.
     *
     * Holds every implementation under `com.oplus.keyboard.base.theme.work`
     * (one each for normal / marker / pack themes), each with a hooked getter.
     */
    val keyAlphaMethods: List<MethodDescriptor> = emptyList(),
    /**
     * New-version key background alpha source (keyboard 1.9.18+).
     *
     * The draw path calls `KeySkinGroup.getKeyBGAlpha255()` and passes the result straight to
     * `Drawable.setAlpha(int)`; the old theme-work `t()` getter is no longer used there. The hook
     * scales the returned value by the user key opacity.
     */
    val keyBgAlpha255Method: MethodDescriptor? = null,
    val toolbarLayoutMethod: MethodDescriptor? = null,
    /**
     * Buffered key renderers refresh setters (`setDrawRefresh(boolean)`).
     *
     * The keyboard paints keys into a cached Bitmap and rebuilds it only when the renderer
     * refresh flag is set (or the buffer is missing). A key-alpha change therefore stays
     * invisible until the next rebuild, which normally only happens on IME restart or a size
     * change. Calling these setters before invalidating makes the slider take effect live.
     *
     * Resolved structurally: every host class exposing `setDrawRefresh(boolean)` is one of the
     * buffered key renderers (main keyboard body + theme preview).
     */
    val keyBufferRefreshMethods: List<MethodDescriptor> = emptyList(),
    val toolbarItemIconMethod: MethodDescriptor? = null,
    val toolCatalogCreateMethod: MethodDescriptor? = null,
    val toolboxDataMethod: MethodDescriptor? = null,
    val toolStateManagerTransformMethod: MethodDescriptor? = null,
    val themeIconMethod: MethodDescriptor? = null,
    val themeManagerClass: ClassDescriptor? = null,
    val themeManagerLazyField: FieldDescriptor? = null,
    val themeManagerInnerMethod: MethodDescriptor? = null,
    val themeManagerApplyMethod: MethodDescriptor? = null,
    // ── per-key symbols (key blending actual key bounds) ──
    val perkeyModelGetter: MethodDescriptor? = null,
    val perkeyEntityClass: ClassDescriptor? = null,
    val perkeyHitTestMethod: MethodDescriptor? = null,
    val perkeyLeftField: FieldDescriptor? = null,
    val perkeyRightField: FieldDescriptor? = null,
    val perkeyTopField: FieldDescriptor? = null,
    val perkeyBottomField: FieldDescriptor? = null,
    val perkeyDrawMethod: MethodDescriptor? = null,
    val perkeyShadowUnderlayMethod: MethodDescriptor? = null,
    val perkeyShadowOverlayMethod: MethodDescriptor? = null,
    val perkeyPressedField: FieldDescriptor? = null,
    val perkeyRedrawMethod: MethodDescriptor? = null,
    val statusMap: Map<String, HookPointState> = emptyMap(),
    val scanErrors: List<String> = emptyList()
) {
    fun isReadyForLiquidGlass(): Boolean =
        (whitelistField != null || blurDecisionMethod != null) && statusMap["whitelist"] == HookPointState.FOUND

    fun isReadyForFoldBypass(): Boolean =
        foldCheckMethod != null && statusMap["fold_check"] == HookPointState.FOUND

    fun isReadyForEffects(): Boolean =
        keyboardTouchMethod != null && statusMap["effects_touch"] == HookPointState.FOUND

    fun isReadyForDayNightBypass(): Boolean =
        dayNightCheckMethod != null && statusMap["day_night_check"] == HookPointState.FOUND

    /**
     * Availability of blur parameter customization (material / color / alpha).
     * The check keeps its historical scope; the radius path is not part of it.
     */
    fun isReadyForBlurCustomization(): Boolean =
        blurParamCreateMethod != null && statusMap["blur_param_create"] == HookPointState.FOUND

    /**
     * blur radius customization availability: the active path hooks the system framework class
     * ViewRootManager.setBlurRadius(int) by runtime reflection and rewrites its argument
     * (the class is absent from the host APK dex; DexKit cannot scan it).
     * No host symbol is required; a failed install fails closed.
     */
    fun isReadyForBlurRadius(): Boolean = true

    fun isReadyForKeyAlpha(): Boolean =
        (keyAlphaMethods.isNotEmpty() || keyBgAlpha255Method != null) &&
            statusMap["key_alpha"] == HookPointState.FOUND

    fun isReadyForToolbarAutofill(): Boolean =
        toolbarItemIconMethod != null && (
            (toolStateManagerTransformMethod != null && statusMap["tool_state_transform"] == HookPointState.FOUND) ||
            (toolbarLayoutMethod != null && statusMap["toolbar_layout"] == HookPointState.FOUND) ||
            (toolCatalogCreateMethod != null && statusMap["tool_catalog_create"] == HookPointState.FOUND)
        )

    /**
     * Availability of the key blending bounds. True only when every required per-key symbol is FOUND;
     * pressed / redraw are optional and excluded from the check.
     */
    fun isReadyForPerKey(): Boolean =
        perkeyModelGetter != null && statusMap["perkey_model_getter"] == HookPointState.FOUND &&
            perkeyEntityClass != null && statusMap["perkey_entity"] == HookPointState.FOUND &&
            perkeyHitTestMethod != null && statusMap["perkey_hit_test"] == HookPointState.FOUND &&
            perkeyLeftField != null && perkeyRightField != null &&
            perkeyTopField != null && perkeyBottomField != null &&
            statusMap["perkey_bounds"] == HookPointState.FOUND

    fun isReadyForMotion(): Boolean =
        isReadyForPerKey() &&
            perkeyDrawMethod != null &&
            statusMap["perkey_draw"] == HookPointState.FOUND &&
            perkeyRedrawMethod != null &&
            statusMap["perkey_redraw"] == HookPointState.FOUND

    fun isReadyForMotionShadows(): Boolean =
        isReadyForMotion() &&
            keyboardDrawMethod != null &&
            statusMap["effects_draw"] == HookPointState.FOUND &&
            perkeyShadowUnderlayMethod != null &&
            statusMap["perkey_shadow_underlay"] == HookPointState.FOUND &&
            perkeyShadowOverlayMethod != null &&
            statusMap["perkey_shadow_overlay"] == HookPointState.FOUND

    fun toJson(): JSONObject = JSONObject().apply {
        put("ruleVersion", ruleVersion)
        put("hostFingerprint", hostFingerprint)
        put("moduleVersionCode", moduleVersionCode)
        put("lastAdaptedVersion", lastAdaptedVersion)
        whitelistField?.let { put("whitelistField", it.toJson()) }
        blurDecisionMethod?.let { put("blurDecisionMethod", it.toJson()) }
        blurApplyMethod?.let { put("blurApplyMethod", it.toJson()) }
        foldCheckMethod?.let { put("foldCheckMethod", it.toJson()) }
        keyboardTouchMethod?.let { put("keyboardTouchMethod", it.toJson()) }
        keyboardDrawMethod?.let { put("keyboardDrawMethod", it.toJson()) }
        dayNightCheckMethod?.let { put("dayNightCheckMethod", it.toJson()) }
        blurParamCreateMethod?.let { put("blurParamCreateMethod", it.toJson()) }
        keyBackgroundMethod?.let { put("keyBackgroundMethod", it.toJson()) }
        if (keyAlphaMethods.isNotEmpty()) {
            put("keyAlphaMethods", JSONArray().apply { keyAlphaMethods.forEach { put(it.toJson()) } })
        }
        keyBgAlpha255Method?.let { put("keyBgAlpha255Method", it.toJson()) }
        if (keyBufferRefreshMethods.isNotEmpty()) {
            put("keyBufferRefreshMethods", JSONArray().apply { keyBufferRefreshMethods.forEach { put(it.toJson()) } })
        }
        toolbarLayoutMethod?.let { put("toolbarLayoutMethod", it.toJson()) }
        toolbarItemIconMethod?.let { put("toolbarItemIconMethod", it.toJson()) }
        toolCatalogCreateMethod?.let { put("toolCatalogCreateMethod", it.toJson()) }
        toolboxDataMethod?.let { put("toolboxDataMethod", it.toJson()) }
        toolStateManagerTransformMethod?.let { put("toolStateManagerTransformMethod", it.toJson()) }
        themeIconMethod?.let { put("themeIconMethod", it.toJson()) }
        themeManagerClass?.let { put("themeManagerClass", it.toJson()) }
        themeManagerLazyField?.let { put("themeManagerLazyField", it.toJson()) }
        themeManagerInnerMethod?.let { put("themeManagerInnerMethod", it.toJson()) }
        themeManagerApplyMethod?.let { put("themeManagerApplyMethod", it.toJson()) }
        perkeyModelGetter?.let { put("perkeyModelGetter", it.toJson()) }
        perkeyEntityClass?.let { put("perkeyEntityClass", it.toJson()) }
        perkeyHitTestMethod?.let { put("perkeyHitTestMethod", it.toJson()) }
        perkeyLeftField?.let { put("perkeyLeftField", it.toJson()) }
        perkeyRightField?.let { put("perkeyRightField", it.toJson()) }
        perkeyTopField?.let { put("perkeyTopField", it.toJson()) }
        perkeyBottomField?.let { put("perkeyBottomField", it.toJson()) }
        perkeyDrawMethod?.let { put("perkeyDrawMethod", it.toJson()) }
        perkeyShadowUnderlayMethod?.let { put("perkeyShadowUnderlayMethod", it.toJson()) }
        perkeyShadowOverlayMethod?.let { put("perkeyShadowOverlayMethod", it.toJson()) }
        perkeyPressedField?.let { put("perkeyPressedField", it.toJson()) }
        perkeyRedrawMethod?.let { put("perkeyRedrawMethod", it.toJson()) }
        val sMap = JSONObject()
        statusMap.forEach { (k, v) -> sMap.put(k, v.name) }
        put("statusMap", sMap)
        val errArr = JSONArray()
        scanErrors.forEach { errArr.put(it) }
        put("scanErrors", errArr)
    }

    companion object {
        fun fromJson(json: JSONObject): OplusInputSymbols {
            val statusMap = mutableMapOf<String, HookPointState>()
            if (json.has("statusMap")) {
                val sMap = json.getJSONObject("statusMap")
                for (k in sMap.keys()) {
                    val state = runCatching { HookPointState.valueOf(sMap.getString(k)) }
                        .getOrDefault(HookPointState.ERROR)
                    statusMap[k] = state
                }
            }
            val scanErrors = mutableListOf<String>()
            if (json.has("scanErrors")) {
                val errArr = json.getJSONArray("scanErrors")
                for (i in 0 until errArr.length()) {
                    scanErrors.add(errArr.getString(i))
                }
            }
            return OplusInputSymbols(
                ruleVersion = json.optInt("ruleVersion", 18),
                hostFingerprint = json.optString("hostFingerprint", ""),
                moduleVersionCode = json.optInt("moduleVersionCode", 0),
                lastAdaptedVersion = json.optString("lastAdaptedVersion", ""),
                whitelistField = if (json.has("whitelistField")) FieldDescriptor.fromJson(json.getJSONObject("whitelistField")) else null,
                blurDecisionMethod = if (json.has("blurDecisionMethod")) MethodDescriptor.fromJson(json.getJSONObject("blurDecisionMethod")) else null,
                blurApplyMethod = if (json.has("blurApplyMethod")) MethodDescriptor.fromJson(json.getJSONObject("blurApplyMethod")) else null,
                foldCheckMethod = if (json.has("foldCheckMethod")) MethodDescriptor.fromJson(json.getJSONObject("foldCheckMethod")) else null,
                keyboardTouchMethod = if (json.has("keyboardTouchMethod")) MethodDescriptor.fromJson(json.getJSONObject("keyboardTouchMethod")) else null,
                keyboardDrawMethod = if (json.has("keyboardDrawMethod")) MethodDescriptor.fromJson(json.getJSONObject("keyboardDrawMethod")) else null,
                dayNightCheckMethod = if (json.has("dayNightCheckMethod")) MethodDescriptor.fromJson(json.getJSONObject("dayNightCheckMethod")) else null,
                blurParamCreateMethod = if (json.has("blurParamCreateMethod")) MethodDescriptor.fromJson(json.getJSONObject("blurParamCreateMethod")) else null,
                keyBackgroundMethod = if (json.has("keyBackgroundMethod")) MethodDescriptor.fromJson(json.getJSONObject("keyBackgroundMethod")) else null,
                keyAlphaMethods = json.optJSONArray("keyAlphaMethods")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { MethodDescriptor.fromJson(it) }
                }
            } ?: emptyList(),
                keyBgAlpha255Method = if (json.has("keyBgAlpha255Method")) MethodDescriptor.fromJson(json.getJSONObject("keyBgAlpha255Method")) else null,
                keyBufferRefreshMethods = json.optJSONArray("keyBufferRefreshMethods")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { MethodDescriptor.fromJson(it) }
                    }
                } ?: emptyList(),
                toolbarLayoutMethod = if (json.has("toolbarLayoutMethod")) MethodDescriptor.fromJson(json.getJSONObject("toolbarLayoutMethod")) else null,
                toolbarItemIconMethod = if (json.has("toolbarItemIconMethod")) MethodDescriptor.fromJson(json.getJSONObject("toolbarItemIconMethod")) else null,
                toolCatalogCreateMethod = if (json.has("toolCatalogCreateMethod")) MethodDescriptor.fromJson(json.getJSONObject("toolCatalogCreateMethod")) else null,
                toolboxDataMethod = if (json.has("toolboxDataMethod")) MethodDescriptor.fromJson(json.getJSONObject("toolboxDataMethod")) else null,
                toolStateManagerTransformMethod = if (json.has("toolStateManagerTransformMethod")) MethodDescriptor.fromJson(json.getJSONObject("toolStateManagerTransformMethod")) else null,
                themeIconMethod = if (json.has("themeIconMethod")) MethodDescriptor.fromJson(json.getJSONObject("themeIconMethod")) else null,
                themeManagerClass = if (json.has("themeManagerClass")) ClassDescriptor.fromJson(json.getJSONObject("themeManagerClass")) else null,
                themeManagerLazyField = if (json.has("themeManagerLazyField")) FieldDescriptor.fromJson(json.getJSONObject("themeManagerLazyField")) else null,
                themeManagerInnerMethod = if (json.has("themeManagerInnerMethod")) MethodDescriptor.fromJson(json.getJSONObject("themeManagerInnerMethod")) else null,
                themeManagerApplyMethod = if (json.has("themeManagerApplyMethod")) MethodDescriptor.fromJson(json.getJSONObject("themeManagerApplyMethod")) else null,
                perkeyModelGetter = if (json.has("perkeyModelGetter")) MethodDescriptor.fromJson(json.getJSONObject("perkeyModelGetter")) else null,
                perkeyEntityClass = if (json.has("perkeyEntityClass")) ClassDescriptor.fromJson(json.getJSONObject("perkeyEntityClass")) else null,
                perkeyHitTestMethod = if (json.has("perkeyHitTestMethod")) MethodDescriptor.fromJson(json.getJSONObject("perkeyHitTestMethod")) else null,
                perkeyLeftField = if (json.has("perkeyLeftField")) FieldDescriptor.fromJson(json.getJSONObject("perkeyLeftField")) else null,
                perkeyRightField = if (json.has("perkeyRightField")) FieldDescriptor.fromJson(json.getJSONObject("perkeyRightField")) else null,
                perkeyTopField = if (json.has("perkeyTopField")) FieldDescriptor.fromJson(json.getJSONObject("perkeyTopField")) else null,
                perkeyBottomField = if (json.has("perkeyBottomField")) FieldDescriptor.fromJson(json.getJSONObject("perkeyBottomField")) else null,
                perkeyDrawMethod = if (json.has("perkeyDrawMethod")) MethodDescriptor.fromJson(json.getJSONObject("perkeyDrawMethod")) else null,
                perkeyShadowUnderlayMethod = if (json.has("perkeyShadowUnderlayMethod")) MethodDescriptor.fromJson(json.getJSONObject("perkeyShadowUnderlayMethod")) else null,
                perkeyShadowOverlayMethod = if (json.has("perkeyShadowOverlayMethod")) MethodDescriptor.fromJson(json.getJSONObject("perkeyShadowOverlayMethod")) else null,
                perkeyPressedField = if (json.has("perkeyPressedField")) FieldDescriptor.fromJson(json.getJSONObject("perkeyPressedField")) else null,
                perkeyRedrawMethod = if (json.has("perkeyRedrawMethod")) MethodDescriptor.fromJson(json.getJSONObject("perkeyRedrawMethod")) else null,
                statusMap = statusMap,
                scanErrors = scanErrors
            )
        }
    }
}
