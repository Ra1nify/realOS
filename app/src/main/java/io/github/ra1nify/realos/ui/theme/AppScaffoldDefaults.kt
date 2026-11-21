package io.github.ra1nify.realos.ui.theme

import androidx.compose.foundation.layout.WindowInsets

/**
 * Global Scaffold and top bar defaults.
 *
 * ## ColorOS 17 top bar blur
 *
 * coui `TopAppBar` delegates to `TopAppBarSurface`, which draws three progressive blur layers
 * (2.7dp / 6.3dp / 10.8dp reference radius) and a 96dp gradient fade. Actions in the `actions`
 * slot share the glass through `LocalCouiToolbarBackdrop` and `CouiToolbarStyle.material`.
 */
internal object AppScaffoldDefaults {

    /**
     * Zero insets shared by every page Scaffold.
     *
     * coui top and bottom bars handle system bar padding themselves. Injected
     * pages size their safe areas from the window.
     */
    val zeroWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
}
