package io.github.ra1nify.realos.xposed.hooks.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.launcher.LauncherConfig
import java.lang.ref.WeakReference

/**
 * Draws the docker background layer behind the hotseat icons on every device.
 *
 * The host only calls `OplusHotseat#setDockerBackground()` when `zn9.v()` allows it (tablet or
 * fold). The hook calls that method directly after `onMeasure` so phones get the rounded layer.
 * Config changes are delivered through a remote-preference listener and kept in memory, so hook
 * callbacks never touch SharedPreferences or IPC.
 */
object LauncherDockerBgHook {

    internal const val TAG = "RealOS-LauncherDockerBg"

    /** Stock radius factor, used only when the host field cannot be read. */
    private const val FALLBACK_RADIUS_FACTOR = 0.26f

    @Volatile
    private var installed = false

    @Volatile
    private var radiusMultiplier = LauncherConfig.DEFAULT_DOCKER_BG_RADIUS

    @Volatile
    private var blurBackendId = LauncherConfig.DEFAULT_DOCKER_BG_BLUR_BACKEND

    @Volatile
    private var blendMode = LauncherConfig.DEFAULT_DOCKER_BG_BLEND_MODE

    // ARGB ints for setBlurParams(mode, blend, mix), selected by the current uiMode at hook time.
    // Null means the stored value did not parse; the stock colours are kept instead of overriding
    // with 0, which a fully transparent colour would otherwise look like.
    @Volatile
    private var lightBlend: Int? = null
    @Volatile
    private var lightMix: Int? = null
    @Volatile
    private var darkBlend: Int? = null
    @Volatile
    private var darkMix: Int? = null

    private var hotseatRef: WeakReference<Any>? = null
    private var remoteListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var mainHandler: Handler? = null

    fun install(module: XposedModule, handles: LauncherHandles) {
        if (installed) {
            module.log(Log.INFO, TAG, "already installed, skip")
            return
        }
        val gates = handles.dockerBg ?: run {
            module.log(Log.WARN, TAG, "fail closed: hotseat background symbols were not resolved")
            return
        }
        val onMeasure = gates.onMeasureMethod ?: run {
            module.log(Log.WARN, TAG, "fail closed: OplusHotseat#onMeasure not resolved")
            return
        }
        val setBackground = gates.setDockerBackgroundMethod ?: run {
            module.log(Log.WARN, TAG, "fail closed: OplusHotseat#setDockerBackground not resolved")
            return
        }
        val bgDrawableField = gates.bgDrawableField ?: run {
            module.log(Log.WARN, TAG, "fail closed: mDockerBackgroundDrawable not resolved")
            return
        }
        installed = true
        mainHandler = Handler(Looper.getMainLooper())

        refreshSnapshot()
        module.log(
            Log.INFO, TAG,
            "Installing hotseat background (backend=$blurBackendId, radius=${radiusMultiplier}x)"
        )

        registerRemoteListener(module)
        installNormalizerHook(module, gates.normalizerMethod)
        installMaskColorHook(module, gates)

        module.hook(onMeasure).intercept { chain ->
            val result = chain.proceed()
            val hotseat = chain.thisObject ?: return@intercept result
            hotseatRef = WeakReference(hotseat)
            val bg = runCatching { bgDrawableField.get(hotseat) }.getOrNull()
            if (reloadRequested || bg == null) {
                rebuild(gates, hotseat, bgDrawableField)
            }
            result
        }
    }

    fun reset() {
        installed = false
        radiusMultiplier = LauncherConfig.DEFAULT_DOCKER_BG_RADIUS
        blurBackendId = LauncherConfig.DEFAULT_DOCKER_BG_BLUR_BACKEND
        blendMode = LauncherConfig.DEFAULT_DOCKER_BG_BLEND_MODE
        lightBlend = null
        lightMix = null
        darkBlend = null
        darkMix = null
        hotseatRef = null
        remoteListener = null
        mainHandler = null
    }

