package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as Material3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * realOS [TextButton] facade.
 *
 * rmuix uses text plus minimum size; Material3 uses a content slot. The facade keeps the rmuix
 * `text: String` / `minWidth` / `minHeight` / `textStyle` parameters and forwards [Material3TextButton].
 *
 * Default text style is `AppTheme.textStyles.body1`, the realOS body style.
 */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = 0.dp,
    minHeight: Dp = 44.dp,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    textStyle: TextStyle? = null,
) {
    Material3TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = minWidth, minHeight = minHeight),
        enabled = enabled,
        colors = colors,
    ) {
        Text(
            text = text,
            style = textStyle ?: AppTheme.textStyles.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
