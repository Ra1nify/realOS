package io.github.ra1nify.realos.xposed.hooks.navigation.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.xposed.hooks.navigation.SystemUiNavBarPresetLoader
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.InjectPage
import io.github.ra1nify.realos.ui.components.preference.JumpPreference
import io.github.ra1nify.realos.ui.components.preference.OverlayDropdownPreference
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.extend.preference.ColorPickerPreference

object SettingsButtonNavHostCallbacks {
    const val REQ_PICK_BACK = 2001
    const val REQ_PICK_HOME = 2002
    const val REQ_PICK_RECENT = 2003

    var onImagePicked: ((requestCode: Int, base64: String) -> Unit)? = null

    fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) return false
        val uri = data.data ?: return false

        val bmp = SystemUiNavBarPresetLoader.decodeSampledBitmapFromUri(activity, uri) ?: return false
        val squareBmp = SystemUiNavBarPresetLoader.createSquareBitmap(bmp, 120)
        bmp.recycle()
        val base64 = SystemUiNavBarPresetLoader.bitmapToBase64(squareBmp)

        when (requestCode) {
            REQ_PICK_BACK -> {
                SettingsConfig.setNavButtonCustomIconBase64(activity, "back", base64)
                SettingsConfig.notifyNavButtonChanged(activity)
                onImagePicked?.invoke(requestCode, base64)
                return true
            }
            REQ_PICK_HOME -> {
                SettingsConfig.setNavButtonCustomIconBase64(activity, "home", base64)
                SettingsConfig.notifyNavButtonChanged(activity)
                onImagePicked?.invoke(requestCode, base64)
                return true
            }
            REQ_PICK_RECENT -> {
                SettingsConfig.setNavButtonCustomIconBase64(activity, "recent", base64)
                SettingsConfig.notifyNavButtonChanged(activity)
                onImagePicked?.invoke(requestCode, base64)
                return true
            }
        }
        return false
    }
}

