package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Application
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.OplusInputEffectsHook
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusInputConfig

/**
 * Hook implementation for OPPO Keyboard (Xiaobu Input Method).
 *
 * Enforces fail-closed error isolation and complete short-circuit when disabled.
 */
object OplusInputHook {

    private const val TAG = "OplusInputHook"
    const val ACTION_KEYBOARD_CONFIG_CHANGED = "io.github.ra1nify.realos.ACTION_KEYBOARD_CONFIG_CHANGED"

    private var isReceiverRegistered = false

    /** Registered receiver and its host Application, unregistered on reset. */
    private var configReceiver: android.content.BroadcastReceiver? = null
    private var receiverApp: java.lang.ref.WeakReference<Application>? = null

    private fun registerConfigReceiver(app: Application) {
        if (isReceiverRegistered) return
        val filter = android.content.IntentFilter(ACTION_KEYBOARD_CONFIG_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                Log.i(TAG, "Received ACTION_KEYBOARD_CONFIG_CHANGED, live reloading keyboard state...")
                runCatching {
                    val targetApp = (context?.applicationContext as? Application) ?: app
                    OplusLiquidGlassHook.recoverActiveViewsAndDelegates(targetApp)
                    OplusLiquidGlassHook.triggerLiveRefresh(targetApp)
                    // Key backgrounds are cached in a bitmap; the broadcast path must refresh
                    // them too, otherwise a config change only shows after an IME restart.
                    OplusLiquidGlassHook.updateLiveKeyAlpha(OplusInputPrefs.getKeyAlpha(targetApp))
                }.onFailure {
                    Log.e(TAG, "Error handling keyboard config changed broadcast", it)
                }
            }
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // RECEIVER_EXPORTED: the realOS app sends this broadcast through `am broadcast`
                // under a different uid than the IME process.
                app.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
            configReceiver = receiver
            receiverApp = java.lang.ref.WeakReference(app)
            isReceiverRegistered = true
            Log.i(TAG, "Registered ACTION_KEYBOARD_CONFIG_CHANGED receiver on host application")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register config receiver", t)
        }
    }

    /**
     * Unregisters the config receiver and clears the registration flag on hot reload or disable.
     *
     * Resets `configReceiver`, `receiverApp` and `isReceiverRegistered`.
     */
    fun reset() {
        val receiver = configReceiver
        val app = receiverApp?.get()
        if (receiver != null && app != null) {
            runCatching { app.unregisterReceiver(receiver) }
                .onFailure { Log.w(TAG, "Failed to unregister config receiver", it) }
        }
        configReceiver = null
        receiverApp = null
        isReceiverRegistered = false
    }

    fun install(module: XposedModule, app: Application) {
        registerConfigReceiver(app)
        val packageName = app.packageName
        // Reads every install-time switch in one IPC call.
        val switches = OplusInputConfig.snapshot(module, packageName)
        val liquidGlassEnabled = switches.liquidGlass
        val keyboardEffectsEnabled = switches.keyboardEffects
        val enhancementsEnabled = switches.enhancements

        // Merged "bypass all limits" switch, default off, gating the whitelist and dark-mode bypasses.
        val bypassAllLimits = runCatching { OplusInputPrefs.isBypassAllLimits(app) }.getOrDefault(false)

        if (!liquidGlassEnabled && !keyboardEffectsEnabled && !enhancementsEnabled) {
            Log.i(TAG, "All OplusInput features disabled for $packageName; short-circuiting.")
            return
        }

        val symbols = OplusInputSymbolResolver.resolve(module, app)

        // ── 0. Install Settings Category & Host Hook ──
        try {
            OplusInputSettingsHook.install(module, app.classLoader)
        } catch (t: Throwable) {
            Log.e(TAG, "Error installing OplusInputSettingsHook", t)
        }

        // ── 1. Install Inline Autofill & Toolbar Button ──
        if (enhancementsEnabled) {
            try {
                OplusInputAutofillHook.install(module, app, symbols)
            } catch (t: Throwable) {
                Log.e(TAG, "Error installing OplusInputAutofillHook", t)
            }
        }

        if (!liquidGlassEnabled && !keyboardEffectsEnabled) {
            return
        }

        // ── 2. Install Liquid Glass (Universal Whitelist + Blur Customization) ──
        if (liquidGlassEnabled) {
            // ── 2a. Universal Whitelist bypass (hot-reloadable) ──
            // initialize() always captures the host's original whitelist collection and field
            // reference, then applies the current switch value once: on → Universal container,
            // off → native collection. Capture and switch state stay independent.
            if (!symbols.isReadyForLiquidGlass()) {
                Log.w(
                    TAG,
                    "Whitelist bypass unavailable: whitelist symbol state is ${symbols.statusMap["whitelist"]}. " +
                        "Failing closed to prevent crashes."
                )
            } else {
                try {
                    val ok = OplusInputWhitelistBypass.initialize(app, symbols)
                    if (!ok) {
                        Log.w(TAG, "Whitlist bypass initialize returned false; live toggle unavailable.")
                    } else {
                        Log.i(TAG, "Universal whitelist bypass initialized (bypassAllLimits=$bypassAllLimits).")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error initializing universal whitelist bypass", t)
                }
            }

            // Install material blur parameter injections (Alpha, Tint, Radius, DayNight bypass).
            // Installed whenever liquid glass is enabled, independent of the whitelist bypass.
            try {
                OplusLiquidGlassHook.install(module, app, symbols)
            } catch (t: Throwable) {
                Log.e(TAG, "Error installing OplusLiquidGlassHook", t)
            }
        }

        // ── 3. Install Fold Check Bypass ──
        if (liquidGlassEnabled) {
            if (!symbols.isReadyForFoldBypass()) {
                Log.w(
                    TAG,
                    "Fold bypass requested, but fold check symbol state is ${symbols.statusMap["fold_check"]}. " +
                        "Failing closed."
                )
            } else {
                try {
                    val method = symbols.foldCheckMethod!!.restore(app.classLoader)
                    if (method == null) {
                        Log.w(TAG, "Failed to restore foldCheckMethod reflection target; fail closed.")
                    } else {
                        module.hook(method).intercept { false }
                        Log.i(
                            TAG,
                            "Fold check bypass hook installed on " +
                                "${symbols.foldCheckMethod.methodName}"
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error installing fold check bypass hook", t)
                }
            }
        }

        // ── 4. Install Samsung-style key press flash MVP ──
        if (keyboardEffectsEnabled) {
            if (!symbols.isReadyForEffects()) {
                Log.w(
                    TAG,
                    "Effects requested, but touch/draw symbol state is " +
                        "${symbols.statusMap["effects_touch"]}/${symbols.statusMap["effects_draw"]}; failing closed."
                )
            } else {
                try {
                    OplusInputEffectsHook.install(module, app, symbols)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error installing OplusInputEffectsHook", t)
                }
            }
        }
    }
}
