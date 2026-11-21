package io.github.ra1nify.realos.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.ui.components.Icon
import io.github.ra1nify.realos.ui.components.Scaffold
import io.github.ra1nify.realos.ui.components.TopAppBar
import io.github.ra1nify.realos.ui.components.TopTips
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults

/**
 * Shared scaffold used by app secondary detail pages (Slot API template).
 *
 * Provides:
 * 1. ColorOS 17 micro-motion top bar (coui TopAppBar) and action slot (actions).
 * 2. System back key and back gesture handling (BackHandler -> onNavigateBack).
 * 3. Parent scroll Column with vertical breathing padding only; cards (BasicComponentGroup) keep
 *    their native 16dp and align with the bottom tips (TopTips).
 * 4. Top-bar back button through the coui circular `TopAppBarButton` shell.
 * 5. Optional restart action (`onRestart`) rendered with the same circular shell as the back button.
 * 6. Popup host (coui Scaffold) hosting OverlayDialog and other popups.
 *
 * rmuix RmuixTheme/TopAppBar exposes no global blur switch (`blurEnabled`); the library default
 * render path applies and the parameter is absent.
 */
@Composable
fun ModuleDetailScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null,
    onRestart: (() -> Unit)? = null,
    restartContentDescription: String? = null,
    tipsTitle: String? = null,
    tipsMessage: String? = null,
    tipsIcon: ImageVector = AppIcons.Info,
    content: @Composable ColumnScope.() -> Unit
) {
    // Intercepts the physical back key and back gesture; exits the secondary page.
    BackHandler(onBack = onNavigateBack)

    // rmuix OverlayDialog and other popups render through the Scaffold RmuixPopupHost.
    // Without a Scaffold ancestor, popup state lands in an unrendered list and taps do nothing.
    // This Scaffold supplies only the popup host: transparent container, zero padding; layout unchanged.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = AppScaffoldDefaults.zeroWindowInsets,
        topBar = {
            // ColorOS 17 top bar: in the Scaffold topBar slot the bar overlays the content,
            // and coui samples content scrolling underneath. Inside a Column it samples the solid background.
            TopAppBar(
                title = title,
                navigationIcon = {
                    BackButton(onClick = onNavigateBack)
                },
                actions = {
                    actions?.invoke(this)
                    if (onRestart != null) {
                        RestartActionButton(
                            onClick = onRestart,
                            contentDescription =
                                restartContentDescription ?: stringResource(R.string.action_restart),
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colorScheme.background)
        ) {

            // Shared scroll container; coui Group owns the section spacing, so the page adds none.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
            ) {
                content()

                if (!tipsMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    // TopTips takes required content, optional title and a Composable icon slot.
                    TopTips(
                        content = tipsMessage,
                        title = tipsTitle,
                        icon = {
                            Icon(
                                imageVector = tipsIcon,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}