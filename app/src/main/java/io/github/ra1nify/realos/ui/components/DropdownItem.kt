package io.github.ra1nify.realos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * Dropdown/selector item description.
 *
 * Same shape as rmuix `DropdownItem`; a data class, not a Composable. Business code passes it as a
 * list item to `OverlaySpinnerPreference`, which renders the options in a coui dialog list.
 *
 * This is not the coui menu contract: `com.itos.coui.menu.CouiMenuItem` takes an `ImageVector`
 * icon and has no summary field, so the composable icon slots and per-item summaries here cannot
 * be expressed by that type. `OverlayDropdownPreference` takes plain strings and uses coui `Menu`.
 *
 * @param text Primary text.
 * @param enabled Whether the item is clickable.
 * @param selected Whether the item is selected.
 * @param onClick Click callback; the dropdown host can ignore it and use `selectedIndex` instead.
 * @param icon Leading icon slot; the argument is the [Modifier] the host passes to the icon cell.
 * @param summary Secondary text.
 * @param children Submenu items; non-empty makes the row a submenu entry.
 */
@Stable
data class DropdownItem(
    val text: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val icon: (@Composable (Modifier) -> Unit)? = null,
    val summary: String? = null,
    val children: List<DropdownItem>? = null,
) {
    /**
     * Compatibility constructor matching the old rmuix `SpinnerEntry(icon, title, summary)`.
     */
    constructor(
        icon: (@Composable (Modifier) -> Unit)? = null,
        title: String? = null,
        summary: String? = null,
    ) : this(
        text = title.orEmpty(),
        icon = icon,
        summary = summary,
    )
}
