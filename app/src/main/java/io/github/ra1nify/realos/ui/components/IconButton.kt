package io.github.ra1nify.realos.ui.components

import androidx.compose.material3.IconButton as Material3IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * realOS [IconButton] facade.
 *
 * coui has no matching component; forwards Material3 [Material3IconButton] with the rmuix call
 * shape: `IconButton(onClick = ..., modifier = ...) { Icon(...) }`.
 *
 * rmuix `holdDownState`, `backgroundColor`, `cornerRadius`, `minWidth`, `minHeight` and other
 * ColorOS physical-feedback parameters are absent; realOS call sites use
 * onClick/modifier/enabled/content. Appearance changes go through [modifier].
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Material3IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}
