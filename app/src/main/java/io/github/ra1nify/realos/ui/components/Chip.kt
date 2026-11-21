package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCapsuleShape
import androidx.compose.material3.Text
import com.itos.coui.chip.CouiChipTabDefaults
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * realOS [Chip] colors.
 *
 * Fields match rmuix `ChipColors`; business code builds colors through [ChipDefaults.chipColors]
 * and reads `contentColor` and related properties.
 */
@Immutable
data class ChipColors(
    val containerColor: Color,
    val contentColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
    val selectedDisabledContainerColor: Color,
    val selectedDisabledContentColor: Color,
    /** Drawn only when specified; [Color.Unspecified] means no border (rmuix default). */
    val borderColor: Color = Color.Unspecified,
    /** Selected border color; unspecified means no border. */
    val selectedBorderColor: Color = Color.Unspecified,
)

/**
 * [Chip] defaults.
 *
 * Keeps the rmuix `ChipDefaults` entry point: business code calls
 * `ChipDefaults.chipColors(containerColor = ..., contentColor = ..., borderColor = ...)`.
 */
object ChipDefaults {

    /** Border width. ColorOS chips have no border by default; borders are a realOS status-label extension. */
    val BorderWidth = 1.dp

    /** Capsule height (28dp, matching the rmuix Suggestion tier). */
    val MinHeight = 28.dp

    /** Horizontal content padding. */
    val HorizontalPadding = 12.dp

    /** Vertical content padding. */
    val VerticalPadding = 4.dp

    /** Gap between the leading icon, the label and the close affordance. */
    val ContentSpacing = 4.dp

    /**
     * Builds [ChipColors] from realOS semantic colors; no hardcoded color values.
     */
    @Composable
    fun chipColors(
        containerColor: Color = AppTheme.colorScheme.surfaceContainerHigh,
        contentColor: Color = AppTheme.colorScheme.onSurface,
        selectedContainerColor: Color = AppTheme.colorScheme.primary,
        selectedContentColor: Color = AppTheme.colorScheme.onPrimary,
        disabledContainerColor: Color = AppTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        disabledContentColor: Color = AppTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        selectedDisabledContainerColor: Color = AppTheme.colorScheme.primary.copy(alpha = 0.38f),
        selectedDisabledContentColor: Color = AppTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
        borderColor: Color = Color.Unspecified,
        selectedBorderColor: Color = Color.Unspecified,
    ): ChipColors =
        ChipColors(
            containerColor = containerColor,
            contentColor = contentColor,
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = selectedContentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
            selectedDisabledContainerColor = selectedDisabledContainerColor,
            selectedDisabledContentColor = selectedDisabledContentColor,
            borderColor = borderColor,
            selectedBorderColor = selectedBorderColor,
        )
}

/**
 * realOS [Chip] facade, composed from COUI primitives.
 *
 * COUI has no generic chip: `chip.ChipTab` is a selectable tab that samples the page backdrop
 * and owns its own selection motion, which does not fit a non-interactive status badge. The
 * capsule is therefore composed from COUI `CouiCapsuleShape` and COUI `Text`, so shape, type
 * scale and colors all come from the COUI design system.
 *
 * Call shape is unchanged: `Chip(onClick, label, selected, enabled, colors, borderWidth,
 * onClose, icon)`.
 */
@Composable
fun Chip(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    colors: ChipColors = ChipDefaults.chipColors(),
    borderWidth: Dp = ChipDefaults.BorderWidth,
    onClose: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val containerColor =
        when {
            !enabled && selected -> colors.selectedDisabledContainerColor
            !enabled -> colors.disabledContainerColor
            selected -> colors.selectedContainerColor
            else -> colors.containerColor
        }
    val contentColor =
        when {
            !enabled && selected -> colors.selectedDisabledContentColor
            !enabled -> colors.disabledContentColor
            selected -> colors.selectedContentColor
            else -> colors.contentColor
        }
    val borderColor = if (selected) colors.selectedBorderColor else colors.borderColor
    val hasBorder = borderColor.isSpecified && borderWidth > 0.dp

    Row(
        modifier =
            modifier
                .heightIn(min = ChipDefaults.MinHeight)
                .clip(CouiCapsuleShape)
                .background(containerColor, CouiCapsuleShape)
                .then(
                    if (hasBorder) {
                        Modifier.border(borderWidth, borderColor, CouiCapsuleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(
                    horizontal = ChipDefaults.HorizontalPadding,
                    vertical = ChipDefaults.VerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipDefaults.ContentSpacing),
    ) {
        icon?.invoke()
        // COUI's own ChipTab renders its label with M3 Text plus this token (14sp medium), so the
        // capsule keeps the COUI chip type scale instead of the 12sp group-footer size.
        Text(
            text = label,
            style = CouiChipTabDefaults.textStyle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClose != null) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = null,
                modifier = Modifier.size(12.dp).clickable(onClick = onClose),
                tint = contentColor,
            )
        }
    }
}
