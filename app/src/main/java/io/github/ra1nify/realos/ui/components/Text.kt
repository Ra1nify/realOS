package io.github.ra1nify.realos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText

/**
 * realOS [Text] facade, forwarding to COUI `Text`.
 *
 * COUI exposes a single `Text` entry point that takes a semantic [CouiTextKind] instead of a free
 * `TextStyle`, so the whole app shares the ColorOS type scale, font family and weights.
 *
 * Mapping from the former `AppTheme.textStyles` roles:
 *
 * | former role | COUI kind |
 * |---|---|
 * | `body1` (16sp normal) | [CouiTextKind.ItemTitle] (16sp medium) |
 * | `subtitle` (14sp medium) | [CouiTextKind.ItemValue] (14sp regular) |
 * | `title3` (18sp medium) | [CouiTextKind.TopBar] (18sp medium) |
 * | `footnote2` (13sp normal) | [CouiTextKind.ItemSummary] (13sp regular) |
 * | `footnote1` (12sp normal) | [CouiTextKind.GroupFooter] (12sp regular) |
 *
 * `color` defaults to [Color.Unspecified], which lets COUI resolve the surrounding content color.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    kind: CouiTextKind = CouiTextKind.ItemTitle,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLineCount: (Int) -> Unit = {},
) {
    CouiText(
        text = text,
        modifier = modifier,
        kind = kind,
        color = color,
        onLineCount = onLineCount,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}
