package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.View.MeasureSpec
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs

/**
 * Custom background overlay layer (Layer 0) injected into Xiaobu Input Method's
 * keyboard View direct parent composite (M0 / FrameLayout).
 *
 * Sits directly between ColorOS liquid glass (or host theme) and the interactive keys,
 * allowing customizable translucent background wallpaper with zero touch interference.
 */
class RealOSBgOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()

    /** Region drawn this frame (local coordinates), computed by onDraw each frame. */
    private val clipRect = Rect()

    private var cachedBitmap: Bitmap? = null
    private var cachedFileStamp: Long = -1L
    private var cachedIsNight: Boolean? = null

    /**
     * true when this overlay is attached to the shared host (covering toolbar/candidate/body/bottom);
     * the size then comes from the host's real measure spec, with no screen-size fallback.
     */
    var hostOverlay: Boolean = false

    /**
     * true when drawing **clips to the current IME visible area**.
     *
     * The host hierarchy contains window-level containers stretched to full-screen height
     * (`mFrame` and others); attached to a host larger than the IME, MATCH_PARENT alone does
     * not keep the drawing inside the IME. With this switch on, onDraw reads the IME's local
     * rect inside the host from [RealOSImeBounds], clips the canvas to it, then draws the
     * image fitCenter inside that rect — the overlay is visible only within the IME.
     *
     * A missing IME rect counts as "IME position undetermined" and drawing is skipped
     * (fail-closed).
     */
    var clipToIme: Boolean = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun clearCache() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedFileStamp = -1L
        cachedIsNight = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Follows the host's laid-out size; no screen-size or AT_MOST spec fallback.
        // - If the parent host is laid out (getWidth/getHeight > 0), use it directly;
        // - otherwise fall back to the parent's EXACTLY spec size;
        // - otherwise measure as 0 (no draw); the outer layer re-triggers attach after the
        //   host is laid out.
        val parentView = parent as? View
        val laidW = parentView?.width ?: 0
        val laidH = parentView?.height ?: 0

        val w = when {
            laidW > 0 -> laidW
            MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            else -> 0
        }
        val h = when {
            laidH > 0 -> laidH
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> 0
        }
        setMeasuredDimension(w, h)
    }

    private fun resolveActiveNightMode(): Boolean {
        // 1. Check host explicit day/night preference
        val hostDayNightMode = runCatching {
            val prefs = context.getSharedPreferences("keyboard_setting", Context.MODE_PRIVATE)
            prefs.getInt("key_day_night_mode", -1)
        }.getOrDefault(-1)
        if (hostDayNightMode == 1) return true
        if (hostDayNightMode == 0) return false
        // 2. Fallback to system uiMode
        return CouiComponentFactory.isNightMode(context)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Custom background serves liquid glass mode
        if (!OplusInputPrefs.isLiquidGlassEnabled(context)) {
            return
        }

        val isNight = resolveActiveNightMode()

        val currentHasImage = OplusInputPrefs.hasBgImage(context, isNight)
        val otherHasImage = OplusInputPrefs.hasBgImage(context, !isNight)

        val currentEnabled = if (isNight) OplusInputPrefs.isDarkBgOverlayEnabled(context) else OplusInputPrefs.isLightBgOverlayEnabled(context)
        val otherEnabled = if (isNight) OplusInputPrefs.isLightBgOverlayEnabled(context) else OplusInputPrefs.isDarkBgOverlayEnabled(context)

        val effectiveNight = when {
            currentEnabled && currentHasImage -> isNight
            otherEnabled && otherHasImage -> !isNight
            currentHasImage -> isNight
            otherHasImage -> !isNight
            else -> return
        }

        val alphaFloat = if (effectiveNight) {
            OplusInputPrefs.getDarkBgOverlayAlpha(context)
        } else {
            OplusInputPrefs.getLightBgOverlayAlpha(context)
        }
        if (alphaFloat <= 0.001f) {
            return
        }

        val bitmap = getOrLoadBitmap(effectiveNight) ?: return
        if (bitmap.isRecycled || width <= 0 || height <= 0) {
            return
        }

        // Decides this frame's drawable region: the whole View by default, shrunk to the IME's
        // local rect inside the host when clipToIme is on.
        val drawLeft: Int
        val drawTop: Int
        val drawRight: Int
        val drawBottom: Int
        if (clipToIme) {
            if (!RealOSImeBounds.imeRectInHost(this, clipRect)) {
                // IME area undetermined -> fail closed, no draw, nothing outside the IME.
                return
            }
            // imeRectInHost returns coordinates relative to the host; this View matches the
            // host's size and origin (MATCH_PARENT, no margin), which makes that rect this
            // View's local coordinate.
            drawLeft = clipRect.left
            drawTop = clipRect.top
            drawRight = clipRect.right
            drawBottom = clipRect.bottom
        } else {
            drawLeft = 0
            drawTop = 0
            drawRight = width
            drawBottom = height
        }

        val regionW = (drawRight - drawLeft).coerceAtLeast(0)
        val regionH = (drawBottom - drawTop).coerceAtLeast(0)
        if (regionW <= 0 || regionH <= 0) return

        // fitCenter: scales the image uniformly to fit, centers it in the drawable region, no cropping.
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0) return

        srcRect.set(0, 0, bw, bh)

        val scale = minOf(regionW.toFloat() / bw, regionH.toFloat() / bh)
        val scaledW = bw * scale
        val scaledH = bh * scale
        val dx = drawLeft + (regionW - scaledW) / 2f
        val dy = drawTop + (regionH - scaledH) / 2f

        dstRect.set(dx, dy, dx + scaledW, dy + scaledH)

        paint.alpha = (alphaFloat * 255).toInt().coerceIn(0, 255)

        val save = canvas.save()
        // Hard clip: even when dstRect goes slightly out of bounds from float or parent-container
        // rounding, nothing draws outside the IME area.
        canvas.clipRect(drawLeft.toFloat(), drawTop.toFloat(), drawRight.toFloat(), drawBottom.toFloat())
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        canvas.restoreToCount(save)
    }

    private fun getOrLoadBitmap(isNight: Boolean): Bitmap? {
        val file = OplusInputPrefs.getBgImageFile(context, isNight)
        val targetFile = if (file.exists()) {
            file
        } else {
            val fallbackFile = OplusInputPrefs.getBgImageFile(context, !isNight)
            if (fallbackFile.exists()) fallbackFile else null
        } ?: return null

        val lastModified = targetFile.lastModified()
        val currentBmp = cachedBitmap
        if (currentBmp != null && !currentBmp.isRecycled && cachedFileStamp == lastModified && cachedIsNight == isNight) {
            return currentBmp
        }

        return runCatching {
            cachedBitmap?.recycle()
            cachedBitmap = null

            // First decode bounds to compute sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(targetFile.absolutePath, options)

            var sampleSize = 1
            val maxDim = 1080
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(targetFile.absolutePath, decodeOpts)
            cachedBitmap = bmp
            cachedFileStamp = lastModified
            cachedIsNight = isNight
            bmp
        }.getOrNull()
    }
}
