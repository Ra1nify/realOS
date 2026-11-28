package io.github.ra1nify.realos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Item
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.text.CouiTextKind
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.launcher.LauncherConfig
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.ui.components.OverlayDialog
import io.github.ra1nify.realos.ui.components.SquircleShape
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.components.pressable
import io.github.ra1nify.realos.ui.components.preference.RadioButtonPreference
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.extend.colorpicker.OverlayColorPickerDialog
import io.github.ra1nify.realos.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * One mask slot as the page edits it.
 *
 * [colour] carries the tint / dodge colour, [strength] is the layer alpha (0..1) and [luminance]
 * only exists for the stock style, where the luminosity layer uses nothing but the colour's
 * luminance. Storing strength separately from the colour lets the stock style keep its own
 * luminance and strength rows; the persisted form is still one #AARRGGBB per slot.
 */
private data class MaskSlot(
    val colour: Color,
    val strength: Float,
    val luminance: Float,
)

/** The four mask slots; light/dark crossed with first/second layer. */
private enum class SlotRef(val light: Boolean, val slotB: Boolean) {
    LIGHT_A(light = true, slotB = false),
    LIGHT_B(light = true, slotB = true),
    DARK_A(light = false, slotB = false),
    DARK_B(light = false, slotB = true),
}

/** Rec. 601 luminance, the same weights the host shader uses to fold a colour to one level. */
private fun Color.perceivedLuminance(): Float = 0.3f * red + 0.59f * green + 0.11f * blue

/**
 * What a shader layer actually reads, so the picker never offers a knob that has no effect.
 *
 * The host formulas decide this, not preference:
 * - MIX `mix(col, s, s.w)` reads all three channels plus alpha;
 * - LUMINOSITY `d + (lum(s) - lum(d))` reads only `lum(s)`, so hue and saturation are dead;
 * - OVERLAY `d<0.5 ? 2sd : 1-2(1-s)(1-d)` reads all channels, with mid grey as the identity;
 * - DODGE_BG `d / (1 - s)` reads all channels, with gain growing as a channel approaches full.
 */
private enum class LayerCapability(
    /** The layer folds the colour to one luminance level, so only a level can be tuned. */
    val greyOnly: Boolean,
    /** The layer's gain diverges with the channel value, so channels need a cap. */
    val dodge: Boolean,
) {
    /** MIX: full colour plus strength. */
    MIX(greyOnly = false, dodge = false),

    /** LUMINOSITY: a single luminance level plus strength. */
    LUMINOSITY(greyOnly = true, dodge = false),

    /** OVERLAY: a grey level used as a contrast amount, at full strength. */
    OVERLAY(greyOnly = true, dodge = false),

    /** DODGE_BG: a capped colour plus strength. */
    DODGE(greyOnly = false, dodge = true),
}

/** Maps the mode's layer types to what their pickers may expose. */
private fun capabilityOf(layerType: Int): LayerCapability = when (layerType) {
    5 -> LayerCapability.LUMINOSITY
    3 -> LayerCapability.DODGE
    2 -> LayerCapability.OVERLAY
    else -> LayerCapability.MIX
}

/**
 * Packs a slot into #AARRGGBB for the layer it feeds.
 *
 * A grey-only layer stores its single level in all three channels, because the host folds the
 * colour back to one luminance value anyway; writing a colour there would be discarded silently.
 */
private fun MaskSlot.toHex(capability: LayerCapability): String {
    val a = (strength.coerceIn(0f, 1f) * 255f).roundToInt()
    if (capability.greyOnly) {
        val level = (luminance.coerceIn(0f, 1f) * 255f).roundToInt()
        return LauncherConfig.formatArgb(android.graphics.Color.argb(a, level, level, level))
    }
    return LauncherConfig.formatArgb(
        android.graphics.Color.argb(
            a,
            (colour.red.coerceIn(0f, 1f) * 255f).roundToInt(),
            (colour.green.coerceIn(0f, 1f) * 255f).roundToInt(),
            (colour.blue.coerceIn(0f, 1f) * 255f).roundToInt(),
        )
    )
}

/**
 * Inverse of [toHex].
 *
 * The packed alpha is the layer strength, so it is lifted into [MaskSlot.strength] and stripped
 * from [MaskSlot.colour]; keeping it in both places let the two fields drift apart whenever a
 * style switch changed which one a picker wrote to.
 */