@Composable
fun SettingsButtonNavCompose(activity: Activity) {
    val context = activity

    // 1. Custom button icon pack
    var customEnabled by remember {
        mutableStateOf(SettingsConfig.isNavButtonCustomEnabled(context))
    }
    var presetIndex by remember {
        mutableIntStateOf(SettingsConfig.getNavButtonPresetIndex(context))
    }

    // 2. Button icon foreground tint (custom button mask color)
    var tintMode by remember {
        mutableIntStateOf(SettingsConfig.getNavButtonTintMode(context))
    }
    var tintLightColor by remember {
        mutableIntStateOf(SettingsConfig.getNavButtonTintLightColor(context))
    }
    var tintDarkColor by remember {
        mutableIntStateOf(SettingsConfig.getNavButtonTintDarkColor(context))
    }

    // 3. Capsule wrap mode and tracking spotlight
    var capsuleMode by remember {
        mutableIntStateOf(SettingsConfig.navButtonGlassCapsuleMode(context))
    }
    var spotlightEnabled by remember {
        mutableStateOf(SettingsConfig.navButtonGlassSpotlightEnabled(context))
    }

    // 4. Custom capsule mask master switch and backing mask
    var glassMaskEnabled by remember {
        mutableStateOf(SettingsConfig.isNavButtonGlassMaskEnabled(context))
    }
    var lightColor by remember {
        mutableStateOf(SettingsConfig.navButtonGlassLightColor(context))
    }
    var lightAlpha by remember {
        mutableIntStateOf(SettingsConfig.navButtonGlassLightAlpha(context))
    }
    var darkColor by remember {
        mutableStateOf(SettingsConfig.navButtonGlassDarkColor(context))
    }
    var darkAlpha by remember {
        mutableIntStateOf(SettingsConfig.navButtonGlassDarkAlpha(context))
    }

    var customBackBase64 by remember {
        mutableStateOf(SettingsConfig.getNavButtonCustomIconBase64(context, "back"))
    }
    var customHomeBase64 by remember {
        mutableStateOf(SettingsConfig.getNavButtonCustomIconBase64(context, "home"))
    }
    var customRecentBase64 by remember {
        mutableStateOf(SettingsConfig.getNavButtonCustomIconBase64(context, "recent"))
    }

    DisposableEffect(Unit) {
        SettingsButtonNavHostCallbacks.onImagePicked = { req, base64 ->
            when (req) {
                SettingsButtonNavHostCallbacks.REQ_PICK_BACK -> customBackBase64 = base64
                SettingsButtonNavHostCallbacks.REQ_PICK_HOME -> customHomeBase64 = base64
                SettingsButtonNavHostCallbacks.REQ_PICK_RECENT -> customRecentBase64 = base64
            }
        }
        onDispose {
            SettingsButtonNavHostCallbacks.onImagePicked = null
        }
    }

    fun pickCustomImage(requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        activity.startActivityForResult(intent, requestCode)
    }

    fun parseColor(hex: String, fallback: Color): Color {
        return runCatching {
            val clean = hex.removePrefix("#")
            val intVal = clean.toLong(16).toInt()
            if (clean.length <= 6) {
                Color(intVal or 0xFF000000.toInt())
            } else {
                Color(intVal)
            }
        }.getOrDefault(fallback)
    }

    fun toHexColor(color: Color): String {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return String.format("#%02X%02X%02X", r, g, b)
    }

    val presetEntries = remember {
        (1..10).toList() + SystemUiConfig.PRESET_INDEX_CUSTOM
    }

    // OverlayDropdownPreference takes (items, selectedIndex) with no entryLabel mapping;
    // labels are pre-rendered in presetEntries index order and mapped back the same way.
    val presetLabels = remember(context, presetEntries) {
        presetEntries.map { idx ->
            if (idx == SystemUiConfig.PRESET_INDEX_CUSTOM) {
                OplusWorkshopUiText.getNavButtonPresetCustomTitle(context)
            } else {
                OplusWorkshopUiText.getNavButtonPresetItemTitle(context, idx)
            }
        }
    }

    val capsuleModes = remember(context) {
        listOf(
            SystemUiConfig.CAPSULE_MODE_NONE to OplusWorkshopUiText.getNavButtonGlassCapsuleModeNone(context),
            SystemUiConfig.CAPSULE_MODE_SINGLE to OplusWorkshopUiText.getNavButtonGlassCapsuleModeSingle(context),
            SystemUiConfig.CAPSULE_MODE_TRIPLE to OplusWorkshopUiText.getNavButtonGlassCapsuleModeTriple(context),
            SystemUiConfig.CAPSULE_MODE_PAIR_BACK_HOME to OplusWorkshopUiText.getNavButtonGlassCapsuleModePairBackHome(context),
            SystemUiConfig.CAPSULE_MODE_PAIR_HOME_RECENT to OplusWorkshopUiText.getNavButtonGlassCapsuleModePairHomeRecent(context)
        )
    }

    // Same as presetLabels: label list aligned to the dropdown indices, split from (mode, label) pairs
    val capsuleLabels = remember(capsuleModes) { capsuleModes.map { it.second } }

    InjectPage(
        title = OplusWorkshopUiText.getNavSectionVisualTuning(context),
        onBack = { activity.finish() },
    ) {
        // ── Card 1: button icon main card (one Column card, two rows) ──
        BasicComponentGroup(
            title = OplusWorkshopUiText.getNavButtonEntryTitle(context),
            footer = OplusWorkshopUiText.getNavButtonCustomSwitchSummary(context)
        ) {
            // Row 1: custom button icon pack
            SwitchPreference(
                title = OplusWorkshopUiText.getNavButtonCustomSwitchTitle(context),
                summary = OplusWorkshopUiText.getNavButtonCustomSwitchSummary(context),
                checked = customEnabled,
                onCheckedChange = { checked ->
                    customEnabled = checked
                    SettingsConfig.setNavButtonCustomEnabled(context, checked)
                    SettingsConfig.notifyNavButtonChanged(context)
                }
            )
            HorizontalDivider()
            // Row 2: custom button mask color (icon foreground tint)
            SwitchPreference(
                title = OplusWorkshopUiText.getNavButtonTintSwitchTitle(context),
                summary = OplusWorkshopUiText.getNavButtonTintSwitchSummary(context),
                checked = tintMode == 1,
                onCheckedChange = { checked ->
                    val newMode = if (checked) 1 else 0
                    tintMode = newMode
                    SettingsConfig.setNavButtonTintMode(context, newMode)
                    SettingsConfig.notifyNavButtonChanged(context)
                }
            )
        }

        // ── Expanded: custom icon pack picker (shown when customEnabled == true) ──
        if (customEnabled) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavButtonPresetCategoryTitle(context)
            ) {
                OverlayDropdownPreference(
                    title = OplusWorkshopUiText.getNavButtonPresetCategoryTitle(context),
                    items = presetLabels,
                    selectedIndex = presetEntries.indexOf(presetIndex).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        presetEntries.getOrNull(index)?.let { idx ->
                            presetIndex = idx
                            SettingsConfig.setNavButtonPresetIndex(context, idx)
                            SettingsConfig.notifyNavButtonChanged(context)
                        }
                    }
                )
            }

            if (presetIndex == SystemUiConfig.PRESET_INDEX_CUSTOM) {
                BasicComponentGroup(
                    title = OplusWorkshopUiText.getNavButtonCustomSectionTitle(context)
                ) {
                    JumpPreference(
                        title = OplusWorkshopUiText.getNavButtonCustomBackTitle(context),
                        summary = if (customBackBase64.isNotBlank()) OplusWorkshopUiText.getNavButtonCustomHintSet(context) else OplusWorkshopUiText.getNavButtonCustomHintEmpty(context),
                        onClick = { pickCustomImage(SettingsButtonNavHostCallbacks.REQ_PICK_BACK) }
                    )
                    HorizontalDivider()
                    JumpPreference(
                        title = OplusWorkshopUiText.getNavButtonCustomHomeTitle(context),
                        summary = if (customHomeBase64.isNotBlank()) OplusWorkshopUiText.getNavButtonCustomHintSet(context) else OplusWorkshopUiText.getNavButtonCustomHintEmpty(context),
                        onClick = { pickCustomImage(SettingsButtonNavHostCallbacks.REQ_PICK_HOME) }
                    )
                    HorizontalDivider()
                    JumpPreference(
                        title = OplusWorkshopUiText.getNavButtonCustomRecentTitle(context),
                        summary = if (customRecentBase64.isNotBlank()) OplusWorkshopUiText.getNavButtonCustomHintSet(context) else OplusWorkshopUiText.getNavButtonCustomHintEmpty(context),
                        onClick = { pickCustomImage(SettingsButtonNavHostCallbacks.REQ_PICK_RECENT) }
                    )
                }
            }
        }

        // ── Expanded: icon tint color pickers (shown when tintMode == 1) ──
        if (tintMode == 1) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavButtonTintSwitchTitle(context)
            ) {
                ColorPickerPreference(
                    color = Color(tintLightColor),
                    onColorChange = { chosenColor ->
                        val argb = chosenColor.toArgb()
                        tintLightColor = argb
                        SettingsConfig.setNavButtonTintLightColor(context, argb)
                        SettingsConfig.notifyNavButtonChanged(context)
                    },
                    title = OplusWorkshopUiText.getNavButtonTintLightColorTitle(context),
                    showAlphaSlider = false
                )
                HorizontalDivider()
                ColorPickerPreference(
                    color = Color(tintDarkColor),
                    onColorChange = { chosenColor ->
                        val argb = chosenColor.toArgb()
                        tintDarkColor = argb
                        SettingsConfig.setNavButtonTintDarkColor(context, argb)
                        SettingsConfig.notifyNavButtonChanged(context)
                    },
                    title = OplusWorkshopUiText.getNavButtonTintDarkColorTitle(context),
                    showAlphaSlider = false
                )
            }
        }

        // ── Card 2: button capsule card ──
        val isCapsuleActive = capsuleMode != SystemUiConfig.CAPSULE_MODE_NONE
        BasicComponentGroup(
            title = OplusWorkshopUiText.getNavButtonGlassCapsuleSection(context),
            footer = OplusWorkshopUiText.getNavButtonGlassCapsuleSummarySpecial(context)
        ) {
            // Row 1: button capsule mode menu
            OverlayDropdownPreference(
                title = OplusWorkshopUiText.getNavButtonGlassCapsuleSection(context),
                items = capsuleLabels,
                selectedIndex = capsuleModes.indexOfFirst { it.first == capsuleMode }.coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    capsuleModes.getOrNull(index)?.let { (mode, _) ->
                        capsuleMode = mode
                        SettingsConfig.setNavButtonGlassCapsuleMode(context, mode)
                        // Selecting "no wrap" (CAPSULE_MODE_NONE) turns the custom capsule mask switch off
                        if (mode == SystemUiConfig.CAPSULE_MODE_NONE) {
                            glassMaskEnabled = false
                            SettingsConfig.setNavButtonGlassMaskEnabled(context, false)
                        }
                        SettingsConfig.notifyNavButtonChanged(context)
                    }
                }
            )

            // AGSL tracking spotlight and custom capsule mask switch render only when isCapsuleActive == true
            if (isCapsuleActive) {
                HorizontalDivider()
                // Row 2: AGSL tracking spotlight pulse
                SwitchPreference(
                    title = OplusWorkshopUiText.getNavButtonGlassSpotlightTitle(context),
                    summary = OplusWorkshopUiText.getNavButtonGlassSpotlightSummary(context),
                    checked = spotlightEnabled,
                    onCheckedChange = { checked ->
                        spotlightEnabled = checked
                        SettingsConfig.setNavButtonGlassSpotlightEnabled(context, checked)
                        SettingsConfig.notifyNavButtonChanged(context)
                    }
                )
                HorizontalDivider()
                // Row 3: custom capsule mask switch
                SwitchPreference(
                    title = OplusWorkshopUiText.getNavButtonGlassMaskTitle(context),
                    summary = OplusWorkshopUiText.getNavButtonGlassMaskSummary(context),
                    checked = glassMaskEnabled,
                    onCheckedChange = { checked ->
                        glassMaskEnabled = checked
                        SettingsConfig.setNavButtonGlassMaskEnabled(context, checked)
                    }
                )
            }
        }

        // ── Cards 3 and 4: backing mask shown only when a capsule is active and its mask switch is on ──
        if (isCapsuleActive && glassMaskEnabled) {
            // Light mode backing mask (per AGENTS.md)
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavButtonSectionLightMask(context)
            ) {
                ColorPickerPreference(
                    color = parseColor(lightColor, Color.White),
                    onColorChange = { chosenColor ->
                        val hex = toHexColor(chosenColor)
                        lightColor = hex
                        SettingsConfig.setNavButtonGlassLightColor(context, hex)
                        SettingsConfig.notifyNavButtonChanged(context)
                    },
                    title = OplusWorkshopUiText.getNavButtonLightMaskColor(context),
                    showAlphaSlider = false
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavButtonLightMaskAlpha(context),
                    value = lightAlpha.toFloat(),
                    valueRange = 10f..100f,
                    valueText = "$lightAlpha%",
                    onValueChange = { newVal ->
                        lightAlpha = newVal.toInt()
                        SettingsConfig.setNavButtonGlassLightAlpha(context, lightAlpha)
                        SettingsConfig.notifyNavButtonChanged(context)
                    }
                )
            }

            // Dark mode backing mask (per AGENTS.md)
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavButtonSectionDarkMask(context)
            ) {
                ColorPickerPreference(
                    color = parseColor(darkColor, Color.Black),
                    onColorChange = { chosenColor ->
                        val hex = toHexColor(chosenColor)
                        darkColor = hex
                        SettingsConfig.setNavButtonGlassDarkColor(context, hex)
                        SettingsConfig.notifyNavButtonChanged(context)
                    },
                    title = OplusWorkshopUiText.getNavButtonDarkMaskColor(context),
                    showAlphaSlider = false
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavButtonDarkMaskAlpha(context),
                    value = darkAlpha.toFloat(),
                    valueRange = 10f..100f,
                    valueText = "$darkAlpha%",
                    onValueChange = { newVal ->
                        darkAlpha = newVal.toInt()
                        SettingsConfig.setNavButtonGlassDarkAlpha(context, darkAlpha)
                        SettingsConfig.notifyNavButtonChanged(context)
                    }
                )
            }
        }
    }
}
