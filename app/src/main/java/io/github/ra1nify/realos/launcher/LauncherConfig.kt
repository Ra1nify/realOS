package io.github.ra1nify.realos.launcher

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.github.ra1nify.realos.BuildConfig
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.launcher.LauncherConfig
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * Configuration for the ColorOS launcher (com.android.launcher).
 *
 * Two configuration sources:
 * - Feature install gates (memory / recents extra styles / color filter): written by
 *   the realOS app, pushed to `launcher_config` remote preferences, and read once by
 *   `LauncherSpec` when the launcher process starts.
 * - Real memory switch: written by the switch injected into the host settings page,
 *   stored in the host's `realos_launcher_prefs`, read first by the host.
 */
object LauncherConfig {

    private const val TAG = "RealOS-LauncherConfig"

    /** Module preferences file name, also the remote preferences group name */
    const val MODULE_PREFS_NAME = "launcher_config"

    /** Preferences file name written inside the host process */
    private const val HOST_PREFS_NAME = "realos_launcher_prefs"

    /** Real memory info (RAM + zRAM + Swap) replacing the official text */
    const val KEY_REAL_MEMORY = "launcher_real_memory_enabled"

    /** Feature install gates. Off means the launcher spec installs no hooks for that feature. */
    const val KEY_MEMORY_HOOKS_ENABLED = "launcher_memory_hooks_enabled"
    const val KEY_RECENTS_EXTRA_STYLES_ENABLED = "launcher_recents_extra_styles_enabled"
    const val KEY_COLOR_FILTER_ENABLED = "launcher_color_filter_enabled"

    /** Master switch: draw the docker background behind the hotseat icons. */
    const val KEY_DOCKER_BG_ENABLED = "launcher_docker_bg_enabled"
    /** Sub switch: use the material blur variant; only effective when the master is on. */
    const val KEY_DOCKER_BG_BLUR_ENABLED = "launcher_docker_bg_blur_enabled"
    /** Radius multiplier of the host dock background, 1f keeps the stock radius. */
    const val KEY_DOCKER_BG_RADIUS = "launcher_docker_bg_radius"
    /** Bumped on every docker background write so the host can reload without a restart. */
    const val KEY_DOCKER_BG_REV = "launcher_docker_bg_rev"
    /** Selects the blur provider; kept as config so a floating-navigation backend can be added later. */
    const val KEY_DOCKER_BG_BLUR_BACKEND = "launcher_docker_bg_blur_backend"
    /**
     * Mask colours passed to `setBlurParams(mode, blend, mix)`.
     *
     * Each blend mode owns its own pair of slots: a slot means something different per mode
     * (MIX reads a full colour, LUMINOSITY only a luminance, OVERLAY a contrast level), so one
     * shared pair would make a value written under one mode silently change meaning under another.
     * The legacy unprefixed keys stay as the read fallback for the stock mode only.
     */
    const val KEY_DOCKER_BG_LIGHT_BLEND = "launcher_docker_bg_light_blend"
    const val KEY_DOCKER_BG_LIGHT_MIX = "launcher_docker_bg_light_mix"
    const val KEY_DOCKER_BG_DARK_BLEND = "launcher_docker_bg_dark_blend"
    const val KEY_DOCKER_BG_DARK_MIX = "launcher_docker_bg_dark_mix"

    /** Mode-scoped key for one slot: `launcher_docker_bg_m<mode>_<light|dark>_<a|b>`. */
    fun maskKey(mode: Int, light: Boolean, slotB: Boolean): String =
        "launcher_docker_bg_m${validBlendMode(mode)}_${if (light) "light" else "dark"}_" +
            if (slotB) "b" else "a"

    /** Blend mode; the host itself always passes 2, the other two are realOS additions. */
    const val KEY_DOCKER_BG_BLEND_MODE = "launcher_docker_bg_blend_mode"

