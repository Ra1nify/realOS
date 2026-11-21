package io.github.ra1nify.realos.xposed.hooks.featureswitch

import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.featureswitch.FeatureSwitchConfig

/**
 * Feature switch hook manager for Settings (com.android.settings)
 * and Wallpapers & Personalization (com.oplus.wallpapers).
 *
 * Implements fail-closed error isolation: each method hook is resolved
 * and registered independently without breaking other features.
 */
object FeatureSwitchHooks {

    private const val TAG = "RealOS-FeatureSwitchHooks"

    private const val CLS_SYS_FEATURE_UTILS = "com.oplus.settings.utils.SysFeatureUtils"
    private const val CLS_FEATURE_UTILS = "com.oplus.settings.utils.FeatureUtils"
    private const val CLS_CUSTOMIZE_FEATURE_UTILS = "com.oplus.settings.utils.CustomizeFeatureUtils"
    private const val CLS_DEVICE_INFO_UTILS = "com.oplus.wallpapers.rely.utils.DeviceInfoUtilsKt"

    fun reset() {
        // Hot-reload reset if needed
    }

    fun installSettingsHooks(module: XposedModule, classLoader: ClassLoader) {
        module.log(Log.INFO, TAG, "Installing settings feature hooks...")

        // ── 1. SysFeatureUtils ──
        runCatching {
            val clazz = Class.forName(CLS_SYS_FEATURE_UTILS, false, classLoader)
            hookBooleanMethod(module, clazz, "isSupportGTMode", true, FeatureSwitchConfig.KEY_GT_MODE)
            hookBooleanMethod(module, clazz, "isAppFrozenSupport", true, FeatureSwitchConfig.KEY_APP_FROZEN)
            hookBooleanMethod(module, clazz, "isSupportPureMode", true, FeatureSwitchConfig.KEY_PURE_MODE)
            hookBooleanMethod(module, clazz, "supportVideoColorEnhance", true, FeatureSwitchConfig.KEY_VIDEO_COLOR_ENHANCE)
            hookBooleanMethod(module, clazz, "supportVideoOsie", true, FeatureSwitchConfig.KEY_VIDEO_OSIE)
            hookBooleanMethod(module, clazz, "supportVideoSuperResolution", true, FeatureSwitchConfig.KEY_VIDEO_SR)
            hookBooleanMethod(module, clazz, "isEnableHighRefreshRate", true, FeatureSwitchConfig.KEY_APP_HIGH_REFRESH)
            hookBooleanMethod(module, clazz, "isDualheadphone", true, FeatureSwitchConfig.KEY_DUAL_HEADPHONE)
            hookBooleanMethod(module, clazz, "isSupportMultiSoundsPlay", true, FeatureSwitchConfig.KEY_MULTI_SOUNDS_PLAY)
            hookBooleanMethod(module, clazz, "isSupportSuperVolume2X", true, FeatureSwitchConfig.KEY_SUPER_VOLUME)
            hookBooleanMethod(module, clazz, "isSupportSuperVolume3X", true, FeatureSwitchConfig.KEY_SUPER_VOLUME)
            hookBooleanMethod(module, clazz, "hasSupportKaraokeFeature", true, FeatureSwitchConfig.KEY_KARAOKE)
            hookBooleanMethod(module, clazz, "isCustomHideAppSupport", true, FeatureSwitchConfig.KEY_CUSTOM_HIDE_APP)
        }.onFailure {
            module.log(Log.WARN, TAG, "SysFeatureUtils not found or failed to hook", it)
        }

        // ── 2. FeatureUtils ──
        runCatching {
            val clazz = Class.forName(CLS_FEATURE_UTILS, false, classLoader)
            hookBooleanMethod(module, clazz, "isSupportFluidCloud", true, FeatureSwitchConfig.KEY_FLUID_CLOUD)
            hookBooleanMethod(module, clazz, "isMiniCapsuleSupport", true, FeatureSwitchConfig.KEY_MINI_CAPSULE)
            hookBooleanMethod(module, clazz, "isTaskbarSupport", true, FeatureSwitchConfig.KEY_TASKBAR)
            hookBooleanMethod(module, clazz, "isPwmRebootCloseSupport", true, FeatureSwitchConfig.KEY_PWM_REBOOT_CLOSE)
            hookBooleanMethod(module, clazz, "isComfortableColorTemperatureSupport", true, FeatureSwitchConfig.KEY_COMFORT_COLOR_TEMP)
            hookBooleanMethod(module, clazz, "isSinglePulseRefreshrateSupport", false, FeatureSwitchConfig.KEY_SINGLE_PULSE_REFRESH)
            hookBooleanMethod(module, clazz, "isSupportDolby", true, FeatureSwitchConfig.KEY_DOLBY)
            hookBooleanMethod(module, clazz, "isDiracSupported", true, FeatureSwitchConfig.KEY_DIRAC)
            hookBooleanMethod(module, clazz, "isSupportDcMin2Nit", true, FeatureSwitchConfig.KEY_DC_MIN_2NIT)
        }.onFailure {
            module.log(Log.WARN, TAG, "FeatureUtils not found or failed to hook", it)
        }

        // ── 3. CustomizeFeatureUtils ──
        runCatching {
            val clazz = Class.forName(CLS_CUSTOMIZE_FEATURE_UTILS, false, classLoader)
            hookBooleanMethod(module, clazz, "isDisableOemUnlock", false, FeatureSwitchConfig.KEY_DISABLE_OEM_UNLOCK_BYPASS)
            hookAppDisallowDisable(module, clazz, FeatureSwitchConfig.KEY_APP_DISALLOW_DISABLE_BYPASS)
            hookBooleanMethod(module, clazz, "isClearCacheDisabled", false, FeatureSwitchConfig.KEY_CLEAR_CACHE_DISABLED_BYPASS)
            hookBooleanMethod(module, clazz, "isVolteSettingUnavailable", false, FeatureSwitchConfig.KEY_VOLTE_UNAVAILABLE_BYPASS)
            hookBooleanMethod(module, clazz, "isVoWifiSettingUnavailable", false, FeatureSwitchConfig.KEY_VOWIFI_UNAVAILABLE_BYPASS)
            hookBooleanMethod(module, clazz, "isSupportVideoWallpaper", true, FeatureSwitchConfig.KEY_VIDEO_WALLPAPER)
            hookBooleanMethod(module, clazz, "isDisallowSwitchToPreviousApp", false, FeatureSwitchConfig.KEY_DISALLOW_SWITCH_PREV_APP_BYPASS)
            hookBooleanMethod(module, clazz, "isOHCommonDevice", true, FeatureSwitchConfig.KEY_ONEPLUS_BRAND)
            hookBooleanMethod(module, clazz, "isOHDomestic", true, FeatureSwitchConfig.KEY_ONEPLUS_BRAND)
        }.onFailure {
            module.log(Log.WARN, TAG, "CustomizeFeatureUtils not found or failed to hook", it)
        }
    }

