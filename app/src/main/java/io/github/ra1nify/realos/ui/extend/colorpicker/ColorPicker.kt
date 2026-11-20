// realOS color picker, rebuilt on COUI-Kit (`com.itos.coui`).
//
// COUI has no color picker widget, so the surfaces are drawn with Compose Canvas, while
// every color, corner and text style comes from the COUI theme:
//   - colors: `CouiTheme.colorScheme`
//   - corners: `CouiShape` (continuous corner) and `CouiCapsuleShape`
//   - hex readout: COUI `Text` with `CouiTextKind.ItemValue`
//
// Gesture contract: `trackPointer` reports (fractionX, fractionY) with the origin at the
// top-left, so fractionY grows downwards. The value axis is drawn with value = 1 at the
// top, therefore it is inverted exactly once, where it is consumed.

package io.github.ra1nify.realos.ui.extend.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText

/**
 * Color picker: saturation/value area, optional hue and alpha tracks, hex readout.
 *
 * @param color Current color.
 * @param onColorChange Fires on drag or preset pick with the new color.
 * @param modifier Modifier applied to the picker.
 * @param showAlphaSlider Whether the alpha track is shown.
 * @param showHexText Whether the `#RRGGBB` / `#AARRGGBB` readout is shown.
 * @param presetColors Preset swatches; an empty list hides the row.
 * @param colors Picker colors.
 */
@Composable
fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showAlphaSlider: Boolean = true,
    showHexText: Boolean = true,
    presetColors: List<Color> = ColorPickerDefaults.presetColors(),
    colors: ColorPickerColors = ColorPickerDefaults.colorPickerColors(),
) {
    val initial = remember { colorToHsv(color) }
    var hue by remember { mutableFloatStateOf(initial.hue) }
    var saturation by remember { mutableFloatStateOf(initial.saturation) }
    var value by remember { mutableFloatStateOf(initial.value) }

    // Tracks external color changes. A fully desaturated color keeps the current hue.
    LaunchedEffect(color) {
        val hsv = colorToHsv(color)
        if (hsv.saturation > 0f) hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ColorPickerDefaults.RowSpacing),
    ) {
        SaturationValueArea(
            hue = hue,
            saturation = saturation,
            value = value,
            thumbColor = colors.thumbColor,
            onPick = { newSaturation, newValue ->
                saturation = newSaturation
                value = newValue
                onColorChange(hsvToColor(hue, newSaturation, newValue, color.alpha))
            },
        )
        HueTrack(
            hue = hue,
            thumbColor = colors.thumbColor,
            trackColor = colors.trackColor,
            onHue = { newHue ->
                hue = newHue
                onColorChange(hsvToColor(newHue, saturation, value, color.alpha))
            },
        )
        if (showAlphaSlider) {
            AlphaTrack(
                color = hsvToColor(hue, saturation, value, 1f),
                alpha = color.alpha,
                thumbColor = colors.thumbColor,
                trackColor = colors.trackColor,
                onAlpha = { newAlpha -> onColorChange(hsvToColor(hue, saturation, value, newAlpha)) },
            )
        }
        if (showHexText) {
            CouiText(
                text = color.toHexString(includeAlpha = showAlphaSlider && color.alpha < 1f),
                kind = CouiTextKind.ItemValue,
                color = colors.hexTextColor,
            )
        }
        if (presetColors.isNotEmpty()) {
            PresetRow(colors = presetColors, onPick = onColorChange)
        }
    }
}

/**
 * Saturation (x axis) / value (y axis) area at [hue], with a ring at [saturation] and [value].
 */
@Composable
private fun SaturationValueArea(
    hue: Float,
    saturation: Float,
    value: Float,
    thumbColor: Color,
    onPick: (saturation: Float, value: Float) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(ColorPickerDefaults.AREA_ASPECT_RATIO)
                .clip(ColorPickerDefaults.AreaShape),
    ) {
        Canvas(
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) {
                    trackPointer { fractionX, fractionY ->
                        // The area paints value = 1 at the top; the gesture reports top-down.
                        onPick(fractionX, 1f - fractionY)
                    }
                },
        ) {
            drawRect(color = hsvToColor(hue, 1f, 1f, 1f))
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            drawThumb(
                center = Offset(saturation * size.width, (1f - value) * size.height),
                radius = ColorPickerDefaults.ThumbSize.toPx() / 2f,
                strokeWidth = ColorPickerDefaults.ThumbStroke.toPx(),
                color = thumbColor,
            )
        }
    }
}