    /**
     * Supported [KEY_DOCKER_BG_BLEND_MODE] values, matching the host shader layer stacks:
     * 1 = one MIX layer (colour + strength), 2 = LUMINOSITY + DODGE_BG (stock), 4 = MIX + OVERLAY.
     * Any other value reaches no shader branch and silently applies no blend at all.
     */
    const val BLEND_MODE_MIX = 1
    const val BLEND_MODE_STOCK = 2
    const val BLEND_MODE_MIX_OVERLAY = 4

    /** Every supported mode, in the order the picker lists them. */
    val BLEND_MODES = intArrayOf(BLEND_MODE_MIX, BLEND_MODE_STOCK, BLEND_MODE_MIX_OVERLAY)

    /** Largest channel a dodge colour may use: `d / (1 - s)` gains 2x at 128 and diverges above it. */
    const val DODGE_MAX_CHANNEL = 128

    const val DEFAULT_REAL_MEMORY = false
    const val DEFAULT_DOCKER_BG_ENABLED = false
    const val DEFAULT_DOCKER_BG_BLUR_ENABLED = false
    const val DEFAULT_DOCKER_BG_RADIUS = 1.0f
    const val DEFAULT_DOCKER_BG_BLEND_MODE = BLEND_MODE_MIX
    const val DEFAULT_DOCKER_BG_BLUR_BACKEND = "none"

    /**
     * Per-mode defaults, matching what each layer stack can render.
     *
     * The stock mode keeps the host's own material colour (`BlurUtils.BlurMaterialColor`:
     * blend `#66526366`, mix `#FF333333`); the other modes start neutral so switching to them
     * does not silently apply a colour that was tuned for a different formula.
     */
    const val DEFAULT_DOCKER_BG_LIGHT_BLEND = "#66526366"
    const val DEFAULT_DOCKER_BG_LIGHT_MIX = "#FE333333"
    const val DEFAULT_DOCKER_BG_DARK_BLEND = "#66526366"
    const val DEFAULT_DOCKER_BG_DARK_MIX = "#FE333333"

    /** MIX default: a transparent layer, so enabling the mode alone changes nothing. */
    const val DEFAULT_MASK_MIX = "#00000000"

    /** OVERLAY default: mid grey is the identity of the overlay formula. */
    const val DEFAULT_MASK_OVERLAY = "#FF808080"
    const val DEFAULT_MEMORY_HOOKS_ENABLED = true
    const val DEFAULT_RECENTS_EXTRA_STYLES_ENABLED = true
    const val DEFAULT_COLOR_FILTER_ENABLED = true

    /** Settings.Secure key of the official "show running memory info" switch */
    const val SECURE_MEMORY_INFO_KEY = "display_memory_information_recent_task"

    /** Provider the host uses to read zram stats (realOS side, read through root) */
    val STATS_AUTHORITY = "${BuildConfig.APPLICATION_ID}.launcher_stats"
    const val METHOD_GET_ZRAM_STATS = "get_zram_stats"

    const val KEY_STATS_ORIG = "orig_bytes"
    const val KEY_STATS_COMPR = "compr_bytes"
    const val KEY_STATS_TOTAL = "total_bytes"
    const val KEY_STATS_SWAP_USED = "swap_used_kb"
    const val KEY_STATS_AT = "stats_at"

    /** Preference fallback path: values written by warmup when the provider is unavailable */
    private const val KEY_ZRAM_ORIG = "zram_orig_bytes"
    private const val KEY_ZRAM_COMPR = "zram_compr_bytes"
    private const val KEY_ZRAM_TOTAL = "zram_total_bytes"
    private const val KEY_EXT_SWAP_USED = "external_swap_used_kb"
    private const val KEY_STATS_AT_PREF = "zram_stats_at"

    /** Validity window of the fallback preference data; older data uses the swap metric */
    private const val STATS_MAX_AGE_MS = 8000L