    private fun refreshSnapshot() {
        radiusMultiplier = LauncherConfig.isDockerBgRadius()
        blurBackendId = LauncherConfig.dockerBgBlurBackend()
        blendMode = LauncherConfig.dockerBgBlendMode()
        // Each mode owns its own slots: a slot means something different per layer stack, so the
        // values are read from the set that belongs to the active mode.
        lightBlend = readSlot(light = true, slotB = false)
        lightMix = readSlot(light = true, slotB = true)
        darkBlend = readSlot(light = false, slotB = false)
        darkMix = readSlot(light = false, slotB = true)
    }

    /** Reads one mode-scoped slot, capping dodge channels because that layer's gain diverges. */
    private fun readSlot(light: Boolean, slotB: Boolean): Int? {
        val mode = blendMode
        val argb = parseArgb(LauncherConfig.dockerBgMask(mode, light, slotB)) ?: return null
        val dodge = mode == LauncherConfig.BLEND_MODE_STOCK && slotB
        return LauncherConfig.clampArgb(argb, dodge)
    }

    /** Parses a stored colour; null lets the host keep its own colour instead of overriding with 0. */
    private fun parseArgb(hex: String): Int? = runCatching {
        android.graphics.Color.parseColor(hex)
    }.getOrNull()

    private fun registerRemoteListener(module: XposedModule) {
        val prefs = runCatching {
            module.getRemotePreferences(LauncherConfig.MODULE_PREFS_NAME)
        }.getOrNull() ?: run {
            Log.w(TAG, "Remote preferences unavailable; config changes need a launcher restart")
            return
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key !in WATCHED_KEYS) return@OnSharedPreferenceChangeListener
            refreshSnapshot()
            mainHandler?.post {
                hotseatRef?.get()?.let { hotseat ->
                    reloadRequested = true
                    requestLayout(hotseat)
                }
            }
        }
        remoteListener = listener
        runCatching { prefs.registerOnSharedPreferenceChangeListener(listener) }
            .onFailure { Log.w(TAG, "Failed to register remote config listener", it) }
    }

    /**
     * Reapplies the configured blend/mix colours after the host's updateBlurParams().
     *
     * The host calls setBlurParams via `invoke-virtual IBlurBaseController;->setBlurParams`,
     * so hooking the concrete implementations does not intercept it. Instead we hook the
     * caller and invoke setBlurParams on mBlurProp directly, the same way radius is applied.
     * The mode and both colours come from config, selected by uiMode.
     */
    private fun installMaskColorHook(module: XposedModule, gates: DockerBgHandles) {
        val update = gates.updateBlurParamsMethod
        val propField = gates.blurPropField
        val propsMethod = gates.setBlurParamsPropsMethod
        val renderMethod = gates.setBlurParamsRenderMethod
        if (update == null || propField == null || (propsMethod == null && renderMethod == null)) {
            Log.w(TAG, "fail closed: updateBlurParams / mBlurProp / setBlurParams not resolved")
            return
        }
        module.hook(update).intercept { chain ->
            val result = chain.proceed()
            val hotseat = chain.thisObject ?: return@intercept result
            val controller = runCatching { propField.get(hotseat) }.getOrNull() ?: return@intercept result
            val uiMode = runCatching {
                val ctx = hotseat.javaClass.getMethod("getContext").invoke(hotseat) as? Context
                ctx?.resources?.configuration?.uiMode ?: 0
            }.getOrDefault(0)
            val night = uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val blend = if (night) darkBlend else lightBlend
            val mix = if (night) darkMix else lightMix
            val method = when {
                propsMethod?.declaringClass?.isInstance(controller) == true -> propsMethod
                renderMethod?.declaringClass?.isInstance(controller) == true -> renderMethod
                else -> null
            }
            if (method != null && blend != null && mix != null) {
                // RenderEffectManager reinterprets the first argument as its own backdrop mode,
                // so the configured mode is only applied on the verified ColorOS path.
                val mode = if (method === renderMethod) {
                    LauncherConfig.BLEND_MODE_STOCK
                } else {
                    blendMode
                }
                runCatching { method.invoke(controller, mode, blend, mix) }
                    .onFailure { Log.w(TAG, "setBlurParams override failed", it) }
            }
            result
        }
        Log.i(TAG, "Installed dock mask colour hook on updateBlurParams")
    }

    /** Scales the stock rounded drawable by the tuned multiplier. */
    private fun installNormalizerHook(module: XposedModule, normalizer: java.lang.reflect.Method?) {
        if (normalizer == null) {
            Log.w(TAG, "Normalizer not resolved; plain background keeps the stock radius")
            return
        }
        module.hook(normalizer).intercept { chain ->
            val result = chain.proceed()
            val baseRadius = chain.args.getOrNull(1) as? Float
            if (result is GradientDrawable && baseRadius != null && baseRadius > 0f) {
                runCatching { result.cornerRadius = baseRadius * radiusMultiplier }
            }
            result
        }
    }

    /** Rebuilds the background and reapplies the current blur backend. */
    private fun rebuild(
        gates: DockerBgHandles,
        hotseat: Any,
        bgDrawableField: java.lang.reflect.Field
    ) {
        runCatching { bgDrawableField.set(hotseat, null) }
        runCatching { gates.setDockerBackgroundMethod?.invoke(hotseat) }
            .onFailure { Log.w(TAG, "setDockerBackground failed", it) }
        if (blurBackendId != DockerBlurBackends.NONE) {
            applyBlur(gates, hotseat, DockerBlurBackends.resolve(blurBackendId))
        }
        reloadRequested = false
    }

    private fun applyBlur(gates: DockerBgHandles, hotseat: Any, backend: DockerBlurBackend) {
        val factor = runCatching {
            gates.radiusFactorField?.get(null) as? Float
        }.getOrNull() ?: FALLBACK_RADIUS_FACTOR
        val height = runCatching {
            hotseat.javaClass.getMethod("getMeasuredHeight").invoke(hotseat) as? Int
        }.getOrNull() ?: 0
        if (height <= 0) return
        backend.applyBlur(gates, hotseat, height * factor * radiusMultiplier)
    }

    private fun requestLayout(hotseat: Any) {
        runCatching {
            hotseat.javaClass.getMethod("requestLayout").invoke(hotseat)
        }.onFailure { Log.w(TAG, "requestLayout failed", it) }
    }

    /**
     * Keys whose change requires a reload.
     *
     * Built once: the mode-scoped mask keys are enumerated from the supported mode list, and the
     * legacy keys stay watched so an install that still writes them also reloads.
     */
    private val WATCHED_KEYS: Set<String> = buildSet {
        add(LauncherConfig.KEY_DOCKER_BG_ENABLED)
        add(LauncherConfig.KEY_DOCKER_BG_BLUR_ENABLED)
        add(LauncherConfig.KEY_DOCKER_BG_RADIUS)
        add(LauncherConfig.KEY_DOCKER_BG_REV)
        add(LauncherConfig.KEY_DOCKER_BG_BLUR_BACKEND)
        add(LauncherConfig.KEY_DOCKER_BG_BLEND_MODE)
        add(LauncherConfig.KEY_DOCKER_BG_LIGHT_BLEND)
        add(LauncherConfig.KEY_DOCKER_BG_LIGHT_MIX)
        add(LauncherConfig.KEY_DOCKER_BG_DARK_BLEND)
        add(LauncherConfig.KEY_DOCKER_BG_DARK_MIX)
        for (mode in LauncherConfig.BLEND_MODES) {
            for (light in booleanArrayOf(true, false)) {
                for (slotB in booleanArrayOf(false, true)) {
                    add(LauncherConfig.maskKey(mode, light, slotB))
                }
            }
        }
    }

    @Volatile
    private var reloadRequested = false
}