/** Hue track: seven-stop gradient from red through the spectrum back to red. */
@Composable
private fun HueTrack(
    hue: Float,
    thumbColor: Color,
    trackColor: Color,
    onHue: (Float) -> Unit,
) {
    GradientTrack(
        brush =
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFFF0000),
                    Color(0xFFFFFF00),
                    Color(0xFF00FF00),
                    Color(0xFF00FFFF),
                    Color(0xFF0000FF),
                    Color(0xFFFF00FF),
                    Color(0xFFFF0000),
                ),
            ),
        fraction = (hue / 360f).coerceIn(0f, 1f),
        thumbColor = thumbColor,
        trackColor = trackColor,
        onFraction = { onHue((it * 360f).coerceIn(0f, 360f)) },
    )
}

/** Alpha track: transparent-to-opaque [color] gradient over a [trackColor] background. */
@Composable
private fun AlphaTrack(
    color: Color,
    alpha: Float,
    thumbColor: Color,
    trackColor: Color,
    onAlpha: (Float) -> Unit,
) {
    GradientTrack(
        brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color)),
        fraction = alpha.coerceIn(0f, 1f),
        thumbColor = thumbColor,
        trackColor = trackColor,
        onFraction = onAlpha,
        trackModifier = Modifier.background(trackColor),
    )
}

/**
 * Horizontal gradient track shared by the hue and alpha rows.
 *
 * The single place where a horizontal track maps a gesture to a value: `trackPointer`
 * yields (fractionX, fractionY), and only the first component is used here.
 */
@Composable
private fun GradientTrack(
    brush: Brush,
    fraction: Float,
    thumbColor: Color,
    trackColor: Color,
    onFraction: (Float) -> Unit,
    trackModifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ColorPickerDefaults.TrackHeight)
                .clip(ColorPickerDefaults.TrackShape)
                .then(trackModifier)
                .border(width = 1.dp, color = trackColor, shape = ColorPickerDefaults.TrackShape)
                .pointerInput(Unit) {
                    trackPointer { fractionX, _ -> onFraction(fractionX.coerceIn(0f, 1f)) }
                },
    ) {
        drawRect(brush = brush)
        val radius = ColorPickerDefaults.ThumbSize.toPx() / 2f
        // Keep the ring fully inside the capsule at both ends.
        val x = (fraction * size.width).coerceIn(radius, (size.width - radius).coerceAtLeast(radius))
        drawThumb(
            center = Offset(x, size.height / 2f),
            radius = radius,
            strokeWidth = ColorPickerDefaults.ThumbStroke.toPx(),
            color = thumbColor,
        )
    }
}

/** Preset swatch row. */
@Composable
private fun PresetRow(
    colors: List<Color>,
    onPick: (Color) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ColorPickerDefaults.RowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { preset ->
            Box(
                modifier =
                    Modifier
                        .size(ColorPickerDefaults.PresetSize)
                        .clip(ColorPickerDefaults.AreaShape)
                        .background(preset)
                        .border(
                            width = 1.dp,
                            color = Color.Black.copy(alpha = 0.12f),
                            shape = ColorPickerDefaults.AreaShape,
                        )
                        .pointerInput(preset) { detectTapGestures { onPick(preset) } },
            )
        }
    }
}

/** Thumb ring: a white ring with a dark hairline so it stays visible on any hue. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThumb(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color,
) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.24f),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth + 1.dp.toPx()),
    )
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth),
    )
}

/**
 * Reports normalized positions in the range 0..1 with the origin at the top-left.
 *
 * [onPosition] receives (fractionX, fractionY); fractionY grows downwards.
 */
private suspend fun PointerInputScope.trackPointer(
    onPosition: (fractionX: Float, fractionY: Float) -> Unit,
) {
    val report: (Offset) -> Unit = { offset ->
        val width = size.width.toFloat().coerceAtLeast(1f)
        val height = size.height.toFloat().coerceAtLeast(1f)
        onPosition((offset.x / width).coerceIn(0f, 1f), (offset.y / height).coerceIn(0f, 1f))
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        report(down.position)
        down.consume()
        drag(down.id) { change ->
            report(change.position)
            change.consume()
        }
    }
}
