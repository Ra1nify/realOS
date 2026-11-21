package io.github.ra1nify.realos.opluskeyboard

import android.content.Context
import android.content.res.Resources
import android.os.Build
import io.github.ra1nify.realos.util.ModuleResources
import io.github.ra1nify.realos.util.RealOSLog as Log
import androidx.annotation.StringRes
import io.github.ra1nify.realos.R
import java.util.Locale
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText

/**
 * User-visible strings for the injected ColorOS Appearance Workshop.
 *
 * Reads the module APK resources via [getModuleResources]. Translations live in
 * `res/values`, `res/values-zh` and `res/values-b+zh+Hant`; there is no in-code table.
 */
object OplusWorkshopUiText {

    private const val TAG = "OplusWorkshopUiText"

    /** Delegates to the shared resolver; kept for the existing call sites. */
    fun getModuleResources(context: Context): Resources? = ModuleResources.get(context)

    /** Returns a localized string; never throws for a missing resource. */
    fun getString(context: Context, @StringRes resId: Int): String {
        // 1. Try reading from module APK resources
        val moduleRes = getModuleResources(context)
        if (moduleRes != null) {
            val resStr = runCatching { moduleRes.getString(resId) }.getOrNull()
            if (!resStr.isNullOrEmpty()) {
                return resStr
            }
        }

        // Module APK resources are the only source: the in-memory table that used to back this
        // path was a duplicate of strings.xml, so a miss here is a real defect worth surfacing.
        Log.w(TAG, "Module string 0x${Integer.toHexString(resId)} unavailable")
        return ""
    }

