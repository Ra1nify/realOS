package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group as CouiGroup

/**
 * realOS [BasicComponentGroup] facade.
 *
 * Forwards coui [CouiGroup]: rows share one smooth rounded card with an optional group title and
 * footer. Call shape matches rmuix: `BasicComponentGroup(title = ..., footer = ...) { ... }`.
 *
 * Parameter mapping (rmuix to coui):
 * - `title`/`footer` map to coui `Group(title, footer)`;
 * - `headerContent` maps to coui `headerActions` (trailing slot of the title row);
 * - `footerContent` has no coui slot and renders after the card;
 * - rmuix `cornerRadius`/`colors`/`showDividers`/`dividerStart`/`dividerEnd`/`horizontalMargin`
 *   are absent; coui `Group` default appearance applies.
 */
@Composable
fun BasicComponentGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        CouiGroup(
            title = title,
            footer = footer,
            headerActions = { headerContent?.invoke() },
            colors = CouiPageDefaults.themeColors(),
            content = content,
        )
        footerContent?.invoke()
    }
}
