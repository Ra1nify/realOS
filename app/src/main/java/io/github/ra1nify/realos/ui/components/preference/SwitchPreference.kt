package io.github.ra1nify.realos.ui.components.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Item
import com.itos.coui.switch.CouiSwitchColors
import com.itos.coui.switch.Switch
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Switch preference with a title and summary.
 *
 * rmuix `SwitchPreference` facade over coui: public parameter names, default value semantics and
 * row click behavior are unchanged; the row uses coui [Item], the switch uses coui [Switch].
 *
 * Row click behavior, same as rmuix:
 * - no [onClick]: the row toggles the switch;
 * - [shortcutOnlyWhenChecked] true with [onClick]: a checked switch makes the row jump, an unchecked row enables the feature;
 * - otherwise the row invokes [onClick]; the switch itself stays independently toggleable.
 *
 * Signature differences from rmuix (no call site uses these parameters):
 * - `titleColor` / `summaryColor`: rmuix `BasicComponentColors` has no coui equivalent;
 *   now nullable [Color], `null` resolves the realOS theme color by enabled state;
 * - `switchColors`: now coui [CouiSwitchColors]; `null` uses the coui theme-derived defaults;
 * - `holdDownState`: coui [Item] has built-in press feedback; the parameter is kept and unused.
 *
 * @param checked Switch checked state.
 * @param onCheckedChange Switch state change callback.
 * @param title Title text.
 * @param modifier Modifier applied to the whole row.
 * @param titleColor Title color; `null` uses the theme primary text color (hint color when disabled).
 * @param summary Summary text.
 * @param summaryColor Summary color; `null` uses the theme secondary text color (hint color when disabled).
 * @param shortcutHint Shortcut hint shown in place of [summary] when the switch is checked (ColorOS shortcut row behavior).
 * @param startAction Leading row content.
 * @param endActions Trailing content before the switch.
 * @param bottomAction Bottom row content rendered below the row with the row padding; coui `Item` has no bottom slot.
 * @param switchColors Switch colors; `null` uses the coui theme defaults.
 * @param insideMargin Row padding; defaults to the coui row metrics (16dp horizontal, 10dp vertical).
 * @param holdDownState rmuix press-state hint; unused, coui `Item` manages its own press state.
 * @param enabled Whether the row is interactive.
 * @param onClick Whole-row click callback; `null` makes the row toggle the switch.
 * @param shortcutOnlyWhenChecked With [onClick] provided, whether only a checked switch makes the row click a jump.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    summary: String? = null,
    summaryColor: Color? = null,
    shortcutHint: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    switchColors: CouiSwitchColors? = null,
    insideMargin: PaddingValues = PreferenceInsideMargin,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    shortcutOnlyWhenChecked: Boolean = false,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentOnClick by rememberUpdatedState(onClick)

    // Row click behavior matches rmuix; see KDoc.
    val rowClick: (() -> Unit)? =
        when {
            onClick == null -> {
                { if (enabled) currentOnCheckedChange(!checked) }
            }

            shortcutOnlyWhenChecked -> {
                {
                    if (checked) {
                        currentOnClick?.invoke()
                    } else if (enabled) {
                        currentOnCheckedChange(true)
                    }
                }
            }

            else -> currentOnClick
        }

    val displayedSummary = if (checked && !shortcutHint.isNullOrBlank()) shortcutHint else summary
    val resolvedTitleColor = titleColor ?: preferenceTitleColor(enabled)
    val resolvedSummaryColor = summaryColor ?: preferenceSummaryColor(enabled)
    val layoutDirection = LocalLayoutDirection.current
    val summaryText = displayedSummary
    val summarySlot: (@Composable () -> Unit)? =
        if (summaryText == null) {
            null
        } else {
            { PreferenceSummaryText(summaryText, resolvedSummaryColor) }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Item(
            modifier = Modifier.fillMaxWidth(),
            title = { PreferenceTitleText(title, resolvedTitleColor) },
            summary = summarySlot,
            icon = startAction,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier =
                            Modifier
                                .padding(end = SwitchPreferenceDefaults.SwitchMarginStart)
                                .weight(1f, fill = false),
                    ) {
                        endActions()
                    }
                    PreferenceSwitch(
                        checked = checked,
                        onCheckedChange = currentOnCheckedChange,
                        enabled = enabled,
                        switchColors = switchColors,
                    )
                }
            },
            onClick = rowClick,
            enabled = enabled,
            contentPadding = insideMargin,
        )
        bottomAction?.let { content ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = insideMargin.calculateStartPadding(layoutDirection),
                            end = insideMargin.calculateEndPadding(layoutDirection),
                            bottom = insideMargin.calculateBottomPadding(),
                        ),
            ) {
                content()
            }
        }
    }
}

/** coui switch: uses the passed colors when non-null; otherwise coui derives them from the theme. */
@Composable
private fun PreferenceSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    switchColors: CouiSwitchColors?,
) {
    val change: (Boolean) -> Unit = { target -> if (enabled) onCheckedChange(target) }
    if (switchColors == null) {
        Switch(
            checked = checked,
            onCheckedChange = change,
            enabled = enabled,
        )
    } else {
        Switch(
            checked = checked,
            onCheckedChange = change,
            enabled = enabled,
            colors = switchColors,
        )
    }
}

/** [SwitchPreference] default sizes. */
object SwitchPreferenceDefaults {
    /** rmuix `SwitchPreferenceDefaults.SwitchMarginStart`: 16dp gap between trailing content and the switch. */
    val SwitchMarginStart = 16.dp
}

// ---------------------------------------------------------------------------
// internal helpers shared by every preference in this package; not public API.
// ---------------------------------------------------------------------------

/** Preference row padding: coui row metrics (16dp horizontal, 10dp vertical), matching other coui rows in `ui/components`. */
internal val PreferenceInsideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

/** Max width of the trailing value/status text; matches rmuix `BasicComponentDefaults.StatusTextMaxWidth`. */
internal val PreferenceValueMaxWidth = 162.dp

/** Start margin of the trailing status text; matches rmuix `BasicComponentDefaults.StatusTextStartMargin`. */
internal val PreferenceValueStartMargin = 24.dp

/** Row title: 16sp Medium, the same type scale as other coui rows in `ui/components`. */
@Composable
internal fun PreferenceTitleText(text: String, color: Color) {
    Text(
        text = text,
        kind = CouiTextKind.ItemTitle,
        color = color,
    )
}

/** Row summary: coui `ItemSummary` scale (13sp). */
@Composable
internal fun PreferenceSummaryText(text: String, color: Color) {
    Text(text = text, kind = CouiTextKind.ItemSummary, color = color)
}

/** Trailing value/status: 14sp secondary text, single line with ellipsis, max width capped. */
@Composable
internal fun PreferenceValueText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier =
            modifier
                .padding(start = PreferenceValueStartMargin)
                .widthIn(max = PreferenceValueMaxWidth),
        text = text,
        kind = CouiTextKind.ItemValue,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Default title color: primary text when enabled, hint color when disabled. */
@Composable
internal fun preferenceTitleColor(enabled: Boolean): Color =
    if (enabled) {
        AppTheme.colorScheme.onSurface
    } else {
        AppTheme.colorScheme.onSurfaceVariantSummary
    }

/** Default summary/status color: secondary text when enabled, hint color when disabled. */
@Composable
internal fun preferenceSummaryColor(enabled: Boolean): Color =
    if (enabled) {
        AppTheme.colorScheme.onSurfaceSecondary
    } else {
        AppTheme.colorScheme.onSurfaceVariantSummary
    }
