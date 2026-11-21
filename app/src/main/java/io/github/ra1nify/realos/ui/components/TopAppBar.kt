package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.CouiTopAppBarScrollBehavior
import com.itos.coui.topappbar.LargeTopAppBar as CouiLargeTopAppBar
import com.itos.coui.topappbar.TopAppBar as CouiTopAppBar
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * realOS compact [TopAppBar] facade.
 *
 * Forwards the coui `TopAppBar`: ColorOS 17 compact bar (52dp) with three-stage progressive blur
 * and gradient fade. Buttons in the `actions` slot share the same glass material.
 *
 * `RealOSTheme` `ProvideCouiBackdrop` / `coui Scaffold` provide the backdrop; the facade creates none.
 *
 * rmuix `color` / `titleColor` / `largeTitle` / `subtitle` / `bottomContent` / `showDivider` /
 * `scrollBehavior` parameters are absent; realOS call sites use
 * `title` / `navigationIcon` / `actions` / `modifier`.
 */
@Composable
fun TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = CouiTopAppBarDefaults.windowInsets,
) {
    CouiTopAppBar(
        modifier = modifier,
        title = {
            CouiText(
                text = title,
                kind = CouiTextKind.TopBar,
                textAlign = TextAlign.Start,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        // The gradient overlay base uses the page background; coui pure white/black differs from the realOS
        // light-gray page and draws a lighter band (wallet card page).
        colors = CouiTopAppBarDefaults.colors(
            backgroundColor = AppTheme.colorScheme.background,
            dark = CouiTheme.isDark,
        ),
        windowInsets = windowInsets,
    )
}

/**
 * realOS collapsing [LargeTopAppBar] facade.
 *
 * Forwards the coui `LargeTopAppBar`: the ColorOS first-level page header. The expanded title uses
 * coui `CouiTextKind.HomeTitle` (40sp semibold) and collapses into the compact 18sp top bar as the
 * page scrolls.
 *
 * The page scroll container must consume [CouiTopAppBarScrollBehavior.state] content padding and
 * attach [CouiTopAppBarScrollBehavior.nestedScrollConnection], otherwise the content jumps while
 * the bar collapses.
 */
@Composable
fun LargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: CouiTopAppBarScrollBehavior? = null,
    windowInsets: WindowInsets = CouiTopAppBarDefaults.windowInsets,
) {
    CouiLargeTopAppBar(
        modifier = modifier,
        // Plain text inherits the coui-provided expanded/collapsed title styles.
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = CouiTopAppBarDefaults.colors(
            backgroundColor = AppTheme.colorScheme.background,
            dark = CouiTheme.isDark,
        ),
        windowInsets = windowInsets,
    )
}
