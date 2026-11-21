package io.github.ra1nify.realos.xposed.hooks.launcher

import android.content.Context
import android.graphics.Bitmap
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule

/**
 * Drawer "filter by color" compatibility with third-party icon packs.
 *
 * ## Host gate
 *
 * `FeatureOption.isSupportColorFilterForTheme()` rejects third-party icon packs with
 * "this icon style does not support the feature". The color pipeline is style-agnostic:
 *
 * ```
 * AppInfo.bitmap.icon
 *   → DrawerIconPalette.parseColorWithBitmap
 *   → n.b(bitmap)   scales to 60×60, converts each pixel to HSV, buckets by ArrangeColor,
 *                   picks the largest bucket
 * ```
 *
 * Color data is cached per slot keyed by `theme + 100`
 * (theme=3 → 103, theme=5 → 105, theme=0 → 100).
 *
 * ## Measurement (217 apps, third-party icon pack)
 *
 * ```
 * current icons:  [BLUE=80, WHITE=41, RED=37, GREEN=27, BLACK_GREY=22, YELLOW=10]
 * original icons: [BLUE=75, WHITE=43, RED=38, GREEN=27, BLACK_GREY=23, YELLOW=11]
 * null bitmaps=0
 * ```
 *
 * Both distributions match; the icon pack keeps original colors.
 *
 * ## Applied changes
 *
 * The two theme conditions (`isDefaultTheme` / `theme == 3|5`) are relaxed; three hard constraints stay:
 *
 * | Condition | Kept | Note |
 * |---|---|---|
 * | `isSupportColorFilter()` | kept | carrier or policy level disable |
 * | `isNightIcon()` | kept | night icons render monochrome |
 * | `icon_dark_flag` | kept | dark icons render monochrome |
 * | `isDefaultTheme()` | relaxed | color extraction unaffected |
 * | `theme != 3/5` | relaxed | cache already keyed by theme+100 |
 *
 * `parseColorData` also gets a null-bitmap guard: it calls
 * `checkNotNullExpressionValue(icon, "icon")`, which throws instead of returning null.
 */
object ColorFilterCompat {

    private const val TAG = "RealOS-ColorFilterCompat"

    private const val FEATURE_OPTION = "com.android.common.config.FeatureOption"
    private const val UI_ENGINE = "com.android.launcher.theme.OplusUIEngine"
    private const val THEME_UTILS = "com.android.common.util.ThemeUtils"
    private const val THEME_UTILS_COMPANION = "com.android.common.util.ThemeUtils\$Companion"
    private const val DRAWER_PALETTE = "com.android.launcher3.allapps.colorfilter.DrawerIconPalette"
    private const val APP_INFO = "com.android.launcher3.model.data.AppInfo"
    private const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
    private const val ICON_CONFIG_HOLDER = "com.android.launcher.theme.LauncherIconConfig"
    private const val ICON_CONFIG = "com.oplus.uxicon.helper.IconConfig"

    /** Persisted icon config fingerprint; identifies an icon pack change across launcher restarts */
    private const val PREFS_NAME = "realos_color_filter"
    private const val KEY_CONFIG_HASH = "icon_config_hash"

    private val APP_CLASSES = listOf(
        "com.android.common.LauncherApplication",
        "com.android.launcher3.LauncherApplication"
    )

    /** The three static cache tables of `DrawerIconPalette` */
    private val CACHE_FIELDS = listOf("colorDatas", "iconTypes", "pkgToComponentKey")

    @Volatile
    private var logged = false

