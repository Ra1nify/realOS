package io.github.ra1nify.realos.xposed.loader

import android.app.Application
import io.github.libxposed.api.XposedModule

/**
 * A per-app spec. One spec covers one app, possibly multiple package variants.
 * Implementations live in their own folder under hooks/<spec>/ with a thin main file.
 */
interface SpecLoader {

    /** All package names this spec applies to (exact match, no fuzzy matching). */
    val packageNames: Set<String>

    /**
     * Optional early hooking: called from the module's onPackageReady, BEFORE the
     * target Application.onCreate runs.
     * Use only for hooks that must be installed before the app's Application is
     * created (e.g. QQ's SkinEngine, whose construction timing is missed otherwise).
     */
    fun onPackageReady(module: XposedModule, classLoader: ClassLoader) {}

    fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        onPackageReady(module, classLoader)
    }

    /**
     * Same as the three-arg overload, but also carries the host APK path.
     * Specs that need DexKit structural discovery use it directly instead of
     * re-resolving the path through ActivityThread reflection.
     */
    fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        onPackageReady(module, packageName, classLoader)
    }

    /** Called from the Application.onCreate hook chain, AFTER the original call proceeds. */
    fun onAppStart(module: XposedModule, app: Application) {}

    /** Optional system_server hook: called from onSystemServerStarting. */
    fun onSystemServerStarting(module: XposedModule, classLoader: ClassLoader) {}

    /** Called before hot reloading to clean up views / receivers / leaks. */
    fun onHotReloadCleanup() {}

    /** Called after hot reloading to reinstall hooks in the currently running application. */
    fun onHotReload(module: XposedModule, app: Application) {
        onPackageReady(module, app.classLoader)
        onAppStart(module, app)
    }
}