private fun slotFrom(hex: String, fallback: String): MaskSlot {
    val argb = runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
        ?: runCatching { android.graphics.Color.parseColor(fallback) }.getOrDefault(0)
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)
    val lum = (0.3f * r + 0.59f * g + 0.11f * b) / 255f
    return MaskSlot(
        colour = Color(red = r, green = g, blue = b),
        strength = android.graphics.Color.alpha(argb) / 255f,
        luminance = lum,
    )
}

/**
 * Dock background tuning page, opened from the launcher settings page.
 *
 * The feature switch lives on the launcher spec page and decides whether the
 * hooks and this page's entry are installed at all. This page only holds the
 * tuning that applies without a launcher restart.
 *
 * Uses [LauncherConfig] directly so it shares state with the launcher process hook.
 */
@Composable
fun LauncherDockPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var blurEnabled by remember {
        mutableStateOf(LauncherConfig.dockerBgBlurBackendForUi(context) != "none")
    }
    var radius by remember { mutableFloatStateOf(LauncherConfig.isDockerBgRadiusForUi(context)) }
    var blendMode by remember { mutableStateOf(LauncherConfig.dockerBgBlendModeForUi(context)) }

    /**
     * Every mode keeps its own four slots.
     *
     * A slot means something different per layer stack (full colour, luminance level, contrast
     * level), so sharing one set across modes would silently reinterpret a value the user tuned
     * under another mode. Keyed by mode, then by [SlotRef].
     */
    fun loadSlots(mode: Int): Map<SlotRef, MaskSlot> =
        SlotRef.entries.associateWith { ref ->
            slotFrom(
                LauncherConfig.dockerBgMaskForUi(context, mode, ref.light, ref.slotB),
                LauncherConfig.maskDefault(mode, ref.light, ref.slotB),
            )
        }

    var slotsByMode by remember {
        mutableStateOf(
            LauncherConfig.BLEND_MODES.associateWith { loadSlots(it) }
        )
    }
    // The active mode's slots; switching mode swaps in that mode's own set.
    val slots = slotsByMode[blendMode] ?: loadSlots(blendMode)
    val lightA = slots.getValue(SlotRef.LIGHT_A)
    val lightB = slots.getValue(SlotRef.LIGHT_B)
    val darkA = slots.getValue(SlotRef.DARK_A)
    val darkB = slots.getValue(SlotRef.DARK_B)

    LaunchedEffect(Unit) {
        LauncherConfig.syncToRemote(context)
    }

    // Layer stack per mode, matching the shader dispatch; second == 0 means the style has one
    // layer only, so the second swatch of each pair is not rendered at all.
    val layers = when (blendMode) {
        LauncherConfig.BLEND_MODE_MIX -> 1 to 0
        LauncherConfig.BLEND_MODE_MIX_OVERLAY -> 1 to 2
        else -> 5 to 3
    }
    val layerACapability = capabilityOf(layers.first)
    val layerBCapability = capabilityOf(layers.second)

    /** Capability of the layer a slot feeds under the active mode. */
    fun capabilityFor(ref: SlotRef): LayerCapability =
        capabilityOf(if (ref.slotB) layers.second else layers.first)

    /** Persists one slot into the active mode's own key, clamped to what its layer can render. */
    fun commitSlot(ref: SlotRef, slot: MaskSlot) {
        val capability = capabilityFor(ref)
        val hex = LauncherConfig.clampMaskChannels(slot.toHex(capability), capability.dodge)
        LauncherConfig.setDockerBgMask(context, blendMode, ref.light, ref.slotB, hex, capability.dodge)
        slotsByMode = slotsByMode.toMutableMap().apply {
            put(
                blendMode,
                slots + (ref to slotFrom(hex, hex)),
            )
        }
    }

    /**
     * Restores the active mode's defaults for all four slots.
     *
     * Written through the same path as a user pick so the launcher process sees it without a
     * restart: local write, remote push and one revision bump per key.
     */
    fun resetMask() {
        val mode = blendMode
        var restored = slots
        for (ref in SlotRef.entries) {
            val capability = capabilityFor(ref)
            val stock = LauncherConfig.maskDefault(mode, ref.light, ref.slotB)
            val hex = LauncherConfig.clampMaskChannels(stock, capability.dodge)
            LauncherConfig.setDockerBgMask(context, mode, ref.light, ref.slotB, hex, capability.dodge)
            restored = restored + (ref to slotFrom(hex, stock))
        }
        slotsByMode = slotsByMode.toMutableMap().apply { put(mode, restored) }
    }

    ModuleDetailScaffold(
        title = stringResource(R.string.launcher_dock_page_title),
        onNavigateBack = onBack,
        modifier = modifier
    ) {
        BasicComponentGroup(
            title = stringResource(R.string.launcher_docker_bg_section_title),
            footer = stringResource(R.string.launcher_docker_bg_footer)
        ) {
            SwitchPreference(
                title = stringResource(R.string.launcher_docker_bg_blur_title),
                summary = stringResource(R.string.launcher_docker_bg_blur_summary),
                checked = blurEnabled,
                onCheckedChange = { enabled ->
                    blurEnabled = enabled
                    LauncherConfig.setDockerBgBlurBackend(
                        context, if (enabled) "stock" else "none"
                    )
                }
            )
        }

        BasicComponentGroup(
            title = stringResource(R.string.launcher_docker_bg_radius_title),
            footer = stringResource(R.string.launcher_docker_bg_radius_footer)
        ) {
            SliderPreference(
                value = radius,
                // The value handed back is used as is. Rounding here writes a number the
                // slider did not produce, and CouiSliderMotion.syncValue() treats the next
                // incoming value that differs from its own as an external edit and ends the
                // gesture, so the drag stopped after the first move. Rounding to 0.01 belongs
                // in onValueChangeFinished; the stored and displayed value keeps two decimals.
                onValueChange = { raw -> radius = raw },
                onValueChangeFinished = {
                    val snapped = (radius * 100f).roundToInt() / 100f
                    radius = snapped
                    LauncherConfig.setDockerBgRadius(context, snapped)
                },
                title = stringResource(R.string.launcher_docker_bg_radius_slider_title),
                valueText = stringResource(
                    R.string.launcher_docker_bg_radius_value,
                    // Two decimals, matching what is stored.
                    (radius * 100f).roundToInt() / 100f
                ),
                valueRange = 0.5f..2.0f,
                steps = 0
            )
        }

        if (blurEnabled) {
            // The style is global while the colours are per mode, so it is its own group.
            BasicComponentGroup(
                title = stringResource(R.string.launcher_docker_bg_style_title),
                footer = stringResource(R.string.launcher_docker_bg_style_footer)
            ) {
                StyleRow(
                    title = stringResource(R.string.launcher_docker_bg_style_mix),
                    summary = stringResource(R.string.launcher_docker_bg_style_mix_summary),
                    selected = blendMode == LauncherConfig.BLEND_MODE_MIX,
                ) {
                    blendMode = LauncherConfig.BLEND_MODE_MIX
                    LauncherConfig.setDockerBgBlendMode(context, LauncherConfig.BLEND_MODE_MIX)
                }
                HorizontalDivider()
                StyleRow(
                    title = stringResource(R.string.launcher_docker_bg_style_stock),
                    summary = stringResource(R.string.launcher_docker_bg_style_stock_summary),
                    selected = blendMode == LauncherConfig.BLEND_MODE_STOCK,
                ) {
                    blendMode = LauncherConfig.BLEND_MODE_STOCK
                    LauncherConfig.setDockerBgBlendMode(context, LauncherConfig.BLEND_MODE_STOCK)
                }
                HorizontalDivider()
                StyleRow(
                    title = stringResource(R.string.launcher_docker_bg_style_mix_overlay),
                    summary = stringResource(R.string.launcher_docker_bg_style_mix_overlay_summary),
                    selected = blendMode == LauncherConfig.BLEND_MODE_MIX_OVERLAY,
                ) {
                    blendMode = LauncherConfig.BLEND_MODE_MIX_OVERLAY
                    LauncherConfig.setDockerBgBlendMode(context, LauncherConfig.BLEND_MODE_MIX_OVERLAY)
                }
            }

            // One group for both modes. The stock material colour is identical in light and dark
            // mode (BlurUtils.BlurMaterialColor), so the stock colours are one setting; the
            // per-mode rows only exist to allow them to diverge.
            BasicComponentGroup(
                title = stringResource(R.string.launcher_docker_bg_mask_title),
                footer = stringResource(R.string.launcher_docker_bg_mask_footer)
            ) {
                // A plain COUI Item, not a JumpPreference: reset is an action, not a page jump,
                // so it must not carry the trailing chevron.
                Item(
                    title = {
                        Text(
                            text = stringResource(R.string.launcher_docker_bg_mask_reset),
                            kind = CouiTextKind.ItemTitle,
                        )
                    },
                    summary = {
                        Text(
                            text = stringResource(R.string.launcher_docker_bg_mask_reset_summary),
                            kind = CouiTextKind.ItemSummary,
                            color = AppTheme.colorScheme.onSurfaceSecondary,
                        )
                    },
                    onClick = { resetMask() },
                )
                HorizontalDivider()
                MaskSwatchRow(
                    slots = listOf(
                        SwatchEntry(
                            label = stringResource(R.string.launcher_docker_bg_swatch_light_a),
                            ref = SlotRef.LIGHT_A,
                            slot = lightA,
                            capability = layerACapability,
                        ),
                        if (layers.second != 0) {
                            SwatchEntry(
                                label = stringResource(R.string.launcher_docker_bg_swatch_light_b),
                                ref = SlotRef.LIGHT_B,
                                slot = lightB,
                                capability = layerBCapability,
                            )
                        } else {
                            null
                        },
                        SwatchEntry(
                            label = stringResource(R.string.launcher_docker_bg_swatch_dark_a),
                            ref = SlotRef.DARK_A,
                            slot = darkA,
                            capability = layerACapability,
                        ),
                        if (layers.second != 0) {
                            SwatchEntry(
                                label = stringResource(R.string.launcher_docker_bg_swatch_dark_b),
                                ref = SlotRef.DARK_B,
                                slot = darkB,
                                capability = layerBCapability,
                            )
                        } else {
                            null
                        },
                    ),
                    onPick = { ref, next -> commitSlot(ref, next) },
                )
            }
        }

        BasicComponentGroup(
            title = stringResource(R.string.launcher_docker_bg_preview_title),
            footer = stringResource(R.string.launcher_docker_bg_preview_footer)
        ) {
            DockerBackgroundPreview(
                multiplier = radius,
                blendMode = blendMode,
                blurEnabled = blurEnabled,
                lightBlend = Color(android.graphics.Color.parseColor(lightA.toHex(layerACapability))),
                lightMix = Color(android.graphics.Color.parseColor(lightB.toHex(layerBCapability))),
                darkBlend = Color(android.graphics.Color.parseColor(darkA.toHex(layerACapability))),
                darkMix = Color(android.graphics.Color.parseColor(darkB.toHex(layerBCapability))),
            )        }
    }
}