    fun install(module: XposedModule, classLoader: ClassLoader) {
        installThemeGate(module, classLoader)
        installNullIconGuard(module, classLoader)
        installBucketTuning(module, classLoader)
        installCacheInvalidation(module, classLoader)
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Relax the theme gate
    // ─────────────────────────────────────────────────────────────

    private fun installThemeGate(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val clazz = Class.forName(FEATURE_OPTION, false, classLoader)
            val method = clazz.getMethod("isSupportColorFilterForTheme")

            module.hook(method).intercept { chain ->
                val original = chain.proceed() as? Boolean ?: false
                if (original) return@intercept original

                // Recomputes the decision with only the hard constraints when the host check fails
                val deviceAllowed = callStaticBoolean(classLoader, FEATURE_OPTION, "isSupportColorFilter") == true
                val nightIcon = callStaticBoolean(classLoader, UI_ENGINE, "isNightIcon") == true
                val darkIcon = iconDarkFlag(classLoader) == true

                val allowed = deviceAllowed && !nightIcon && !darkIcon

                if (!logged) {
                    logged = true
                    module.log(
                        Log.INFO, TAG,
                        "Gate relaxed: deviceAllowed=$deviceAllowed nightIcon=$nightIcon " +
                            "darkIcon=$darkIcon -> $allowed"
                    )
                }
                allowed
            }
            module.log(Log.INFO, TAG, "Installed isSupportColorFilterForTheme (third-party icon pack compatibility)")
        }.onFailure {
            module.log(Log.WARN, TAG, "fail closed: theme gate hook install failed", it)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Null bitmap guard
    // ─────────────────────────────────────────────────────────────

    /**
     * `DrawerIconPalette.parseColorData(AppInfo)` calls
     * `checkNotNullExpressionValue(appInfo.bitmap.icon, "icon")` and throws
     * `IllegalStateException` on a null bitmap. This hook skips such apps early.
     */
    private fun installNullIconGuard(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val paletteClass = Class.forName(DRAWER_PALETTE, false, classLoader)
            val appInfoClass = Class.forName(APP_INFO, false, classLoader)
            val method = paletteClass
                .getDeclaredMethod("parseColorData", appInfoClass)
                .apply { isAccessible = true }

            module.hook(method).intercept { chain ->
                val appInfo = chain.args.getOrNull(0)
                if (appInfo != null && iconOf(appInfo) == null) {
                    // void method; returning null skips the original implementation
                    return@intercept null
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "Installed parseColorData (null bitmap guard)")
        }.onFailure {
            module.log(Log.WARN, TAG, "fail open: null bitmap guard hook install failed (color extraction may throw)", it)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Bucket rule override: orange and purple no longer map to red
    // ─────────────────────────────────────────────────────────────

    /**
     * Replaces the host per-pixel bucketing.
     *
     * The host maps `[270,360] ∪ [0,32]` to red, which captures orange (~30°) and purple (~300°).
     * This override narrows the red range and moves purple to blue; see [ColorBucketClassifier].
     *
     * Only `n.c(ItemInfo, Bitmap)` is replaced; host caching, writes, and page building are unchanged.
     *
     * On symbol resolution failure no hook is installed and host classification stays.
     */
    private fun installBucketTuning(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val classifier = ColorBucketClassifier.create(classLoader)
            if (classifier == null) {
                module.log(Log.WARN, TAG, "Bucket rule inactive: symbol resolution failed (keeping the official classification)")
                return@runCatching
            }

            val algoClass = Class.forName(
                ColorBucketClassifier.algorithmClassName(), false, classLoader
            )
            val itemInfoClass = Class.forName(ITEM_INFO, false, classLoader)
            val method = algoClass
                .getDeclaredMethod("c", itemInfoClass, Bitmap::class.java)
                .apply { isAccessible = true }

            // Structure check: the return type must expose ArrangeColor (`a()` returns the enum with no args)
            val returnType = method.returnType
            val looksLikeColorInfo = returnType.methods.any {
                it.name == "a" && it.parameterCount == 0
            }
            if (!looksLikeColorInfo) {
                module.log(
                    Log.WARN, TAG,
                    "Bucket rule inactive: c() returned an unexpected type (${returnType.name})"
                )
                return@runCatching
            }

            module.hook(method).intercept { chain ->
                val bitmap = chain.args.getOrNull(1) as? Bitmap
                    ?: return@intercept chain.proceed()
                // On classification failure runs the host implementation instead of returning null
                classifier.classify(bitmap) ?: return@intercept chain.proceed()
            }

            ColorBucketClassifier.logInstalled(module)
            module.log(Log.INFO, TAG, "Installed the bucket rule override (orange and purple no longer fall into red)")
        }.onFailure {
            module.log(Log.WARN, TAG, "fail closed: bucket rule override failed (keeping the official classification)", it)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Color cache invalidation on icon pack change
    // ─────────────────────────────────────────────────────────────

    /**
     * Invalidates the color cache together with the icon configuration.
     *
     * ## Host cache key
     *
     * `DrawerIconPalette` keys its cache by a single number:
     *
     * ```java
     * currentIconConfigIndex = iconConfig.getTheme();   // for example 5
     * if (theme != 2) currentIconConfigIndex += 100;    // → 105
     * ```
     *
     * A new icon pack usually keeps the same `theme`, so entries are reused:
     *
     * ```java
     * if (!curIconConfigColorDatas.containsKey(componentKey)) {   // cached entries are skipped
     *     parseColorData(appInfo);
     * }
     * ```
     *
     * `IconCache` then reloads the new icons while color data stays on the old pack.
     *
     * ## Applied behavior
     *
     * On entry to `parseAllColorDatas()` the host `IconConfig.hasChanged()` compares
     * theme / iconShape / foreign / iconSize and the remaining fields. A reported change
     * clears the three cache tables and the createNew branch recolors every icon.
     *
     * `getIconConfig()` returns the instance held by the host; `copy()` stores the snapshot,
     * so `hasChanged` compares two distinct instances.
     *
     * A change recounts every icon (about 1–2 seconds); the button shows "loading data"
     * as on first entry.
     */
    private fun installCacheInvalidation(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val paletteClass = Class.forName(DRAWER_PALETTE, false, classLoader)
            val parseAll = paletteClass.getDeclaredMethod("parseAllColorDatas")
                .apply { isAccessible = true }

            val tables = CACHE_FIELDS.mapNotNull { name ->
                runCatching {
                    paletteClass.getDeclaredField(name).apply { isAccessible = true }
                }.getOrNull()
            }
            if (tables.isEmpty()) {
                module.log(Log.WARN, TAG, "Cache invalidation inactive: cache table field not found")
                return@runCatching
            }

            val holderClass = runCatching { Class.forName(ICON_CONFIG_HOLDER, false, classLoader) }.getOrNull()
            val configClass = runCatching { Class.forName(ICON_CONFIG, false, classLoader) }.getOrNull()

            val getInstance = holderClass?.let { runCatching { it.getMethod("getInstance") }.getOrNull() }
            val getIconConfig = holderClass?.let { runCatching { it.getMethod("getIconConfig") }.getOrNull() }
            val hasChanged = configClass?.let { runCatching { it.getMethod("hasChanged", configClass) }.getOrNull() }
            val copy = configClass?.let { runCatching { it.getMethod("copy") }.getOrNull() }

            val lock = Any()
            var memoryConfig: Any? = null

            module.hook(parseAll).intercept { chain ->
                // Config comparison holds the lock; proceed() runs outside it
                synchronized(lock) {
                    runCatching {
                        val ctx = appContext(classLoader)
                        val changed = if (ctx != null) {
                            detectChangeBySettings(ctx)
                        } else if (getInstance != null && getIconConfig != null && configClass != null) {
                            val current = getIconConfig.invoke(getInstance.invoke(null))
                            if (current != null) {
                                val byPrefs = detectChangeByPrefs(current, classLoader)
                                if (byPrefs != null) {
                                    byPrefs
                                } else {
                                    // Falls back to the in-memory comparison when SharedPreferences is unavailable
                                    val previous = memoryConfig
                                    val result = previous != null &&
                                        hasChanged?.invoke(current, previous) == true
                                    memoryConfig = copy?.invoke(current)
                                    result
                                }
                            } else false
                        } else false

                        if (changed) {
                            var cleared = 0
                            for (field in tables) {
                                val table = field.get(null) as? java.util.Hashtable<*, *>
                                    ?: continue
                                table.clear()
                                cleared++
                            }
                            module.log(
                                Log.INFO, TAG,
                                "Icon config changed; cleared the color cache ($cleared tables) and will re-extract all colors"
                            )
                        }
                    }.onFailure { module.log(Log.WARN, TAG, "Failed to check the icon config change", it) }
                }
                chain.proceed()
            }

            module.log(Log.INFO, TAG, "Installed color cache invalidation (recomputed after an icon pack change)")
        }.onFailure {
            module.log(
                Log.WARN, TAG,
                "fail closed: color cache invalidation hook install failed (colors may not update after an icon pack change)", it
            )
        }
    }

    /**
     * Detects an icon configuration change through system settings and the persisted fingerprint.
     */
    private fun detectChangeBySettings(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return false
        val cr = context.contentResolver
        val pack = android.provider.Settings.System.getString(cr, "chosse_icon_pack_name") ?: ""
        val config = android.provider.Settings.System.getString(cr, "com.oplus.icons.icon_config") ?: ""
        val shape = android.provider.Settings.System.getString(cr, "custom_icon_shape") ?: ""
        val hash = (pack + "_" + config + "_" + shape).hashCode()
        val known = prefs.contains(KEY_CONFIG_HASH)
        val stored = prefs.getInt(KEY_CONFIG_HASH, 0)
        if (!known || stored != hash) {
            runCatching { prefs.edit().putInt(KEY_CONFIG_HASH, hash).apply() }
            return true
        }
        return false
    }

    /**
     * Detects an icon configuration change through the persisted fingerprint.
     *
     * The in-process baseline from `parseAllColorDatas` is lost on launcher restart,
     * so the fingerprint is stored in SharedPreferences.
     *
     * Returns null when SharedPreferences is unavailable; the caller uses the in-memory comparison.
     *
     * A first record counts as a change: without a stored baseline the on-disk color cache
     * cannot be matched to the current icon configuration. It is cleared once and does not
     * trigger again after the fingerprint is written.
     */
    private fun detectChangeByPrefs(current: Any, classLoader: ClassLoader): Boolean? {
        val prefs = appContext(classLoader)
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return null

        val hash = current.hashCode()
        val known = prefs.contains(KEY_CONFIG_HASH)
        val stored = prefs.getInt(KEY_CONFIG_HASH, 0)
        runCatching { prefs.edit().putInt(KEY_CONFIG_HASH, hash).apply() }

        return !known || stored != hash
    }

    private fun iconOf(appInfo: Any): Any? = runCatching {
        val bitmapInfo = appInfo.javaClass.getField("bitmap").get(appInfo) ?: return null
        bitmapInfo.javaClass.getField("icon").get(bitmapInfo)
    }.getOrNull()

    // ─────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────

    private fun callStaticBoolean(
        classLoader: ClassLoader,
        className: String,
        method: String
    ): Boolean? = runCatching {
        Class.forName(className, false, classLoader)
            .getMethod(method)
            .invoke(null) as? Boolean
    }.getOrNull()

    /** `ThemeUtils.a(context)`: reads `icon_dark_flag == 1`, the "dark icons" switch */
    private fun iconDarkFlag(classLoader: ClassLoader): Boolean? {
        val ctx = appContext(classLoader) ?: return null

        runCatching {
            Class.forName(THEME_UTILS, false, classLoader)
                .getMethod("a", Context::class.java)
                .apply { isAccessible = true }
                .invoke(null, ctx) as? Boolean
        }.getOrNull()?.let { return it }

        return runCatching {
            val companion = Class.forName(THEME_UTILS_COMPANION, false, classLoader)
            val instance = companion.getField("INSTANCE").get(null)
            companion.getMethod("a", Context::class.java)
                .apply { isAccessible = true }
                .invoke(instance, ctx) as? Boolean
        }.getOrNull()
    }

    private fun appContext(classLoader: ClassLoader): Context? {
        for (name in APP_CLASSES) {
            val ctx = runCatching {
                Class.forName(name, false, classLoader)
                    .getMethod("getAppContext")
                    .invoke(null) as? Context
            }.getOrNull()
            if (ctx != null) return ctx
        }
        return null
    }
}
