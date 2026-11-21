package io.github.ra1nify.realos.opluskeyboard

import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.opluskeyboard.OplusInputConfig

/**
 * Configuration reader for OPPO Keyboard (Xiaobu Input Method) hook.
 *
 * Reads preferences written by realOS UI from remote preferences.
 */
object OplusInputConfig {

    private const val PREFS_NAME = "app_config"

    private fun prefKey(key: String, pkg: String) = "${key}_$pkg"

    /**
     * Reads the install-time switches in one IPC call.
     *
     * `getRemotePreferences` is a cross-process handle; reading keys one at a time
     * rebuilds it per key. This reads 3~4 keys through a single handle.
     */
    data class Switches(
        val liquidGlass: Boolean,
        val keyboardEffects: Boolean,
        val enhancements: Boolean,
        val keyboardSounds: Boolean
    )

    fun snapshot(module: XposedModule, packageName: String): Switches = try {
        val prefs = module.getRemotePreferences(PREFS_NAME)
        val liquidKey = prefKey("oplus_input_liquid_glass", packageName)
        Switches(
            liquidGlass = if (prefs.contains(liquidKey)) {
                prefs.getBoolean(liquidKey, true)
            } else {
                prefs.getBoolean(prefKey("oplus_input_workshop", packageName), true)
            },
            keyboardEffects = prefs.getBoolean(
                prefKey("oplus_input_keyboard_effects", packageName), true
            ),
            enhancements = prefs.getBoolean(prefKey("oplus_input_enhancements", packageName), true),
            keyboardSounds = prefs.getBoolean(
                prefKey("oplus_input_keyboard_sounds", packageName), true
            )
        )
    } catch (_: Throwable) {
        // Keeps the per-getter legacy defaults (all true) when the remote read fails
        Switches(liquidGlass = true, keyboardEffects = true, enhancements = true, keyboardSounds = true)
    }

    fun isLiquidGlassEnabled(module: XposedModule, packageName: String): Boolean {
        return try {
            val prefs = module.getRemotePreferences(PREFS_NAME)
            if (prefs.contains(prefKey("oplus_input_liquid_glass", packageName))) {
                prefs.getBoolean(prefKey("oplus_input_liquid_glass", packageName), true)
            } else {
                prefs.getBoolean(prefKey("oplus_input_workshop", packageName), true)
            }
        } catch (_: Throwable) {
            true
        }
    }

    fun isKeyboardEffectsEnabled(module: XposedModule, packageName: String): Boolean {
        return try {
            val prefs = module.getRemotePreferences(PREFS_NAME)
            prefs.getBoolean(prefKey("oplus_input_keyboard_effects", packageName), true)
        } catch (_: Throwable) {
            true
        }
    }

    fun isWorkshopEnabled(module: XposedModule, packageName: String): Boolean {
        return isLiquidGlassEnabled(module, packageName)
    }

    fun isEnhancementsEnabled(module: XposedModule, packageName: String): Boolean {
        return try {
            val prefs = module.getRemotePreferences(PREFS_NAME)
            prefs.getBoolean(prefKey("oplus_input_enhancements", packageName), true)
        } catch (_: Throwable) {
            true
        }
    }

    fun isKeyboardSoundEnabled(module: XposedModule, packageName: String): Boolean {
        return try {
            val prefs = module.getRemotePreferences(PREFS_NAME)
            prefs.getBoolean(prefKey("oplus_input_keyboard_sounds", packageName), true)
        } catch (_: Throwable) {
            true
        }
    }

    const val KEY_SYSTEM_OPLUS_INLINE_AUTOFILL = "system_oplus_inline_autofill"

    /**
     * Dedicated switch for hooking InputMethodInfo.isInlineSuggestionsEnabled in system_server.
     * Controlled explicitly by user in realOS System Framework settings, rather than silent hooking.
     */
    fun isSystemOplusInlineAutofillEnabled(module: XposedModule): Boolean {
        return try {
            val prefs = module.getRemotePreferences(PREFS_NAME)
            prefs.getBoolean(KEY_SYSTEM_OPLUS_INLINE_AUTOFILL, true)
        } catch (_: Throwable) {
            true
        }
    }
}
