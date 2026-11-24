package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.os.UserHandle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import java.util.concurrent.atomic.AtomicBoolean

/** Business hooks for the personality clock plugin. */
internal object ClockFontPluginHook {

    private const val TAG = "RealOS-ClockFontPlugin"

    private val contextHookInstalled = AtomicBoolean(false)
    private val pluginHooksInstalled = AtomicBoolean(false)

    @Volatile
    private var hostPackage: String? = null

    fun install(module: XposedModule, hostPackage: String) {
        this.hostPackage = hostPackage
        if (!contextHookInstalled.compareAndSet(false, true)) return
        Log.i(TAG, "clockfont plugin hook install requested pkg=$hostPackage")
        module.log(Log.INFO, TAG, "clockfont plugin hook install requested pkg=$hostPackage")

        val contextImpl = runCatching { Class.forName("android.app.ContextImpl") }.getOrNull()
        if (contextImpl == null) {
            Log.w(TAG, "ContextImpl not found; clockfont plugin hook disabled")
            module.log(Log.WARN, TAG, "ContextImpl not found; clockfont plugin hook disabled")
            return
        }

        hookContextMethod(
            module,
            contextImpl,
            "createPackageContext",
            String::class.java,
            Int::class.javaPrimitiveType!!
        )
        hookContextMethod(
            module,
            contextImpl,
            "createPackageContextAsUser",
            String::class.java,
            Int::class.javaPrimitiveType!!,
            UserHandle::class.java
        )
    }

    fun reset() {
        contextHookInstalled.set(false)
        pluginHooksInstalled.set(false)
        ClockFontHookSupport.reset()
        ClockFontPanelHooks.reset()
        hostPackage = null
        ClockFontHost.invalidate()
    }

    private fun hookContextMethod(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        vararg params: Class<*>
    ) {
        val method = runCatching { clazz.getDeclaredMethod(name, *params) }.getOrNull() ?: return
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept { chain -> onContext(module, chain) }
            Log.i(TAG, "context hook installed: ContextImpl.$name")
            module.log(Log.INFO, TAG, "context hook installed: ContextImpl.$name")
        }.onFailure {
            Log.w(TAG, "failed to hook ContextImpl.$name: ${it.message}")
            module.log(Log.WARN, TAG, "failed to hook ContextImpl.$name: ${it.message}")
        }
    }

    private fun onContext(module: XposedModule, chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val pluginPackage = chain.args.getOrNull(0) as? String ?: return result
        if (pluginPackage != ClockFontSymbolResolver.TARGET_PLUGIN) return result

        val pluginContext = result as? Context ?: return result
        val hostContext = (chain.thisObject as? Context) ?: pluginContext
        ClockFontHost.attach(module, hostContext)

        if (!pluginHooksInstalled.compareAndSet(false, true)) return result

        val loader = pluginContext.classLoader
        val includePanel = hostPackage == SpecPackages.WALLPAPERS
        val symbols = runCatching {
            ClockFontSymbolResolver.resolvePlugin(
                module = module,
                context = ClockFontHost.context(),
                includePanel = includePanel,
                pluginContext = pluginContext,
                pluginLoader = loader
            )
        }.onFailure {
            pluginHooksInstalled.set(false)
            Log.e(TAG, "clockfont plugin symbol resolution failed", it)
            module.log(
                Log.ERROR,
                TAG,
                "clockfont plugin symbol resolution failed: ${it.message}"
            )
        }.getOrNull() ?: return result
        installPluginHooks(module, loader, symbols, includePanel)
        return result
    }

    private fun installPluginHooks(
        module: XposedModule,
        loader: ClassLoader,
        symbols: ClockFontPluginSymbols,
        includePanel: Boolean
    ) {
        Log.i(TAG, "installing plugin hooks profile=${if (includePanel) "panel+render" else "render"}")
        module.log(Log.INFO, TAG, "installing plugin hooks for ${ClockFontSymbolResolver.TARGET_PLUGIN}")

        ClockFontRenderHooks.install(module, loader, symbols)
        ClockFontTextHooks.install(module, loader, symbols)
        ClockFontGalleryHooks.install(module, loader, symbols)

        // Panel hooks are Wallpapers-only.
        if (includePanel) {
            ClockFontPanelHooks.install(module, loader, symbols)
        }
    }

}
