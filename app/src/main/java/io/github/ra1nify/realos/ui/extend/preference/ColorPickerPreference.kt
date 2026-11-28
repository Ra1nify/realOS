// Ported from rmuix-extend preference/ColorPickerPreference.kt (compose-rmuix-ui, Apache-2.0),
// realOS-maintained copy.
//
// Platform adaptation: the settings row uses coui `Item` (`com.itos.coui.layout.Item`)
// with `AppTheme` semantic colors. The color swatch border uses the secondary text
// color at 20% alpha.
// Public signatures and confirm-only commit behavior match the original.

package io.github.ra1nify.realos.ui.extend.preference

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Item
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.ui.extend.colorpicker.ColorPickerDefaults
import io.github.ra1nify.realos.ui.extend.colorpicker.OverlayColorPickerDialog
import io.github.ra1nify.realos.ui.extend.colorpicker.colorPickerString
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Settings row with a color swatch that opens [OverlayColorPickerDialog].
 *
 * [onColorChange] fires with the chosen color on confirm only; the row keeps
 * no draft color while the dialog is open.
 *
 * @param color Current color.
 * @param onColorChange Receives the chosen color after confirm.
 * @param title Row title.
 * @param modifier Modifier applied to the row.
 * @param summary Optional summary text.
 * @param enabled Whether the row accepts input.
 * @param showAlphaSlider Whether the dialog shows the opacity parameter row.
 * @param extraRows Extra rows inserted into the dialog picker, forwarded to
 *   [OverlayColorPickerDialog].
 * @param dialogTitle Dialog title, defaults to [title].
 * @param previewShape Swatch shape.
 * @param previewSize Swatch size.
 * @param confirmLabel Dialog confirm label.
 * @param cancelLabel Dialog cancel label.
 */
@Composable
fun ColorPickerPreference(
    color: Color,
    onColorChange: (Color) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    showAlphaSlider: Boolean = true,
    extraRows: (@Composable ColumnScope.() -> Unit)? = null,
    dialogTitle: String = title,
    previewShape: Shape = CircleShape,
    previewSize: Dp = ColorPickerDefaults.SwatchSize,
    confirmLabel: String = colorPickerString(R.string.oplus_input_dialog_ok),
    cancelLabel: String = colorPickerString(R.string.oplus_input_dialog_cancel),
) {
    var showDialog by remember { mutableStateOf(false) }

    val titleColor =
        if (enabled) AppTheme.colorScheme.onSurface else AppTheme.colorScheme.onSurfaceVariantSummary
    val summaryColor =
        if (enabled) AppTheme.colorScheme.onSurfaceSecondary else AppTheme.colorScheme.onSurfaceVariantSummary

    val summaryText = summary
    val summarySlot: (@Composable () -> Unit)? =
        if (summaryText == null) {
            null
        } else {
            {
                Text(
                    text = summaryText,
                    kind = CouiTextKind.ItemSummary,
                    color = summaryColor,
                )
            }
        }

    Item(
        modifier = modifier,
        onClick =
            if (enabled) {
                { showDialog = true }
            } else {
                null
            },
        enabled = enabled,
        title = {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = titleColor,
            )
        },
        summary = summarySlot,
        trailing = {
            Box(
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .size(previewSize)
                        .clip(previewShape)
                        .background(color)
                        .border(width = 1.dp, color = summaryColor.copy(alpha = 0.2f), shape = previewShape),
                contentAlignment = Alignment.Center,
            ) {}
        },
    )

    if (enabled) {
        OverlayColorPickerDialog(
            show = showDialog,
            color = color,
            onColorChange = {},
            onConfirm = { chosen ->
                showDialog = false
                onColorChange(chosen)
            },
            onDismissRequest = { showDialog = false },
            title = dialogTitle,
            showAlphaSlider = showAlphaSlider,
            extraRows = extraRows,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
        )
    }
}
