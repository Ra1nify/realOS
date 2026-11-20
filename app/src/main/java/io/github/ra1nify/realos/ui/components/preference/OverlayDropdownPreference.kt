package io.github.ra1nify.realos.ui.components.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Preference that opens an overlay list of plain string options.
 *
 * rmuix `OverlayDropdownPreference` list-of-String overload facade over coui:
 * the row uses coui [Item] and the overlay uses coui [Menu] anchored to that row.
 *
 * Signature differences from rmuix (no call site uses these parameters):
 * - `dropdownColors`: rmuix `DropdownColors` has no coui equivalent, removed;
 *   selected styling comes from coui [Menu];
 * - `renderInRootScaffold`: coui [Menu] creates its own Popup window, with no root/current
 *   Scaffold distinction, removed;
 * - `titleColor` / `summaryColor`: rmuix `BasicComponentColors` changed to nullable [Color];
 * - `entry` / `entries` overloads (rmuix supports multiple dropdown groups) are unused by call sites, not ported.
 *
 * @param items Option text list.
 * @param selectedIndex Current selected index; out of range or `-1` means no selection (no value shown).
 * @param title Title text.
 * @param modifier Modifier applied to the whole row.
 * @param titleColor Title color; `null` uses the theme primary text color.
 * @param summary Summary text.
 * @param summaryColor Summary color; `null` uses the theme secondary text color.
 * @param startAction Leading row content.
 * @param bottomAction Bottom row content rendered below the row with the row padding; coui `Item` has no bottom slot.
 * @param insideMargin Row padding; defaults to the coui row metrics (16dp horizontal, 10dp vertical).
 * @param maxHeight Overlay max height; `null` adds no limit.
 * @param enabled Whether the row is interactive; an empty option list also makes the row non-interactive.
 * @param showValue Whether to show the selected option text at the row end.
 * @param onExpandedChange Overlay expanded state change callback.
 * @param onSelectedIndexChange Selected index change callback.
 */
@Composable
fun OverlayDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    summary: String? = null,
    summaryColor: Color? = null,
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = PreferenceInsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberCouiMenuAnchor()
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    val itemsNotEmpty = items.isNotEmpty()
    val actualEnabled = enabled && itemsNotEmpty
    val resolvedTitleColor = titleColor ?: preferenceTitleColor(actualEnabled)
    val resolvedSummaryColor = summaryColor ?: preferenceSummaryColor(actualEnabled)
    val selectedText = items.getOrNull(selectedIndex)?.takeIf { it.isNotBlank() }
    val layoutDirection = LocalLayoutDirection.current
    val summaryText = summary
    val summarySlot: (@Composable () -> Unit)? =
        if (summaryText == null) {
            null
        } else {
            { PreferenceSummaryText(summaryText, resolvedSummaryColor) }
        }

    fun updateExpanded(next: Boolean) {
        if (expanded == next) return
        expanded = next
        currentOnExpandedChange?.invoke(next)
    }

    val rowClick: (() -> Unit)? =
        if (actualEnabled) {
            {
                if (expanded) {
                    updateExpanded(false)
                } else {
                    // Records the touch point so the menu opens next to the row instead of the window origin.
                    anchor.open { updateExpanded(true) }
                }
            }
        } else {
            null
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Item(
                modifier = Modifier.fillMaxWidth().couiMenuAnchor(anchor),
                title = { PreferenceTitleText(title, resolvedTitleColor) },
                summary = summarySlot,
                icon = startAction,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showValue && selectedText != null) {
                            PreferenceValueText(selectedText, resolvedSummaryColor)
                        }
                        Icon(
                            imageVector =
                                if (expanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                            contentDescription = null,
                            tint =
                                if (actualEnabled) {
                                    AppTheme.colorScheme.onSurfaceSecondary
                                } else {
                                    AppTheme.colorScheme.onSurfaceVariantSummary
                                },
                        )
                    }
                },
                onClick = rowClick,
                enabled = actualEnabled,
                contentPadding = insideMargin,
            )
            Menu(
                items =
                    items.mapIndexed { index, item ->
                        CouiMenuItem(
                            value = index,
                            text = item,
                            selected = index == selectedIndex,
                        )
                    },
                expanded = expanded,
                onDismissRequest = { updateExpanded(false) },
                onItemClick = { item ->
                    updateExpanded(false)
                    currentOnSelectedIndexChange?.invoke(item.value)
                },
                anchor = anchor,
                modifier = if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier,
            )
        }
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
