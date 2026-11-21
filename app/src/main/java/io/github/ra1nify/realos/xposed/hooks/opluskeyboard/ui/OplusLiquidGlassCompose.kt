package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.OplusInputWhitelistBypass
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.OplusLiquidGlassHook
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.extend.preference.ColorPickerPreference
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.components.InjectPage

private fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Throwable) {
        fallback
    }
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

@Composable
fun OplusLiquidGlassCompose(activity: Activity) {
    val context = activity

    var bypassAllLimits by remember {
        mutableStateOf(OplusInputPrefs.isBypassAllLimits(context))
    }
    var blurRadius by remember {
        mutableFloatStateOf(OplusInputPrefs.getBlurRadius(context).toFloat())
    }
    var keyAlpha by remember {
        mutableFloatStateOf(OplusInputPrefs.getKeyAlpha(context))
    }
    var cornerRadius by remember {
        mutableFloatStateOf(OplusInputPrefs.getCornerRadius(context).toFloat())
    }

    var lightMaskColor by remember {
        mutableStateOf(OplusInputPrefs.getLightMaskColor(context))
    }
    var lightAlpha by remember {
        mutableFloatStateOf(OplusInputPrefs.getLightMaskAlpha(context))
    }

    var darkMaskColor by remember {
        mutableStateOf(OplusInputPrefs.getDarkMaskColor(context))
    }
    var darkAlpha by remember {
        mutableFloatStateOf(OplusInputPrefs.getDarkMaskAlpha(context))
    }

    // Shared persistence entry: dragging updates local state only, one write 250ms after the last change; one debounce per key.
    val writer = rememberSliderPrefWriter()

    // Leaving inside the debounce window cancels the writes through the composition scope.
    // Flushing the current state on exit keeps the last values.
    DisposableEffect(Unit) {
        onDispose {
            writer.cancelAll()
            runCatching {
                OplusInputPrefs.setBlurRadius(context, blurRadius.toInt())
                OplusInputPrefs.setKeyAlpha(context, keyAlpha)
                OplusInputPrefs.setCornerRadius(context, cornerRadius.toInt())
                OplusInputPrefs.setLightMaskAlpha(context, lightAlpha)
                OplusInputPrefs.setDarkMaskAlpha(context, darkAlpha)
            }
        }
    }

    /**
     * Applies the current mask color/alpha to the live keyboard material.
     *
     * Colors and switches read their values from the prefs inside the hook; mask sliders pass the
     * just-dragged value explicitly because the pref write is debounced.
     *
     * No view-tree recovery and no delegate refresh: this is the same lightweight contract as the
     * blur and corner sliders, and it is safe to call on every pointer move.
     */
    fun refreshAppearance(
        lightAlphaOverride: Float? = null,
        darkAlphaOverride: Float? = null
    ) {
    OplusLiquidGlassHook.updateLiveMaskAlpha(context, lightAlphaOverride, darkAlphaOverride)
}

InjectPage(
    title = OplusWorkshopUiText.getToolbarTitle(context),
    onBack = { activity.finish() },
    actions = { KeyboardPreviewActionButton() },
) {
        // Section 1: Card 1 - Core Material Configuration
        BasicComponentGroup(
            title = OplusWorkshopUiText.getSectionCoreMaterial(context)
        ) {
            SwitchPreference(
                title = OplusWorkshopUiText.getSwitchBypassAllLimitsTitle(context),
                summary = OplusWorkshopUiText.getSwitchBypassAllLimitsSummary(context),
                checked = bypassAllLimits,
                onCheckedChange = { checked ->
                    bypassAllLimits = checked
                    OplusInputPrefs.setBypassAllLimits(context, checked)
                    OplusLiquidGlassHook.updateBypassAllLimits(checked)
                    OplusInputWhitelistBypass.refresh(context)
                }
            )

            HorizontalDivider()

            SliderPreference(
                title = OplusWorkshopUiText.getSliderBlurRadius(context),
                value = blurRadius,
                valueRange = 30f..300f,
                valueText = "${blurRadius.toInt()} px",
                onValueChange = { value ->
                    blurRadius = value
                    OplusLiquidGlassHook.updateLiveBlurRadius(value.toInt(), context, refreshDelegates = false)
                    writer.submit("blur") {
                        OplusInputPrefs.setBlurRadius(context, value.toInt())
                    }
                }
            )

            HorizontalDivider()

            SliderPreference(
                title = OplusWorkshopUiText.getSliderKeyOpacity(context),
                value = keyAlpha,
                valueRange = 0f..1f,
                valueText = "${(keyAlpha * 100).toInt()}%",
                onValueChange = { value ->
                    keyAlpha = value
                    OplusLiquidGlassHook.updateLiveKeyAlpha(value)
                    writer.submit("key_alpha") {
                        OplusInputPrefs.setKeyAlpha(context, value)
                    }
                }
            )

            HorizontalDivider()

            SliderPreference(
                title = OplusWorkshopUiText.getSliderCornerRadius(context),
                value = cornerRadius,
                valueRange = 0f..40f,
                valueText = "${cornerRadius.toInt()} px",
                onValueChange = { value ->
                    cornerRadius = value
                    OplusLiquidGlassHook.updateLiveCornerRadius(value.toInt(), context, refreshDelegates = false)
                    writer.submit("corner") {
                        OplusInputPrefs.setCornerRadius(context, value.toInt())
                    }
                }
            )
        }

        // Section 2: Card 2 - Light Mode Customization
        BasicComponentGroup(
            title = OplusWorkshopUiText.getSectionLightCustom(context)
        ) {
            ColorPickerPreference(
                title = OplusWorkshopUiText.getLightMaskColorTitle(context),
                color = parseHexColor(lightMaskColor, Color(0xFFE1E1E1)),
                showAlphaSlider = false,
                dialogTitle = OplusWorkshopUiText.getColorPickerTitle(context),
                onColorChange = { color ->
                    val hex = colorToHex(color)
                    lightMaskColor = hex
                    OplusInputPrefs.setLightMaskColor(context, hex)
                    refreshAppearance()
                }
            )

            HorizontalDivider()

            SliderPreference(
                title = OplusWorkshopUiText.getSliderLightAlpha(context),
                value = lightAlpha,
                valueRange = 0f..1f,
                valueText = "${(lightAlpha * 100).toInt()}%",
                onValueChange = { value ->
                    lightAlpha = value
                    refreshAppearance(lightAlphaOverride = value)
                    writer.submit("light_mask_alpha") {
                        OplusInputPrefs.setLightMaskAlpha(context, value)
                    }
                }
            )
        }

        // Section 3: Card 3 - Dark Mode Customization
        BasicComponentGroup(
            title = OplusWorkshopUiText.getSectionDarkCustom(context)
        ) {
            ColorPickerPreference(
                title = OplusWorkshopUiText.getDarkMaskColorTitle(context),
                color = parseHexColor(darkMaskColor, Color(0xFF0D0D0D)),
                showAlphaSlider = false,
                dialogTitle = OplusWorkshopUiText.getColorPickerTitle(context),
                onColorChange = { color ->
                    val hex = colorToHex(color)
                    darkMaskColor = hex
                    OplusInputPrefs.setDarkMaskColor(context, hex)
                    refreshAppearance()
                }
            )

            HorizontalDivider()

            SliderPreference(
                title = OplusWorkshopUiText.getSliderDarkAlpha(context),
                value = darkAlpha,
                valueRange = 0f..1f,
                valueText = "${(darkAlpha * 100).toInt()}%",
                onValueChange = { value ->
                    darkAlpha = value
                    refreshAppearance(darkAlphaOverride = value)
                    writer.submit("dark_mask_alpha") {
                        OplusInputPrefs.setDarkMaskAlpha(context, value)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Live material reference: the robot color rotates over time; with the blank
        // space below, blur / mask strength changes show while the keyboard covers it.
        AndroidBotPreview()

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = OplusWorkshopUiText.getBotHint(context),
            kind = CouiTextKind.ItemSummary,
            color = AppTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )

        // Bottom spacing; matches the View fallback page. At the bottom of the scroll the robot sits in the keyboard-covered area.
        Spacer(modifier = Modifier.height(180.dp))
    }
}
