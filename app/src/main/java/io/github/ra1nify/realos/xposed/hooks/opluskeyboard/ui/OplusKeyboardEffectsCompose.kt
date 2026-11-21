package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.ui.components.pressable
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.components.SquircleShape
import io.github.ra1nify.realos.ui.extend.colorpicker.OverlayColorPickerDialog
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.BoardEffectType
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.EffectColorPreset
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.FootPrintIcon
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.KeyEffectType
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.MotionEffectType
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.preference.OverlayDropdownPreference
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.ui.components.InjectPage

@Composable
fun OplusKeyboardEffectsCompose(activity: Activity) {
    val context = activity

    var selectedBoard by remember {
        mutableStateOf(OplusInputPrefs.getEffectsBoard(context))
    }
    var selectedKey by remember {
        mutableStateOf(OplusInputPrefs.getEffectsKey(context))
    }
    var selectedFootprint by remember {
        mutableStateOf(OplusInputPrefs.getFootPrintIcon(context))
    }
    var selectedMotion by remember {
        mutableStateOf(OplusInputPrefs.getEffectsMotion(context))
    }
    var selectedColorPreset by remember {
        mutableStateOf(OplusInputPrefs.getEffectsColorPreset(context))
    }
    var customPalette by remember {
        mutableStateOf(OplusInputPrefs.getCustomPalette(context))
    }
    var editingColorIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingColor by remember { mutableStateOf(false) }

    var alphaRatio by remember {
        mutableFloatStateOf(OplusInputPrefs.getEffectsAlphaRatio(context))
    }
    var rangeRatio by remember {
        mutableFloatStateOf(OplusInputPrefs.getEffectsRangeRatio(context))
    }
    var speedRatio by remember {
        mutableFloatStateOf(OplusInputPrefs.getEffectsSpeedRatio(context))
    }

    // Same as the liquid glass page: dragging updates local state only, one write after the last change, flush on exit.
    val writer = rememberSliderPrefWriter()
    DisposableEffect(Unit) {
        onDispose {
            writer.cancelAll()
            runCatching {
                OplusInputPrefs.setEffectsAlphaRatio(context, alphaRatio)
                OplusInputPrefs.setEffectsRangeRatio(context, rangeRatio)
                OplusInputPrefs.setEffectsSpeedRatio(context, speedRatio)
            }
        }
    }

    InjectPage(
        title = OplusWorkshopUiText.getKeyboardEffectsTitle(context),
        onBack = { activity.finish() },
        actions = { KeyboardPreviewActionButton() },
    ) {
        // ── Section 1: Effects Selection (light effect and motion cards) ──
        BasicComponentGroup(
            title = OplusWorkshopUiText.getKeyboardEffectsTitle(context)
        ) {
            // 1. Board (keyboard background) effects
            val boardType = remember(selectedBoard) {
                BoardEffectType.fromKey(selectedBoard)
            }
            val boardOptions = BoardEffectType.entries
            OverlayDropdownPreference(
                title = OplusWorkshopUiText.getSectionEffectsBoard(context),
                summary = boardType.getSummary(context).takeIf { it.isNotBlank() },
                items = boardOptions.map { it.getTitle(context) },
                selectedIndex = boardOptions.indexOf(boardType),
                onSelectedIndexChange = { index ->
                    val type = boardOptions[index]
                    selectedBoard = type.key
                    OplusInputPrefs.setEffectsBoard(context, type.key)
                }
            )

            HorizontalDivider()

            // 2. Key (per-key press) effects
            val keyType = remember(selectedKey) {
                KeyEffectType.fromKey(selectedKey)
            }
            val keyOptions = KeyEffectType.entries
            OverlayDropdownPreference(
                title = OplusWorkshopUiText.getSectionEffectsKey(context),
                summary = keyType.getSummary(context).takeIf { it.isNotBlank() },
                items = keyOptions.map { it.getTitle(context) },
                selectedIndex = keyOptions.indexOf(keyType),
                onSelectedIndexChange = { index ->
                    val type = keyOptions[index]
                    selectedKey = type.key
                    OplusInputPrefs.setEffectsKey(context, type.key)
                }
            )

            // 2b. Foot print icon (shown when the key effect is FOOT_PRINT)
            if (keyType == KeyEffectType.FOOT_PRINT) {
                HorizontalDivider()
                val footprintIcon = remember(selectedFootprint) {
                    FootPrintIcon.fromKey(selectedFootprint)
                }
                val footprintOptions = FootPrintIcon.entries
                OverlayDropdownPreference(
                    title = OplusWorkshopUiText.getSectionFootPrint(context),
                    items = footprintOptions.map { it.getTitle(context) },
                    selectedIndex = footprintOptions.indexOf(footprintIcon),
                    onSelectedIndexChange = { index ->
                        val icon = footprintOptions[index]
                        selectedFootprint = icon.key
                        OplusInputPrefs.setFootPrintIcon(context, icon.key)
                    }
                )
            }

            HorizontalDivider()

            // 3. Key press-down motion
            val motionType = remember(selectedMotion) {
                MotionEffectType.fromKey(selectedMotion)
            }
            val motionOptions = MotionEffectType.entries
            OverlayDropdownPreference(
                title = OplusWorkshopUiText.getSectionEffectsMotion(context),
                summary = motionType.getSummary(context).takeIf { it.isNotBlank() },
                items = motionOptions.map { it.getTitle(context) },
                selectedIndex = motionOptions.indexOf(motionType),
                onSelectedIndexChange = { index ->
                    val type = motionOptions[index]
                    selectedMotion = type.key
                    OplusInputPrefs.setEffectsMotion(context, type.key)
                }
            )

            HorizontalDivider()

            // 4. Color preset
            val colorPreset = remember(selectedColorPreset) {
                EffectColorPreset.fromKey(selectedColorPreset)
            }
            val presetOptions = EffectColorPreset.entries
            OverlayDropdownPreference(
                title = OplusWorkshopUiText.getSectionEffectsColor(context),
                items = presetOptions.map { it.getTitle(context) },
                selectedIndex = presetOptions.indexOf(colorPreset),
                onSelectedIndexChange = { index ->
                    val preset = presetOptions[index]
                    selectedColorPreset = preset.key
                    OplusInputPrefs.setEffectsColorPreset(context, preset.key)
                }
            )

            if (colorPreset == EffectColorPreset.CUSTOM) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = OplusWorkshopUiText.getEffectCustomPaletteTitle(context),
                            kind = CouiTextKind.ItemTitle,
                            color = AppTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${customPalette.size} / 8",
                            kind = CouiTextKind.GroupFooter,
                            color = AppTheme.colorScheme.onSurfaceSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        customPalette.forEachIndexed { index, colorInt ->
                            Box(
                                modifier = Modifier.size(46.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(SquircleShape(10.dp))
                                        .background(Color(colorInt))
                                        .border(
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = AppTheme.colorScheme.dividerLine.copy(alpha = 0.35f)
                                            ),
                                            shape = SquircleShape(10.dp)
                                        )
                                        .pressable(
                                            pressedScale = 0.92f,
                                            // rmuix pressable no longer uses theme-level haptics; click feedback is set explicitly
                                            haptic = HapticFeedbackType.LongPress,
                                            onClick = {
                                                editingColorIndex = index
                                            }
                                        )
                                )

                                if (customPalette.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(AppTheme.colorScheme.surface.copy(alpha = 0.92f))
                                            .border(
                                                width = 1.dp,
                                                color = AppTheme.colorScheme.dividerLine.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .pressable(
                                                pressedScale = 0.85f,
                                                // Same: the delete swatch keeps the click haptic
                                                haptic = HapticFeedbackType.LongPress,
                                                onClick = {
                                                    if (customPalette.size > 1) {
                                                        val updated = customPalette.toMutableList().also { it.removeAt(index) }
                                                        customPalette = updated
                                                        OplusInputPrefs.setCustomPalette(context, updated)
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Close,
                                            contentDescription = OplusWorkshopUiText.getEffectCustomPaletteDelete(context),
                                            tint = AppTheme.colorScheme.onSurfaceSecondary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (customPalette.size < 8) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(SquircleShape(10.dp))
                                    .background(AppTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                                    .border(
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = AppTheme.colorScheme.dividerLine.copy(alpha = 0.6f)
                                        ),
                                        shape = SquircleShape(10.dp)
                                    )
                                    .pressable(
                                        pressedScale = 0.92f,
                                        // Same: the add swatch keeps the click haptic
                                        haptic = HapticFeedbackType.LongPress,
                                        onClick = {
                                            isAddingColor = true
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Add,
                                    contentDescription = OplusWorkshopUiText.getEffectCustomPaletteAdd(context),
                                    tint = AppTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Section 2: Fine Tuning Sliders (fine-tuning parameters) ──
        BasicComponentGroup(
            title = OplusWorkshopUiText.getSectionEffectsParams(context)
        ) {
            // 1. Effect alpha
            SliderPreference(
                title = OplusWorkshopUiText.getSliderEffectsAlpha(context),
                value = alphaRatio,
                valueRange = 0f..1f,
                valueText = "${(alphaRatio * 100).toInt()}%",
                onValueChange = { value ->
                    alphaRatio = value
                    writer.submit("effects_alpha") { OplusInputPrefs.setEffectsAlphaRatio(context, value) }
                }
            )

            HorizontalDivider()

            // 2. Spread range
            SliderPreference(
                title = OplusWorkshopUiText.getSliderEffectsRange(context),
                value = rangeRatio,
                valueRange = 0.5f..2.0f,
                valueText = "${(rangeRatio * 100).toInt()}%",
                onValueChange = { value ->
                    rangeRatio = value
                    writer.submit("effects_range") { OplusInputPrefs.setEffectsRangeRatio(context, value) }
                }
            )

            HorizontalDivider()

            // 3. Animation speed
            SliderPreference(
                title = OplusWorkshopUiText.getSliderEffectsSpeed(context),
                value = speedRatio,
                valueRange = 0.5f..2.0f,
                valueText = "${(speedRatio * 100).toInt()}%",
                onValueChange = { value ->
                    speedRatio = value
                    writer.submit("effects_speed") { OplusInputPrefs.setEffectsSpeedRatio(context, value) }
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

    if (editingColorIndex != null && editingColorIndex!! in customPalette.indices) {
        val targetIdx = editingColorIndex!!
        OverlayColorPickerDialog(
            show = true,
            title = OplusWorkshopUiText.getEffectCustomPaletteEdit(context),
            confirmLabel = OplusWorkshopUiText.getString(context, R.string.oplus_input_dialog_ok),
            cancelLabel = OplusWorkshopUiText.getString(context, R.string.oplus_input_dialog_cancel),
            color = Color(customPalette[targetIdx]),
            // The dialog keeps its own draft color; live color changes are not written here. Only confirm persists.
            onColorChange = {},
            onConfirm = { newColor ->
                val updated = customPalette.toMutableList()
                updated[targetIdx] = newColor.toArgb()
                customPalette = updated
                OplusInputPrefs.setCustomPalette(context, updated)
                editingColorIndex = null
            },
            onDismissRequest = {
                editingColorIndex = null
            }
        )
    }

    if (isAddingColor) {
        val defaultNewColor = AppTheme.colorScheme.primary
        OverlayColorPickerDialog(
            show = true,
            title = OplusWorkshopUiText.getEffectCustomPaletteAdd(context),
            confirmLabel = OplusWorkshopUiText.getString(context, R.string.oplus_input_dialog_ok),
            cancelLabel = OplusWorkshopUiText.getString(context, R.string.oplus_input_dialog_cancel),
            color = defaultNewColor,
            // Same: adding a swatch persists only on confirm; cancel writes nothing
            onColorChange = {},
            onConfirm = { newColor ->
                if (customPalette.size < 8) {
                    val updated = customPalette + newColor.toArgb()
                    customPalette = updated
                    OplusInputPrefs.setCustomPalette(context, updated)
                }
                isAddingColor = false
            },
            onDismissRequest = {
                isAddingColor = false
            }
        )
    }
}
