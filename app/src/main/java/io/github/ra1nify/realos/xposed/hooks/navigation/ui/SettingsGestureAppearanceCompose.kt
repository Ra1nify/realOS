package io.github.ra1nify.realos.xposed.hooks.navigation.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.navigation.ArrowIcon
import io.github.ra1nify.realos.navigation.GestureAppearance
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.InjectPage
import io.github.ra1nify.realos.ui.components.preference.JumpPreference
import io.github.ra1nify.realos.ui.components.preference.OverlayDropdownPreference
import io.github.ra1nify.realos.ui.components.preference.RadioButtonPreference
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.extend.preference.ColorPickerPreference
import io.github.ra1nify.realos.xposed.hooks.navigation.SystemUiNavBarPresetLoader

object SettingsGestureAppearanceCallbacks {
    const val REQ_PICK_ARROW_ICON = 2101

    var onCustomArrowPicked: (() -> Unit)? = null

    fun handleActivityResult(context: Context, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_PICK_ARROW_ICON) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true

        val bmp = SystemUiNavBarPresetLoader.decodeSampledBitmapFromUri(context, uri) ?: return true
        val squareBmp = SystemUiNavBarPresetLoader.createSquareBitmap(bmp, 120)
        bmp.recycle()
        val base64 = SystemUiNavBarPresetLoader.bitmapToBase64(squareBmp)
        SettingsConfig.setGestureBackCustomIcon(context, base64)
        SettingsConfig.notifyGestureGlassChanged(context)
        onCustomArrowPicked?.invoke()
        return true
    }
}

