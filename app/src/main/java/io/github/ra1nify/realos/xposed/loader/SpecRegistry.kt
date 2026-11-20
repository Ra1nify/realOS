package io.github.ra1nify.realos.xposed.loader

import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.BuildConfig

/**
 * Central spec registry for realOS.
 * Registered specs run for their matched packages.
 * Controlled at build-time by BuildConfig.SCOPE_* flags from local.properties.
 * Specs are loaded reflectively so disabled scope source code can be excluded from compilation.
 */
object SpecRegistry {

    private const val TAG = "RealOS-SpecRegistry"

    private fun loadSpec(fqn: String): SpecLoader? {
        return runCatching {
            val clazz = Class.forName(fqn)
            clazz.getField("INSTANCE").get(null) as? SpecLoader
        }.onFailure { t ->
            Log.w(TAG, "Failed to load SpecLoader for $fqn", t)
        }.getOrNull()
    }

    /** Per-app specs included in current build. */
    val specs: List<SpecLoader> = buildList {
        if (BuildConfig.SCOPE_OPLUS_KEYBOARD) loadSpec("io.github.ra1nify.realos.xposed.hooks.opluskeyboard.OplusInputSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_NAVIGATION) loadSpec("io.github.ra1nify.realos.xposed.hooks.navigation.SystemUISpec")?.let { add(it) }
        if (BuildConfig.SCOPE_FEATURESWITCH) loadSpec("io.github.ra1nify.realos.xposed.hooks.featureswitch.FeatureSwitchSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_FONT) loadSpec("io.github.ra1nify.realos.xposed.hooks.font.SystemFontSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_MEDIA_PROVIDER) loadSpec("io.github.ra1nify.realos.xposed.hooks.mediaprovider.MediaProviderSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_CAMERA) loadSpec("io.github.ra1nify.realos.xposed.hooks.camera.OplusCameraSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_LAUNCHER) loadSpec("io.github.ra1nify.realos.xposed.hooks.launcher.LauncherSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_WALLET) loadSpec("io.github.ra1nify.realos.xposed.hooks.wallet.WalletSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_FIDO) loadSpec("io.github.ra1nify.realos.xposed.hooks.fido.FidoScanRedirectSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_INTENTROUTE) loadSpec("io.github.ra1nify.realos.xposed.hooks.intentroute.IntentRouteAlignmentSpec")?.let { add(it) }
        if (BuildConfig.SCOPE_CLOCKFONT) loadSpec("io.github.ra1nify.realos.xposed.hooks.clockfont.ClockFontSpec")?.let { add(it) }
    }

    private val specsByPackage: Map<String, List<SpecLoader>> by lazy {
        val map = HashMap<String, MutableList<SpecLoader>>()
        for (spec in specs) {
            for (pkg in spec.packageNames) {
                map.getOrPut(pkg) { mutableListOf() }.add(spec)
            }
        }
        map
    }

    /** Resolve all specs for a package name, or empty list if the package is not covered. */
    fun findSpecs(packageName: String): List<SpecLoader> {
        return specsByPackage[packageName] ?: emptyList()
    }

    /** Resolve primary spec for a package name, or null if the package is not covered. */
    fun findSpec(packageName: String): SpecLoader? = findSpecs(packageName).firstOrNull()
}
