package io.github.ra1nify.realos.opluskeyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethod
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusInputConfig
import io.github.ra1nify.realos.opluskeyboard.OplusModuleRef

/**
 * Shared preferences manager for OPPO Keyboard (Xiaobu Input Method).
 *
 * Runs inside the com.oplus.keyboard process for zero-IPC read/write from SkinActivity and ImeService.
 */
object OplusInputPrefs {

    private const val PREFS_NAME = "realos_oplus_prefs"
    private const val LEGACY_PREFS_NAME = "luckyfuse_oplus_prefs"

    const val KEY_WORKSHOP_ENABLED = "workshop_enabled"
    const val KEY_ENHANCEMENTS_ENABLED = "enhancements_enabled"
    const val KEY_AUTOFILL_ENABLED = "autofill_enabled"
    const val KEY_AUTOFILL_BUTTON_ENABLED = "autofill_button_enabled"
    const val KEY_LIQUID_GLASS_ENABLED = "liquid_glass_enabled"
    const val KEY_BYPASS_DAY_NIGHT = "bypass_day_night"

    /**
     * Combined liquid glass "limit bypass" switch (default off).
     *
     * On: bypasses (a) the app whitelist limit and (b) the follow-system
     * dark mode limit at once.
     */
    const val KEY_BYPASS_ALL_LIMITS = "bypass_all_limits"
    const val KEY_LIGHT_MASK_ALPHA = "light_mask_alpha" // 0.0f ~ 1.00f (step 0.10f)
    const val KEY_DARK_MASK_ALPHA = "dark_mask_alpha"   // 0.0f ~ 1.00f (step 0.10f)
    const val KEY_BLUR_RADIUS = "blur_radius"           // 30 ~ 300
    const val KEY_CORNER_RADIUS = "corner_radius"       // dp, docked keyboard top corner radius (0 ~ 40)
    const val KEY_KEY_ALPHA = "key_alpha"               // 0.0f ~ 0.80f
    const val KEY_LIGHT_MASK_COLOR = "light_mask_color"
    const val KEY_DARK_MASK_COLOR = "dark_mask_color"
    const val KEY_LIGHT_BG_OVERLAY_ENABLED = "light_bg_overlay_enabled"
    const val KEY_LIGHT_BG_OVERLAY_ALPHA = "light_bg_overlay_alpha"
    const val KEY_DARK_BG_OVERLAY_ENABLED = "dark_bg_overlay_enabled"
    const val KEY_DARK_BG_OVERLAY_ALPHA = "dark_bg_overlay_alpha"
    const val KEY_EFFECTS_ENABLED = "effects_enabled"
    const val KEY_EFFECTS_PRESET = "effects_preset"
    const val KEY_EFFECTS_BOARD = "effects_board"
    const val KEY_EFFECTS_KEY = "effects_key"
    const val KEY_EFFECTS_MOTION = "effects_motion"
    const val KEY_EFFECTS_COLOR_PRESET = "effects_color_preset"
    const val KEY_EFFECTS_FOOT_PRINT = "effects_foot_print"
    const val KEY_EFFECTS_ALPHA_RATIO = "effects_alpha_ratio"   // 0.0f ~ 1.0f (0% ~ 100%)
    const val KEY_EFFECTS_RANGE_RATIO = "effects_range_ratio"   // 0.5f ~ 2.0f (50% ~ 200%)
    const val KEY_EFFECTS_SPEED_RATIO = "effects_speed_ratio"   // 0.5f ~ 2.0f (50% ~ 200%)
    const val KEY_EFFECTS_CUSTOM_PALETTE = "oplus_input_effects_custom_palette"
    val DEFAULT_CUSTOM_PALETTE: List<Int> = listOf(
        0xFF00E5FF.toInt(), // Cyan
        0xFFFF007F.toInt()  // Pink
    )

    // ── Keys Cafe mechanical key sounds ──
    const val KEY_KEYBOARD_SOUND_ENABLED = "keyboard_sound_enabled"
    const val KEY_KEYBOARD_SOUND_STYLE = "keyboard_sound_style"
    const val KEY_KEYBOARD_SOUND_VOLUME = "keyboard_sound_volume"