    /** Returns a localized formatted string. */
    fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        val raw = getString(context, resId)
        return runCatching { String.format(getActiveLocale(context), raw, *formatArgs) }.getOrDefault(raw)
    }

    private fun getActiveLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        } ?: Locale.getDefault()
    }


    fun getLiquidGlassTitle(context: Context): String = getString(context, R.string.oplus_input_liquid_glass_title)
    fun getLiquidGlassPrefTitle(context: Context): String = getString(context, R.string.oplus_input_liquid_glass_pref_title)
    fun getKeyboardEffectsTitle(context: Context): String = getString(context, R.string.oplus_input_keyboard_effects_title)
    fun getKeyboardEffectsPrefTitle(context: Context): String = getString(context, R.string.oplus_input_keyboard_effects_pref_title)
    fun getEnhancementsTitle(context: Context): String = getString(context, R.string.oplus_input_enhancements_title)
    fun getEnhancementsPrefTitle(context: Context): String = getString(context, R.string.oplus_input_enhancements_pref_title)
    fun getPoweredByRealOS(context: Context): String = getString(context, R.string.oplus_input_powered_by_realos)
    @Deprecated("Renamed to getPoweredByRealOS", ReplaceWith("getPoweredByRealOS(context)"))
    fun getPoweredByLuckyFuse(context: Context): String = getPoweredByRealOS(context)
    fun getHelloWorldTitle(context: Context): String = getString(context, R.string.oplus_input_hello_world_title)
    fun getHelloWorldDesc(context: Context): String = getString(context, R.string.oplus_input_hello_world_desc)

    fun getSectionFeatures(context: Context): String = getString(context, R.string.oplus_input_section_features)
    fun getSectionMaterial(context: Context): String = getString(context, R.string.oplus_input_section_material)
    fun getSectionKeyboardEffects(context: Context): String = getString(context, R.string.oplus_input_section_keyboard_effects)
    fun getSectionAdvanced(context: Context): String = getString(context, R.string.oplus_input_section_advanced)

    /** Page-load failure text (see CouiComponentFactory.createPageLoadFailedView). */
    fun getPageLoadFailedTitle(context: Context): String =
        getString(context, R.string.oplus_input_page_load_failed_title)

    fun getPageLoadFailedHint(context: Context): String =
        getString(context, R.string.oplus_input_page_load_failed_hint)

    fun getToolbarTitle(context: Context): String = getString(context, R.string.oplus_input_toolbar_title)
    fun getOfficialSkinBtn(context: Context): String = getString(context, R.string.oplus_input_official_skin_btn)
    fun getAutofillSwitchTitle(context: Context): String = getString(context, R.string.oplus_input_autofill_switch_title)
    fun getAutofillSwitchSummary(context: Context): String = getString(context, R.string.oplus_input_autofill_switch_summary)
    fun getAutofillSwitchNativeSummary(context: Context): String = getString(context, R.string.oplus_input_autofill_switch_native_summary)
    fun getAutofillButtonSwitchTitle(context: Context): String = getString(context, R.string.oplus_input_autofill_button_switch_title)
    fun getAutofillButtonSwitchSummary(context: Context): String = getString(context, R.string.oplus_input_autofill_button_switch_summary)
    fun getSystemInlineAutofillTitle(context: Context): String = getString(context, R.string.system_oplus_autofill_inline_title)
    fun getSystemInlineAutofillDesc(context: Context): String = getString(context, R.string.system_oplus_autofill_inline_desc)

    fun getBannerRestrictionWarn(context: Context): String = getString(context, R.string.oplus_input_banner_restriction_warn)
    fun getBannerResetButton(context: Context): String = getString(context, R.string.oplus_input_banner_reset_button)

    fun getToolbarPreviewKeyboardDesc(context: Context): String = getString(context, R.string.oplus_input_toolbar_preview_keyboard_desc)
    fun getBotHint(context: Context): String = getString(context, R.string.oplus_input_bot_hint)

    fun getPreviewTitle(context: Context): String = getString(context, R.string.oplus_input_preview_title)
    fun getSwitchForceBlurTitle(context: Context): String = getString(context, R.string.oplus_input_switch_force_blur_title)
    fun getSwitchForceBlurSummary(context: Context): String = getString(context, R.string.oplus_input_switch_force_blur_summary)
    fun getSwitchBypassDarkTitle(context: Context): String = getString(context, R.string.oplus_input_switch_bypass_dark_title)
    fun getSwitchBypassDarkSummary(context: Context): String = getString(context, R.string.oplus_input_switch_bypass_dark_summary)
    fun getSwitchBypassAllLimitsTitle(context: Context): String = getString(context, R.string.oplus_input_switch_bypass_all_limits_title)
    fun getSwitchBypassAllLimitsSummary(context: Context): String = getString(context, R.string.oplus_input_switch_bypass_all_limits_summary)

    fun getGroupMaterialSliders(context: Context): String = getString(context, R.string.oplus_input_group_material_sliders)
    fun getSliderLightAlpha(context: Context): String = getString(context, R.string.oplus_input_slider_light_alpha)
    fun getSliderDarkAlpha(context: Context): String = getString(context, R.string.oplus_input_slider_dark_alpha)
    fun getSliderBlurRadius(context: Context): String = getString(context, R.string.oplus_input_slider_blur_radius)
    fun getSliderKeyOpacity(context: Context): String = getString(context, R.string.oplus_input_slider_key_opacity)
    fun getSliderCornerRadius(context: Context): String = getString(context, R.string.oplus_input_slider_corner_radius)

    fun getSectionCoreMaterial(context: Context): String = getString(context, R.string.oplus_input_section_core_material)
    fun getSectionLightCustom(context: Context): String = getString(context, R.string.oplus_input_section_light_custom)
    fun getSectionDarkCustom(context: Context): String = getString(context, R.string.oplus_input_section_dark_custom)

    fun getLightMaskColorTitle(context: Context): String = getString(context, R.string.oplus_input_light_mask_color)
    fun getDarkMaskColorTitle(context: Context): String = getString(context, R.string.oplus_input_dark_mask_color)
    fun getColorPickerTitle(context: Context): String = getString(context, R.string.oplus_input_color_picker_title)
    fun getColorHexHint(context: Context): String = getString(context, R.string.oplus_input_color_hex_hint)
    fun getSaturationContrastTitle(context: Context): String = getString(context, R.string.oplus_input_saturation_contrast_title)

    fun getBgOverlaySwitchTitle(context: Context): String = getString(context, R.string.oplus_input_bg_overlay_switch_title)
    fun getBgOverlaySwitchSummary(context: Context): String = getString(context, R.string.oplus_input_bg_overlay_switch_summary)
    fun getBgImageTitle(context: Context): String = getString(context, R.string.oplus_input_bg_image_title)
    fun getBgImageNotSet(context: Context): String = getString(context, R.string.oplus_input_bg_image_not_set)
    fun getBgImageIsSet(context: Context): String = getString(context, R.string.oplus_input_bg_image_is_set)
    fun getBgOverlayAlphaTitle(context: Context): String = getString(context, R.string.oplus_input_bg_overlay_alpha_title)

    fun getClearBgImageDialogTitle(context: Context): String = getString(context, R.string.oplus_input_clear_bg_image_dialog_title)
    fun getClearBgImageDialogMsg(context: Context): String = getString(context, R.string.oplus_input_clear_bg_image_dialog_msg)
    fun getDialogClear(context: Context): String = getString(context, R.string.oplus_input_dialog_clear)
    fun getDialogReplace(context: Context): String = getString(context, R.string.oplus_input_dialog_replace)
    fun getDialogCancel(context: Context): String = getString(context, R.string.oplus_input_dialog_cancel)
    fun getDialogOk(context: Context): String = getString(context, R.string.oplus_input_dialog_ok)

    fun getKeysCafeTitle(context: Context): String = getString(context, R.string.oplus_input_keys_cafe_title)
    fun getKeysCafeDesc(context: Context): String = getString(context, R.string.oplus_input_keys_cafe_desc)
    fun getSwitchEffectsMasterTitle(context: Context): String = getString(context, R.string.oplus_input_switch_effects_master_title)
    fun getSwitchEffectsMasterSummary(context: Context): String = getString(context, R.string.oplus_input_switch_effects_master_summary)

    fun getSectionEffectsBoard(context: Context): String = getString(context, R.string.oplus_input_section_effects_board)
    fun getSectionEffectsKey(context: Context): String = getString(context, R.string.oplus_input_section_effects_key)
    fun getSectionEffectsMotion(context: Context): String = getString(context, R.string.oplus_input_section_effects_motion)
    fun getSectionEffectsColor(context: Context): String = getString(context, R.string.oplus_input_section_effects_color)
    fun getSectionEffectsParams(context: Context): String = getString(context, R.string.oplus_input_section_effects_params)
    fun getSliderEffectsAlpha(context: Context): String = getString(context, R.string.oplus_input_slider_effects_alpha)
    fun getSliderEffectsRange(context: Context): String = getString(context, R.string.oplus_input_slider_effects_range)
    fun getSliderEffectsSpeed(context: Context): String = getString(context, R.string.oplus_input_slider_effects_speed)

    // ── Foot Print icon picker ──
    fun getSectionFootPrint(context: Context): String = getString(context, R.string.oplus_input_section_effects_foot_print)
    fun getFootPrintCustomPick(context: Context): String = getString(context, R.string.oplus_input_foot_print_custom_pick)
    fun getFootPrintCustomClear(context: Context): String = getString(context, R.string.oplus_input_foot_print_custom_clear)

    // System navigation page (settings injection).
    fun getNavGestureEntryTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_entry_title)
    fun getNavButtonEntryTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_entry_title)
    fun getNavButtonIconTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_icon_title)
    fun getNavButtonGlassTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_title)
    fun getNavButtonGlassSpotlightTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_spotlight_title)
    fun getNavButtonGlassSpotlightSummary(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_spotlight_summary)
    fun getNavButtonGlassCapsuleSection(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_section)
    fun getNavButtonGlassCapsuleSummary(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_summary)
    fun getNavButtonGlassCapsuleModeNone(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_mode_none)
    fun getNavButtonGlassCapsuleModeSingle(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_mode_single)
    fun getNavButtonGlassCapsuleModeTriple(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_mode_triple)
    fun getNavButtonGlassCapsuleModePairBackHome(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_mode_pair_back_home)
    fun getNavButtonGlassCapsuleModePairHomeRecent(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_mode_pair_home_recent)
    fun getNavPoweredBy(context: Context): String =
        getString(context, R.string.settings_nav_powered_by)
    fun getNavPageSectionFeatures(context: Context): String =
        getString(context, R.string.settings_nav_page_section_features)

    fun getNavButtonCustomSwitchTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_switch_title)
    fun getNavButtonCustomSwitchSummary(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_switch_summary)
    fun getNavButtonTintSwitchTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_tint_switch_title)
    fun getNavButtonTintSwitchSummary(context: Context): String =
        getString(context, R.string.settings_nav_button_tint_switch_summary)
    fun getNavButtonTintColorTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_tint_color_title)
    fun getNavButtonSectionLightTint(context: Context): String =
        getString(context, R.string.settings_nav_button_section_light_tint)
    fun getNavButtonTintLightColorTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_tint_light_color_title)
    fun getNavButtonSectionDarkTint(context: Context): String =
        getString(context, R.string.settings_nav_button_section_dark_tint)
    fun getNavButtonTintDarkColorTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_tint_dark_color_title)
    fun getNavButtonPresetCategoryTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_preset_category_title)
    fun getNavButtonPresetCustomTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_preset_custom_title)
    fun getNavButtonPresetItemTitle(context: Context, index: Int): String =
        if (index == 11) getNavButtonPresetCustomTitle(context)
        else getString(context, R.string.settings_nav_button_preset_item_title, index)

    fun getNavButtonCustomSectionTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_section_title)
    fun getNavButtonCustomBackTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_back_title)
    fun getNavButtonCustomHomeTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_home_title)
    fun getNavButtonCustomRecentTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_recent_title)
    fun getNavButtonCustomHintEmpty(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_hint_empty)
    fun getNavButtonCustomHintSet(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_hint_set)
    fun getNavButtonCustomReset(context: Context): String =
        getString(context, R.string.settings_nav_button_custom_reset)

    /** Back-gesture vibration switch (full-screen gesture settings; cancel-zone weak pulse only). */
    fun getNavGestureVibrateTitle(context: Context): String =
        getString(context, R.string.settings_nav_page_vibrate_title)
    fun getNavGestureVibrateSummary(context: Context): String =
        getString(context, R.string.settings_nav_page_vibrate_summary)

    // Three subpage entries for Full-screen gesture settings (parent list + child top bars).
    fun getNavSubVibrationTitle(context: Context): String =
        getString(context, R.string.settings_nav_sub_vibration_title)
    fun getNavSubFeedbackTitle(context: Context): String =
        getString(context, R.string.settings_nav_sub_feedback_title)
    fun getNavSubAppearanceTitle(context: Context): String =
        getString(context, R.string.settings_nav_sub_appearance_title)
    fun getNavSubVisualTitle(context: Context): String =
        getString(context, R.string.settings_nav_sub_visual_title)
    fun getNavSubGlassTitle(context: Context): String =
        getString(context, R.string.settings_nav_sub_glass_title)
    fun getNavSubAdvancedTitle(context: Context): String =
        getString(context, R.string.settings_nav_sub_advanced_title)

    /** In-development line for placeholder subpages (liquid glass / advanced settings). */
    fun getNavSubWip(context: Context): String =
        getString(context, R.string.settings_nav_sub_wip)

    // Back-gesture appearance (full-screen gesture settings).
    fun getNavAppearReverseLeft(context: Context): String =
        getString(context, R.string.settings_nav_appear_reverse_left)
    fun getNavAppearReverseLeftSummary(context: Context): String =
        getString(context, R.string.settings_nav_appear_reverse_left_summary)
    fun getNavAppearMaterialMode(context: Context): String =
        getString(context, R.string.settings_nav_appear_material_mode)
    fun getNavAppearMaterialStock(context: Context): String =
        getString(context, R.string.settings_nav_appear_material_stock)
    fun getNavAppearMaterialGlass(context: Context): String =
        getString(context, R.string.settings_nav_appear_material_glass)
    fun getNavAppearGlassMatrixDiy(context: Context): String =
        getString(context, R.string.settings_nav_appear_glass_matrix_diy)
    fun getNavAppearGlassMatrixDiySummary(context: Context): String =
        getString(context, R.string.settings_nav_appear_glass_matrix_diy_summary)
    fun getNavAppearGlassStroke(context: Context): String =
        getString(context, R.string.settings_nav_appear_glass_stroke)
    fun getNavAppearGlassStrokeSummary(context: Context): String =
        getString(context, R.string.settings_nav_appear_glass_stroke_summary)
    fun getNavAppearGlassBlur(context: Context): String =
        getString(context, R.string.settings_nav_appear_glass_blur)
    fun getNavAppearMaskAlpha(context: Context): String =
        getString(context, R.string.settings_nav_appear_mask_alpha)
    fun getNavAppearMaskColor(context: Context): String =
        getString(context, R.string.settings_nav_appear_mask_color)

    // Full-screen navigation handle (gesture visual settings).
    fun getNavHandleSectionTitle(context: Context): String =
        getString(context, R.string.settings_nav_handle_section_title)
    fun getNavHandleLengthTitle(context: Context): String =
        getString(context, R.string.settings_nav_handle_length_title)
    fun getNavHandleLengthSummary(context: Context): String =
        getString(context, R.string.settings_nav_handle_length_summary)
    fun getNavHandleGlassTitle(context: Context): String =
        getString(context, R.string.settings_nav_handle_glass_title)
    fun getNavHandleGlassSummary(context: Context): String =
        getString(context, R.string.settings_nav_handle_glass_summary)
    fun getNavHandleStrokeTitle(context: Context): String =
        getString(context, R.string.settings_nav_handle_stroke_title)
    fun getNavHandleStrokeSummary(context: Context): String =
        getString(context, R.string.settings_nav_handle_stroke_summary)
    fun getNavAppearShadowColor(context: Context): String =
        getString(context, R.string.settings_nav_appear_shadow_color)
    fun getNavAppearShadowAlpha(context: Context): String =
        getString(context, R.string.settings_nav_appear_shadow_alpha)
    fun getNavAppearStrokeColor(context: Context): String =
        getString(context, R.string.settings_nav_appear_stroke_color)
    fun getNavAppearStrokeAlpha(context: Context): String =
        getString(context, R.string.settings_nav_appear_stroke_alpha)
    fun getNavAppearStrokeWidth(context: Context): String =
        getString(context, R.string.settings_nav_appear_stroke_width)
    fun getNavAppearArrowColor(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_color)
    fun getNavAppearArrowAlpha(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_alpha)
    fun getNavAppearArrowSize(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_size)
    fun getNavAppearConflictTitle(context: Context): String =
        getString(context, R.string.settings_nav_appear_conflict_title)
    fun getNavAppearConflictColor(context: Context): String =
        getString(context, R.string.settings_nav_appear_conflict_color)
    fun getNavAppearConflictDrawable(context: Context): String =
        getString(context, R.string.settings_nav_appear_conflict_drawable)

    fun getNavAppearCardBase(context: Context): String =
        getString(context, R.string.settings_nav_appear_card_base)
    fun getNavAppearCardLight(context: Context): String =
        getString(context, R.string.settings_nav_appear_card_light)
    fun getNavAppearCardDark(context: Context): String =
        getString(context, R.string.settings_nav_appear_card_dark)
    fun getNavAppearBasePlaceholder(context: Context): String =
        getString(context, R.string.settings_nav_appear_base_placeholder)

    // Arrow Experience mode, icon selection, dynamic style (arrow customization baseline card).
    fun getNavAppearArrowExperience(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_experience)
    fun getNavAppearArrowExperienceStock(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_experience_stock)
    fun getNavAppearArrowExperienceDynamic(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_experience_dynamic)
    fun getNavAppearDynamicStyle(context: Context): String =
        getString(context, R.string.settings_nav_appear_dynamic_style)
    fun getNavAppearStyleDotMatrix5(context: Context): String =
        getString(context, R.string.settings_nav_appear_style_dot_matrix_5)
    fun getNavAppearStyleDotFlow(context: Context): String =
        getString(context, R.string.settings_nav_appear_style_dot_flow)
    fun getNavAppearStyleElasticChevron(context: Context): String =
        getString(context, R.string.settings_nav_appear_style_elastic_chevron)
    fun getNavAppearStylePulseBars(context: Context): String =
        getString(context, R.string.settings_nav_appear_style_pulse_bars)
    fun getNavAppearStyleBowstring(context: Context): String =
        getString(context, R.string.settings_nav_appear_style_bowstring)
    fun getNavAppearArrowIcon(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_icon)
    fun getNavAppearIconSystem(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_system)
    fun getNavAppearIconRound(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_round)
    fun getNavAppearIconRoundStem(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_round_stem)
    fun getNavAppearIconDot(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_dot)
    fun getNavAppearIconDotStem(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_dot_stem)
    fun getNavAppearIconTriangle(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_triangle)
    fun getNavAppearIconTriangleOutline(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_triangle_outline)
    fun getNavAppearIconCustom(context: Context): String =
        getString(context, R.string.settings_nav_appear_icon_custom)
    fun getNavAppearArrowDiy(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_diy)
    fun getNavAppearArrowDiySummary(context: Context): String =
        getString(context, R.string.settings_nav_appear_arrow_diy_summary)
    fun getNavAppearShadowDiy(context: Context): String =
        getString(context, R.string.settings_nav_appear_shadow_diy)
    fun getNavAppearShadowDiySummary(context: Context): String =
        getString(context, R.string.settings_nav_appear_shadow_diy_summary)
    fun getNavAppearStrokeDiy(context: Context): String =
        getString(context, R.string.settings_nav_appear_stroke_diy)
    fun getNavAppearStrokeDiySummary(context: Context): String =
        getString(context, R.string.settings_nav_appear_stroke_diy_summary)

    // Two-stage back-gesture vibration tuning.
    fun getNavVibState1(context: Context): String =
        getString(context, R.string.settings_nav_vib_state1)
    fun getNavVibState2(context: Context): String =
        getString(context, R.string.settings_nav_vib_state2)
    fun getNavVibWeakStrength(context: Context): String =
        getString(context, R.string.settings_nav_vib_weak_strength)
    fun getNavVibStep(context: Context): String =
        getString(context, R.string.settings_nav_vib_step)
    fun getNavVibInterval(context: Context): String =
        getString(context, R.string.settings_nav_vib_interval)
    fun getNavVibStrongStrength(context: Context): String =
        getString(context, R.string.settings_nav_vib_strong_strength)
    fun getNavVibTakeOver(context: Context): String =
        getString(context, R.string.settings_nav_vib_takeover)
    fun getNavVibTakeOverSummary(context: Context): String =
        getString(context, R.string.settings_nav_vib_takeover_summary)

    fun getNavButtonConflictBack(context: Context): String =
        getString(context, R.string.settings_nav_button_conflict_back)
    fun getNavButtonConflictHome(context: Context): String =
        getString(context, R.string.settings_nav_button_conflict_home)
    fun getNavButtonConflictRecent(context: Context): String =
        getString(context, R.string.settings_nav_button_conflict_recent)
    fun getNavButtonConflictOverride(context: Context): String =
        getString(context, R.string.settings_nav_button_conflict_override)

    fun getNavButtonSectionLightMask(context: Context): String =
        getString(context, R.string.settings_nav_button_section_light_mask)
    fun getNavButtonLightMaskColor(context: Context): String =
        getString(context, R.string.settings_nav_button_light_mask_color)
    fun getNavButtonLightMaskAlpha(context: Context): String =
        getString(context, R.string.settings_nav_button_light_mask_alpha)

    fun getNavButtonSectionDarkMask(context: Context): String =
        getString(context, R.string.settings_nav_button_section_dark_mask)
    fun getNavButtonDarkMaskColor(context: Context): String =
        getString(context, R.string.settings_nav_button_dark_mask_color)
    fun getNavButtonDarkMaskAlpha(context: Context): String =
        getString(context, R.string.settings_nav_button_dark_mask_alpha)

    // Full-screen gesture liquid glass.
    fun getNavGestureGlassSectionHandle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_section_handle)
    fun getNavGestureHandleGlassTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_handle_glass_title)
    fun getNavGestureHandleGlassSummary(context: Context): String =
        getString(context, R.string.settings_nav_gesture_handle_glass_summary)
    fun getNavGestureGlassCausticTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_caustic_title)
    fun getNavGestureGlassCausticSummary(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_caustic_summary)
    fun getNavGestureGlassSpotlightTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_spotlight_title)
    fun getNavGestureGlassSpotlightSummary(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_spotlight_summary)

    fun getNavGestureGlassSectionSide(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_section_side)
    fun getNavGestureSideGlassTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_side_glass_title)
    fun getNavGestureSideGlassSummary(context: Context): String =
        getString(context, R.string.settings_nav_gesture_side_glass_summary)
    fun getNavGestureGlassStrokeTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_stroke_title)
    fun getNavGestureGlassStrokeSummary(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_stroke_summary)
    fun getNavGestureGlassInnerShadowTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_inner_shadow_title)
    fun getNavGestureGlassInnerShadowSummary(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_inner_shadow_summary)
    fun getNavGestureGlassChevronPreserveTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_chevron_preserve_title)
    fun getNavGestureGlassChevronPreserveSummary(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_chevron_preserve_summary)

    fun getNavGestureGlassSectionLight(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_section_light)
    fun getNavGestureGlassLightAlphaTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_light_alpha_title)
    fun getNavGestureGlassSectionDark(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_section_dark)
    fun getNavGestureGlassDarkAlphaTitle(context: Context): String =
        getString(context, R.string.settings_nav_gesture_glass_dark_alpha_title)

    // Keyboard mechanical sounds.
    fun getKeyboardSoundsTitle(context: Context): String =
        getString(context, R.string.oplus_input_keyboard_sounds_title)
    fun getKeyboardSoundsDesc(context: Context): String =
        getString(context, R.string.oplus_input_keyboard_sounds_desc)
    fun getKeyboardSoundsPrefTitle(context: Context): String =
        getString(context, R.string.oplus_input_keyboard_sounds_pref_title)
    fun getSoundSwitchTitle(context: Context): String =
        getString(context, R.string.oplus_input_sound_switch_title)
    fun getSoundSwitchSummary(context: Context): String =
        getString(context, R.string.oplus_input_sound_switch_summary)
    fun getSoundStyleTitle(context: Context): String =
        getString(context, R.string.oplus_input_sound_style_title)
    fun getSoundVolumeTitle(context: Context): String =
        getString(context, R.string.oplus_input_sound_volume_title)
    fun getSoundTestPadTitle(context: Context): String =
        getString(context, R.string.oplus_input_sound_test_pad_title)
    fun getSoundTestStandard(context: Context): String =
        getString(context, R.string.oplus_input_sound_test_standard)
    fun getSoundTestDelete(context: Context): String =
        getString(context, R.string.oplus_input_sound_test_delete)
    fun getSoundTestFunction(context: Context): String =
        getString(context, R.string.oplus_input_sound_test_function)

    // Custom color palette.
    fun getEffectColorCustom(context: Context): String =
        getString(context, R.string.oplus_input_effect_color_custom)

    fun getNavSectionVisualTuning(context: Context): String = getString(context, R.string.settings_nav_section_visual_tuning)
    fun getNavSectionHapticTuning(context: Context): String = getString(context, R.string.settings_nav_section_haptic_tuning)
    fun getNavSectionLogicTuning(context: Context): String = getString(context, R.string.settings_nav_section_logic_tuning)
    fun getNavSectionAdvancedTuning(context: Context): String = getString(context, R.string.settings_nav_section_advanced_tuning)
    fun getNavUiSectionTitle(context: Context): String = getString(context, R.string.settings_nav_ui_section_title)

    fun getNavGestureHandleMaskTitle(context: Context): String = getString(context, R.string.settings_nav_gesture_handle_mask_title)
    fun getNavGestureHandleMaskSummary(context: Context): String = getString(context, R.string.settings_nav_gesture_handle_mask_summary)
    fun getNavAppearHandleDiy(context: Context): String = getString(context, R.string.settings_nav_appear_handle_diy)
    fun getNavAppearHandleDiySummary(context: Context): String = getString(context, R.string.settings_nav_appear_handle_diy_summary)
    fun getNavGestureSectionLightMask(context: Context): String = getString(context, R.string.settings_nav_gesture_section_light_mask)
    fun getNavGestureSectionDarkMask(context: Context): String = getString(context, R.string.settings_nav_gesture_section_dark_mask)
    fun getNavGestureMaskColor(context: Context): String = getString(context, R.string.settings_nav_gesture_mask_color)
    fun getNavGestureMaskAlpha(context: Context): String = getString(context, R.string.settings_nav_gesture_mask_alpha)
    fun getNavGestureTopExclusionTitle(context: Context): String = getString(context, R.string.settings_nav_gesture_top_exclusion_title)
    fun getNavGestureTopExclusionSummary(context: Context): String = getString(context, R.string.settings_nav_gesture_top_exclusion_summary)
    fun getNavGestureBottomExclusionTitle(context: Context): String = getString(context, R.string.settings_nav_gesture_bottom_exclusion_title)
    fun getNavGestureBottomExclusionSummary(context: Context): String = getString(context, R.string.settings_nav_gesture_bottom_exclusion_summary)
    fun getNavGestureRangeTitle(context: Context): String = getString(context, R.string.settings_nav_gesture_range_title)
    fun getNavGestureRangeItemTitle(context: Context): String = getString(context, R.string.settings_nav_gesture_range_item_title)
    fun getNavGestureRangeFooter(context: Context): String = getString(context, R.string.settings_nav_gesture_range_footer)
    fun getNavButtonGlassCapsuleSummarySpecial(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_capsule_summary_special)
    fun getNavButtonGlassMaskTitle(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_mask_title)
    fun getNavButtonGlassMaskSummary(context: Context): String =
        getString(context, R.string.settings_nav_button_glass_mask_summary)
    fun getEffectCustomPaletteTitle(context: Context): String =
        getString(context, R.string.oplus_input_effect_custom_palette_title)
    fun getEffectCustomPaletteAdd(context: Context): String =
        getString(context, R.string.oplus_input_effect_custom_palette_add)
    fun getEffectCustomPaletteEdit(context: Context): String =
        getString(context, R.string.oplus_input_effect_custom_palette_edit)
    fun getEffectCustomPaletteDelete(context: Context): String =
        getString(context, R.string.oplus_input_effect_custom_palette_delete)
}
