package io.github.ra1nify.realos.ui.components

import androidx.compose.material3.Icon as Material3Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * realOS [Icon] facade.
 *
 * coui exposes no Icon component (icons are `CouiIcons` Composable values); the facade forwards
 * the Material3 API with the rmuix call shape:
 * `Icon(imageVector, contentDescription, modifier, tint)`.
 *
 * Default tint reads [LocalContentColor], supplied by the `RealOSTheme` MaterialTheme. ColorOS
 * semantic colors come from explicit `AppTheme.colorScheme.*` arguments.
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Material3Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/**
 * Painter overload of [Icon] with the four-parameter rmuix call shape.
 */
@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Material3Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
