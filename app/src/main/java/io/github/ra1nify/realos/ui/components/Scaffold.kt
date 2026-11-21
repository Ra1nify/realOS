package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.itos.coui.layout.Scaffold as CouiScaffold
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * realOS [Scaffold] facade.
 *
 * Forwards the coui `Scaffold` (self-drawn content viewport, per-bar sampling, content and insets
 * decoupled) with the rmuix call shape and `PaddingValues` content.
 *
 * Zero insets use `AppScaffoldDefaults.zeroWindowInsets` on the app and injection sides. The
 * facade forwards it to coui `windowInsets`; coui adds no system-bar insets.
 *
 * @param contentWindowInsets Insets parameter in the rmuix/M3 call shape (system bars plus cutout by default).
 * @param windowInsets Optional override; non-null takes precedence over [contentWindowInsets].
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: (@Composable () -> Unit)? = null,
    containerColor: Color = AppTheme.colorScheme.background,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    windowInsets: WindowInsets? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    // coui Scaffold renders its FAB slot as a full-screen Box plus content;
    // the rmuix/M3 empty-lambda "no FAB" form maps to null.
    val fabContent = floatingActionButton
    CouiScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = containerColor,
        windowInsets = windowInsets ?: contentWindowInsets,
        floatingActionButton = if (fabContent != null) { { fabContent() } } else null,
    ) { innerPadding ->
        // Content receives the padding of the scroll-container inner edge (see LocalScaffoldContentPadding);
        // content scrolls under the coui top and bottom bars, and the bar blur stays visible.
        //
        // Nested Scaffolds (a page adds a layer only as a popup host, with no bars of its own) keep the
        // outer bar padding; the facade merges both with maxOf.
        val outerPadding = LocalScaffoldContentPadding.current
        val mergedPadding = PaddingValues(
            top = maxOf(outerPadding.calculateTopPadding(), innerPadding.calculateTopPadding()),
            bottom = maxOf(outerPadding.calculateBottomPadding(), innerPadding.calculateBottomPadding()),
        )
        CompositionLocalProvider(LocalScaffoldContentPadding provides mergedPadding) {
            content(innerPadding)
        }
    }
}
