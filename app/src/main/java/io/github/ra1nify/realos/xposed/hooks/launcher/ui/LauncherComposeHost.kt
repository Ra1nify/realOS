package io.github.ra1nify.realos.xposed.hooks.launcher.ui

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
import io.github.ra1nify.realos.ui.theme.SystemThemeColor
import io.github.ra1nify.realos.ui.components.Scaffold
import io.github.ra1nify.realos.ui.theme.RealOSTheme
import androidx.compose.foundation.layout.WindowInsets
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults

/**
 * Launcher Compose host.
 *
 * Difference from `CameraComposeHost`: it never calls addAssetPath on the shared host AssetManager.
 *
 * Both APKs use package id 0x7f, so adding the module APK to the host AssetManager makes
 * resources with the same id replace each other. In the launcher that crashes the settings list
 * with `Resources$NotFoundException` while resolving a host layout xml id that resolves to a
 * module string resource.
 *
 * Module resources come from a separate AssetManager / `createPackageContext`, with
 * [SafeModuleResources] as the lookup fallback, leaving host resource resolution untouched.
 */
object LauncherComposeHost {

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

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    }

    /** Host resources take priority; missing entries fall back to module resources */

    private class ModuleContextWrapper(
        base: Context,
        private val safeResources: Resources
    ) : ContextWrapper(base) {
        override fun getResources(): Resources = safeResources
    }

    /**
     * Resolves module resources. `createPackageContext` (separate AssetManager) comes first, and
     * on failure a separate AssetManager is created; the host AssetManager is never touched.
     */
    /** Module resource cache: `createPackageContext` is a PackageManager IPC, so it does not run per injection. */
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
                CompositionLocalProvider(LocalContext provides wrappedContext) {
                    val isDark = isSystemInDarkTheme()
                    val systemAccent = remember(isDark) {
                        SystemThemeColor.get(wrappedContext, isDark)
                    }
                    // rmuix RmuixTheme takes no `darkTheme` / `accentColor` / `blurEnabled` arguments: the palette
                    // decides light and dark, and the host accent overrides primary / primaryVariant only
                    // (matching ui/theme/Theme.kt). coui: theme, dark mode, and backdrop come from RealOSTheme.
                    RealOSTheme(darkTheme = isDark) {
                        // The root is a coui Scaffold: it creates the backdrop sampling layer and draws
                        // in-page topBar/bottomBar as separate bars, keeping floating bar shadows clean.
                        // Injected hosts may consume system bar insets to zero, so zero insets are passed
                        // explicitly; each child page handles the safe area from measured window values.
                        Scaffold(windowInsets = AppScaffoldDefaults.zeroWindowInsets) { _ ->
                            content()
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
