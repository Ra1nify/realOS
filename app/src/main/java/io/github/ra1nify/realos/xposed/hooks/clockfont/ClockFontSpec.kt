package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.app.Application
import android.util.Log as AndroidLog
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * ColorOS shared clock-font spec.
 *
 * Stage 0 / scope off: no injection, no hooks.
 * Stage 1: observe-only probe. It hooks only
 * `ContextImpl.createPackageContext(...)` to learn which class loader the host
 * receives for the two clock plugin APKs, and logs which known symbols resolve.
 * It installs no business hooks and changes no behavior.
 *
 * Stages 2/3 (font list / typeface hooks) are not implemented yet and are
 * rejected by the build gate. Do not restore the old `ClassLoader.loadClass`
 * approach: LSPosed calls onPackageReady from inside LoadedApk.getClassLoader()
 * while the LoadedApk lock is held; executing Kotlin code inside a class-load
 * hook deadlocked the Launcher main thread and caused the 2026-11-20 bootloop.
 *
 * See docs/dev/clockfont-disabled/README.md and
 * docs/dev/clockfont-disabled/SYMBOLS.md.
 */
object ClockFontSpec : SpecLoader {

    private const val TAG = "RealOS-ClockFontSpec"

    override val packageNames: Set<String> = SpecPackages.CLOCK_FONT_HOSTS

    override fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        // Temporary Phase 1 diagnostic: direct logcat, not gated by RealOSLog.
        AndroidLog.i(TAG, "onPackageReady pkg=$packageName stage=${BuildConfig.CLOCKFONT_STAGE}")
        module.log(AndroidLog.INFO, TAG, "onPackageReady pkg=$packageName stage=${BuildConfig.CLOCKFONT_STAGE}")
        // Only a framework ContextImpl hook; no host/plugin class loading here.
        if (BuildConfig.CLOCKFONT_STAGE == 1) {
            ClockFontLoaderProbe.install(module)
        }
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        AndroidLog.i(TAG, "onAppStart pkg=${app.packageName} stage=${BuildConfig.CLOCKFONT_STAGE}")
        module.log(AndroidLog.INFO, TAG, "onAppStart pkg=${app.packageName} stage=${BuildConfig.CLOCKFONT_STAGE}")
        if (BuildConfig.CLOCKFONT_STAGE == 1) {
            ClockFontLoaderProbe.install(module)
        }
    }

    override fun onHotReloadCleanup() {
        ClockFontLoaderProbe.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        AndroidLog.i(TAG, "onHotReload pkg=${app.packageName} stage=${BuildConfig.CLOCKFONT_STAGE}")
        module.log(AndroidLog.INFO, TAG, "onHotReload pkg=${app.packageName} stage=${BuildConfig.CLOCKFONT_STAGE}")
        ClockFontLoaderProbe.reset()
        if (BuildConfig.CLOCKFONT_STAGE == 1) {
            ClockFontLoaderProbe.install(module)
        }
    }
}