    /** zram stats read by realOS through root */
    data class RootZramStats(
        val zramOrigBytes: Long,
        val zramComprBytes: Long,
        val zramTotalBytes: Long,
        val externalSwapUsedKb: Long,
        val at: Long
    ) {
        val isValid: Boolean
            get() = zramOrigBytes > 0L && zramComprBytes > 0L
    }

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    private var hostPrefs: SharedPreferences? = null

    fun prime(module: XposedModule) {
        val p = runCatching { module.getRemotePreferences(MODULE_PREFS_NAME) }.getOrElse {
            module.log(Log.WARN, TAG, "getRemotePreferences($MODULE_PREFS_NAME) threw", it)
            null
        }
        remotePrefs = p
        module.log(
            Log.INFO, TAG,
            "remote preferences ${if (p == null) "UNAVAILABLE (using defaults)" else "OK"}"
        )
    }

    /** Defensive: a package-ready callback normally primes the handle; onAppStart re-checks it. */
    fun primeIfNeeded(module: XposedModule) {
        if (remotePrefs == null) prime(module)
    }

    fun initHost(context: Context) {
        if (hostPrefs != null) return
        val app = context.applicationContext ?: context
        hostPrefs = runCatching {
            app.getSharedPreferences(HOST_PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    // ── Feature install gates ──
    //
    // These are read once by LauncherSpec at launcher start. Off means the spec
    // installs no hooks for that feature; the switch itself is not a runtime
    // callback gate. The stock memory switch and real-memory switch stay in the
    // launcher settings page after the memory hooks are installed.

    /** Host-side read; defaults preserve the previous always-installed behavior. */
    fun isMemoryHooksEnabled(): Boolean =
        readRemoteBoolean(KEY_MEMORY_HOOKS_ENABLED, DEFAULT_MEMORY_HOOKS_ENABLED)

    fun isRecentsExtraStylesEnabled(): Boolean =
        readRemoteBoolean(KEY_RECENTS_EXTRA_STYLES_ENABLED, DEFAULT_RECENTS_EXTRA_STYLES_ENABLED)

    fun isColorFilterEnabled(): Boolean =
        readRemoteBoolean(KEY_COLOR_FILTER_ENABLED, DEFAULT_COLOR_FILTER_ENABLED)

    /** realOS app-side reads. */
    fun isMemoryHooksEnabledForUi(context: Context): Boolean =
        readModuleBoolean(context, KEY_MEMORY_HOOKS_ENABLED, DEFAULT_MEMORY_HOOKS_ENABLED)

    fun isRecentsExtraStylesEnabledForUi(context: Context): Boolean =
        readModuleBoolean(
            context,
            KEY_RECENTS_EXTRA_STYLES_ENABLED,
            DEFAULT_RECENTS_EXTRA_STYLES_ENABLED
        )

    fun isColorFilterEnabledForUi(context: Context): Boolean =
        readModuleBoolean(context, KEY_COLOR_FILTER_ENABLED, DEFAULT_COLOR_FILTER_ENABLED)

    /**
     * realOS app-side writes. The gate is read once when the launcher process
     * starts, so the value must be visible through remote preferences before
     * the launcher restart.
     */
    fun setMemoryHooksEnabled(context: Context, enabled: Boolean) =
        writeFeatureSwitch(context, KEY_MEMORY_HOOKS_ENABLED, enabled)

    fun setRecentsExtraStylesEnabled(context: Context, enabled: Boolean) =
        writeFeatureSwitch(context, KEY_RECENTS_EXTRA_STYLES_ENABLED, enabled)

    fun setColorFilterEnabled(context: Context, enabled: Boolean) =
        writeFeatureSwitch(context, KEY_COLOR_FILTER_ENABLED, enabled)

    fun isDockerBgEnabled(): Boolean =
        readRemoteBoolean(KEY_DOCKER_BG_ENABLED, DEFAULT_DOCKER_BG_ENABLED)

    fun isDockerBgEnabledForUi(context: Context): Boolean =
        readModuleBoolean(context, KEY_DOCKER_BG_ENABLED, DEFAULT_DOCKER_BG_ENABLED)

    fun isDockerBgBlurEnabledForUi(context: Context): Boolean =
        readModuleBoolean(context, KEY_DOCKER_BG_BLUR_ENABLED, DEFAULT_DOCKER_BG_BLUR_ENABLED)

    fun isDockerBgRadius(): Float =
        readRemoteFloat(KEY_DOCKER_BG_RADIUS, DEFAULT_DOCKER_BG_RADIUS)

    fun isDockerBgRadiusForUi(context: Context): Float =
        readModuleFloat(context, KEY_DOCKER_BG_RADIUS, DEFAULT_DOCKER_BG_RADIUS)

    /** Blend mode; a stored value outside the supported set fails closed to the stock mode. */
    fun dockerBgBlendMode(): Int =
        validBlendMode(readRemoteInt(KEY_DOCKER_BG_BLEND_MODE, DEFAULT_DOCKER_BG_BLEND_MODE))

    fun dockerBgBlendModeForUi(context: Context): Int =
        validBlendMode(readModuleInt(context, KEY_DOCKER_BG_BLEND_MODE, DEFAULT_DOCKER_BG_BLEND_MODE))

    /** Only 1, 2 and 4 reach a host shader branch; any other value applies no blend, so use 2. */
    fun validBlendMode(mode: Int): Int = when (mode) {
        BLEND_MODE_MIX, BLEND_MODE_STOCK, BLEND_MODE_MIX_OVERLAY -> mode
        else -> BLEND_MODE_STOCK
    }

    fun setDockerBgBlendMode(context: Context, mode: Int) {
        val safe = validBlendMode(mode)
        writeModuleInt(context, KEY_DOCKER_BG_BLEND_MODE, safe)
        pushRemoteInt(KEY_DOCKER_BG_BLEND_MODE, safe)
        bumpDockerBgRevision(context)
    }

    fun dockerBgBlurBackend(): String {
        val stored = readRemoteString(KEY_DOCKER_BG_BLUR_BACKEND, "")
        if (stored.isNotEmpty()) return stored
        return if (readRemoteBoolean(KEY_DOCKER_BG_BLUR_ENABLED, false)) {
            "stock"
        } else {
            DEFAULT_DOCKER_BG_BLUR_BACKEND
        }
    }

    fun dockerBgBlurBackendForUi(context: Context): String {
        val stored = readModuleString(context, KEY_DOCKER_BG_BLUR_BACKEND, "")
        if (stored.isNotEmpty()) return stored
        return if (readModuleBoolean(context, KEY_DOCKER_BG_BLUR_ENABLED, false)) {
            "stock"
        } else {
            DEFAULT_DOCKER_BG_BLUR_BACKEND
        }
    }

    fun setDockerBgBlurBackend(context: Context, backend: String) {
        writeModuleString(context, KEY_DOCKER_BG_BLUR_BACKEND, backend)
        pushRemoteString(KEY_DOCKER_BG_BLUR_BACKEND, backend)
        bumpDockerBgRevision(context)
    }

    /**
     * Default for one mode-scoped slot.
     *
     * The stock mode keeps the host material colour, so an untouched install still matches the
     * launcher; the other modes start at their own neutral value.
     */
    fun maskDefault(mode: Int, light: Boolean, slotB: Boolean): String = when (validBlendMode(mode)) {
        BLEND_MODE_MIX -> DEFAULT_MASK_MIX
        BLEND_MODE_MIX_OVERLAY -> if (slotB) DEFAULT_MASK_OVERLAY else DEFAULT_MASK_MIX
        else -> when {
            light && !slotB -> DEFAULT_DOCKER_BG_LIGHT_BLEND
            light -> DEFAULT_DOCKER_BG_LIGHT_MIX
            !slotB -> DEFAULT_DOCKER_BG_DARK_BLEND
            else -> DEFAULT_DOCKER_BG_DARK_MIX
        }
    }

    /** Host-side read of one mode-scoped slot; the stock mode falls back to the legacy key. */
    fun dockerBgMask(mode: Int, light: Boolean, slotB: Boolean): String {
        val scoped = readRemoteString(maskKey(mode, light, slotB), "")
        if (scoped.isNotEmpty()) return scoped
        return readRemoteString(legacyMaskKey(light, slotB), maskDefault(mode, light, slotB))
    }

    /** App-side read of one mode-scoped slot, same legacy fallback as the host side. */
    fun dockerBgMaskForUi(context: Context, mode: Int, light: Boolean, slotB: Boolean): String {
        val scoped = readModuleString(context, maskKey(mode, light, slotB), "")
        if (scoped.isNotEmpty()) return scoped
        return readModuleString(
            context,
            legacyMaskKey(light, slotB),
            maskDefault(mode, light, slotB),
        )
    }

    /** Pre-mode keys, kept only so an existing install keeps its tuned stock colour. */
    private fun legacyMaskKey(light: Boolean, slotB: Boolean): String = when {
        light && !slotB -> KEY_DOCKER_BG_LIGHT_BLEND
        light -> KEY_DOCKER_BG_LIGHT_MIX
        !slotB -> KEY_DOCKER_BG_DARK_BLEND
        else -> KEY_DOCKER_BG_DARK_MIX
    }

    /** Writes one mode-scoped slot through the same path a user pick takes. */
    fun setDockerBgMask(
        context: Context,
        mode: Int,
        light: Boolean,
        slotB: Boolean,
        value: String,
        dodge: Boolean = false,
    ) {
        setDockerBgMask(context, maskKey(mode, light, slotB), value, dodge)
    }

    fun setDockerBgMask(context: Context, key: String, value: String, dodge: Boolean = false) {
        val safe = clampMaskChannels(value, dodge)
        writeModuleString(context, key, safe)
        pushRemoteString(key, safe)
        bumpDockerBgRevision(context)
    }

    /**
     * Snaps a #AARRGGBB / #RRGGBB hex colour to what the active blend mode can render.
     *
     * The dodge layer divides by (1 - channel), so a channel at full scale diverges; a dodge colour
     * is capped at [DODGE_MAX_CHANNEL] (2x gain). Mix and overlay use bounded formulas and keep the
     * full 0..255 range.
     */
    fun clampMaskChannels(value: String, dodge: Boolean = false): String {
        val argb = runCatching { android.graphics.Color.parseColor(value) }.getOrNull()
            ?: return value
        return formatArgb(clampArgb(argb, dodge))
    }

    /** Caps the channels of an ARGB int; shared by the UI write path and the host read path. */
    fun clampArgb(argb: Int, dodge: Boolean): Int {
        if (!dodge) return argb
        val max = DODGE_MAX_CHANNEL
        return android.graphics.Color.argb(
            android.graphics.Color.alpha(argb),
            android.graphics.Color.red(argb).coerceAtMost(max),
            android.graphics.Color.green(argb).coerceAtMost(max),
            android.graphics.Color.blue(argb).coerceAtMost(max),
        )
    }

    /** `#AARRGGBB` for an ARGB int. */
    fun formatArgb(argb: Int): String = String.format(
        "#%02X%02X%02X%02X",
        android.graphics.Color.alpha(argb),
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb),
    )

    fun setDockerBgEnabled(context: Context, enabled: Boolean) {
        writeFeatureSwitch(context, KEY_DOCKER_BG_ENABLED, enabled)
        if (!enabled) writeFeatureSwitch(context, KEY_DOCKER_BG_BLUR_ENABLED, false)
        bumpDockerBgRevision(context)
    }

    fun setDockerBgRadius(context: Context, value: Float) {
        writeModuleFloat(context, KEY_DOCKER_BG_RADIUS, value)
        pushRemoteFloat(KEY_DOCKER_BG_RADIUS, value)
        bumpDockerBgRevision(context)
    }

    /** Pushes existing local values into remote preferences (page entry / migration). */
    fun syncToRemote(context: Context) {
        pushRemoteBoolean(KEY_MEMORY_HOOKS_ENABLED, isMemoryHooksEnabledForUi(context))
        pushRemoteBoolean(
            KEY_RECENTS_EXTRA_STYLES_ENABLED,
            isRecentsExtraStylesEnabledForUi(context)
        )
        pushRemoteBoolean(KEY_COLOR_FILTER_ENABLED, isColorFilterEnabledForUi(context))
        pushRemoteBoolean(KEY_DOCKER_BG_ENABLED, isDockerBgEnabledForUi(context))
        pushRemoteBoolean(KEY_DOCKER_BG_BLUR_ENABLED, isDockerBgBlurEnabledForUi(context))
        pushRemoteFloat(KEY_DOCKER_BG_RADIUS, isDockerBgRadiusForUi(context))
        pushRemoteInt(KEY_DOCKER_BG_BLEND_MODE, dockerBgBlendModeForUi(context))
        // Every mode owns its own slots, so all of them are pushed; the host reads the set that
        // belongs to the active mode.
        for (mode in BLEND_MODES) {
            for (light in booleanArrayOf(true, false)) {
                for (slotB in booleanArrayOf(false, true)) {
                    pushRemoteString(
                        maskKey(mode, light, slotB),
                        dockerBgMaskForUi(context, mode, light, slotB),
                    )
                }
            }
        }
        bumpDockerBgRevision(context)
    }

    /** Monotonic revision so the host can reload on change without a launcher restart. */
    private fun bumpDockerBgRevision(context: Context) {
        val current = runCatching {
            modulePrefs(context).getLong(KEY_DOCKER_BG_REV, 0L)
        }.getOrDefault(0L)
        val next = current + 1
        runCatching {
            modulePrefs(context).edit().putLong(KEY_DOCKER_BG_REV, next).apply()
        }.onFailure { Log.w(TAG, "Failed to write local docker bg revision", it) }
        runCatching {
            XposedServiceManager.getRemotePreferences(MODULE_PREFS_NAME)
                ?.edit()
                ?.putLong(KEY_DOCKER_BG_REV, next)
                ?.commit()
        }.onFailure { Log.w(TAG, "Failed to push docker bg revision", it) }
    }

    // ── zram stats: realOS(root) → provider → host ──

    /** Called by the realOS root fetcher; writes module preferences, the fallback when the provider is unavailable */
    fun publishRootStats(
        context: Context,
        zramOrigBytes: Long,
        zramComprBytes: Long,
        zramTotalBytes: Long,
        externalSwapUsedKb: Long,
        at: Long
    ) {
        runCatching {
            modulePrefs(context).edit()
                .putLong(KEY_ZRAM_ORIG, zramOrigBytes)
                .putLong(KEY_ZRAM_COMPR, zramComprBytes)
                .putLong(KEY_ZRAM_TOTAL, zramTotalBytes)
                .putLong(KEY_EXT_SWAP_USED, externalSwapUsedKb)
                .putLong(KEY_STATS_AT_PREF, at)
                .apply()
        }.onFailure { Log.w(TAG, "Failed to write the zram stats", it) }
    }

    /**
     * Host-side read of the zram stats produced by the root read.
     *
     * Reads the provider synchronously first; falls back to the module preferences written
     * by warmup. Returns null when both fail, and the caller uses the swap metric.
     */
    fun readRootStats(context: Context?): RootZramStats? {
        readFromProvider(context)?.let { return it }
        return readFromPrefs()
    }

    private fun readFromProvider(context: Context?): RootZramStats? {
        val ctx = context?.applicationContext ?: context ?: return null
        return runCatching {
            val bundle = ctx.contentResolver.call(
                Uri.parse("content://$STATS_AUTHORITY"),
                METHOD_GET_ZRAM_STATS,
                null,
                null
            ) ?: return null
            val stats = RootZramStats(
                zramOrigBytes = bundle.getLong(KEY_STATS_ORIG, 0L),
                zramComprBytes = bundle.getLong(KEY_STATS_COMPR, 0L),
                zramTotalBytes = bundle.getLong(KEY_STATS_TOTAL, 0L),
                externalSwapUsedKb = bundle.getLong(KEY_STATS_SWAP_USED, 0L),
                at = bundle.getLong(KEY_STATS_AT, 0L)
            )
            if (stats.isValid) stats else null
        }.getOrElse {
            Log.w(TAG, "Provider read of the zram stats failed; falling back to preferences", it)
            null
        }
    }

    private fun readFromPrefs(): RootZramStats? {
        val p = remotePrefs ?: return null
        return runCatching {
            val at = p.getLong(KEY_STATS_AT_PREF, 0L)
            if (at <= 0L) return null
            if (System.currentTimeMillis() - at > STATS_MAX_AGE_MS) return null
            val stats = RootZramStats(
                zramOrigBytes = p.getLong(KEY_ZRAM_ORIG, 0L),
                zramComprBytes = p.getLong(KEY_ZRAM_COMPR, 0L),
                zramTotalBytes = p.getLong(KEY_ZRAM_TOTAL, 0L),
                externalSwapUsedKb = p.getLong(KEY_EXT_SWAP_USED, 0L),
                at = at
            )
            if (stats.isValid) stats else null
        }.getOrNull()
    }

    // ── Real memory switch (host side) ──

    fun isRealMemoryEnabled(context: Context? = null): Boolean =
        getBoolean(context, KEY_REAL_MEMORY, DEFAULT_REAL_MEMORY)

    fun setRealMemoryEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext ?: context
        initHost(app)
        hostPrefs?.edit()?.putBoolean(KEY_REAL_MEMORY, enabled)?.apply()
    }

    /** Reads the host's official "show running memory info" switch */
    fun isOfficialMemorySwitchOn(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, SECURE_MEMORY_INFO_KEY, 0) == 1
    }.getOrDefault(false)

