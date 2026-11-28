// realOS color picker.
//
// Fixed order, top to bottom: preview row, hue ring with its inset table, opacity row. The dialog
// title is rendered by the COUI dialog above this content.
//
// The wheel is drawn with Compose Canvas because COUI has no color picker widget; every other
// element is a COUI control:
//
// - the preview row is a `layout.Item` with the swatch as its icon and the `#AARRGGBB` value as its
//   trailing text; tapping it opens COUI's own `dialog.InputDialog`, so editing uses the system
//   input preset instead of a hand-drawn text field. The swatch is the one place besides the wheel
//   that draws: a checkerboard is the only way to show that a color is transparent;
// - the opacity row uses the settings `SliderPreference` (COUI `Item` plus `slider.Slider`), the
//   same component the settings pages and the keyboard hooks use.
//
// Geometry contract (docs/dev/color-picker-spec.md):
// - the ring owns hue and its hit zone is the annulus `innerRadius..outerRadius`; the table's
//   half-diagonal is `WheelSquareFill * innerRadius`, so the two zones cannot overlap and a table
//   corner is never taken for hue. A press inside `innerRadius` but outside the table snaps to the
//   nearest table edge;
// - the table paints value = 1 at the top, so the y axis is inverted exactly once, where the
//   gesture is consumed;
// - the hex value is always eight digits, so an opaque color never collapses to `#RRGGBB`;
// - alpha is local state, so the opacity slider sees its own value in the same frame.

package io.github.ra1nify.realos.ui.extend.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionColors
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.InputDialog
import com.itos.coui.layout.Item
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.util.ModuleResources
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SQRT_2 = 1.41421356f

/** Holds the colour this picker last emitted, so its own echo is never mistaken for an edit. */
private class EmittedColor {
    var color: Color? = null
}

/**
 * Color picker: preview row, hue ring with an inset saturation/value table, opacity row.
 *
 * @param color Current color.
 * @param onColorChange Fires on drag, on the opacity row or on a confirmed hex edit.
 * @param modifier Modifier applied to the picker.
 * @param showAlphaSlider Whether the opacity row is shown.
 * @param showHexText Whether the preview row shows the `#AARRGGBB` value and opens the hex editor;
 *   when false the row is a plain swatch.
 * @param colors Picker colors.
 * @param extraRows Further parameter rows inserted after the opacity row. Build them from COUI
 *   controls, for example `Item` + `Slider`, or a `SliderPreference`.
 */
@Composable
fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showAlphaSlider: Boolean = true,
    showHexText: Boolean = true,
    colors: ColorPickerColors = ColorPickerDefaults.colorPickerColors(),
    extraRows: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val initial = remember { colorToHsv(color) }
    var hue by remember { mutableFloatStateOf(initial.hue) }
    var saturation by remember { mutableFloatStateOf(initial.saturation) }
    var value by remember { mutableFloatStateOf(initial.value) }
    // Alpha is local state for the same reason hue is: the opacity slider has to see the value it
    // just reported in the same frame. Feeding it from `color` adds a host round-trip, and
    // CouiSliderMotion.syncValue() ends the running gesture as soon as the incoming value differs
    // from what the drag has already produced (see COUI docs/components/slider.md).
    var alpha by remember { mutableFloatStateOf(color.alpha) }
    val emitted = remember { EmittedColor() }

    // Tracks external color changes. A fully desaturated color keeps the current hue.
    //
    // The echo of our own emission is recognised by an 8-bit ARGB comparison rather than by the
    // packed Color value: alpha is packed into a half float, so a raw drag value and its packed
    // read-back differ by a fraction, and treating that as an external edit would re-seed the
    // slider mid-gesture and cancel it.
    LaunchedEffect(color) {
        val echo = emitted.color
        if (echo != null && echo.toArgb() == color.toArgb()) return@LaunchedEffect
        val hsv = colorToHsv(color)
        if (hsv.saturation > 0f) hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
        alpha = color.alpha
    }

    /** Emits [next] and remembers it, so the host echo is not applied back into the local state. */
    fun emit(next: Color) {
        emitted.color = next
        onColorChange(next)
    }

    // Everything below is driven by the local state, so the preview, the wheel and the opacity
    // slider all move in the frame that produced the gesture; `color` is only the external input.
    val currentColor = hsvToColor(hue, saturation, value, alpha)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ColorPickerDefaults.RowSpacing),
    ) {
        ColorPreviewRow(
            color = currentColor,
            showHex = showHexText,
            colors = colors,
            onColorChange = { emit(it) },
        )
        HueWheel(
            hue = hue,
            saturation = saturation,
            value = value,
            colors = colors,
            onHue = { newHue ->
                hue = newHue
                emit(hsvToColor(newHue, saturation, value, alpha))
            },
            onSaturationValue = { newSaturation, newValue ->
                saturation = newSaturation
                value = newValue
                emit(hsvToColor(hue, newSaturation, newValue, alpha))
            },
        )
        if (showAlphaSlider) {
            // While the drag runs only the local alpha is written; the host is told once, when the
            // gesture finishes. Emitting per frame re-composes the host (and, in the dialog, the
            // whole picker) on every move, and a frame that lands late makes the slider's `value`
            // disagree with its own drag - CouiSliderMotion.syncValue() then ends the gesture.
            // The preview, the hex value and the wheel all read the local alpha, so they still
            // follow the thumb live.
            SliderPreference(
                value = alpha,
                onValueChange = { newAlpha -> alpha = newAlpha },
                onValueChangeFinished = { emit(hsvToColor(hue, saturation, value, alpha)) },
                title = colorPickerString(R.string.color_picker_alpha_title),
                valueText = colorPickerString(R.string.color_picker_alpha_value, (alpha * 100).roundToInt()),
                insideMargin = PaddingValues(0.dp),
            )
        }
        val insertedRows = extraRows
        if (insertedRows != null) insertedRows.invoke(this)
    }
}

