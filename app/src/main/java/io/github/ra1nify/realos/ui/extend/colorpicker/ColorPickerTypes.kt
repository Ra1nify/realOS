// realOS color picker types.
//
// The hue ring and its inset saturation/value table are drawn locally because COUI has no color
// picker widget; everything else in the picker is a COUI control.
//
// Geometry contract (docs/dev/color-picker-spec.md): the ring owns hue and its hit zone is the
// annulus `innerRadius..outerRadius`, the table keeps `WheelSquareFill` of the inner radius as its
// half-diagonal, so the two zones cannot overlap.

package io.github.ra1nify.realos.ui.extend.colorpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.theme.CouiTheme

/**
 * [ColorPicker] colors.
 *
 * @param thumbColor Table thumb ring color.
 * @param trackColor Table thumb hairline.
 * @param hexTextColor Hex value text.
 * @param thumbShadowColor Soft halo behind the table thumb.
 * @param checkerLight Checkerboard light cell under the preview swatch.
 * @param checkerDark Checkerboard dark cell under the preview swatch.
 */
@Immutable
data class ColorPickerColors(
    val thumbColor: Color,
    val trackColor: Color,
    val hexTextColor: Color,
    val thumbShadowColor: Color = Color.Black.copy(alpha = 0.16f),
    val checkerLight: Color = Color.White,
    val checkerDark: Color = Color(0xFFDBDBDB),
)

/**
 * [ColorPicker] geometry and defaults, expressed with COUI tokens.
 *
 * The wheel proportions follow the ColorOS color wheel: the ring takes
 * [WheelRingWidthRatio] of the side and the table keeps [WheelSquareFill] of the radius that
 * remains inside the ring, which leaves a visible gutter and keeps the table clear of the ring.
 */
object ColorPickerDefaults {

    /**
     * Largest wheel side; the wheel is square and centers inside the available width.
     *
     * Bounded by the COUI dialog width (392dp) rather than height: the picker content is a plain
     * column with no scroll container, so the wheel cannot push a slider out of reach.
     */
    val WheelMaxSize = 210.dp

    /** Hue ring width as a fraction of the wheel side. */
    const val WheelRingWidthRatio = 0.11f

    /** Saturation/value table half-diagonal as a fraction of the ring inner radius. */
    const val WheelSquareFill = 0.9f

    /** Table thumb diameter. */
    val ThumbSize = 20.dp

    /** Table thumb ring stroke width. */
    val ThumbStroke = 2.dp

    /** Vertical spacing between picker rows: the COUI settings group spacing. */
    val RowSpacing = CouiPageDefaults.groupSpacing

    /** Settings row swatch diameter. */
    val SwatchSize = 28.dp

    /** Hue ring stops: red through the spectrum back to red. */
    val spectrumColors: List<Color> =
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF00FFFF),
            Color(0xFF0000FF),
            Color(0xFFFF00FF),
            Color(0xFFFF0000),
        )

    @Composable
    fun colorPickerColors(
        thumbColor: Color = CouiTheme.colorScheme.onAccent,
        trackColor: Color = CouiTheme.colorScheme.outlineVariant,
        hexTextColor: Color = CouiTheme.colorScheme.onSurfaceVariant,
        thumbShadowColor: Color = CouiTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        checkerLight: Color = CouiTheme.colorScheme.surface,
        checkerDark: Color = CouiTheme.colorScheme.outlineVariant,
    ): ColorPickerColors =
        remember(thumbColor, trackColor, hexTextColor, thumbShadowColor, checkerLight, checkerDark) {
            ColorPickerColors(
                thumbColor = thumbColor,
                trackColor = trackColor,
                hexTextColor = hexTextColor,
                thumbShadowColor = thumbShadowColor,
                checkerLight = checkerLight,
                checkerDark = checkerDark,
            )
        }
}