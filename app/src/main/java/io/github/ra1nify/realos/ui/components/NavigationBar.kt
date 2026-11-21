package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar as CouiNavigationBar
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Registration data of a single bottom-bar item.
 *
 * [CouiNavigationBar] accepts a data list plus `selectedIndex` and `onSelection`, with no
 * per-item Composable in a `RowScope`. The facade collects [FloatingNavigationBarItem] calls
 * into a composition-time registry and passes the list to the coui bottom bar.
 */
private class FloatingNavigationBarItemSlot {
    var label: String = ""
    var icon: ImageVector? = null
    var selectedIcon: ImageVector? = null
    var selected: Boolean = false
    var enabled: Boolean = true
    var onClick: () -> Unit = {}
}

/** Item registry paired with [NavigationBar]. Plain fields, not snapshot state. */
private class FloatingNavigationBarRegistry {
    val slots = mutableListOf<FloatingNavigationBarItemSlot>()

    fun newSlot(): FloatingNavigationBarItemSlot =
        FloatingNavigationBarItemSlot().also { slots += it }

    fun removeSlot(slot: FloatingNavigationBarItemSlot) {
        slots.remove(slot)
    }
}

/** Current bottom bar seen by [FloatingNavigationBarItem]; null outside a bottom bar. */
private val LocalFloatingNavigationBarRegistry =
    compositionLocalOf<FloatingNavigationBarRegistry?> { null }

/**
 * coui bottom-bar items require an icon. rmuix accepts `icon = null` (text-only mode); an empty
 * ImageVector fills in with no extra icon dependency.
 */
private val PlaceholderNavigationIcon: ImageVector =
    ImageVector.Builder(
        name = "realos_placeholder_navigation_icon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).build()

/**
 * realOS [NavigationBar] facade.
 *
 * Forwards the coui `NavigationBar` (ColorOS 17 floating capsule bar with pointer-tracking light, drag
 * selection and liquid deformation) and keeps the rmuix form: `NavigationBar { FloatingNavigationBarItem(...) }`.
 * `RealOSTheme` provides the backdrop. Items do not render; they register parameters and the coui
 * bottom bar draws them. An empty item list renders no bottom bar; the coui
 * `require(items.isNotEmpty())` check does not fire.
 */
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val registry = remember { FloatingNavigationBarRegistry() }

    // Zero-size container; items register only and take no visible layout space.
    CompositionLocalProvider(
        LocalFloatingNavigationBarRegistry provides registry,
    ) {
        Box(modifier = Modifier.size(0.dp)) {
            content()
        }
    }

    val slots = registry.slots.toList()
    if (slots.isEmpty()) return

    val selectedIndex = slots.indexOfFirst { it.selected }.coerceAtLeast(0)
    CouiNavigationBar(
        modifier = modifier,
        items =
            slots.map { slot ->
                CouiNavigationItem(
                    title = slot.label,
                    icon = slot.icon ?: PlaceholderNavigationIcon,
                    selectedIcon = slot.selectedIcon ?: slot.icon ?: PlaceholderNavigationIcon,
                )
            },
        selectedIndex = selectedIndex,
        onSelection = { index ->
            slots.getOrNull(index)?.onClick?.invoke()
        },
    )
}

/**
 * realOS bottom-bar item facade.
 *
 * Inside [NavigationBar] it registers parameters and draws nothing. Standalone (no registry), it
 * degrades to a simple icon and text button with visible semantics.
 *
 * rmuix `colors`, `badge` and `badgeOffset` parameters are absent; realOS call sites do not use
 * them (badges go through the preference facade).
 */
@Composable
fun FloatingNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val registry = LocalFloatingNavigationBarRegistry.current
    if (registry != null) {
        val slot = remember(registry) { registry.newSlot() }
        slot.label = label
        slot.icon = icon
        slot.selectedIcon = selectedIcon
        slot.selected = selected
        slot.enabled = enabled
        slot.onClick = onClick
        DisposableEffect(registry, slot) {
            onDispose { registry.removeSlot(slot) }
        }
        return
    }

    // Standalone fallback: outside the coui bottom bar, keeps a clickable icon and label.
    Column(
        modifier = modifier.clickable(enabled = enabled, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Selected state uses selectedIcon (filled); unselected uses icon (outlined).
        val shownIcon = if (selected) (selectedIcon ?: icon) else icon
        Icon(
            imageVector = shownIcon ?: PlaceholderNavigationIcon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            kind = CouiTextKind.GroupFooter,
            color = if (selected) AppTheme.colorScheme.primary else AppTheme.colorScheme.onSurfaceSecondary,
        )
    }
}