@Composable
fun SettingsGestureAppearanceCompose(activity: Activity) {
    val context = activity

    var appearance by remember {
        mutableStateOf(SettingsConfig.gestureAppearance(context))
    }

    var customArrowBase64 by remember {
        mutableStateOf(SettingsConfig.gestureBackCustomIconBase64(context))
    }

    DisposableEffect(Unit) {
        SettingsGestureAppearanceCallbacks.onCustomArrowPicked = {
            customArrowBase64 = SettingsConfig.gestureBackCustomIconBase64(context)
        }
        onDispose {
            SettingsGestureAppearanceCallbacks.onCustomArrowPicked = null
        }
    }

    fun updateAppearance(transform: (GestureAppearance) -> GestureAppearance) {
        val next = transform(appearance)
        appearance = next
        SettingsConfig.setGestureAppearance(context, next)
    }

    fun pickCustomArrow() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        activity.startActivityForResult(intent, SettingsGestureAppearanceCallbacks.REQ_PICK_ARROW_ICON)
    }

    // OverlayDropdownPreference accepts plain string entries; option labels are mapped here.
    fun arrowIconLabel(icon: ArrowIcon): String = when (icon) {
        ArrowIcon.SYSTEM -> OplusWorkshopUiText.getNavAppearIconSystem(context)
        ArrowIcon.ROUND -> OplusWorkshopUiText.getNavAppearIconRound(context)
        ArrowIcon.ROUND_STEM -> OplusWorkshopUiText.getNavAppearIconRoundStem(context)
        ArrowIcon.DOT -> OplusWorkshopUiText.getNavAppearIconDot(context)
        ArrowIcon.DOT_STEM -> OplusWorkshopUiText.getNavAppearIconDotStem(context)
        ArrowIcon.TRIANGLE -> OplusWorkshopUiText.getNavAppearIconTriangle(context)
        ArrowIcon.TRIANGLE_OUTLINE -> OplusWorkshopUiText.getNavAppearIconTriangleOutline(context)
        ArrowIcon.CUSTOM -> OplusWorkshopUiText.getNavAppearIconCustom(context)
    }

    fun dynamicStyleLabel(styleId: Int): String = when (styleId) {
        GestureAppearance.DYNAMIC_STYLE_DOT_MATRIX_5 -> OplusWorkshopUiText.getNavAppearStyleDotMatrix5(context)
        GestureAppearance.DYNAMIC_STYLE_DOT_FLOW -> OplusWorkshopUiText.getNavAppearStyleDotFlow(context)
        GestureAppearance.DYNAMIC_STYLE_ELASTIC_CHEVRON -> OplusWorkshopUiText.getNavAppearStyleElasticChevron(context)
        GestureAppearance.DYNAMIC_STYLE_PULSE_BARS -> OplusWorkshopUiText.getNavAppearStylePulseBars(context)
        GestureAppearance.DYNAMIC_STYLE_BOWSTRING -> OplusWorkshopUiText.getNavAppearStyleBowstring(context)
        else -> ""
    }

    InjectPage(
        title = OplusWorkshopUiText.getNavSectionVisualTuning(context),
        onBack = { activity.finish() },
    ) {
        // ── Section 1: Base Settings ──
        BasicComponentGroup(
            title = OplusWorkshopUiText.getNavAppearCardBase(context)
        ) {
            SwitchPreference(
                title = OplusWorkshopUiText.getNavAppearReverseLeft(context),
                summary = OplusWorkshopUiText.getNavAppearReverseLeftSummary(context),
                checked = appearance.reverseLeft,
                onCheckedChange = { checked ->
                    updateAppearance { it.copy(reverseLeft = checked) }
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = OplusWorkshopUiText.getNavAppearArrowDiy(context),
                summary = OplusWorkshopUiText.getNavAppearArrowDiySummary(context),
                checked = appearance.arrowDiyEnabled,
                onCheckedChange = { checked ->
                    updateAppearance { it.copy(arrowDiyEnabled = checked) }
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = OplusWorkshopUiText.getNavAppearShadowDiy(context),
                summary = OplusWorkshopUiText.getNavAppearShadowDiySummary(context),
                checked = appearance.shadowDiyEnabled,
                onCheckedChange = { checked ->
                    updateAppearance { it.copy(shadowDiyEnabled = checked) }
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = OplusWorkshopUiText.getNavAppearStrokeDiy(context),
                summary = OplusWorkshopUiText.getNavAppearStrokeDiySummary(context),
                checked = appearance.strokeDiyEnabled,
                onCheckedChange = { checked ->
                    updateAppearance { it.copy(strokeDiyEnabled = checked) }
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = OplusWorkshopUiText.getNavAppearHandleDiy(context),
                summary = OplusWorkshopUiText.getNavAppearHandleDiySummary(context),
                checked = appearance.handleDiyEnabled,
                onCheckedChange = { checked ->
                    updateAppearance { it.copy(handleDiyEnabled = checked) }
                }
            )
        }

        // ── Section 2: Arrow DIY ──
        if (appearance.arrowDiyEnabled) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavAppearArrowExperience(context)
            ) {
                RadioButtonPreference(
                    title = OplusWorkshopUiText.getNavAppearArrowExperienceStock(context),
                    selected = appearance.arrowExperienceMode == GestureAppearance.ARROW_EXPERIENCE_STOCK,
                    onClick = {
                        updateAppearance { it.copy(arrowExperienceMode = GestureAppearance.ARROW_EXPERIENCE_STOCK) }
                    }
                )
                HorizontalDivider()
                RadioButtonPreference(
                    title = OplusWorkshopUiText.getNavAppearArrowExperienceDynamic(context),
                    selected = appearance.arrowExperienceMode == GestureAppearance.ARROW_EXPERIENCE_DYNAMIC,
                    onClick = {
                        updateAppearance { it.copy(arrowExperienceMode = GestureAppearance.ARROW_EXPERIENCE_DYNAMIC) }
                    }
                )
            }

            if (appearance.arrowExperienceMode == GestureAppearance.ARROW_EXPERIENCE_STOCK) {
                BasicComponentGroup(
                    title = OplusWorkshopUiText.getNavAppearArrowIcon(context)
                ) {
                    OverlayDropdownPreference(
                        title = OplusWorkshopUiText.getNavAppearArrowIcon(context),
                        items = ArrowIcon.entries.map { icon -> arrowIconLabel(icon) },
                        selectedIndex = ArrowIcon.entries.indexOf(ArrowIcon.fromKey(appearance.arrowIconKey)),
                        onSelectedIndexChange = { index ->
                            ArrowIcon.entries.getOrNull(index)?.let { selectedIcon ->
                                updateAppearance { it.copy(arrowIconKey = selectedIcon.key) }
                            }
                        }
                    )

                    if (appearance.arrowIconKey == ArrowIcon.CUSTOM.key) {
                        HorizontalDivider()
                        JumpPreference(
                            title = OplusWorkshopUiText.getNavButtonCustomSectionTitle(context),
                            summary = if (customArrowBase64.isNotBlank()) OplusWorkshopUiText.getNavButtonCustomHintSet(context) else OplusWorkshopUiText.getNavButtonCustomHintEmpty(context),
                            onClick = { pickCustomArrow() }
                        )
                    }
                }
            } else {
                BasicComponentGroup(
                    title = OplusWorkshopUiText.getNavAppearDynamicStyle(context)
                ) {
                    val dynamicStyles = listOf(
                        GestureAppearance.DYNAMIC_STYLE_DOT_MATRIX_5,
                        GestureAppearance.DYNAMIC_STYLE_DOT_FLOW,
                        GestureAppearance.DYNAMIC_STYLE_ELASTIC_CHEVRON,
                        GestureAppearance.DYNAMIC_STYLE_PULSE_BARS,
                        GestureAppearance.DYNAMIC_STYLE_BOWSTRING
                    )
                    OverlayDropdownPreference(
                        title = OplusWorkshopUiText.getNavAppearDynamicStyle(context),
                        items = dynamicStyles.map { styleId -> dynamicStyleLabel(styleId) },
                        selectedIndex = dynamicStyles.indexOf(appearance.dynamicPointerStyle),
                        onSelectedIndexChange = { index ->
                            dynamicStyles.getOrNull(index)?.let { styleId ->
                                updateAppearance { it.copy(dynamicPointerStyle = styleId) }
                            }
                        }
                    )
                }
            }

            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavAppearArrowSize(context)
            ) {
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearArrowSize(context),
                    value = appearance.arrowSizeDp,
                    valueRange = 10f..36f,
                    valueText = "${appearance.arrowSizeDp.toInt()} dp",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(arrowSizeDp = newVal) }
                    }
                )
            }

            BasicComponentGroup(
                title = "${OplusWorkshopUiText.getNavAppearArrowDiy(context)} - ${OplusWorkshopUiText.getNavAppearCardLight(context)}"
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavAppearArrowColor(context),
                    color = Color(appearance.day.arrow.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(day = it.day.copy(arrow = it.day.arrow.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearArrowAlpha(context),
                    value = appearance.day.arrow.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.day.arrow.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(day = it.day.copy(arrow = it.day.arrow.copy(alpha = newVal.toInt()))) }
                    }
                )
            }

            BasicComponentGroup(
                title = "${OplusWorkshopUiText.getNavAppearArrowDiy(context)} - ${OplusWorkshopUiText.getNavAppearCardDark(context)}"
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavAppearArrowColor(context),
                    color = Color(appearance.night.arrow.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(night = it.night.copy(arrow = it.night.arrow.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearArrowAlpha(context),
                    value = appearance.night.arrow.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.night.arrow.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(night = it.night.copy(arrow = it.night.arrow.copy(alpha = newVal.toInt()))) }
                    }
                )
            }
        }

        // ── Section 3: Shadow DIY ──
        if (appearance.shadowDiyEnabled) {
            BasicComponentGroup(
                title = "${OplusWorkshopUiText.getNavAppearShadowDiy(context)} - ${OplusWorkshopUiText.getNavAppearCardLight(context)}"
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavAppearShadowColor(context),
                    color = Color(appearance.day.shadow.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(day = it.day.copy(shadow = it.day.shadow.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearShadowAlpha(context),
                    value = appearance.day.shadow.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.day.shadow.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(day = it.day.copy(shadow = it.day.shadow.copy(alpha = newVal.toInt()))) }
                    }
                )
            }

            BasicComponentGroup(
                title = "${OplusWorkshopUiText.getNavAppearShadowDiy(context)} - ${OplusWorkshopUiText.getNavAppearCardDark(context)}"
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavAppearShadowColor(context),
                    color = Color(appearance.night.shadow.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(night = it.night.copy(shadow = it.night.shadow.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearShadowAlpha(context),
                    value = appearance.night.shadow.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.night.shadow.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(night = it.night.copy(shadow = it.night.shadow.copy(alpha = newVal.toInt()))) }
                    }
                )
            }
        }

        // ── Section 4: Stroke DIY ──
        if (appearance.strokeDiyEnabled) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavAppearStrokeDiy(context)
            ) {
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearStrokeWidth(context),
                    value = appearance.day.stroke.widthDp,
                    valueRange = 0.1f..4.0f,
                    valueText = String.format("%.1f dp", appearance.day.stroke.widthDp),
                    onValueChange = { newVal ->
                        updateAppearance {
                            it.copy(
                                day = it.day.copy(stroke = it.day.stroke.copy(widthDp = newVal)),
                                night = it.night.copy(stroke = it.night.stroke.copy(widthDp = newVal))
                            )
                        }
                    }
                )
            }

            BasicComponentGroup(
                title = "${OplusWorkshopUiText.getNavAppearStrokeDiy(context)} - ${OplusWorkshopUiText.getNavAppearCardLight(context)}"
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavAppearStrokeColor(context),
                    color = Color(appearance.day.stroke.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(day = it.day.copy(stroke = it.day.stroke.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearStrokeAlpha(context),
                    value = appearance.day.stroke.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.day.stroke.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(day = it.day.copy(stroke = it.day.stroke.copy(alpha = newVal.toInt()))) }
                    }
                )
            }

            BasicComponentGroup(
                title = "${OplusWorkshopUiText.getNavAppearStrokeDiy(context)} - ${OplusWorkshopUiText.getNavAppearCardDark(context)}"
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavAppearStrokeColor(context),
                    color = Color(appearance.night.stroke.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(night = it.night.copy(stroke = it.night.stroke.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavAppearStrokeAlpha(context),
                    value = appearance.night.stroke.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.night.stroke.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(night = it.night.copy(stroke = it.night.stroke.copy(alpha = newVal.toInt()))) }
                    }
                )
            }
        }

        // ── Section 5: Full Screen Gesture Handle Scale (handle bar DIY) ──
        if (appearance.handleDiyEnabled) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavHandleSectionTitle(context)
            ) {
                SliderPreference(
                    title = OplusWorkshopUiText.getNavHandleLengthTitle(context),
                    summary = OplusWorkshopUiText.getNavHandleLengthSummary(context),
                    value = appearance.handleLengthScale.toFloat(),
                    valueRange = 50f..200f,
                    valueText = "${appearance.handleLengthScale}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(handleLengthScale = newVal.toInt()) }
                    }
                )
            }

            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavGestureSectionLightMask(context)
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavGestureMaskColor(context),
                    color = Color(appearance.day.handle.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(day = it.day.copy(handle = it.day.handle.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavGestureMaskAlpha(context),
                    value = appearance.day.handle.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.day.handle.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(day = it.day.copy(handle = it.day.handle.copy(alpha = newVal.toInt()))) }
                    }
                )
            }

            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavGestureSectionDarkMask(context)
            ) {
                ColorPickerPreference(
                    title = OplusWorkshopUiText.getNavGestureMaskColor(context),
                    color = Color(appearance.night.handle.color),
                    showAlphaSlider = false,
                    onColorChange = { chosenColor ->
                        val argb = (chosenColor.toArgb() and 0x00FFFFFF) or 0xFF000000.toInt()
                        updateAppearance { it.copy(night = it.night.copy(handle = it.night.handle.copy(color = argb))) }
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavGestureMaskAlpha(context),
                    value = appearance.night.handle.alpha.toFloat(),
                    valueRange = 0f..255f,
                    valueText = "${(appearance.night.handle.alpha * 100 / 255)}%",
                    onValueChange = { newVal ->
                        updateAppearance { it.copy(night = it.night.copy(handle = it.night.handle.copy(alpha = newVal.toInt()))) }
                    }
                )
            }
        }
    }
}
