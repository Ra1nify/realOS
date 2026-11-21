package io.github.ra1nify.realos.xposed.hooks.font

import android.app.Application
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.xposed.loader.SpecLoader

/**
 * SpecLoader entry for the font business: injects the realOS font manager
 * entry into com.android.settings (Personalization -> Fonts).
 *
 * Fail closed: everything lives inside SystemFontSettingsHook's own
 * runCatching boundary; this spec only forwards the install call.
 */
object SystemFontSpec : SpecLoader {

    private const val TAG = "RealOS-SystemFontSpec"

    override val packageNames: Set<String> = setOf(SpecPackages.SETTINGS)

    override fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        if (packageName != SpecPackages.SETTINGS) return
        module.log(Log.INFO, TAG, "onPackageReady: $packageName")
        runCatching { SystemFontSettingsHook.install(module, classLoader) }
            .onFailure { module.log(Log.ERROR, TAG, "SystemFontSettingsHook.install failed", it) }
    }

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        onPackageReady(module, packageName, classLoader, null)
    }

    override fun onPackageReady(module: XposedModule, classLoader: ClassLoader) {
        onPackageReady(module, SpecPackages.SETTINGS, classLoader, null)
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        if (app.packageName != SpecPackages.SETTINGS) return
        runCatching { SystemFontSettingsHook.install(module, app.classLoader) }
            .onFailure { module.log(Log.ERROR, TAG, "SystemFontSettingsHook.install failed", it) }
    }

    override fun onHotReloadCleanup() {
        runCatching { SystemFontSettingsHook.reset() }
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        onHotReloadCleanup()
        onPackageReady(module, app.packageName, app.classLoader, app.applicationInfo?.sourceDir)
        onAppStart(module, app)
    }
}