    fun installWallpapersHooks(module: XposedModule, classLoader: ClassLoader) {
        module.log(Log.INFO, TAG, "Installing wallpapers feature hooks...")

        runCatching {
            val clazz = Class.forName(CLS_DEVICE_INFO_UTILS, false, classLoader)
            // DeviceInfoUtilsKt.R() controls OnePlus brand check in Wallpapers.apk
            val methodR = clazz.getDeclaredMethod("R")
            module.hook(methodR).intercept { chain ->
                if (FeatureSwitchConfig.isHookEnabled(FeatureSwitchConfig.KEY_ONEPLUS_RED_ONE) ||
                    FeatureSwitchConfig.isHookEnabled(FeatureSwitchConfig.KEY_ONEPLUS_BRAND)
                ) {
                    true
                } else {
                    chain.proceed()
                }
            }
            module.log(Log.INFO, TAG, "HookPoint[feat_oneplus_red_one -> DeviceInfoUtilsKt.R] state=FOUND")
        }.onFailure {
            module.log(Log.WARN, TAG, "DeviceInfoUtilsKt.R not found in wallpapers", it)
        }
    }

    private fun hookBooleanMethod(
        module: XposedModule,
        clazz: Class<*>,
        methodName: String,
        forcedValue: Boolean,
        configKey: String
    ) {
        runCatching {
            val method = clazz.getDeclaredMethod(methodName)
            module.hook(method).intercept { chain ->
                if (FeatureSwitchConfig.isHookEnabled(configKey)) {
                    forcedValue
                } else {
                    chain.proceed()
                }
            }
            module.log(Log.INFO, TAG, "HookPoint[$configKey -> ${clazz.simpleName}.$methodName] state=FOUND")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[$configKey -> ${clazz.simpleName}.$methodName] state=MISSING: ${it.message}")
        }
    }

    private fun hookAppDisallowDisable(
        module: XposedModule,
        clazz: Class<*>,
        configKey: String
    ) {
        runCatching {
            val method = clazz.getDeclaredMethod("isAppDisallowDisable", String::class.java)
            module.hook(method).intercept { chain ->
                if (FeatureSwitchConfig.isHookEnabled(configKey)) {
                    false
                } else {
                    chain.proceed()
                }
            }
            module.log(Log.INFO, TAG, "HookPoint[$configKey -> ${clazz.simpleName}.isAppDisallowDisable] state=FOUND")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[$configKey -> ${clazz.simpleName}.isAppDisallowDisable] state=MISSING: ${it.message}")
        }
    }
}
