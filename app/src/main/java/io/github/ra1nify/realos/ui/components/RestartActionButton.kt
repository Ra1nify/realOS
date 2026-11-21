package io.github.ra1nify.realos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.itos.coui.topappbar.TopAppBarButton as CouiTopAppBarButton
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Standard spec page restart action.
 *
 * The shared template for every spec page except the Android framework page: a ColorOS circular
 * top-bar button with the same shell, material and shadow as [BackButton].
 *
 * Must sit in the `actions` slot of a coui top bar; the coui button requires the toolbar effect
 * scope and throws outside it.
 *
 * Only the trigger is standardized. Each page keeps its own restart call, root check and Toast
 * messages, because the process and failure text differ.
 */
@Composable
fun RestartActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = stringResource(R.string.action_restart),
) {
    CouiTopAppBarButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            imageVector = AppIcons.Refresh,
            contentDescription = contentDescription,
            tint = AppTheme.colorScheme.onSurface,
        )
    }
}