/**
 * Final preview row: color swatch on the left, `#AARRGGBB` on the right, and a tap opens the COUI
 * input dialog. The swatch draws a checkerboard under the color so a transparent value still reads
 * as transparent.
 */
@Composable
private fun ColorPreviewRow(
    color: Color,
    showHex: Boolean,
    colors: ColorPickerColors,
    onColorChange: (Color) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val openEditor: () -> Unit = { editing = true }

    Item(
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        icon = { ColorSwatch(color = color, colors = colors) },
        iconSize = ColorPickerDefaults.SwatchSize,
        onClick = if (showHex) openEditor else null,
        title = {},
        trailing = {
            if (showHex) {
                CouiText(
                    text = color.toHexArgb(),
                    kind = CouiTextKind.ItemValue,
                    color = colors.hexTextColor,
                )
            }
        },
    )

    if (editing) {
        HexInputDialog(
            initialColor = color,
            onColorChange = onColorChange,
            onDismissRequest = { editing = false },
        )
    }
}

/** Color swatch over a checkerboard, so an opaque and a transparent value look different. */
@Composable
private fun ColorSwatch(color: Color, colors: ColorPickerColors) {
    val shape = CouiShape(8.dp)
    Canvas(
        modifier =
            Modifier
                .size(ColorPickerDefaults.SwatchSize)
                .clip(shape),
    ) {
        drawCheckerboard(
            rect = Rect(Offset.Zero, size),
            cell = 4.dp.toPx(),
            light = colors.checkerLight,
            dark = colors.checkerDark,
        )
        drawRect(color = color)
        drawRect(color = colors.trackColor, style = Stroke(width = 1.dp.toPx()))
    }
}

/** Checkerboard inside [rect]; the preview swatch is the only place that needs it. */
private fun DrawScope.drawCheckerboard(
    rect: Rect,
    cell: Float,
    light: Color,
    dark: Color,
) {
    drawRect(color = light, topLeft = rect.topLeft, size = rect.size)
    var y = rect.top
    var row = 0
    while (y < rect.bottom) {
        var x = rect.left + if (row % 2 == 0) 0f else cell
        while (x < rect.right) {
            drawRect(
                color = dark,
                topLeft = Offset(x, y),
                size = Size(minOf(cell, rect.right - x), minOf(cell, rect.bottom - y)),
            )
            x += cell * 2f
        }
        y += cell
        row++
    }
}

/**
 * COUI input preset for the hex value.
 *
 * The field accepts `#AARRGGBB` or `#RRGGBB`; confirm stays disabled until the text parses, so an
 * invalid edit cannot reach the picker.
 */
