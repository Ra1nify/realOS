package io.github.ra1nify.realos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itos.coui.switch.Switch as CouiSwitch

/**
 * realOS [Switch] facade.
 *
 * Forwards the coui `Switch` (ColorOS 17 switch with drag, spring rebound, loading state and
 * system haptics) with the rmuix call shape: `Switch(checked, onCheckedChange, modifier, enabled)`.
 *
 * Colors use coui theme defaults. `RealOSTheme` maps the realOS primary to coui
 * `CouiColorScheme.accent`; the checked track uses the realOS accent.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    CouiSwitch(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        loading = loading,
    )
}