    /**
     * Writes the persisted value of the host's official switch.
     * The host's own condition stays pinned; this keeps persistence in sync with user actions.
     */
    fun setOfficialMemorySwitch(context: Context, enabled: Boolean): Boolean = runCatching {
        Settings.Secure.putInt(
            context.contentResolver,
            SECURE_MEMORY_INFO_KEY,
            if (enabled) 1 else 0
        )
        true
    }.getOrElse {
        Log.w(TAG, "Failed to write Settings.Secure.$SECURE_MEMORY_INFO_KEY", it)
        false
    }

    private fun modulePrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(MODULE_PREFS_NAME, Context.MODE_PRIVATE)

    /** Remote read used at launcher start; an unavailable handle falls back to the default. */
    private fun readRemoteBoolean(key: String, defaultVal: Boolean): Boolean {
        val p = remotePrefs ?: return defaultVal
        return runCatching { p.getBoolean(key, defaultVal) }.getOrDefault(defaultVal)
    }

    private fun readRemoteFloat(key: String, defaultVal: Float): Float {
        val p = remotePrefs ?: return defaultVal
        return runCatching { p.getFloat(key, defaultVal) }.getOrDefault(defaultVal)
    }

    private fun readRemoteInt(key: String, defaultVal: Int): Int {
        val p = remotePrefs ?: return defaultVal
        return runCatching { p.getInt(key, defaultVal) }.getOrDefault(defaultVal)
    }

