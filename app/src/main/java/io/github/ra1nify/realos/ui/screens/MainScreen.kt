package io.github.ra1nify.realos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import io.github.ra1nify.realos.ui.components.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.components.NavigationBar
import io.github.ra1nify.realos.ui.components.FloatingNavigationBarItem
import io.github.ra1nify.realos.ui.components.LargeTopAppBar
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults

enum class MainTab(val titleRes: Int) {
    INFO(R.string.nav_info),
    DETAIL(R.string.nav_detail),
    SETTINGS(R.string.nav_settings)
}

@Composable
fun MainScreen() {
    // Navigation history stack; the root page is INFO (index 0).
    val tabBackStack = remember { mutableStateListOf(0) }
    val selectedTab = tabBackStack.lastOrNull() ?: 0
    val tabs = MainTab.entries
    var isDetailSubPage by remember { mutableStateOf(false) }

    // Back handling: a non-root page or a stack depth above 1 pops to the previous page.
    // DetailScreen's BackHandler handles subpage back presses first.
    // On the INFO root page (0) the handler is disabled and the system exits the app.
    BackHandler(enabled = (tabBackStack.size > 1 || selectedTab != 0) && !isDetailSubPage) {
        if (tabBackStack.size > 1) {
            tabBackStack.removeAt(tabBackStack.lastIndex)
        } else {
            tabBackStack.clear()
            tabBackStack.add(0)
        }
    }

    val showMainNavigation = !(selectedTab == 1 && isDetailSubPage)

    // One collapsing bar state per tab; a page that starts at scroll top starts expanded.
    val scrollBehavior = key(selectedTab, showMainNavigation) {
        CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = AppScaffoldDefaults.zeroWindowInsets,
        topBar = {
            if (showMainNavigation) {
                LargeTopAppBar(
                    title = stringResource(tabs[selectedTab].titleRes),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            if (showMainNavigation) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        // Selected tab uses filled icons, unselected uses outlined.
                        val icon = when (tab) {
                            MainTab.INFO -> Icons.Outlined.Info
                            MainTab.DETAIL -> Icons.Outlined.Dashboard
                            MainTab.SETTINGS -> Icons.Outlined.Settings
                        }
                        val selectedIcon = when (tab) {
                            MainTab.INFO -> Icons.Filled.Info
                            MainTab.DETAIL -> Icons.Filled.Dashboard
                            MainTab.SETTINGS -> Icons.Filled.Settings
                        }
                        FloatingNavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (selectedTab != index) {
                                    tabBackStack.remove(index)
                                    tabBackStack.add(index)
                                }
                            },
                            icon = icon,
                            selectedIcon = selectedIcon,
                            label = stringResource(tab.titleRes)
                        )
                    }
                }
            }
        },
        containerColor = AppTheme.colorScheme.background
    ) { innerPadding ->
        val density = LocalDensity.current
        val contentPadding = remember(scrollBehavior, innerPadding, density) {
            scrollBehavior.state.contentPadding(innerPadding, density)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colorScheme.background)
        ) {
            Crossfade(
                targetState = selectedTab,
                label = "MainTabCrossfade"
            ) { tabIndex ->
                when (tabs[tabIndex]) {
                    MainTab.INFO -> InfoScreen(contentPadding = contentPadding)
                    MainTab.DETAIL -> DetailScreen(
                        contentPadding = contentPadding,
                        onSubPageChange = { isSubPage ->
                            isDetailSubPage = isSubPage
                        }
                    )
                    MainTab.SETTINGS -> SettingsScreen(contentPadding = contentPadding)
                }
            }
        }
    }
}