@Composable
private fun HexInputDialog(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    var value by remember { mutableStateOf(TextFieldValue(initialColor.toHexArgb())) }
    val parsed = parseHexArgb(value.text)

    InputDialog(
        value = value,
        onValueChange = { new ->
            val filtered = filterHexInput(new.text)
            // Only rewrite the value when filtering changed something, so the caret survives typing.
            value = if (filtered == new.text) new else TextFieldValue(filtered, TextRange(filtered.length))
        },
        onDismissRequest = onDismissRequest,
        confirmAction =
            CouiDialogAction(
                text = colorPickerString(R.string.oplus_input_dialog_ok),
                onClick = {
                    parsed?.let(onColorChange)
                    onDismissRequest()
                },
                enabled = parsed != null,
                style = CouiDialogActionStyle.Primary,
                colors =
                    CouiDialogActionColors(
                        surface = scheme.accent,
                        content = scheme.onAccent,
                        disabledSurface = scheme.accent.copy(alpha = 0.4f),
                        disabledContent = scheme.onAccent.copy(alpha = 0.6f),
                    ),
            ),
        clearContentDescription = colorPickerString(R.string.color_picker_hex_clear),
        title = colorPickerString(R.string.color_picker_hex_title),
        placeholder = "#AARRGGBB",
        dismissAction =
            CouiDialogAction(
                text = colorPickerString(R.string.oplus_input_dialog_cancel),
                onClick = onDismissRequest,
            ),
    )
}

/** Hue ring plus the inset saturation/value table, centered and capped at [WheelMaxSize]. */
@Composable
private fun HueWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    colors: ColorPickerColors,
    onHue: (Float) -> Unit,
    onSaturationValue: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { ColorPickerDefaults.ThumbSize.toPx() / 2f }
    val currentOnHue by rememberUpdatedState(onHue)
    val currentOnSaturationValue by rememberUpdatedState(onSaturationValue)

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, ColorPickerDefaults.WheelMaxSize)
        Canvas(
            modifier =
                Modifier
                    .size(side)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val sidePx = size.width.toFloat()
                            val zone = wheelZone(down.position, sidePx)
                            if (zone == WheelZone.NONE) return@awaitEachGesture
                            // The zone is chosen on the press and kept for the gesture, so a drag
                            // from the ring across the table cannot switch what it controls.
                            applyWheelZone(
                                zone,
                                down.position,
                                sidePx,
                                currentOnHue,
                                currentOnSaturationValue,
                            )
                            down.consume()
                            drag(down.id) { change ->
                                applyWheelZone(
                                    zone,
                                    change.position,
                                    sidePx,
                                    currentOnHue,
                                    currentOnSaturationValue,
                                )
                                change.consume()
                            }
                        }
                    },
        ) {
            drawWheel(hue = hue, saturation = saturation, value = value, colors = colors, thumbRadius = thumbRadiusPx)
        }
    }
}

/** Resolves one of this module's strings from either the module or a host process. */
@Composable
internal fun colorPickerString(resId: Int, vararg args: Any): String {
    val context = LocalContext.current
    val key = args.toList()
    return remember(context, resId, key) { ModuleResources.string(context, resId, *args) }
}

/** Keeps `#` plus at most eight hex digits. */
private fun filterHexInput(raw: String): String {
    val digits = raw.removePrefix("#").filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }.take(8)
    return "#$digits"
}

private enum class WheelZone { NONE, RING, SQUARE }

/**
 * Wheel geometry for one side length.
 *
 * Drawing and hit testing both take it from here, so the painted ring and the selectable ring can
 * never drift apart.
 */
private class WheelGeometry(side: Float) {
    val center = Offset(side / 2f, side / 2f)
    val outerRadius = side / 2f
    val ringWidth = side * ColorPickerDefaults.WheelRingWidthRatio
    val innerRadius = outerRadius - ringWidth
    val tableEdge = innerRadius * SQRT_2 * ColorPickerDefaults.WheelSquareFill
    val tableLeft = (side - tableEdge) / 2f
    val tableRect = Rect(tableLeft, tableLeft, tableLeft + tableEdge, tableLeft + tableEdge)
}

