// realOS color dialog, rebuilt on COUI-Kit (`com.itos.coui`).
//
// The shell is COUI's own `Dialog` (COUI window, scrim, corner and enter motion) instead of a
// locally styled Material3 Surface, and the buttons are COUI dialog actions driven by
// `CouiTheme.colorScheme`. Only the picker body inside the content slot is drawn locally,
// because COUI ships no color picker widget.
//
// Draft semantics are unchanged: `onColorChange` fires live during drag, `onConfirm` fires
// once with the draft color.

package io.github.ra1nify.realos.ui.extend.colorpicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionColors
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog as CouiDialog
import com.itos.coui.theme.CouiTheme
import io.github.ra1nify.realos.R

/**
 * Color dialog hosting [ColorPicker].
 *
 * The dialog holds the draft color: [onColorChange] fires during drag, and
 * [onConfirm] fires on the confirm button.
 *
 * @param show Whether the dialog is shown.
 * @param color Color at dialog open.
 * @param onColorChange Live draft color callback.
 * @param onConfirm Confirm button callback with the draft color.
 * @param onDismissRequest Callback on dismiss (outside tap, back key or cancel).
 * @param modifier Modifier applied to the dialog window.
 * @param title Dialog title.
 * @param showAlphaSlider Whether the opacity parameter row is shown.
 * @param extraRows Extra rows inserted after the opacity row, forwarded to [ColorPicker].
 * @param confirmLabel Confirm button label.
 * @param cancelLabel Cancel button label.
 * @param colors Picker colors.
 */
@Composable
fun OverlayColorPickerDialog(
    show: Boolean,
    color: Color,
    onColorChange: (Color) -> Unit,
    onConfirm: (Color) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    showAlphaSlider: Boolean = true,
    extraRows: (@Composable ColumnScope.() -> Unit)? = null,
    confirmLabel: String = colorPickerString(R.string.oplus_input_dialog_ok),
    cancelLabel: String = colorPickerString(R.string.oplus_input_dialog_cancel),
    colors: ColorPickerColors = ColorPickerDefaults.colorPickerColors(),
) {
    var draft by remember { mutableStateOf(color) }
    LaunchedEffect(show) {
        if (show) draft = color
    }

    if (!show) return

    val scheme = CouiTheme.colorScheme
    CouiDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmAction =
            CouiDialogAction(
                text = confirmLabel,
                onClick = { onConfirm(draft) },
                style = CouiDialogActionStyle.Primary,
                colors =
                    CouiDialogActionColors(
                        surface = scheme.accent,
                        content = scheme.onAccent,
                        disabledSurface = scheme.accent.copy(alpha = 0.4f),
                        disabledContent = scheme.onAccent.copy(alpha = 0.6f),
                    ),
            ),
        dismissAction =
            CouiDialogAction(
                text = cancelLabel,
                onClick = onDismissRequest,
            ),
    ) {
        // COUI's Dialog content slot carries no padding of its own (only `message` gets one), so the
        // picker supplies the dialog content margin itself; rows inside keep zero horizontal padding
        // and rely on this, which keeps the wheel and rows aligned on the same edge.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CouiDialogDefaults.contentPadding,
                        end = CouiDialogDefaults.contentPadding,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
        ) {
            ColorPicker(
                color = draft,
                onColorChange = {
                    draft = it
                    onColorChange(it)
                },
                showAlphaSlider = showAlphaSlider,
                extraRows = extraRows,
                colors = colors,
            )
        }
    }
}
