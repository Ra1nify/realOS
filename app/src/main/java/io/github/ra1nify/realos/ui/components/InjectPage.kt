package io.github.ra1nify.realos.ui.components

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Full-page skeleton inside the injected host: coui Scaffold, top bar (circular glass back button)
 * and scrolling content.
 *
 * The top bar occupies the Scaffold `topBar` slot, not the first row of the content Column. coui
 * samples content scrolling under the bar area into the backdrop and renders the ColorOS 17
 * gradient blur. A top bar inside the content Column leaves a solid background and an opaque top.
 *
 * Safe area: the injected host window is edge-to-edge (`FLAG_LAYOUT_IN_SCREEN` plus transparent
 * system bars); the content container fills the screen and Compose system-bar insets can be 0
 * after host ancestor consumption. The code reads the root window status bar height and passes it
 * to the top bar as padding; the top bar sits below the status bar, and the Scaffold topBar height
 * insets the content, which still scrolls under the bar area.
 */
@Composable
fun InjectPage(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Zero insets apply to the Scaffold content window padding only (topBar height insets the content).
    // The top-bar windowInsets handle the system-bar safe area; no double padding.
    val systemBars = rememberSystemBarsInsets()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppTheme.colorScheme.background,
        contentWindowInsets = AppScaffoldDefaults.zeroWindowInsets,
        topBar = {
            TopAppBar(
                title = title,
                // Same semantics as the coui default (top plus horizontal): the status bar height keeps bar content
                // and body inside the safe area; horizontal insets cover the landscape cutout.
                windowInsets = WindowInsets(
                    left = systemBars.left,
                    top = systemBars.top,
                    right = systemBars.right,
                ),
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { actions?.invoke(this) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Padding inside the scroll container: content scrolls below the top bar, bar blur visible.
                .padding(innerPadding)
                .padding(bottom = 32.dp),
        ) {
            content()
        }
    }
}

/** System-bar insets the top bar consumes (px). */
private data class SystemBarsInsets(val left: Int, val top: Int, val right: Int) {
    companion object {
        val Zero = SystemBarsInsets(0, 0, 0)
    }
}

/**
 * Reads the current window status bar and horizontal system-bar insets (px).
 *
 * `rootWindowInsets` is window-level and unaffected by intermediate View consumption. When
 * unavailable, the status bar falls back to the android `status_bar_height` dimension, then 0.
 * Orientation changes and cutout policy changes trigger global layout and resample.
 */
@Composable
private fun rememberSystemBarsInsets(): SystemBarsInsets {
    val view = LocalView.current
    var insets by remember(view) { mutableStateOf(view.resolveSystemBarsInsets()) }

    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            // Unchanged values do not trigger recomposition.
            insets = view.resolveSystemBarsInsets()
        }
        observer.addOnGlobalLayoutListener(listener)
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }

    return insets
}

/** See [rememberSystemBarsInsets]. */
private fun View.resolveSystemBarsInsets(): SystemBarsInsets {
    val fromWindow = runCatching {
        rootWindowInsets?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
    }.getOrNull()
    if (fromWindow != null && (fromWindow.top > 0 || fromWindow.left > 0 || fromWindow.right > 0)) {
        return SystemBarsInsets(fromWindow.left, fromWindow.top, fromWindow.right)
    }

    val res = context.resources
    val id = res.getIdentifier("status_bar_height", "dimen", "android")
    val top = if (id > 0) runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0) else 0
    return if (top > 0) SystemBarsInsets(0, top, 0) else SystemBarsInsets.Zero
}
