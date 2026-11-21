package io.github.ra1nify.realos.xposed.hooks.fido

import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * SpecLoader entry point for the system_server FIDO scan redirect.
 *
 * The feature only exists in system_server; the package list keeps the spec
 * discoverable for the `android`/`system` targets and the scope gating.
 */
object FidoScanRedirectSpec : SpecLoader {

    private const val TAG = "RealOS-FidoScanRedirectSpec"

    override val packageNames: Set<String> = SpecPackages.ANDROID_VARIANTS

    override fun onSystemServerStarting(module: XposedModule, classLoader: ClassLoader) {
        module.log(Log.INFO, TAG, "onSystemServerStarting")
        FidoScanRedirectHook.install(module, classLoader)
    }
}
