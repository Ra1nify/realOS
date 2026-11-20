package io.github.ra1nify.realos.xposed.hooks.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Self-drawn preview for plugin styles: one column of vertical pills.
 *
 * The host ships Lottie assets for stacked and tiled only, so a plugin style has no matching
 * animation; the plugin provides a `@Composable` preview and attaches it to [StyleDescriptor].
 */
@Composable
fun SlimRowsPreview(modifier: Modifier = Modifier) {
    val rowColor = AppTheme.colorScheme.onBackground.copy(alpha = 0.10f)
    val accent = AppTheme.colorScheme.onBackground.copy(alpha = 0.22f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(rowColor, RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(if (index == 0) 18.dp else 14.dp)
                        .background(accent, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 38.dp)
                        .size(width = 52.dp, height = 6.dp)
                        .background(accent, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