    private fun readRemoteString(key: String, defaultVal: String): String {
        val p = remotePrefs ?: return defaultVal
        return runCatching { p.getString(key, defaultVal) ?: defaultVal }.getOrDefault(defaultVal)
    }

    /** Module-side read for the realOS UI; never throws. */
    private fun readModuleBoolean(context: Context?, key: String, defaultVal: Boolean): Boolean {
        val ctx = context?.applicationContext ?: context ?: return defaultVal
        return runCatching { modulePrefs(ctx).getBoolean(key, defaultVal) }.getOrDefault(defaultVal)
    }

    private fun readModuleFloat(context: Context, key: String, defaultVal: Float): Float =
        runCatching {
            modulePrefs(context.applicationContext ?: context).getFloat(key, defaultVal)
        }.getOrDefault(defaultVal)

    private fun readModuleString(context: Context, key: String, defaultVal: String): String =
        runCatching {
            modulePrefs(context.applicationContext ?: context).getString(key, defaultVal) ?: defaultVal
        }.getOrDefault(defaultVal)

    private fun readModuleInt(context: Context, key: String, defaultVal: Int): Int =
        runCatching {
            modulePrefs(context.applicationContext ?: context).getInt(key, defaultVal)
        }.getOrDefault(defaultVal)

    private fun writeModuleInt(context: Context, key: String, value: Int) {
        runCatching {
            modulePrefs(context).edit().putInt(key, value).apply()
        }.onFailure { Log.w(TAG, "Failed to write $key", it) }
    }