    const val DEFAULT_KEYBOARD_SOUND_ENABLED = false
    const val DEFAULT_KEYBOARD_SOUND_STYLE = "RETRO"
    const val DEFAULT_KEYBOARD_SOUND_VOLUME = 1.0f

    const val DEFAULT_LIGHT_ALPHA = 0.80f
    const val DEFAULT_DARK_ALPHA = 0.60f
    const val DEFAULT_BLUR_RADIUS = 150
    /** Default top corner radius of the docked keyboard (dp); matches the host docked default 28dp. */
    const val DEFAULT_CORNER_RADIUS = 28
    const val DEFAULT_KEY_ALPHA = 0.54f
    const val DEFAULT_LIGHT_MASK_COLOR = "#E1E1E1"
    const val DEFAULT_DARK_MASK_COLOR = "#0D0D0D"
    const val DEFAULT_BG_OVERLAY_ALPHA = 0.50f
    const val DEFAULT_EFFECTS_BOARD = "RING_SPREAD"
    const val DEFAULT_EFFECTS_KEY = "COLOR_FLASH"
    const val DEFAULT_EFFECTS_MOTION = "SPRING_DOWN"
    const val DEFAULT_EFFECTS_COLOR_PRESET = "POPSICLE"
    const val DEFAULT_EFFECTS_FOOT_PRINT = "CAT"
    const val DEFAULT_EFFECTS_ALPHA_RATIO = 1.0f
    const val DEFAULT_EFFECTS_RANGE_RATIO = 1.0f
    const val DEFAULT_EFFECTS_SPEED_RATIO = 1.0f

    @Volatile
    private var legacyMigrated = false

    /** getPrefs instance cache (see below); keyed by package name. */
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Volatile
    private var cachedPrefsKey: String? = null

    /** Background image existence cache (see [hasBgImage]). */
    @Volatile
    private var bgImageCacheAt: Long = 0L

    private val bgImageCache = java.util.concurrent.ConcurrentHashMap<Boolean, Boolean>()

    private const val BG_IMAGE_CACHE_TTL_MS = 1000L

    /** Called after the background image is written or deleted; clears the [hasBgImage] cache. */
    fun invalidateBgImageCache() {
        bgImageCache.clear()
        bgImageCacheAt = 0L
    }

    /**
     * Returns the host-side SharedPreferences.
     *
     * The getter has 60+ call sites, many on draw/touch paths (per-key redraw, the
     * per-frame background overlay). `getSharedPreferences` runs a locked framework
     * lookup on each call; this caches the instance (one object per process, reads
     * stay live). The cache key is the package name, one entry per process.
     */
    fun getPrefs(context: Context): SharedPreferences {
        val key = context.packageName
        cachedPrefs?.let { if (cachedPrefsKey == key) return it }
        synchronized(this) {
            cachedPrefs?.let { if (cachedPrefsKey == key) return it }
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // One-time legacy config migration
            if (!legacyMigrated) {
                legacyMigrated = true
                if (sp.all.isEmpty()) {
                    val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                    if (legacy.all.isNotEmpty()) {
                        val editor = sp.edit()
                        for ((k, v) in legacy.all) {
                            when (v) {
                                is Boolean -> editor.putBoolean(k, v)
                                is Int -> editor.putInt(k, v)
                                is Long -> editor.putLong(k, v)
                                is Float -> editor.putFloat(k, v)
                                is String -> editor.putString(k, v)
                            }
                        }
                        editor.apply()
                    }
                }
            }
            cachedPrefs = sp
            cachedPrefsKey = key
            return sp
        }
    }

    fun isWorkshopEnabled(context: Context): Boolean {
        val module = OplusModuleRef.get()
        if (module != null) {
            return OplusInputConfig.isWorkshopEnabled(module, context.packageName)
        }
        return getPrefs(context).getBoolean(KEY_WORKSHOP_ENABLED, true)
    }