private fun wheelZone(position: Offset, side: Float): WheelZone {
    val geometry = WheelGeometry(side)
    val distance = (position - geometry.center).getDistance()
    return when {
        distance in geometry.innerRadius..geometry.outerRadius -> WheelZone.RING
        // Inside the ring but outside the table: keep the press, snap to the nearest table edge.
        distance <= geometry.innerRadius -> WheelZone.SQUARE
        else -> WheelZone.NONE
    }
}

private fun applyWheelZone(
    zone: WheelZone,
    position: Offset,
    side: Float,
    onHue: (Float) -> Unit,
    onSaturationValue: (Float, Float) -> Unit,
) {
    val geometry = WheelGeometry(side)
    when (zone) {
        WheelZone.RING -> {
            val degrees =
                atan2(position.y - geometry.center.y, position.x - geometry.center.x) *
                    (180f / PI.toFloat())
            onHue((degrees + 360f) % 360f)
        }
        WheelZone.SQUARE -> {
            val x = position.x.coerceIn(geometry.tableRect.left, geometry.tableRect.right)
            val y = position.y.coerceIn(geometry.tableRect.top, geometry.tableRect.bottom)
            onSaturationValue(
                ((x - geometry.tableRect.left) / geometry.tableEdge).coerceIn(0f, 1f),
                (1f - (y - geometry.tableRect.top) / geometry.tableEdge).coerceIn(0f, 1f),
            )
        }
        WheelZone.NONE -> Unit
    }
}

private fun DrawScope.drawWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    colors: ColorPickerColors,
    thumbRadius: Float,
) {
    val geometry = WheelGeometry(size.minDimension)

    // Hue ring: an annulus filled with a sweep gradient. The first and last spectrum stop are
    // both red, so the seam at the 3 o'clock position is invisible.
    val ringPath =
        Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(center = geometry.center, radius = geometry.outerRadius))
            addOval(Rect(center = geometry.center, radius = geometry.innerRadius))
        }
    drawPath(
        path = ringPath,
        brush = Brush.sweepGradient(colors = ColorPickerDefaults.spectrumColors, center = geometry.center),
    )

    // Hue indicator, mid-ring.
    val radians = hue / 180f * PI.toFloat()
    val midRadius = (geometry.outerRadius + geometry.innerRadius) / 2f
    val indicator =
        Offset(
            geometry.center.x + midRadius * cos(radians),
            geometry.center.y + midRadius * sin(radians),
        )
    drawCircle(colors.thumbColor, radius = geometry.ringWidth * 0.42f, center = indicator)
    drawCircle(
        color = colors.trackColor,
        radius = geometry.ringWidth * 0.42f,
        center = indicator,
        style = Stroke(width = geometry.ringWidth * 0.12f),
    )
    drawCircle(hsvToColor(hue, 1f, 1f, 1f), radius = geometry.ringWidth * 0.26f, center = indicator)

    // Saturation (x) / value (y) table at the current hue.
    val tableRect = geometry.tableRect
    val tableCorner = CornerRadius(geometry.tableEdge * 0.08f)
    drawRoundRect(
        color = hsvToColor(hue, 1f, 1f, 1f),
        topLeft = tableRect.topLeft,
        size = tableRect.size,
        cornerRadius = tableCorner,
    )
    drawRoundRect(
        brush =
            Brush.horizontalGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                startX = tableRect.left,
                endX = tableRect.right,
            ),
        topLeft = tableRect.topLeft,
        size = tableRect.size,
        cornerRadius = tableCorner,
    )
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = tableRect.top,
                endY = tableRect.bottom,
            ),
        topLeft = tableRect.topLeft,
        size = tableRect.size,
        cornerRadius = tableCorner,
    )

    // Table thumb: a ring, so it reads both on white and on black.
    val thumbCenter =
        Offset(
            tableRect.left + saturation.coerceIn(0f, 1f) * geometry.tableEdge,
            tableRect.top + (1f - value.coerceIn(0f, 1f)) * geometry.tableEdge,
        )
    val stroke = ColorPickerDefaults.ThumbStroke.toPx()
    drawCircle(
        color = colors.thumbShadowColor,
        radius = thumbRadius,
        center = thumbCenter,
        style = Stroke(width = stroke + 1.dp.toPx()),
    )
    drawCircle(
        color = colors.thumbColor,
        radius = thumbRadius,
        center = thumbCenter,
        style = Stroke(width = stroke),
    )
}
