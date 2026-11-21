package io.github.ra1nify.realos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionColors
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog as CouiDialog
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * realOS [OverlayDialog] facade.
 *
 * Maps the rmuix OverlayDialog onto the coui [CouiDialog] (system Dialog window with bottom
 * slide-in and large-screen center scale) and keeps the rmuix call shape: `show`, `title`,
 * `summary`, `confirmButtonText`, `confirmButtonDestructive`, `onConfirm`, `dismissButtonText`,
 * `onDismiss`, `onDismissRequest`.
 *
 * Parameter mapping:
 * - `enableWindowDim = false` sets the coui palette `dimAmount` to 0;
 * - `largeScreen = true` selects [CouiDialogPlacement.Center], otherwise bottom slide-in;
 * - `cornerRadius` passes through to coui;
 * - `icon` and `content` render in the coui dialog body area.
 *
 * coui Dialog has no post-exit callback; rmuix `onDismissFinished` is absent. `show = false`
 * skips composition and keeps no rmuix exit-animation window.
 */
@Composable
fun OverlayDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    enableWindowDim: Boolean = true,
    confirmButtonText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmButtonDestructive: Boolean = false,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    largeScreen: Boolean? = null,
    cornerRadius: Dp? = null,
    content: @Composable () -> Unit = {},
) {
    if (!show) return

    val dialogColors = CouiDialogDefaults.themeColors()
    val effectiveColors =
        if (enableWindowDim) dialogColors else dialogColors.copy(dimAmount = 0f)
    val fallbackDismiss: () -> Unit = { (onDismiss ?: onDismissRequest)?.invoke() }

    val actionColors: CouiDialogActionColors =
        if (confirmButtonDestructive) {
            // No error/destructive slot in the realOS palette; the destructive confirm uses the coui dialog red.
            val destructiveColor = dialogColors.destructive
            CouiDialogActionColors(
                surface = destructiveColor,
                content = AppTheme.colorScheme.onPrimary,
                disabledSurface = destructiveColor.copy(alpha = 0.31f),
                disabledContent = AppTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            )
        } else {
            CouiDialogDefaults.primaryActionColors(AppTheme.colorScheme.primary)
        }

    // CouiDialogAction requires non-empty text; an empty string counts as not provided.
    val resolvedConfirmText = (confirmButtonText ?: dismissButtonText)?.takeIf { it.isNotBlank() }
    val resolvedDismissText = dismissButtonText?.takeIf { it.isNotBlank() }
    val hasConfirmButton = confirmButtonText?.isNotBlank() == true
    val confirmAction =
        CouiDialogAction(
            text = resolvedConfirmText ?: stringResource(android.R.string.ok),
            onClick = {
                if (hasConfirmButton) {
                    (onConfirm ?: onDismiss ?: onDismissRequest)?.invoke()
                } else {
                    fallbackDismiss()
                }
            },
            style = CouiDialogActionStyle.Primary,
            colors = actionColors,
        )
    val dismissAction =
        if (hasConfirmButton && resolvedDismissText != null) {
            CouiDialogAction(
                text = resolvedDismissText,
                onClick = { (onDismiss ?: onDismissRequest)?.invoke() },
                style = CouiDialogActionStyle.Secondary,
            )
        } else {
            null
        }

    CouiDialog(
        modifier = modifier,
        onDismissRequest = { (onDismissRequest ?: onDismiss)?.invoke() },
        confirmAction = confirmAction,
        title = title,
        message = summary,
        dismissAction = dismissAction,
        placement = if (largeScreen == true) CouiDialogPlacement.Center else CouiDialogPlacement.Bottom,
        colors = effectiveColors,
        cornerRadius = cornerRadius ?: Dp.Unspecified,
        content = {
            icon?.invoke()
            content()
        },
    )
}
