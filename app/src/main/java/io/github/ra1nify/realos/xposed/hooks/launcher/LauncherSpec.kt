package io.github.ra1nify.realos.xposed.hooks.launcher

import android.app.Application
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.hooks.launcher.recents.RecentsStyleGate
import io.github.ra1nify.realos.xposed.hooks.launcher.recents.RecentsStyleRegistry
import io.github.ra1nify.realos.xposed.hooks.launcher.recents.RecentsStyles
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import java.util.concurrent.Executors
import io.github.ra1nify.realos.launcher.LauncherConfig

/**
 * SpecLoader entry point for the ColorOS launcher (`com.android.launcher`).
 *
 * Feature install gates live in the realOS launcher detail page. Once a feature's
 * hooks are installed, the launcher settings page still owns its runtime controls:
 * the official memory switch and the injected real-memory switch, plus the recents
 * style selection.
 *
 * Symbol resolution includes one DexKit scan and runs on a background thread from `onAppStart`:
 * - the launcher process starts with the system, so the main thread stays free;
 * - results are cached per host APK path, and a cache hit performs reflection restore only.
 */
object LauncherSpec : SpecLoader {

    private const val TAG = "RealOS-LauncherSpec"

    override val packageNames: Set<String> = SpecPackages.LAUNCHER_VARIANTS

    override val hotReloadInstallIsAsync: Boolean = true

    @Volatile
    private var installExecutor = newInstallExecutor()

    @Volatile
    private var installScheduled = false

    private fun newInstallExecutor() = Executors.newSingleThreadExecutor { r ->
        Thread(r, "realOS-launcher-install").apply { isDaemon = true }
    }

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        if (packageName !in SpecPackages.LAUNCHER_VARIANTS) return
        // Warms up configuration only; hook installation waits for the Application
        LauncherConfig.prime(module)
        module.log(Log.INFO, TAG, "onPackageReady: $packageName")
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        if (app.packageName !in SpecPackages.LAUNCHER_VARIANTS) return

        LauncherConfig.initHost(app)
        LauncherConfig.primeIfNeeded(module)

        module.log(Log.INFO, TAG, "Starting symbol resolution and hook installation")
        scheduleInstall(module, app)
    }

    private fun scheduleInstall(module: XposedModule, app: Application) {
        if (installScheduled) return
        installScheduled = true

        installExecutor.execute {
            try {
                // Install gates are read before symbol resolution so an all-off
                // configuration skips the DexKit scan and every hook install.
                val memoryEnabled = LauncherConfig.isMemoryHooksEnabled()
                val recentsExtraEnabled = LauncherConfig.isRecentsExtraStylesEnabled()
                val colorFilterEnabled = LauncherConfig.isColorFilterEnabled()
                val dockerBgEnabled = LauncherConfig.isDockerBgEnabled()
                module.log(
                    Log.INFO, TAG,
                    "feature install gates: memory=$memoryEnabled, " +
                        "recentsExtra=$recentsExtraEnabled, colorFilter=$colorFilterEnabled, " +
                        "dockerBg=$dockerBgEnabled"
                )
                if (!memoryEnabled && !recentsExtraEnabled && !colorFilterEnabled && !dockerBgEnabled) {
                    module.log(Log.INFO, TAG, "all feature gates off; skipping symbol resolution and hook install")
                    return@execute
                }

                // Layer 1: resolves descriptors (a cache hit performs reflection restore only)
                val symbols = LauncherSymbolResolver.resolve(
                    module, app, app.classLoader, dockerBgEnabled
                )
                module.log(Log.INFO, TAG, "symbols: ${LauncherSymbolResolver.describe(symbols)}")

                // Descriptors to reflection handles; failed hook points are recorded and short-circuited
                val handles = LauncherHandles.restore(symbols, app.classLoader)
                if (handles.failedKeys.isNotEmpty()) {
                    module.log(
                        Log.WARN, TAG,
                        "Symbol restore failed; these hook points will short-circuit: ${handles.failedKeys}"
                    )
                }

                if (memoryEnabled) {
                    runCatching { LauncherMemoryHook.install(module, handles) }
                        .onFailure { module.log(Log.ERROR, TAG, "memory hooks install failed", it) }
                } else {
                    module.log(Log.INFO, TAG, "memory hooks disabled by config; skipping install")
                }

                if (recentsExtraEnabled) {
                    runCatching {
                        RecentsStyles.bootstrap()
                        LauncherRecentsStyleHook.install(module, handles)
                        RecentsStyleGate.install(module, handles)
                    }.onFailure { module.log(Log.ERROR, TAG, "recents extra styles install failed", it) }
                } else {
                    module.log(Log.INFO, TAG, "recents extra styles disabled by config; skipping install")
                }

                // Hotseat background layer; onMeasure hook calls the host setter directly
                if (dockerBgEnabled) {
                    runCatching { LauncherDockerBgHook.install(module, handles) }
                        .onFailure { module.log(Log.ERROR, TAG, "hotseat background install failed", it) }
                } else {
                    module.log(Log.INFO, TAG, "hotseat background disabled by config; skipping install")
                }

                // Launcher settings entry to the standalone dock background page
                if (dockerBgEnabled) {
                    runCatching { LauncherDockSettingsInject.install(module, handles) }
                        .onFailure { module.log(Log.ERROR, TAG, "dock settings entry install failed", it) }
                } else {
                    module.log(Log.INFO, TAG, "docker bg off; skipping dock settings entry")
                }

                // Drawer "filter by color" compatibility with third-party icon packs
                if (colorFilterEnabled) {
                    runCatching { ColorFilterCompat.install(module, app.classLoader) }
                        .onFailure { module.log(Log.ERROR, TAG, "color filter install failed", it) }
                } else {
                    module.log(Log.INFO, TAG, "color filter disabled by config; skipping install")
                }

                module.log(Log.INFO, TAG, "launcher hooks install finished")
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "launcher hook install failed", t)
            } finally {
                installScheduled = false
            }
        }
    }

    override fun onHotReloadCleanup() {
        installScheduled = false
        installExecutor.shutdownNow()
        installExecutor = newInstallExecutor()
        RecentsStyleGate.reset()
        RecentsStyleRegistry.clearInstances()
        LauncherMemoryHook.reset()
        LauncherDockerBgHook.reset()
        LauncherDockSettingsInject.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        installScheduled = false
        RecentsStyleGate.reset()
        RecentsStyleRegistry.clearInstances()
        LauncherMemoryHook.reset()
        LauncherDockerBgHook.reset()
        LauncherDockSettingsInject.reset()
        onAppStart(module, app)
    }
}
