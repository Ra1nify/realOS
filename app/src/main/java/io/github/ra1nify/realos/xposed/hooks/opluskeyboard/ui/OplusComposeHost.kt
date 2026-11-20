package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import io.github.ra1nify.realos.xposed.common.SafeModuleResources

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.ui.theme.SystemThemeColor
import io.github.ra1nify.realos.ui.components.MenuMaterialPageRoot
import io.github.ra1nify.realos.ui.components.Scaffold
import io.github.ra1nify.realos.ui.theme.LocalMenuMaterialResolver
import io.github.ra1nify.realos.ui.theme.RealOSTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults

/**
 * Host helper to bridge Jetpack Compose into DocumentEmptyActivity without host COUI reflection.
 *
 * Invariant: addAssetPath never runs on the host's shared AssetManager. Host APK and
 * module APK both use package ID 0x7f; module assets load through their own AssetManager.
 */
object OplusComposeHost {

    private class BridgeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class BridgeSavedStateOwner(private val lco: LifecycleOwner) : SavedStateRegistryOwner {
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

    private class ModuleContextWrapper(
        base: Context,
        private val safeResources: Resources
    ) : ContextWrapper(base) {
        override fun getResources(): Resources = safeResources
    }

    /** Module resource cache; `createPackageContext` is a PackageManager IPC call. */
    @Volatile
    private var cachedModuleResources: Resources? = null

    private fun resolveModuleResources(activity: Activity, module: XposedModule?): Resources? {
        cachedModuleResources?.let { return it }
        val pkgName = module?.getModuleApplicationInfo()?.packageName ?: "io.github.ra1nify.realos"
        runCatching {
            val resources = activity.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY).resources
            cachedModuleResources = resources
            return resources
        }

        return runCatching {
            val appInfo = module?.getModuleApplicationInfo() ?: return null
            val constructor = AssetManager::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            val am = constructor.newInstance()
            AssetManager::class.java
                .getMethod("addAssetPath", String::class.java)
                .invoke(am, appInfo.sourceDir)
            @Suppress("DEPRECATION")
            Resources(am, activity.resources.displayMetrics, activity.resources.configuration)
        }.getOrNull()
    }

    fun create(
        activity: Activity,
        module: XposedModule? = null,
        content: @Composable () -> Unit
    ): View {
        val decorView = activity.window.decorView

        val moduleRes = resolveModuleResources(activity, module)
        val safeRes = SafeModuleResources(activity.resources, moduleRes)
        val wrappedContext = ModuleContextWrapper(activity, safeRes)

        val resolvedLco: LifecycleOwner = (activity as? LifecycleOwner)
            ?: decorView.findViewTreeLifecycleOwner()
            ?: BridgeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }

        val resolvedSso: SavedStateRegistryOwner = (activity as? SavedStateRegistryOwner)
            ?: decorView.findViewTreeSavedStateRegistryOwner()
            ?: BridgeSavedStateOwner(resolvedLco)

        val resolvedVmso: ViewModelStoreOwner = (activity as? ViewModelStoreOwner)
            ?: decorView.findViewTreeViewModelStoreOwner()
            ?: object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

        decorView.setViewTreeLifecycleOwner(resolvedLco)
        decorView.setViewTreeSavedStateRegistryOwner(resolvedSso)
        decorView.setViewTreeViewModelStoreOwner(resolvedVmso)

        return ComposeView(wrappedContext).apply {
            setViewTreeLifecycleOwner(resolvedLco)
            setViewTreeSavedStateRegistryOwner(resolvedSso)
            setViewTreeViewModelStoreOwner(resolvedVmso)

            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(
                    LocalContext provides wrappedContext
                ) {
                    val isDark = isSystemInDarkTheme()
                    val systemAccent = remember(isDark) {
                        SystemThemeColor.get(wrappedContext, isDark)
                    }
                    // rmuix's RmuixTheme has no `darkTheme` / `accentColor` / `blurEnabled` parameters:
                    // light/dark comes from the palette; the host accent covers only primary /
                    // primaryVariant (same as ui/theme/Theme.kt).
                    // coui: theme, light/dark mode and the backdrop layer all come from RealOSTheme.
                    // A lite build packages only the keyboard business, so the keyboard supplies
                    // the menu-material resolver itself; a full build leaves the default in place
                    // and realOS owns the decision.
                    val menuMaterialResolver =
                        if (BuildConfig.MODE_LITE) {
                            OplusKeyboardMenuMaterialAdapter
                        } else {
                            LocalMenuMaterialResolver.current
                        }
                    CompositionLocalProvider(
                        LocalMenuMaterialResolver provides menuMaterialResolver
                    ) {
                        RealOSTheme(darkTheme = isDark) {
                            // coui menus blur the page only while a menu backdrop is provided, and
                            // the host material style (solid / blur / glass) decides whether they
                            // should. Root uses the coui Scaffold: it builds the page backdrop
                            // sampling layer and draws the page topBar/bottomBar as separate bars.
                            // System bar insets may be consumed to 0 by ancestors in the host; zero
                            // insets are passed explicitly, each subpage handles its own safe area.
                            MenuMaterialPageRoot(modifier = Modifier.fillMaxSize()) {
                                Scaffold(windowInsets = AppScaffoldDefaults.zeroWindowInsets) { _ ->
                                    content()
                                }
                            }
                        }
                    }
                }
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
}
