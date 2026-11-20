package io.github.ra1nify.realos.ui.components

import androidx.compose.material3.CircularProgressIndicator as Material3CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * realOS [CircularProgressIndicator] facade.
 *
 * coui reimplements its progress bar as `COUICircularProgressBar` and exposes no public
 * CircularProgressIndicator; the facade forwards the Material3 API.
 *
 * rmuix `state` (pause/error icon), `colors`, `strokeWidth`, `size` and `showShadow` parameters are
 * absent; realOS call sites use `modifier` and `color`.
 *
 * @param progress 0f..1f draws determinate progress; `null` (default) draws indeterminate.
 * @param color Ring color; defaults to the realOS semantic primary.
 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = AppTheme.colorScheme.primary,
) {
    if (progress == null) {
        Material3CircularProgressIndicator(
            modifier = modifier,
            color = color,
        )
    } else {
        val coercedProgress = progress.coerceIn(0f, 1f)
        Material3CircularProgressIndicator(
            progress = { coercedProgress },
            modifier = modifier,
            color = color,
        )
    }
}
