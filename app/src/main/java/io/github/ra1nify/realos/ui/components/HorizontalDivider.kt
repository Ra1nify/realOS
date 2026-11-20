package io.github.ra1nify.realos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider as CouiDivider
import com.itos.coui.theme.CouiTheme

/**
 * realOS [HorizontalDivider] alias.
 *
 * Passes through the coui `Divider` with the kit's own defaults: 0.33dp hairline, 16dp start/end
 * inset ([CouiPageDefaults.rowPadding]) and `CouiTheme.colorScheme.outlineVariant`. The page adds
 * no local drawing, color or indent override, so group separators match ColorOS 17 exactly.
 *
 * @param startIndent Start inset; defaults to the coui row padding.
 * @param endIndent End inset; defaults to the coui row padding.
 * @param color Divider color; defaults to the coui outline variant.
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = CouiPageDefaults.rowPadding,
    endIndent: Dp = CouiPageDefaults.rowPadding,
    color: Color = CouiTheme.colorScheme.outlineVariant,
) {
    CouiDivider(
        modifier = modifier,
        startIndent = startIndent,
        endIndent = endIndent,
        color = color,
    )
}
