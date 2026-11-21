package io.github.ra1nify.realos.xposed.hooks.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.ra1nify.realos.ui.theme.SystemThemeColor
import androidx.compose.foundation.layout.WindowInsets
import io.github.ra1nify.realos.ui.components.Scaffold
import io.github.ra1nify.realos.ui.theme.RealOSTheme
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults

/**
 * RealOS tab content (Roui build).
 *
 * - All widgets use Roui: Roui has no `R.*` / `stringResource` / `painterResource` dependency; it is
 *   pure Compose used directly in the host process, with no resource bridge or reflection into host widgets.
 * - Night mode is passed in explicitly: host `Camera.onConfigurationChanged` forces uiMode to
 *   NIGHT_YES; `isSystemInDarkTheme()` reads the polluted configuration.
 * - The top-bar placeholder uses `contentPadding`, not `ComposeView.setPadding`: AbstractComposeView lays out
 *   children with `layout(0, 0, w, h)` and its own padding does not take part in layout.
 * - Feature switches live in the host's own SharedPreferences ([OplusCameraHostPrefs]), not across processes.
 */
internal fun createCameraTabContent(container: ViewGroup, topInset: MutableState<Int>): View {
    val context = container.context
    val lifecycleOwner = BridgeLifecycleOwner().apply {
        registry.currentState = Lifecycle.State.RESUMED
    }
    val viewModelOwner = BridgeViewModelStoreOwner()
    val savedStateOwner = BridgeSavedStateOwner()

    // The DecorView is the window root; Compose's WindowRecomposer looks up ViewTreeLifecycleOwner there.
    // attachTabContent runs in onViewCreated, before the pager attaches the Fragment view,
    // and the ancestor chain is broken (container.parent == null); owners are set directly on the DecorView.
    activityOf(context)?.window?.decorView?.let { decor ->
        decor.setViewTreeLifecycleOwner(lifecycleOwner)
        decor.setViewTreeViewModelStoreOwner(viewModelOwner)
        decor.setViewTreeSavedStateRegistryOwner(savedStateOwner)
    }

    var ancestor: View? = container
    while (ancestor != null) {
        ancestor.setViewTreeLifecycleOwner(lifecycleOwner)
        ancestor.setViewTreeViewModelStoreOwner(viewModelOwner)
        ancestor.setViewTreeSavedStateRegistryOwner(savedStateOwner)
        ancestor = ancestor.parent as? View
    }

    return ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(viewModelOwner)
        setViewTreeSavedStateRegistryOwner(savedStateOwner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            val night = OplusCameraThemeHook.currentSystemNight(context)
            val systemAccent = SystemThemeColor.get(context, night)
            // coui: theme, dark mode, and the backdrop layer all come from RealOSTheme;
            // no manual theme container or overlay-host Scaffold is built.
            RealOSTheme(darkTheme = night) {
                // The root uses the coui Scaffold: it builds the backdrop sampling layer internally and
                // draws the page's topBar/bottomBar as separate bars, keeping floating bar shadows correct.
                // In the host, system bar insets may be consumed by ancestors down to 0, so zero insets are
                // passed explicitly; each child page handles safe areas from measured window values.
                Scaffold(windowInsets = AppScaffoldDefaults.zeroWindowInsets) { _ ->
                    CameraTabScreen(context, topInset)
                }
            }
        }
    }
}

@Composable
private fun CameraTabScreen(context: Context, topInsetState: MutableState<Int>) {
    var themeFollowEnabled by remember {
        mutableStateOf(OplusCameraHostPrefs.isThemeFollowSystemEnabled(context))
    }
    val topInset = with(LocalDensity.current) { topInsetState.value.toDp() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.background),
        contentPadding = PaddingValues(top = topInset + 12.dp, bottom = 32.dp)
    ) {
        item {
            BasicComponentGroup(
                title = OplusCameraTabText.appearanceSection(context),
                footer = OplusCameraTabText.appearanceFooter(context)
            ) {
                SwitchPreference(
                    title = OplusCameraTabText.themeFollowTitle(context),
                    summary = OplusCameraTabText.themeFollowSummary(context),
                    checked = themeFollowEnabled,
                    onCheckedChange = { checked ->
                        themeFollowEnabled = checked
                        OplusCameraHostPrefs.setThemeFollowSystemEnabled(context, checked)
                        // The theme applies at the Activity setTheme stage; the current settings page is recreated after the change
                        val activity = activityOf(context)
                        activity?.window?.decorView?.post { runCatching { activity.recreate() } }
                    }
                )
            }
        }
    }
}

private fun activityOf(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private class BridgeLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

private class BridgeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private class BridgeSavedStateOwner : SavedStateRegistryOwner {
    // SavedStateRegistry.attach() requires the owner to still be INITIALIZED;
    // attach/restore runs first, then the lifecycle is pushed to RESUMED.
    private val lifecycleRegistry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.INITIALIZED
    }
    private val controller = SavedStateRegistryController.create(this).apply {
        performAttach()
        performRestore(null)
    }

    init {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
}