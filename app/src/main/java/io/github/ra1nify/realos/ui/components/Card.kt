package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.theme.CouiTheme

/**
 * realOS [Card] facade, composed from COUI primitives.
 *
 * COUI cards are `layout.Group` (a settings card whose rows share one surface, with category
 * spacing, title and footer); it is not a generic container. This facade therefore keeps the
 * generic-container semantics and takes its shape and colors from the COUI design system:
 *
 * - shape: [CouiPageDefaults.shape], the ColorOS card squircle (24dp, corner weight 3);
 * - container: `CouiTheme.colorScheme.card`, the COUI card surface;
 * - content: `CouiTheme.colorScheme.onSurface`.
 *
 * Call shape is unchanged: `insideMargin` pads inside the card, and a non-null `onClick` makes
 * the whole card clickable.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    val shape = CouiPageDefaults.shape
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        Column(
            modifier =
                modifier
                    .clip(shape)
                    .background(scheme.card, shape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(insideMargin),
            content = content,
        )
    }
}