/** One selectable blend style row; a radio row reads better than a switch for three options. */
@Composable
private fun StyleRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RadioButtonPreference(
        title = title,
        summary = summary,
        selected = selected,
        onClick = onClick,
    )
}

/**
 * One swatch in the mask row; `null` entries in the row mean the style does not use that layer.
 */
private data class SwatchEntry(
    val label: String,
    val ref: SlotRef,
    val slot: MaskSlot,
    val capability: LayerCapability,
)

/**
 * One row of mask swatches, light mode pair followed by dark mode pair.
 *
 * A style that uses a single layer omits the second swatch of each pair entirely rather than
 * showing a disabled entry: the layer does not exist, so there is nothing to tune. The picker
 * that opens is chosen by [LayerCapability] so it never offers a knob the shader ignores.
 */
@Composable
private fun MaskSwatchRow(
    slots: List<SwatchEntry?>,
    onPick: (SlotRef, MaskSlot) -> Unit,
) {
    var editing by remember { mutableStateOf<SlotRef?>(null) }
    val visible = slots.filterNotNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEach { entry ->
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(SquircleShape(12.dp))
                    .background(entry.slot.colour)
                    .border(
                        border = BorderStroke(
                            width = 1.dp,
                            color = AppTheme.colorScheme.dividerLine.copy(alpha = 0.35f),
                        ),
                        shape = SquircleShape(12.dp),
                    )
                    .pressable(
                        pressedScale = 0.92f,
                        haptic = HapticFeedbackType.LongPress,
                        onClick = { editing = entry.ref },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${(entry.slot.strength * 100f).roundToInt()}",
                    kind = CouiTextKind.GroupFooter,
                    color = if (entry.slot.colour.perceivedLuminance() > 0.5f) Color.Black else Color.White,
                )
            }
        }
    }

    val editRef = editing
    val editEntry = editRef?.let { ref -> slots.filterNotNull().firstOrNull { it.ref == ref } }
    if (editRef != null && editEntry != null) {
        when (editEntry.capability) {
            // Full colour layer: the colour picker is the whole surface. Its alpha slider is the
            // layer strength, so the picked alpha must be written to `strength`; keeping it only in
            // the colour would leave the two fields disagreeing across style switches.
            LayerCapability.MIX,
            LayerCapability.DODGE -> OverlayColorPickerDialog(
                show = true,
                color = editEntry.slot.colour.copy(alpha = editEntry.slot.strength),
                // The dialog keeps its own draft; only confirm persists, so a cancelled pick
                // writes nothing.
                onColorChange = {},
                onConfirm = { picked ->
                    onPick(
                        editRef,
                        editEntry.slot.copy(
                            colour = picked.copy(alpha = 1f),
                            strength = picked.alpha,
                        ),
                    )
                    editing = null
                },
                onDismissRequest = { editing = null },
                title = editEntry.label,
                showAlphaSlider = true,
            )

            // Luminance layer: only the folded level is read, so expose a level slider instead of
            // a colour wheel that would be silently collapsed.
            LayerCapability.LUMINOSITY,
            LayerCapability.OVERLAY -> LayerSliderDialog(
                title = editEntry.label,
                levelTitle = if (editEntry.capability == LayerCapability.OVERLAY) {
                    stringResource(R.string.launcher_docker_bg_contrast_title)
                } else {
                    stringResource(R.string.launcher_docker_bg_luminance_title)
                },
                // Overlay reads the level as the contrast amount and keeps strength at full, so the
                // strength slider would be inert there.
                showStrength = editEntry.capability == LayerCapability.LUMINOSITY,
                level = editEntry.slot.luminance,
                strength = editEntry.slot.strength,
                onConfirm = { level, strength ->
                    // Overlay has no strength knob, so pin it to full rather than writing back a
                    // draft the user never saw; the layer would otherwise be scaled twice.
                    val applied = if (editEntry.capability == LayerCapability.OVERLAY) 1f else strength
                    val grey = Color(level, level, level)
                    onPick(
                        editRef,
                        editEntry.slot.copy(colour = grey, luminance = level, strength = applied),
                    )
                    editing = null
                },
                onDismissRequest = { editing = null },
            )
        }
    }
}

