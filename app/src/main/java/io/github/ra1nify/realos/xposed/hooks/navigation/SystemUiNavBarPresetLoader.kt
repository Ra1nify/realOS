package io.github.ra1nify.realos.xposed.hooks.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import io.github.ra1nify.realos.BuildConfig
import android.util.Base64
import android.util.DisplayMetrics
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import io.github.ra1nify.realos.navigation.SystemUiConfig

/**
 * Navigation button preset icon loader and KeyButtonDrawable builder.
 */
object SystemUiNavBarPresetLoader {

    private const val TAG = "RealOS-SysUiNavBarPreset"
    private const val ASSETS_PATH = "assets/navbar_presets"
    private val REALOS_PKG = BuildConfig.APPLICATION_ID

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    fun getPresetBitmap(module: XposedModule, type: String, index: Int): Bitmap? {
        val safeIndex = index.coerceIn(1, 10)
        val key = "${type}_$safeIndex"
        bitmapCache[key]?.let { return it }

        val apkPath = runCatching {
            module.getModuleApplicationInfo().sourceDir
        }.getOrNull()

        if (apkPath == null) {
            module.log(Log.WARN, TAG, "fail closed: module APK sourceDir unavailable")
            return null
        }
        return loadBitmapFromZip(apkPath, key, type, safeIndex) { msg, t ->
            if (t != null) module.log(Log.ERROR, TAG, msg, t) else module.log(Log.INFO, TAG, msg)
        }
    }

    fun getPresetBitmap(context: Context, type: String, index: Int): Bitmap? {
        val safeIndex = index.coerceIn(1, 10)
        val key = "${type}_$safeIndex"
        bitmapCache[key]?.let { return it }

        val apkPath = if (context.packageName == REALOS_PKG) {
            context.applicationInfo.sourceDir
        } else {
            runCatching {
                context.packageManager.getApplicationInfo(REALOS_PKG, 0).sourceDir
            }.getOrNull()
        }

        if (apkPath == null) {
            Log.w(TAG, "fail closed: module APK sourceDir unavailable")
            return null
        }
        return loadBitmapFromZip(apkPath, key, type, safeIndex) { msg, t ->
            if (t != null) Log.e(TAG, msg, t) else Log.i(TAG, msg)
        }
    }

    fun getCustomBitmap(module: XposedModule, context: Context, type: String): Bitmap? {
        val cacheKey = "custom_$type"
        bitmapCache[cacheKey]?.let { return it }

        val key = when (type) {
            "back" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_BACK_BASE64
            "home" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_HOME_BASE64
            "recent" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_RECENT_BASE64
            else -> return null
        }

        val b64 = runCatching {
            android.provider.Settings.System.getString(context.contentResolver, key)
        }.getOrNull()

        val bmp = decodeBase64Bitmap(b64) ?: getPresetBitmap(module, type, 1)
        if (bmp != null) {
            bitmapCache[cacheKey] = bmp
            module.log(Log.INFO, TAG, "Cached the custom button icon: $type (${bmp.width}x${bmp.height})")
        }
        return bmp
    }

    fun getCustomBitmap(context: Context, type: String): Bitmap? {
        val cacheKey = "custom_$type"
        bitmapCache[cacheKey]?.let { return it }

        val key = when (type) {
            "back" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_BACK_BASE64
            "home" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_HOME_BASE64
            "recent" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_RECENT_BASE64
            else -> return null
        }

        val b64 = runCatching {
            android.provider.Settings.System.getString(context.contentResolver, key)
        }.getOrNull()

        val bmp = decodeBase64Bitmap(b64) ?: getPresetBitmap(context, type, 1)
        if (bmp != null) {
            bitmapCache[cacheKey] = bmp
        }
        return bmp
    }

    fun clearCustomBitmapCache() {
        bitmapCache.remove("custom_back")
        bitmapCache.remove("custom_home")
        bitmapCache.remove("custom_recent")
    }

    fun setCustomBitmapBase64(type: String, base64: String) {
        val cacheKey = "custom_$type"
        if (base64.isEmpty()) {
            bitmapCache.remove(cacheKey)
            return
        }
        val bmp = decodeBase64Bitmap(base64)
        if (bmp != null) {
            bitmapCache[cacheKey] = bmp
        } else {
            bitmapCache.remove(cacheKey)
        }
    }

