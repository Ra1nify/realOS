// realOS color picker types, rebuilt on COUI-Kit (`com.itos.coui`).
// COUI ships no color picker widget, so the picker is drawn locally; every color,
// corner and text style it uses comes from the COUI theme instead of local constants.

package io.github.ra1nify.realos.ui.extend.colorpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme

/**
 * [ColorPicker] colors.
 *
 * @param thumbColor Ring color of the area, hue and alpha thumbs.
 * @param trackColor Border color of the hue and alpha tracks.
 * @param hexTextColor Hex readout color.
 */
@Immutable
data class ColorPickerColors(
    val thumbColor: Color,
    val trackColor: Color,
    val hexTextColor: Color,
)

/**
 * [ColorPicker] defaults, expressed with COUI theme tokens and COUI shapes.
 */
object ColorPickerDefaults {
    /** Saturation/value area aspect ratio. */
    const val AREA_ASPECT_RATIO = 1.6f

    /** Saturation/value area corner radius, applied through [CouiShape]. */
    val AreaCornerRadius = 12.dp

    /** Hue and alpha track height. */
    val TrackHeight = 12.dp

    /** Thumb ring diameter. */
    val ThumbSize = 20.dp

    /** Preset swatch diameter. */
    val PresetSize = 28.dp

    /** Vertical spacing between picker rows. */
    val RowSpacing = 12.dp

    /** Thumb ring stroke width. */
    val ThumbStroke = 2.dp

    /** COUI squircle for the saturation/value area and the preset swatches. */
    val AreaShape: Shape = CouiShape(AreaCornerRadius)

    /** COUI capsule for the hue and alpha tracks. */
    val TrackShape: Shape = CouiCapsuleShape

    /** Preset swatches: colors plus black and white. */
    @Composable
    fun presetColors(): List<Color> =
        remember {
            listOf(
                Color(0xFFD32F2F),
                Color(0xFFF57C00),
                Color(0xFFFBC02D),
                Color(0xFF388E3C),
                Color(0xFF0080FF),
                Color(0xFF7B1FA2),
                Color(0xFF000000),
                Color(0xFFFFFFFF),
            )
        }

    @Composable
    fun colorPickerColors(
        thumbColor: Color = CouiTheme.colorScheme.onAccent,
        trackColor: Color = CouiTheme.colorScheme.outlineVariant,
        hexTextColor: Color = CouiTheme.colorScheme.onSurfaceVariant,
    ): ColorPickerColors =
        remember(thumbColor, trackColor, hexTextColor) {
            ColorPickerColors(thumbColor = thumbColor, trackColor = trackColor, hexTextColor = hexTextColor)
        }
}
