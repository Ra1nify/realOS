package io.github.ra1nify.realos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itos.coui.topappbar.TopAppBarButton as CouiTopAppBarButton
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Top-bar back button.
 *
 * Uses the coui `TopAppBarButton`: the ColorOS 17 circular glass shell (`CouiShape(999.dp)`) with
 * the material and shadow from the top-bar effect scope, matching the bar background.
 *
 * Top bar `navigationIcon` slot only; page body back buttons use another component.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CouiTopAppBarButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = AppIcons.ArrowBack,
            contentDescription = null,
            tint = AppTheme.colorScheme.onSurface,
        )
    }
}
