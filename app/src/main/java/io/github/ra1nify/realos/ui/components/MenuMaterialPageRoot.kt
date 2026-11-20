package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import io.github.ra1nify.realos.ui.theme.rememberMenuMaterial

/**
 * Page root that decides whether coui menus may sample the page behind them.
 *
 * coui menus use the blurred material only while a menu backdrop is provided; without one they fall
 * back to the opaque `container` colour. Which behaviour is correct is declared by the system
 * material style, so the backdrop is provided for blur and glass and withheld for solid.
 *
 * The returned backdrop modifier has to sit on the page root, because that is the node coui records
 * into the sampling source. [content] is expected to fill the given area.
 */
@Composable
fun MenuMaterialPageRoot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (rememberMenuMaterial().usesBackdrop) {
        ProvideCouiMenuBackdrop { backdropModifier ->
            Box(modifier = modifier.then(backdropModifier)) { content() }
        }
    } else {
        Box(modifier = modifier) { content() }
    }
}