    fun setWorkshopEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_WORKSHOP_ENABLED, enabled).apply()

    fun isEnhancementsEnabled(context: Context): Boolean {
        val module = OplusModuleRef.get()
        if (module != null) {
            return OplusInputConfig.isEnhancementsEnabled(module, context.packageName)
        }
        return getPrefs(context).getBoolean(KEY_ENHANCEMENTS_ENABLED, true)
    }

    fun setEnhancementsEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_ENHANCEMENTS_ENABLED, enabled).apply()

    /**
     * Detects whether the target IME declares supportsInlineSuggestions="true".
     *
     * The check reads only the APK payload:
     * 1. Package whitelist: the package must be one of [SpecPackages.OPLUS_KEYBOARD_VARIANTS];
     * 2. Static XML parse: PackageManager resolves the target IME Service metadata
     *    (android.view.im) and reads `android:supportsInlineSuggestions="true"`;
     * 3. Static APK fallback: on system Resources parse failure, the IME config XML is
     *    read from the APK (ZipFile) and its constant pool checked for the declaration;
     * 4. No InputMethodManager / InputMethodInfo / dumpsys query is made.
     *
     * **The result is cached**: [computeInlineSuggestionsNativelyDeclared] runs
     * `queryIntentServices` plus a full `res/xml` byte scan, and `isAutofillEnabled`
     * calls it from `onCreateInlineSuggestionsRequest`. Key: `packageName@versionCode_lastUpdateTime`.
     *
     * Note: do not put a block comment start token in this body (Kotlin block comments nest).
     */
    fun isInlineSuggestionsNativelyDeclared(
        context: Context,
        targetPackageName: String = context.packageName
    ): Boolean {
        if (targetPackageName !in SpecPackages.OPLUS_KEYBOARD_VARIANTS) return false
        val key = inlineDeclaredCacheKey(context, targetPackageName)
        inlineDeclaredCache[key]?.let { return it }
        val computed = computeInlineSuggestionsNativelyDeclared(context, targetPackageName)
        inlineDeclaredCache[key] = computed
        return computed
    }

    /** Probe cache: key = `packageName@versionCode_lastUpdateTime`; invalidated on host upgrade. */
    private val inlineDeclaredCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun inlineDeclaredCacheKey(context: Context, targetPackageName: String): String {
        val stamp = runCatching {
            val info = context.packageManager.getPackageInfo(targetPackageName, 0)
            "${info.versionCode}_${info.lastUpdateTime}"
        }.getOrNull() ?: "unknown"
        return "$targetPackageName@$stamp"
    }

    private fun computeInlineSuggestionsNativelyDeclared(
        context: Context,
        targetPackageName: String
    ): Boolean {
        val fromXml = runCatching {
            val pm = context.packageManager
            val intent = Intent(InputMethod.SERVICE_INTERFACE).setPackage(targetPackageName)
            val serviceInfo = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
                .mapNotNull { it.serviceInfo }
                .firstOrNull { it.metaData?.containsKey("android.view.im") == true }
                ?: pm.queryIntentServices(intent, PackageManager.GET_META_DATA).firstOrNull()?.serviceInfo
                ?: return@runCatching false

            val xmlRes = serviceInfo.metaData?.getInt("android.view.im", 0) ?: return@runCatching false
            if (xmlRes == 0) return@runCatching false

            val targetResources = if (context.packageName == targetPackageName) {
                context.resources
            } else {
                pm.getResourcesForApplication(serviceInfo.applicationInfo)
            }
            val parser = targetResources.getXml(xmlRes)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next()
            }
            if (eventType == XmlPullParser.START_TAG && parser.name == "input-method") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "supportsInlineSuggestions") {
                        return@runCatching parser.getAttributeBooleanValue(i, false) ||
                                parser.getAttributeValue(i).equals("true", ignoreCase = true) ||
                                parser.getAttributeValue(i) == "1"
                    }
                }
            }
            false
        }.getOrNull()

        if (fromXml == true) return true

        // Purely static APK fallback: open base.apk and scan the XML constant pool directly
        return runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(targetPackageName, 0)
            val apkPath = appInfo.sourceDir ?: return@runCatching false
            ZipFile(File(apkPath)).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".xml") && (entry.name.startsWith("res/xml/") || entry.name.contains("ime_config") || entry.name == "res/6Y.xml")) {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val content = String(bytes, Charsets.ISO_8859_1)
                        if (content.contains("supportsInlineSuggestions")) {
                            return@runCatching true
                        }
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    fun isAutofillEnabled(context: Context): Boolean {
        if (isInlineSuggestionsNativelyDeclared(context)) return true
        return getPrefs(context).getBoolean(KEY_AUTOFILL_ENABLED, true)
    }

    fun setAutofillEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_AUTOFILL_ENABLED, enabled).apply()

    fun isAutofillButtonEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AUTOFILL_BUTTON_ENABLED, true)

    fun setAutofillButtonEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_AUTOFILL_BUTTON_ENABLED, enabled).apply()

    fun isSystemInlineAutofillEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(OplusInputConfig.KEY_SYSTEM_OPLUS_INLINE_AUTOFILL, true)

    fun setSystemInlineAutofillEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(OplusInputConfig.KEY_SYSTEM_OPLUS_INLINE_AUTOFILL, enabled).apply()

    fun isLiquidGlassEnabled(context: Context): Boolean {
        val module = OplusModuleRef.get()
        if (module != null) {
            return OplusInputConfig.isLiquidGlassEnabled(module, context.packageName)
        }
        return getPrefs(context).getBoolean(KEY_LIQUID_GLASS_ENABLED, true)
    }

    fun setLiquidGlassEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_LIQUID_GLASS_ENABLED, enabled).apply()

    fun isBypassDayNight(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BYPASS_DAY_NIGHT, true)

    fun setBypassDayNight(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_BYPASS_DAY_NIGHT, enabled).apply()

    /**
     * Combined "limit bypass" switch, default off.
     * On = bypass the whitelist limit and the follow-system dark mode limit.
     */
    fun isBypassAllLimits(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BYPASS_ALL_LIMITS, false)

    fun setBypassAllLimits(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_BYPASS_ALL_LIMITS, enabled).apply()

    fun getLightMaskAlpha(context: Context): Float =
        getPrefs(context).getFloat(KEY_LIGHT_MASK_ALPHA, DEFAULT_LIGHT_ALPHA)

    fun setLightMaskAlpha(context: Context, alpha: Float) =
        getPrefs(context).edit().putFloat(KEY_LIGHT_MASK_ALPHA, alpha).apply()

    fun getDarkMaskAlpha(context: Context): Float =
        getPrefs(context).getFloat(KEY_DARK_MASK_ALPHA, DEFAULT_DARK_ALPHA)

    fun setDarkMaskAlpha(context: Context, alpha: Float) =
        getPrefs(context).edit().putFloat(KEY_DARK_MASK_ALPHA, alpha).apply()

    fun getLightMaskColor(context: Context): String =
        getPrefs(context).getString(KEY_LIGHT_MASK_COLOR, DEFAULT_LIGHT_MASK_COLOR) ?: DEFAULT_LIGHT_MASK_COLOR

    fun setLightMaskColor(context: Context, color: String) =
        getPrefs(context).edit().putString(KEY_LIGHT_MASK_COLOR, color).apply()

    fun getDarkMaskColor(context: Context): String =
        getPrefs(context).getString(KEY_DARK_MASK_COLOR, DEFAULT_DARK_MASK_COLOR) ?: DEFAULT_DARK_MASK_COLOR

    fun setDarkMaskColor(context: Context, color: String) =
        getPrefs(context).edit().putString(KEY_DARK_MASK_COLOR, color).apply()

    fun isLightBgOverlayEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_LIGHT_BG_OVERLAY_ENABLED, false)

    fun setLightBgOverlayEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_LIGHT_BG_OVERLAY_ENABLED, enabled).apply()

    fun getLightBgOverlayAlpha(context: Context): Float =
        getPrefs(context).getFloat(KEY_LIGHT_BG_OVERLAY_ALPHA, DEFAULT_BG_OVERLAY_ALPHA)

    fun setLightBgOverlayAlpha(context: Context, alpha: Float) =
        getPrefs(context).edit().putFloat(KEY_LIGHT_BG_OVERLAY_ALPHA, alpha).apply()

    fun isDarkBgOverlayEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_DARK_BG_OVERLAY_ENABLED, false)

    fun setDarkBgOverlayEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_DARK_BG_OVERLAY_ENABLED, enabled).apply()

    fun getDarkBgOverlayAlpha(context: Context): Float =
        getPrefs(context).getFloat(KEY_DARK_BG_OVERLAY_ALPHA, DEFAULT_BG_OVERLAY_ALPHA)

    fun setDarkBgOverlayAlpha(context: Context, alpha: Float) =
        getPrefs(context).edit().putFloat(KEY_DARK_BG_OVERLAY_ALPHA, alpha).apply()

    fun getBgImageFile(context: Context, isNight: Boolean): java.io.File {
        val newFile = java.io.File(context.filesDir, if (isNight) "realos_bg_dark.png" else "realos_bg_light.png")
        if (!newFile.exists()) {
            val oldFile = java.io.File(context.filesDir, if (isNight) "luckyfuse_bg_dark.png" else "luckyfuse_bg_light.png")
            if (oldFile.exists()) {
                runCatching { oldFile.renameTo(newFile) }
                if (newFile.exists()) return newFile
                return oldFile
            }
        }
        return newFile
    }

    /**
     * Whether the background image exists.
     *
     * Each call builds a `File` and runs `exists()` (plus a possible `renameTo`
     * migration); the background overlay's `onDraw` asks twice per frame.
     * The result is cached for 1 second.
     */
    fun hasBgImage(context: Context, isNight: Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (now - bgImageCacheAt < BG_IMAGE_CACHE_TTL_MS) {
            bgImageCache[isNight]?.let { return it }
        }
        val exists = getBgImageFile(context, isNight).exists()
        bgImageCache[isNight] = exists
        bgImageCacheAt = now
        return exists
    }

    fun deleteBgImage(context: Context, isNight: Boolean): Boolean {
        val deleted = getBgImageFile(context, isNight).delete()
        bgImageCache.remove(isNight)
        bgImageCacheAt = 0L
        return deleted
    }

    fun getBlurRadius(context: Context): Int =
        getPrefs(context).getInt(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS)

    fun setBlurRadius(context: Context, radius: Int) =
        getPrefs(context).edit().putInt(KEY_BLUR_RADIUS, radius).apply()

    /** Docked keyboard top corner radius (dp); applied in docked mode when the host passes a top corner > 0. */
    fun getCornerRadius(context: Context): Int =
        getPrefs(context).getInt(KEY_CORNER_RADIUS, DEFAULT_CORNER_RADIUS)

    fun setCornerRadius(context: Context, radiusDp: Int) =
        getPrefs(context).edit().putInt(KEY_CORNER_RADIUS, radiusDp).apply()

    fun getKeyAlpha(context: Context): Float =
        getPrefs(context).getFloat(KEY_KEY_ALPHA, DEFAULT_KEY_ALPHA)

    fun setKeyAlpha(context: Context, alpha: Float) =
        getPrefs(context).edit().putFloat(KEY_KEY_ALPHA, alpha).apply()

    fun isEffectsEnabled(context: Context): Boolean {
        val module = OplusModuleRef.get()
        if (module != null && !OplusInputConfig.isKeyboardEffectsEnabled(module, context.packageName)) {
            return false
        }
        val board = getEffectsBoard(context)
        val key = getEffectsKey(context)
        val motion = getEffectsMotion(context)
        return !board.equals("NONE", ignoreCase = true) ||
            !key.equals("NONE", ignoreCase = true) ||
            !motion.equals("NONE", ignoreCase = true)
    }

    @Deprecated("Effects are now controlled directly by individual parameters")
    fun setEffectsEnabled(context: Context, enabled: Boolean) {}

    fun getEffectsBoard(context: Context): String =
        getPrefs(context).getString(KEY_EFFECTS_BOARD, DEFAULT_EFFECTS_BOARD) ?: DEFAULT_EFFECTS_BOARD

    fun setEffectsBoard(context: Context, board: String) =
        getPrefs(context).edit().putString(KEY_EFFECTS_BOARD, board).apply()

    fun getEffectsKey(context: Context): String =
        getPrefs(context).getString(KEY_EFFECTS_KEY, DEFAULT_EFFECTS_KEY) ?: DEFAULT_EFFECTS_KEY

    fun setEffectsKey(context: Context, key: String) =
        getPrefs(context).edit().putString(KEY_EFFECTS_KEY, key).apply()

    fun getEffectsMotion(context: Context): String =
        getPrefs(context).getString(KEY_EFFECTS_MOTION, DEFAULT_EFFECTS_MOTION) ?: DEFAULT_EFFECTS_MOTION

    fun setEffectsMotion(context: Context, motion: String) =
        getPrefs(context).edit().putString(KEY_EFFECTS_MOTION, motion).apply()

    fun getEffectsColorPreset(context: Context): String =
        getPrefs(context).getString(KEY_EFFECTS_COLOR_PRESET, DEFAULT_EFFECTS_COLOR_PRESET) ?: DEFAULT_EFFECTS_COLOR_PRESET

    fun setEffectsColorPreset(context: Context, preset: String) =
        getPrefs(context).edit().putString(KEY_EFFECTS_COLOR_PRESET, preset).apply()

    fun getCustomPalette(context: Context): List<Int> {
        val raw = getPrefs(context).getString(KEY_EFFECTS_CUSTOM_PALETTE, null) ?: return DEFAULT_CUSTOM_PALETTE
        return try {
            val array = org.json.JSONArray(raw)
            val list = mutableListOf<Int>()
            for (i in 0 until array.length()) {
                list.add(array.getInt(i))
            }
            if (list.isEmpty()) DEFAULT_CUSTOM_PALETTE else list.take(8)
        } catch (_: Throwable) {
            DEFAULT_CUSTOM_PALETTE
        }
    }

    fun setCustomPalette(context: Context, colors: List<Int>) {
        val clamped = colors.take(8)
        val array = org.json.JSONArray()
        for (color in clamped) {
            array.put(color)
        }
        getPrefs(context).edit().putString(KEY_EFFECTS_CUSTOM_PALETTE, array.toString()).apply()
    }

    fun getEffectsAlphaRatio(context: Context): Float =
        getPrefs(context).getFloat(KEY_EFFECTS_ALPHA_RATIO, DEFAULT_EFFECTS_ALPHA_RATIO)

    fun setEffectsAlphaRatio(context: Context, ratio: Float) =
        getPrefs(context).edit().putFloat(KEY_EFFECTS_ALPHA_RATIO, ratio).apply()

    fun getEffectsRangeRatio(context: Context): Float =
        getPrefs(context).getFloat(KEY_EFFECTS_RANGE_RATIO, DEFAULT_EFFECTS_RANGE_RATIO)

    fun setEffectsRangeRatio(context: Context, ratio: Float) =
        getPrefs(context).edit().putFloat(KEY_EFFECTS_RANGE_RATIO, ratio).apply()

    fun getEffectsSpeedRatio(context: Context): Float =
        getPrefs(context).getFloat(KEY_EFFECTS_SPEED_RATIO, DEFAULT_EFFECTS_SPEED_RATIO)

    fun setEffectsSpeedRatio(context: Context, ratio: Float) =
        getPrefs(context).edit().putFloat(KEY_EFFECTS_SPEED_RATIO, ratio).apply()

    // ── Keyboard Sound ──

    fun isKeyboardSoundEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_KEYBOARD_SOUND_ENABLED, DEFAULT_KEYBOARD_SOUND_ENABLED)

    fun setKeyboardSoundEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_KEYBOARD_SOUND_ENABLED, enabled).apply()

    fun getKeyboardSoundStyle(context: Context): String =
        getPrefs(context).getString(KEY_KEYBOARD_SOUND_STYLE, DEFAULT_KEYBOARD_SOUND_STYLE)
            ?: DEFAULT_KEYBOARD_SOUND_STYLE

    fun setKeyboardSoundStyle(context: Context, style: String) =
        getPrefs(context).edit().putString(KEY_KEYBOARD_SOUND_STYLE, style).apply()

    fun getKeyboardSoundVolume(context: Context): Float =
        getPrefs(context).getFloat(KEY_KEYBOARD_SOUND_VOLUME, DEFAULT_KEYBOARD_SOUND_VOLUME)

    fun setKeyboardSoundVolume(context: Context, volume: Float) =
        getPrefs(context).edit().putFloat(KEY_KEYBOARD_SOUND_VOLUME, volume).apply()

    // ── Foot Print icon (key blending FOOT_PRINT) ──

    fun getFootPrintIcon(context: Context): String =
        getPrefs(context).getString(KEY_EFFECTS_FOOT_PRINT, DEFAULT_EFFECTS_FOOT_PRINT)
            ?: DEFAULT_EFFECTS_FOOT_PRINT

    fun setFootPrintIcon(context: Context, icon: String) =
        getPrefs(context).edit().putString(KEY_EFFECTS_FOOT_PRINT, icon).apply()

    /** Custom foot print icon file (user-imported PNG); same name as Samsung `custom_foot_print.png`. */
    fun getCustomFootPrintFile(context: Context): File {
        val dir = File(context.filesDir, "foot_print")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "custom_foot_print.png")
    }

    fun hasCustomFootPrint(context: Context): Boolean = getCustomFootPrintFile(context).exists()

    /**
     * Copies the picked image as the custom foot print icon (decode and normalize to a
     * square PNG). Returns success; failures do not throw.
     */
    fun saveCustomFootPrint(context: Context, uri: android.net.Uri): Boolean {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            } ?: return false
            // Normalizes to a 256x256 square (center crop), near the 200dp vector icon size.
            val target = 256
            val square = android.graphics.Bitmap.createBitmap(target, target, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(square)
            val side = minOf(bitmap.width, bitmap.height).toFloat()
            val src = android.graphics.Rect(
                ((bitmap.width - side) / 2f).toInt(),
                ((bitmap.height - side) / 2f).toInt(),
                ((bitmap.width + side) / 2f).toInt(),
                ((bitmap.height + side) / 2f).toInt()
            )
            val dst = android.graphics.Rect(0, 0, target, target)
            c.drawBitmap(bitmap, src, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            FileOutputStream(getCustomFootPrintFile(context)).use { out ->
                square.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            square.recycle()
            true
        } catch (t: Throwable) {
            Log.w("OplusKeyboardEffects", "saveCustomFootPrint failed", t)
            false
        }
    }

    fun clearCustomFootPrint(context: Context): Boolean =
        try { getCustomFootPrintFile(context).delete() } catch (t: Throwable) { false }

    fun resetToDefaults(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_WORKSHOP_ENABLED, true)
            .putBoolean(KEY_LIQUID_GLASS_ENABLED, true)
            .putBoolean(KEY_BYPASS_DAY_NIGHT, true)
            .putBoolean(KEY_BYPASS_ALL_LIMITS, false)
            .putFloat(KEY_LIGHT_MASK_ALPHA, DEFAULT_LIGHT_ALPHA)
            .putFloat(KEY_DARK_MASK_ALPHA, DEFAULT_DARK_ALPHA)
            .putString(KEY_LIGHT_MASK_COLOR, DEFAULT_LIGHT_MASK_COLOR)
            .putString(KEY_DARK_MASK_COLOR, DEFAULT_DARK_MASK_COLOR)
            .putBoolean(KEY_LIGHT_BG_OVERLAY_ENABLED, false)
            .putFloat(KEY_LIGHT_BG_OVERLAY_ALPHA, DEFAULT_BG_OVERLAY_ALPHA)
            .putBoolean(KEY_DARK_BG_OVERLAY_ENABLED, false)
            .putFloat(KEY_DARK_BG_OVERLAY_ALPHA, DEFAULT_BG_OVERLAY_ALPHA)
            .putInt(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS)
            .putInt(KEY_CORNER_RADIUS, DEFAULT_CORNER_RADIUS)
            .putFloat(KEY_KEY_ALPHA, DEFAULT_KEY_ALPHA)
            .putFloat(KEY_EFFECTS_ALPHA_RATIO, DEFAULT_EFFECTS_ALPHA_RATIO)
            .putFloat(KEY_EFFECTS_RANGE_RATIO, DEFAULT_EFFECTS_RANGE_RATIO)
            .putFloat(KEY_EFFECTS_SPEED_RATIO, DEFAULT_EFFECTS_SPEED_RATIO)
            .remove(KEY_EFFECTS_CUSTOM_PALETTE)
            .apply()
    }
}
