package io.github.ra1nify.realos.ui.components.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import io.github.ra1nify.realos.ui.components.DropdownItem
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Preference that opens an overlay list with icons and summaries.
 *
 * rmuix `OverlaySpinnerPreference` `items: List<DropdownItem>` overload facade over coui:
 * the row uses coui [Item], the opened list uses coui [Dialog], matching the rmuix dialog mode.
 *
 * Signature differences from rmuix (no call site uses these parameters):
 * - `spinnerColors`: rmuix `DropdownColors` has no coui equivalent, removed;
 *   the selected state uses the theme accent color;
 * - `renderInRootScaffold`: coui `Dialog` creates its own window, with no root/current
 *   Scaffold distinction, removed;
 * - `maxHeight`: overridden by the coui `Dialog` height cap; the parameter is kept and unused;
 * - `entry` / `entries` overloads and the `dialogButtonString` / `popupModifier` overloads are unused by call sites,
 *   not ported; the dialog close button text is the Android SDK "cancel" string.
 *
 * @param items Option list.
 * @param selectedIndex Current selected index; out of range or `-1` means no selection.
 * @param title Title text, also the dialog title.
 * @param modifier Modifier applied to the whole row.
 * @param titleColor Title color; `null` uses the theme primary text color.
 * @param summary Summary text.
 * @param summaryColor Summary color; `null` uses the theme secondary text color.
 * @param startAction Leading row content.
 * @param bottomAction Bottom row content rendered below the row with the row padding; coui `Item` has no bottom slot.
 * @param insideMargin Row padding; defaults to the coui row metrics (16dp horizontal, 10dp vertical).
 * @param maxHeight Dialog max height; unused, the coui `Dialog` height cap applies.
 * @param enabled Whether the row is interactive; an empty option list also makes the row non-interactive.
 * @param showValue Whether to show the selected option text at the row end.
 * @param onExpandedChange Dialog expanded state change callback.
 * @param onSelectedIndexChange Selected index change callback.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OverlaySpinnerPreference(
    items: List<DropdownItem>,
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
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    val itemsNotEmpty = items.isNotEmpty()
    val actualEnabled = enabled && itemsNotEmpty
    val resolvedTitleColor = titleColor ?: preferenceTitleColor(actualEnabled)
    val resolvedSummaryColor = summaryColor ?: preferenceSummaryColor(actualEnabled)
    val selectedText = items.getOrNull(selectedIndex)?.text?.takeIf { it.isNotBlank() }
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
            { updateExpanded(true) }
        } else {
            null
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Item(
            modifier = Modifier.fillMaxWidth(),
            title = { PreferenceTitleText(title, resolvedTitleColor) },
            summary = summarySlot,
            icon = startAction,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showValue && selectedText != null) {
                        PreferenceValueText(selectedText, resolvedSummaryColor)
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
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

    if (expanded) {
        Dialog(
            onDismissRequest = { updateExpanded(false) },
            title = title,
            confirmAction =
                CouiDialogAction(
                    text = stringResource(android.R.string.cancel),
                    onClick = { updateExpanded(false) },
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    SpinnerOptionRow(
                        item = item,
                        selected = index == selectedIndex,
                        enabled = enabled && item.enabled,
                        onClick = {
                            updateExpanded(false)
                            currentOnSelectedIndexChange?.invoke(index)
                        },
                    )
                }
            }
        }
    }
}

/** Single option row inside the dialog: icon on the left, text in the middle, a check at the selected row end. */
@Composable
private fun SpinnerOptionRow(
    item: DropdownItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val textColor =
        if (selected) {
            AppTheme.colorScheme.primary
        } else {
            AppTheme.colorScheme.onSurface
        }
    Item(
        modifier = Modifier.fillMaxWidth(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.icon?.let { icon ->
                    icon(Modifier)
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column {
                    PreferenceTitleText(item.text, textColor)
                    item.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                        PreferenceSummaryText(summary, AppTheme.colorScheme.onSurfaceSecondary)
                    }
                }
            }
        },
        trailing = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AppTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
        enabled = enabled,
        // The dialog adds 24dp horizontal padding; this row uses 8dp.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    )
}