    fun createSquareBitmap(source: Bitmap, targetSize: Int = 120): Bitmap {
        val maxSide = Math.max(source.width, source.height).coerceAtLeast(1)
        val squareBmp = Bitmap.createBitmap(maxSide, maxSide, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(squareBmp)
        val left = (maxSide - source.width) / 2.0f
        val top = (maxSide - source.height) / 2.0f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(source, left, top, paint)
        val scaled = Bitmap.createScaledBitmap(squareBmp, targetSize, targetSize, true)
        if (scaled !== squareBmp) {
            squareBmp.recycle()
        }
        scaled.density = DisplayMetrics.DENSITY_XXHIGH
        return scaled
    }

    fun bitmapToBase64(bmp: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeBase64Bitmap(base64Str: String?): Bitmap? {
        if (base64Str.isNullOrEmpty()) return null
        return runCatching {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val opts = BitmapFactory.Options().apply {
                inDensity = DisplayMetrics.DENSITY_XXHIGH
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.apply {
                density = DisplayMetrics.DENSITY_XXHIGH
            }
        }.getOrNull()
    }

    fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        reqWidth: Int = 240,
        reqHeight: Int = 240
    ): Bitmap? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty()) return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            var sampleSize = 1
            while (options.outWidth / (sampleSize * 2) >= reqWidth &&
                options.outHeight / (sampleSize * 2) >= reqHeight
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

            val degrees = runCatching {
                val exif = ExifInterface(ByteArrayInputStream(bytes))
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)

            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                if (rotated !== rawBmp) {
                    rawBmp.recycle()
                }
                rotated
            } else {
                rawBmp
            }
        }.getOrNull()
    }

    private fun loadBitmapFromZip(
        apkPath: String,
        cacheKey: String,
        type: String,
        safeIndex: Int,
        logger: (String, Throwable?) -> Unit
    ): Bitmap? {
        val entryName = "$ASSETS_PATH/preset_${type}_$safeIndex.png"
        val bitmap = runCatching {
            ZipFile(apkPath).use { zip ->
                val entry = zip.getEntry(entryName) ?: run {
                    logger("Preset asset missing: $entryName", null)
                    return null
                }
                zip.getInputStream(entry).use { stream ->
                    val opts = BitmapFactory.Options().apply {
                        inDensity = DisplayMetrics.DENSITY_XXHIGH
                    }
                    BitmapFactory.decodeStream(stream, null, opts)?.also {
                        it.density = DisplayMetrics.DENSITY_XXHIGH
                    }
                }
            }
        }.getOrElse {
            logger("Reading the preset image threw: $entryName", it)
            null
        }

        if (bitmap != null) {
            bitmapCache[cacheKey] = bitmap
            logger("Cached the preset icon: $cacheKey (${bitmap.width}x${bitmap.height})", null)
        }
        return bitmap
    }

    private val unsafe: Any? by lazy {
        runCatching {
            val f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
            f.isAccessible = true
            f.get(null)
        }.getOrNull()
    }

    private fun allocateShadowDrawableState(stateClass: Class<*>, module: XposedModule? = null): Any? {
        val fromCtor = runCatching {
            stateClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }.getOrNull()
        if (fromCtor != null) return fromCtor

        return runCatching {
            val u = unsafe ?: run {
                module?.log(Log.WARN, TAG, "fail closed: no Unsafe instance, cannot allocate ShadowDrawableState")
                return null
            }
            val method = u.javaClass.getMethod("allocateInstance", Class::class.java)
            method.invoke(u, stateClass)
        }.getOrElse {
            module?.log(Log.WARN, TAG, "allocateInstance failed", it)
            null
        }
    }

    fun createKeyButtonDrawable(
        module: XposedModule,
        context: Context,
        template: Any,
        bitmap: Bitmap,
        tintMode: Int,
        tintLightColor: Int = SystemUiConfig.DEFAULT_TINT_LIGHT_COLOR,
        tintDarkColor: Int = SystemUiConfig.DEFAULT_TINT_DARK_COLOR,
        handles: SystemUiHandles
    ): Any? {
        return runCatching {
            val templateState = (template as? Drawable)?.constantState ?: run {
                module.log(Log.WARN, TAG, "fail closed: template.constantState is null: template=$template")
                return null
            }
            val stateClass = handles.shadowDrawableStateClass ?: return null

            val newState = allocateShadowDrawableState(stateClass, module) ?: run {
                module.log(Log.WARN, TAG, "fail closed: cannot allocate a ShadowDrawableState instance")
                return null
            }

            fun copyField(fieldName: String) {
                runCatching {
                    val f = stateClass.getDeclaredField(fieldName).apply { isAccessible = true }
                    f.set(newState, f.get(templateState))
                }.onFailure {
                    module.log(Log.WARN, TAG, "copyField $fieldName failed: ${it.message}")
                }
            }

            copyField("mShadowOffsetX")
            copyField("mShadowOffsetY")
            copyField("mShadowSize")
            copyField("mShadowColor")
            copyField("mDarkIntensity")

            if (tintMode == 1) {
                runCatching {
                    stateClass.getDeclaredField("mLightColor").apply { isAccessible = true }.setInt(newState, tintDarkColor)
                }.onFailure {
                    module.log(Log.WARN, TAG, "set mLightColor failed: ${it.message}")
                }
                runCatching {
                    stateClass.getDeclaredField("mDarkColor").apply { isAccessible = true }.setInt(newState, tintLightColor)
                }.onFailure {
                    module.log(Log.WARN, TAG, "set mDarkColor failed: ${it.message}")
                }
            } else {
                copyField("mLightColor")
                copyField("mDarkColor")
            }

            runCatching {
                stateClass.getDeclaredField("mAlpha").apply { isAccessible = true }.set(newState, 255)
            }
            runCatching {
                stateClass.getDeclaredField("mSupportsAnimation").apply { isAccessible = true }.set(newState, false)
            }
            runCatching {
                stateClass.getDeclaredField("mHorizontalFlip").apply { isAccessible = true }.set(newState, false)
            }
            runCatching {
                stateClass.getDeclaredField("mLastDrawnIcon").apply { isAccessible = true }.set(newState, null)
            }

            val ctor = handles.keyButtonDrawableCtor ?: run {
                module.log(Log.WARN, TAG, "fail closed: keyButtonDrawableCtor is null")
                return null
            }
            val keyButtonCls = handles.keyButtonDrawableClass ?: run {
                module.log(Log.WARN, TAG, "fail closed: keyButtonDrawableClass is null")
                return null
            }
            val innerDrawable = BitmapDrawable(context.resources, bitmap)
            val customDrawable = ctor.newInstance(innerDrawable, newState)

            val shadowSize = runCatching {
                stateClass.getDeclaredField("mShadowSize").apply { isAccessible = true }.getInt(newState)
            }.getOrDefault(0)
            val shadowColor = runCatching {
                stateClass.getDeclaredField("mShadowColor").apply { isAccessible = true }.getInt(newState)
            }.getOrDefault(0)
            if (shadowSize > 0) {
                runCatching {
                    val shadowPaint = keyButtonCls.getDeclaredField("mShadowPaint").apply { isAccessible = true }.get(customDrawable) as? Paint
                    shadowPaint?.colorFilter = PorterDuffColorFilter(shadowColor, PorterDuff.Mode.SRC_ATOP)
                }
            }

            val currentDarkIntensity = runCatching {
                stateClass.getDeclaredField("mDarkIntensity").apply { isAccessible = true }.getFloat(newState)
            }.getOrDefault(0f)

            val initialColor = if (tintMode == 1) {
                android.animation.ArgbEvaluator()
                    .evaluate(currentDarkIntensity, tintDarkColor, tintLightColor) as Int
            } else {
                val nativeLightColor = runCatching {
                    stateClass.getDeclaredField("mLightColor").apply { isAccessible = true }.getInt(newState)
                }.getOrDefault(SystemUiConfig.DEFAULT_TINT_DARK_COLOR)
                val nativeDarkColor = runCatching {
                    stateClass.getDeclaredField("mDarkColor").apply { isAccessible = true }.getInt(newState)
                }.getOrDefault(SystemUiConfig.DEFAULT_TINT_LIGHT_COLOR)
                android.animation.ArgbEvaluator()
                    .evaluate(currentDarkIntensity, nativeLightColor, nativeDarkColor) as Int
            }

            runCatching {
                val iconPaint = keyButtonCls.getDeclaredField("mIconPaint").apply { isAccessible = true }.get(customDrawable) as? Paint
                iconPaint?.colorFilter = PorterDuffColorFilter(initialColor, PorterDuff.Mode.SRC_ATOP)
            }

            module.log(
                Log.INFO,
                TAG,
                "Built KeyButtonDrawable: tintMode=$tintMode initialColor=${String.format("#%08X", initialColor)} darkIntensity=$currentDarkIntensity"
            )
            customDrawable
        }.getOrElse {
            module.log(Log.ERROR, TAG, "fail closed: building the custom KeyButtonDrawable threw", it)
            null
        }
    }
}
