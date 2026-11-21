package io.github.ra1nify.realos.xposed.hooks.navigation

import android.app.Application
import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.opluskeyboard.OplusModuleRef
import io.github.ra1nify.realos.xposed.hooks.navigation.glass.SystemUiGestureHandleHook
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.xposed.symbol.HookPointState

/**
 * SpecLoader entry for the navigation business.
 *
 * The gesture/nav feature spans two hosts:
 * - com.android.systemui: the actual gesture and nav-bar hooks;
 * - com.android.settings: the config UI pages injected into the host settings app
 *   (SettingsNavInject + gesture compose hosts).
 *
 * Both branches resolve their own symbol layers and install independently
 * with fail-closed isolation.
 */
object SystemUISpec : SpecLoader {

    private const val TAG = "RealOS-SystemUISpec"

    override val packageNames: Set<String> =
        SpecPackages.SYSTEMUI_VARIANTS + SpecPackages.SETTINGS_VARIANTS

    @Volatile
    private var symbols: SystemUiSymbols? = null

    @Volatile
    private var handles: SystemUiHandles? = null

    @Volatile
    private var isInstalled = false

    @Volatile
    private var settingsSymbols: SettingsSymbols? = null

    @Volatile
    private var settingsHandles: SettingsHandles? = null

    @Volatile
    private var settingsInstalled = false

    override fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        if (packageName !in packageNames) return
        module.log(Log.INFO, TAG, "onPackageReady enter: $packageName")

        when (packageName) {
            SpecPackages.SYSTEMUI -> installSystemUiHooks(module, packageName, classLoader, sourceDir)
            SpecPackages.SETTINGS -> installSettingsUiHooks(module, classLoader, sourceDir)
        }
    }

    /** com.android.systemui branch: gesture and nav-bar hooks. */
    private fun installSystemUiHooks(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        SystemUiConfig.prime(module)

        val appContext = runCatching {
            val actThreadCls = Class.forName("android.app.ActivityThread")
            val currAppMethod = actThreadCls.getMethod("currentApplication")
            currAppMethod.invoke(null) as? Context
        }.getOrNull()

        // 1. Resolve symbols (Layer 1)
        val sym = SystemUiSymbolResolver.resolve(module, appContext, classLoader, sourceDir)
        symbols = sym

        // 2. Restore handles (Layer 2)
        val h = SystemUiHandles.restore(sym, classLoader)
        handles = h

        // 3. Log status map for diagnostics
        ALL_SYSTEMUI_HOOK_KEYS.forEach { key ->
            val state = sym.statusMap[key] ?: HookPointState.MISSING
            module.log(Log.INFO, TAG, "HookPoint[$key] state=$state")
        }

        // 4. Install hooks independently with fail-closed protection (Layer 3)
        if (h.canHookGestureBack) {
            try {
                SystemUiGestureBackHook.install(module, classLoader, h)
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to install gesture back hook", t)
            }
        } else {
            module.log(Log.WARN, TAG, "Gesture back hook skipped: missing symbols")
        }

        if (h.canHookNavBarButtons) {
            try {
                SystemUiNavBarButtonHook.install(module, classLoader, h)
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to install nav bar button hook", t)
            }
        } else {
            module.log(Log.WARN, TAG, "NavBar buttons hook skipped: missing symbols")
        }

        if (h.canHookGestureHandle) {
            try {
                SystemUiGestureHandleHook.install(module, classLoader, h)
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to install gesture handle hook", t)
            }
        } else {
            module.log(Log.WARN, TAG, "Gesture handle hook skipped: missing symbols")
        }

        isInstalled = true
        module.log(Log.INFO, TAG, "onPackageReady finished: $packageName")
    }

    /** com.android.settings branch: config UI pages injected into the host settings app. */
    private fun installSettingsUiHooks(module: XposedModule, classLoader: ClassLoader, sourceDir: String?) {
        OplusModuleRef.set(module)
        SystemUiConfig.prime(module)

        val appContext = runCatching {
            val actThreadCls = Class.forName("android.app.ActivityThread")
            val currAppMethod = actThreadCls.getMethod("currentApplication")
            currAppMethod.invoke(null) as? Context
        }.getOrNull()

        // 1. Resolve symbols (Layer 1)
        val sym = SettingsSymbolResolver.resolve(module, appContext, classLoader, sourceDir)
        settingsSymbols = sym

        // 2. Restore handles (Layer 2)
        val h = SettingsHandles.restore(sym, classLoader)
        settingsHandles = h

        // 3. Log status map for diagnostics
        SettingsSymbols.ALL_SETTINGS_HOOK_KEYS.forEach { key ->
            val state = sym.statusMap[key] ?: HookPointState.MISSING
            module.log(Log.INFO, TAG, "HookPoint[$key] state=$state")
        }

        // 4. Install hooks with fail-closed protection (Layer 3)
        if (h.canInjectNav) {
            try {
                SettingsNavInject.install(module, classLoader, h)
                settingsInstalled = true
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "SettingsNavInject.install failed", t)
            }
        } else {
            module.log(Log.WARN, TAG, "SettingsNavInject skipped: missing symbols")
        }
    }

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        onPackageReady(module, packageName, classLoader, null)
    }

    override fun onPackageReady(module: XposedModule, classLoader: ClassLoader) {
        onPackageReady(module, SpecPackages.SYSTEMUI, classLoader, null)
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        if (app.packageName !in packageNames) return
        module.log(Log.INFO, TAG, "onAppStart: ${app.packageName}")

        if (app.packageName == SpecPackages.SYSTEMUI && !isInstalled) {
            onPackageReady(module, app.packageName, app.classLoader, app.applicationInfo?.sourceDir)
        }
        if (app.packageName == SpecPackages.SETTINGS && !settingsInstalled) {
            onPackageReady(module, app.packageName, app.classLoader, app.applicationInfo?.sourceDir)
        }
    }

    override fun onHotReloadCleanup() {
        isInstalled = false
        settingsInstalled = false
        runCatching { SystemUiGestureHandleHook.cleanup() }
        // Both hooks hold Application-level broadcast receivers; cleanup unregisters them.
        runCatching { SystemUiGestureAppearanceHook.reset() }
        runCatching { SystemUiNavBarButtonHook.reset() }
        // Clears the per-host-class reflection caches on hot reload.
        runCatching { NavButtonDispatcherViews.clearCache() }
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        onHotReloadCleanup()
        onPackageReady(module, app.packageName, app.classLoader, app.applicationInfo?.sourceDir)
        onAppStart(module, app)
    }
}