    /** Module-side write for the realOS UI; a failed write is logged once per call. */
    private fun writeModuleBoolean(context: Context, key: String, enabled: Boolean) {
        runCatching {
            modulePrefs(context).edit().putBoolean(key, enabled).apply()
        }.onFailure { Log.w(TAG, "Failed to write $key", it) }
    }

    private fun writeModuleFloat(context: Context, key: String, value: Float) {
        runCatching {
            modulePrefs(context).edit().putFloat(key, value).apply()
        }.onFailure { Log.w(TAG, "Failed to write $key", it) }
    }

    private fun writeModuleString(context: Context, key: String, value: String) {
        runCatching {
            modulePrefs(context).edit().putString(key, value).apply()
        }.onFailure { Log.w(TAG, "Failed to write $key", it) }
    }

    /**
     * Local write plus remote push. A plain SharedPreferences write only touches
     * the module's file; the host reads the separate remote group.
     */
    private fun writeFeatureSwitch(context: Context, key: String, enabled: Boolean) {
        writeModuleBoolean(context, key, enabled)
        pushRemoteBoolean(key, enabled)
    }

    private fun pushRemoteBoolean(key: String, enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(MODULE_PREFS_NAME)
                ?.edit()
                ?.putBoolean(key, enabled)
                ?.commit()
        }.onFailure { Log.w(TAG, "Failed to push $key to remote preferences", it) }
    }

    private fun pushRemoteFloat(key: String, value: Float) {
        runCatching {
            XposedServiceManager.getRemotePreferences(MODULE_PREFS_NAME)
                ?.edit()
                ?.putFloat(key, value)
                ?.commit()
        }.onFailure { Log.w(TAG, "Failed to push $key to remote preferences", it) }
    }

    private fun pushRemoteString(key: String, value: String) {
        runCatching {
            XposedServiceManager.getRemotePreferences(MODULE_PREFS_NAME)
                ?.edit()
                ?.putString(key, value)
                ?.commit()
        }.onFailure { Log.w(TAG, "Failed to push $key to remote preferences", it) }
    }

    private fun pushRemoteInt(key: String, value: Int) {
        runCatching {
            XposedServiceManager.getRemotePreferences(MODULE_PREFS_NAME)
                ?.edit()
                ?.putInt(key, value)
                ?.commit()
        }.onFailure { Log.w(TAG, "Failed to push $key to remote preferences", it) }
    }

    private fun getBoolean(context: Context?, key: String, defaultVal: Boolean): Boolean {
        val ctx = context?.applicationContext ?: context
        if (ctx != null) {
            initHost(ctx)
            val hp = hostPrefs
            if (hp != null && hp.contains(key)) {
                return runCatching { hp.getBoolean(key, defaultVal) }.getOrDefault(defaultVal)
            }
        }
        val rp = remotePrefs
        if (rp != null && rp.contains(key)) {
            return runCatching { rp.getBoolean(key, defaultVal) }.getOrDefault(defaultVal)
        }
        return defaultVal
    }
}
