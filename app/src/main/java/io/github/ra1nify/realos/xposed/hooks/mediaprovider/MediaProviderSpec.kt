package io.github.ra1nify.realos.xposed.hooks.mediaprovider

import android.app.Application
import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.xposed.symbol.HookPointState

object MediaProviderSpec : SpecLoader {

    private const val TAG = "RealOS-MediaProviderSpec"

    override val packageNames: Set<String> = SpecPackages.MEDIA_PROVIDER_VARIANTS

    @Volatile
    private var symbols: MediaProviderSymbols? = null

    override fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        if (packageName !in SpecPackages.MEDIA_PROVIDER_VARIANTS) return
        module.log(Log.INFO, TAG, "onPackageReady enter: $packageName")

        val appContext = currentApplication()
        val resolved = MediaProviderSymbolResolver.resolve(
            module,
            appContext,
            classLoader,
            sourceDir
        )
        symbols = resolved
        logStatus(module, resolved)
        MediaProviderPickerRefreshHook.install(module, resolved, classLoader)
    }

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        onPackageReady(module, packageName, classLoader, null)
    }

    override fun onPackageReady(module: XposedModule, classLoader: ClassLoader) {
        onPackageReady(module, SpecPackages.MEDIA_PROVIDER, classLoader, null)
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        if (app.packageName !in SpecPackages.MEDIA_PROVIDER_VARIANTS) return

        val resolved = MediaProviderSymbolResolver.resolve(
            module,
            app,
            app.classLoader,
            app.applicationInfo?.sourceDir
        )
        symbols = resolved
        logStatus(module, resolved)
        MediaProviderPickerRefreshHook.install(module, resolved, app.classLoader)
    }

    override fun onHotReloadCleanup() {
        symbols = null
        MediaProviderPickerRefreshHook.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        onHotReloadCleanup()
        onPackageReady(module, app.packageName, app.classLoader, app.applicationInfo?.sourceDir)
        onAppStart(module, app)
    }

    private fun logStatus(module: XposedModule, symbols: MediaProviderSymbols) {
        MediaProviderSymbols.ALL_HOOK_KEYS.forEach { key ->
            val state = symbols.statusMap[key] ?: HookPointState.MISSING
            module.log(
                if (state == HookPointState.FOUND) Log.INFO else Log.WARN,
                TAG,
                "HookPoint[$key] state=$state"
            )
        }
        module.log(
            Log.INFO,
            TAG,
            "MediaProvider Symbols: DbNotify=${state(symbols.canNotifyChange)}, " +
                "PickerDeleteNotify=${state(symbols.canNotifyDelete)}, " +
                "PickerSync=${state(symbols.canTriggerSync)}, " +
                "FullyResolved=${state(symbols.isFullyResolved)}"
        )
        if (symbols.scanErrors.isNotEmpty()) {
            module.log(
                Log.WARN,
                TAG,
                "Scan errors(count=${symbols.scanErrors.size}): ${symbols.scanErrors.joinToString("; ")}"
            )
        }
    }

    private fun state(ok: Boolean): String = if (ok) "FOUND" else "MISSING"

    private fun currentApplication(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()
}