/**
 * Dialog for a layer that reads a single level: one or two sliders and nothing else.
 *
 * Used where a colour wheel would be misleading, because the shader folds the colour to one
 * luminance value before using it.
 */
@Composable
private fun LayerSliderDialog(
    title: String,
    levelTitle: String,
    showStrength: Boolean,
    level: Float,
    strength: Float,
    onConfirm: (Float, Float) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Keyed on the slot's own values: opening a different swatch must start from that swatch's
    // current numbers, not from whatever the previous dialog left behind.
    var draftLevel by remember(title, level) { mutableFloatStateOf(level) }
    var draftStrength by remember(title, strength) { mutableFloatStateOf(strength) }

    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
        confirmButtonText = stringResource(R.string.oplus_input_dialog_ok),
        onConfirm = { onConfirm(draftLevel, draftStrength) },
        dismissButtonText = stringResource(R.string.oplus_input_dialog_cancel),
        onDismiss = onDismissRequest,
    ) {
        // COUI's Dialog content slot carries no padding of its own (only `message` gets one), so
        // the body supplies the dialog margin itself, matching the colour picker's wrapper.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CouiDialogDefaults.contentPadding,
                    end = CouiDialogDefaults.contentPadding,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
        ) {
            SliderPreference(
                value = draftLevel,
                onValueChange = { raw -> draftLevel = raw },
                title = levelTitle,
                valueText = stringResource(
                    R.string.launcher_docker_bg_percent_value,
                    (draftLevel * 100f).roundToInt()
                ),
                valueRange = 0f..1f,
                // The dialog wrapper already supplies the horizontal margin.
                insideMargin = PaddingValues(horizontal = 0.dp),
            )
            if (showStrength) {
                SliderPreference(
                    value = draftStrength,
                    onValueChange = { raw -> draftStrength = raw },
                    title = stringResource(R.string.launcher_docker_bg_strength_title),
                    valueText = stringResource(
                        R.string.launcher_docker_bg_percent_value,
                        (draftStrength * 100f).roundToInt()
                    ),
                    valueRange = 0f..1f,
                    insideMargin = PaddingValues(horizontal = 0.dp),
                )
            }
        }
    }
}
