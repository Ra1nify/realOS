package io.github.ra1nify.realos.xposed.loader

import io.github.ra1nify.realos.BuildConfig

/**
 * Package names shared by BOTH the hook side (spec matching) and the realOS UI
 * (spec section rendering). Single source of truth — when an app ships multiple
 * package variants, add them here once instead of duplicating in each spec/UI.
 */
object SpecPackages {
    const val OPLUS_KEYBOARD = "com.oplus.keyboard"
    const val REALME_KEYBOARD = "com.realme.keyboard"

    /**
     * Full builds hook the ColorOS keyboard. The lite build is embedded into the
     * Realme sibling by LSPatch: its application package is com.realme.keyboard
     * while the internal class namespace is still com.oplus.keyboard.
     *
     * Keeping the mapping here makes every package check, the UI entry and the
     * restart policy use the same host package without lite-specific branches.
     */
    val OPLUS_KEYBOARD_VARIANTS: Set<String> =
        if (BuildConfig.MODE_LITE && BuildConfig.LITE_TARGET == "opluskeyboard") {
            setOf(REALME_KEYBOARD)
        } else {
            setOf(OPLUS_KEYBOARD)
        }

    /** Single keyboard host package for the current build. */
    val KEYBOARD_HOST: String = OPLUS_KEYBOARD_VARIANTS.first()

    const val ANDROID = "android"
    const val SYSTEM = "system"
    val ANDROID_VARIANTS: Set<String> = setOf(ANDROID, SYSTEM)

    const val SYSTEMUI = "com.android.systemui"
    val SYSTEMUI_VARIANTS: Set<String> = setOf(SYSTEMUI)

    const val SETTINGS = "com.android.settings"
    val SETTINGS_VARIANTS: Set<String> = setOf(SETTINGS)

    const val MEDIA_PROVIDER = "com.android.providers.media.module"
    val MEDIA_PROVIDER_VARIANTS: Set<String> = setOf(MEDIA_PROVIDER)

    const val OPLUS_CAMERA = "com.oplus.camera"
    val OPLUS_CAMERA_VARIANTS: Set<String> = setOf(OPLUS_CAMERA)

    const val LAUNCHER = "com.android.launcher"
    val LAUNCHER_VARIANTS: Set<String> = setOf(LAUNCHER)

    // ColorOS Wallet uses this package name only. com.nearme.wallet.* are internal class
    // packages (legacy OPLUS namespace); com.coloros.wallet has no device evidence.
    const val WALLET = "com.finshell.wallet"
    val WALLET_VARIANTS: Set<String> = setOf(WALLET)

    const val WALLPAPERS = "com.oplus.wallpapers"
    val WALLPAPERS_VARIANTS: Set<String> = setOf(WALLPAPERS)

    const val AOD = "com.oplus.aod"

    /**
     * Processes that load the clock plugin code. The plugin APKs
     * (com.oplus.keyguard.clock.base / com.oplus.keyguard.personality.clocks)
     * are loaded into these host processes via createPackageContext; they are
     * not separate injection targets.
     */
    val CLOCK_FONT_HOSTS: Set<String> = setOf(
        WALLPAPERS,
        SYSTEMUI,
        LAUNCHER,
        AOD
    )

    // Hosts whose hardcoded link targets are normalized by the system_server
    // intent route alignment hook.
    const val OCR_SCANNER = "com.coloros.ocrscanner"
    const val FLOAT_ASSISTANT = "com.coloros.floatassistant"
    val INTENT_ROUTE_HOSTS: Set<String> = setOf(OCR_SCANNER, FLOAT_ASSISTANT)
}
