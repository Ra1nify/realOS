package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class OplusPerKeyTarget(
    val entity: Any,
    val localBounds: RectF
)

/**
 * Rendering engine for Samsung Keys Cafe visual effects.
 *
 * Implements:
 * 1. Multi-touch: each simultaneous finger tap gets its own effect instance.
 * 2. Board Blending: RING_SPREAD, BLOB_SPREAD, SPARK, OUTLINE.
 * 3. Key Blending: COLOR_FLASH, OUTLINE, BLOB, BLOB_SPREAD.
 * 4. Key Motion: SPRING_DOWN, SPRING_UP, BOUNCE_UP, BOUNCE_TOUCH, TILT.
 * 5. Samsung color preset palette cycling.
 * 6. onDraw allocates nothing: paints, matrices, paths, and math structs are reused.
 */
class OplusKeyboardEffectController {

    private companion object {
        // ─────────────────────────────────────────────────────────────────────
        // Samsung Keys Cafe animation primitives (1:1, source in each effector file)
        // ─────────────────────────────────────────────────────────────────────

        /** `AnimatorInterpolators.SINE_IN_OUT80 = PathInterpolator(0.17f, 0.17f, 0.2f, 1.0f)`. */
        val SINE_IN_OUT80 = PathInterpolator(0.17f, 0.17f, 0.2f, 1.0f)

        /** `KeyColorFlashEffector` uses `new DecelerateInterpolator()`. */
        val DECELERATE = DecelerateInterpolator()

        /**
         * Every Samsung effector stores `ValueAnimator.getAnimatedFraction()` as `animationFraction`.
         * Android's `getAnimatedFraction()` returns the **interpolated** fraction. The host here has
         * no ValueAnimator; these helpers apply that interpolator to linear progress.
         */
        fun afSine(linear: Float): Float = SINE_IN_OUT80.getInterpolation(linear)
        fun afDecel(linear: Float): Float = DECELERATE.getInterpolation(linear)

        /** `LinearDecreaseAlpha.getAlpha(af, coef) = (int)((1 - af) * coef)`. */
        fun linearDecreaseAlpha(af: Float, coefficient: Int): Int =
            ((1f - af) * coefficient).toInt().coerceIn(0, 255)

        /** `LinearIncreaseAlpha.getAlpha(af, coef) = (int)(af * coef)`. */
        fun linearIncreaseAlpha(af: Float, coefficient: Int): Int =
            (af * coefficient).toInt().coerceIn(0, 255)

        /**
         * `BlobSpreadAlpha.getAlpha(af, coef)` (coef ignored):
         * af<0.3 → mapRangeToRange(af,0,0.3,0,1)*255; otherwise mapRangeToRange(af,0.3,1,1,0)*255.
         */
        fun blobSpreadAlpha(af: Float): Int {
            val t = if (af < 0.3f) {
                (af - 0f) / (0.3f - 0f)
            } else {
                1f + (0f - 1f) * ((af - 0.3f) / (1f - 0.3f))
            }
            return (t * 255f).toInt().coerceIn(0, 255)
        }

        /**
         * `PaintGunAlpha.getAlpha(af, coef)` (coef ignored):
         * af<0.3 → 255; otherwise `mapRangeToRange(af,0.3,1,1,0)*255` (linear decay to 0).
         */
        fun paintGunAlpha(af: Float): Int {
            if (af < 0.3f) return 255
            val t = 1f + (0f - 1f) * ((af - 0.3f) / (1f - 0.3f))
            return (t * 255f).toInt().coerceIn(0, 255)
        }

        // ── Durations (original setDuration values per effector) ──
        const val DURATION_KEY_FLASH = 1000L      // KeyColorFlashEffector
        const val DURATION_KEY_OUTLINE = 1200L    // KeyOutlineEffector
        const val DURATION_KEY_BLOB = 1200L       // KeyBlobEffector
        const val DURATION_KEY_SPREAD = 1200L     // KeyBlobSpreadEffector

        const val DURATION_BOARD_RING = 1800L     // BoardRingSpreadEffector
        const val DURATION_BOARD_BLOB = 1500L     // BoardBlobSpreadEffector
        const val DURATION_BOARD_SPARK = 2000L    // BoardSparkEffector
        const val DURATION_BOARD_OUTLINE = 1000L  // BoardOutlineEffector
        const val DURATION_BOARD_BLOB_STATIC = 1500L    // BoardBlobEffector
        const val DURATION_BOARD_SPARK_SPIN = 2000L     // BoardSparkSpinEffector
        const val DURATION_BOARD_PAINT_GUN = 2000L      // BoardPaintGunEffector
        const val DURATION_BOARD_FIRE_CRACKER = 1500L   // BoardFireCrackerEffector
        const val MAX_MOTION_REDRAW_FAILURES = 8
        const val MOTION_MAX_LIFETIME_MS = 600L

        // ── KeyOutlineEffector resources ──
        /** `honey_tea_key_outline_effect_stroke_width` = 6dp. */
        const val KEY_OUTLINE_STROKE_DP = 6f
        /** `honey_tea_corner_radius` / `honeytea_key_effect_outline` corner radius = 8dp. */
        const val KEY_OUTLINE_CORNER_DP = 8f
        /** Key background corner radius (equivalent of setClipToOutline clipping) = 8dp. */
        const val KEY_CLIP_CORNER_DP = 8f

        // ── KeyBlobEffector / KeyBlobSpreadEffector constants ──
        const val KEY_BLOB_DOT_COUNT = 5
        const val KEY_BLOB_DOT_SCALE = 0.8f          // KeyBlobEffector.DOT_SCALE
        const val KEY_BLOB_SPREAD_DOT_SCALE = 1.5f   // KeyBlobSpreadEffector.DOT_SCALE
        const val KEY_BLOB_MOTION_SCALE = 1.5f       // MOTION_SCALE (both use 1.5)
        const val KEY_BLOB_MOTION_FREQUENCY = 0.2f   // MOTION_POSITION_FREQUENCY
        const val KEY_BLOB_ALPHA = 255               // KeyBlobEffector alpha coefficient
        const val KEY_BLOB_SPREAD_ALPHA = 125        // KeyBlobSpreadEffector alpha coefficient
        const val KEY_BLOB_BG_LIGHTER = 0.3f         // ColorUtil.lighter(randomColor, 0.3f)

        // ── KeySparkEffector (key-layer SPARK_SPIN) constants ──
        /** `DOTS_COUNT = 3` (board SPARK uses 5). */
        const val KEY_SPARK_DOTS_COUNT = 3
        /** `DOT_ANGLE = 120` (board SPARK uses 72). */
        const val KEY_SPARK_DOT_ANGLE = 120
        /** `ANIM_DURATION = 1200`. */
        const val DURATION_KEY_SPARK_SPIN = 1200L
        /** `WIDTH_FROM_SCALE = 0.3`: `fromDotRadius = max(keyW, keyH) * 0.3`. */
        const val KEY_SPARK_FROM_SCALE = 0.3f
        /** `WIDTH_TO_RELATIVE_SCALE = 0.5`: `toDotRadius = fromDotRadius * 0.5`. */
        const val KEY_SPARK_TO_RELATIVE_SCALE = 0.5f
        /** `MAX_DOT_ITSELF_ROTATION = 90`: per-particle self-rotation cap. */
        const val KEY_SPARK_MAX_DOT_ITSELF_ROTATION = 90f
        /** `MAX_DOT_LOC_ROTATION = 45`: orbit angle rotation cap. */
        const val KEY_SPARK_MAX_DOT_LOC_ROTATION = 45f
        /** `rectCornerR = dp(5)` (board SPARK uses dp(10)). */
        const val KEY_SPARK_CORNER_DP = 5f
        /** `fromRotateZ = randomRange(0, 90)`. */
        const val KEY_SPARK_FROM_ROTATE_Z_MAX = 90f
        /** `iS = randomRange(0, 150)` (board SPARK uses 0..90). */
        const val KEY_SPARK_ANGLE_DELTA_MAX = 150f

        // ── KeyFootPrintEffector constants ──
        /** `ANIM_DURATION = 2000`. */
        const val DURATION_KEY_FOOT_PRINT = 2000L
        /** Coefficient of `LinearDecreaseAlpha(af, 175)`. */
        const val KEY_FOOT_PRINT_ALPHA = 175
        /** Icon side length = `(int)(min(keyW, keyH) * 0.75f)`. */
        const val KEY_FOOT_PRINT_SCALE = 0.75f
        /** Rotation angle `randomRange(-30, 30)` (degrees). */
        const val KEY_FOOT_PRINT_ROTATE_MAX = 30f

        // ── BoardBlobSpreadEffector constants ──
        const val BOARD_BLOB_DOT_SCALE = 0.3f        // constructor param dotScale
        const val BOARD_BLOB_MOTION_SCALE = 0.7f     // constructor param motionScale
        const val BOARD_BLOB_MOTION_FREQUENCY = 0.2f // MOTION_POSITION_FREQUENCY
        const val BOARD_BLOB_DOT_COUNT = 3           // DOT_COUNT

        // ── BoardSparkEffector constants (dp, converted by g(dp)=(int)(dp*density)) ──
        const val BOARD_SPARK_ORBIT_DP = 100f        // TO_RADIUS
        const val BOARD_SPARK_DOT_START_DP = 30f     // fromDotRadius (half side length)
        const val BOARD_SPARK_DOT_END_DP = 15f       // toDotRadius (half side length)
        const val BOARD_SPARK_CORNER_DP = 10f        // rectCornerR
        const val BOARD_SPARK_DOTS_COUNT = 5
        const val BOARD_SPARK_DOT_ANGLE = 72
        const val BOARD_SPARK_ALPHA = 125

        // ── BoardBlobEffector constants ──
        /** Constructor defaults (`bindEffector` passes mask=6 -> both take defaults): dotScale=0.3, motionScale=0.5. */
        const val BOARD_BLOB_STATIC_DOT_SCALE = 0.3f
        const val BOARD_BLOB_STATIC_MOTION_SCALE = 0.5f
        const val BOARD_BLOB_STATIC_MOTION_FREQUENCY = 0.2f
        const val BOARD_BLOB_STATIC_DOT_COUNT = 5
        /** Constructor sets `paint.setAlpha(125)`; runtime `LinearIncreaseAlpha(af,255)` overrides it. */
        const val BOARD_BLOB_STATIC_ALPHA = 255

        // ── BoardSparkSpinEffector constants ──
        /** `iS = randomRange(0, 360)` (SPARK uses 0..90). */
        const val BOARD_SPARK_SPIN_ANGLE_MAX = 360f

        // ── BoardPaintGunEffector constants ──
        /** Constructor defaults: minScale=0.3, maxScale=0.7 (`bindEffector` passes mask=12 -> both take defaults). */
        const val BOARD_PAINT_GUN_MIN_SCALE = 0.3f
        const val BOARD_PAINT_GUN_MAX_SCALE = 0.7f
        /** Whole-bitmap rotation angle from `randomRange(-100, 100)` (degrees). */
        const val BOARD_PAINT_GUN_ROTATE_MAX = 100f
        /** Base bitmap side-length factor (`getRect`: `desSize = min(w,h) * randomFloat(min,max)`). */
        const val BOARD_PAINT_GUN_START_FRACTION = 0.1f
        val BOARD_PAINT_GUN_DRAWABLES = intArrayOf(
            R.drawable.ic_paint_gun_1,
            R.drawable.ic_paint_gun_2,
            R.drawable.ic_paint_gun_3,
            R.drawable.ic_paint_gun_4
        )

        // ── BoardFireCrackerEffector constants ──
        /** Coefficient of `LinearDecreaseAlpha(af, 175)`. */
        const val BOARD_FIRE_CRACKER_ALPHA = 175
        /** Fuse-phase radius coefficient: `f2 = af * 80` (raw px, **density not applied**). */
        const val BOARD_FIRE_CRACKER_FUSE_RADIUS = 80f
        /** Draws the center circle (fuse) when `f2 < 15`, otherwise bursts in 8 directions. */
        const val BOARD_FIRE_CRACKER_FUSE_THRESHOLD = 15f
        /** Animation start fraction (`ValueAnimator.ofFloat(0.1f, 1.0f)`). */
        const val BOARD_FIRE_CRACKER_START_FRACTION = 0.1f
        /** 8 burst directions (degrees). */
        val BOARD_FIRE_CRACKER_ANGLES = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
        /**
         * Firework particle size: Samsung's `fire_cracker` drawable is a 6dp x 6dp solid oval
         * (`res/drawable/fire_cracker.xml`: shape=oval, solid=black, size=6dp) tinted with
         * `PorterDuffColorFilter(random color, SRC_IN)`. The value here is 6dp.
         */
        const val BOARD_FIRE_CRACKER_DOT_DP = 6f

        /**
         * Samsung `ColorUtil.lighter(color, factor)`:
         * `argb(255, (int)(((R*(1-f))/255 + f)*255), ...)` with alpha fixed at 255.
         */
        fun lighter(color: Int, factor: Float): Int {
            val inverse = 1f - factor
            val r = ((((Color.red(color) * inverse) / 255f) + factor) * 255f).toInt().coerceIn(0, 255)
            val g = ((((Color.green(color) * inverse) / 255f) + factor) * 255f).toInt().coerceIn(0, 255)
            val b = ((((Color.blue(color) * inverse) / 255f) + factor) * 255f).toInt().coerceIn(0, 255)
            return Color.argb(255, r, g, b)
        }

        /** Samsung `p122p7.AbstractC0482m.g(dp) = (int)(dp * density)`. */
        fun dp(value: Float, density: Float): Float = (value * density).toInt().toFloat()

        /**
         * Samsung `MathUtil.randomRange(min, max) = rand.nextInt((int)max - (int)min + 1) + (int)min`.
         * Returns an integer from that range as a float.
         */
        fun randomRangeInt(min: Float, max: Float): Float {
            val lo = min.toInt()
            val hi = max.toInt()
            return (lo + Random.nextInt(hi - lo + 1)).toFloat()
        }

        /** Samsung EffectColorProvider: fixed palettes choose one pool entry; RANDOM uses RGB 100..255. */
        fun pickSamsungEffectColor(palette: IntArray): Int {
            if (palette.isNotEmpty()) {
                return palette[Random.nextInt(palette.size)]
            }
            return Color.rgb(
                Random.nextInt(100, 256),
                Random.nextInt(100, 256),
                Random.nextInt(100, 256)
            )
        }
    }

    // Pre-allocated reusable Paint objects
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }
    // Reusable matrices and paths
    private val tempMatrix = Matrix()
    private val tempPath = Path()
    private val tempRect = RectF()
    private val pathMeasure = PathMeasure()

    /** The four PAINT_GUN vectors rasterized at intrinsic size on first use, then cached. */
    private val paintGunBitmaps = arrayOfNulls<Bitmap>(BOARD_PAINT_GUN_DRAWABLES.size)
    private var paintGunResourcesUnavailable = false
    private var fireCrackerBitmap: Bitmap? = null
    private var fireCrackerResourcesUnavailable = false

    /** Reused rounded-corner clip path for each key effect. */
    private val keyClipPath = Path()

    // Color counter for palette cycling
    private val colorIndexCounter = AtomicInteger(0)

    // Active animation collections
    private val activeBoardEffects = CopyOnWriteArrayList<BoardEffect>()
    private val activeKeyEffects = CopyOnWriteArrayList<KeyEffect>()
    private val activeMotions = CopyOnWriteArrayList<MotionEffect>()

    private var viewRef: WeakReference<View>? = null
    private var motionDrawFrameTimeMs: Long? = null
    private var motionFramePosted = false
    private var motionRedrawFailureCount = 0
    private val motionFrameRunnable = Runnable {
        motionFramePosted = false
        val view = viewRef?.get() ?: return@Runnable
        if (activeMotions.isEmpty()) {
            motionRedrawFailureCount = 0
            return@Runnable
        }

        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
            if (view.isAttachedToWindow) {
                view.invalidate()
            }
            activeMotions.clear()
            motionRedrawFailureCount = 0
            return@Runnable
        }
        val provider = perKeyBoundsProvider ?: run {
            activeMotions.clear()
            motionRedrawFailureCount = 0
            return@Runnable
        }

        val now = SystemClock.uptimeMillis()
        val hadMotions = activeMotions.isNotEmpty()
        val finishedEntities = ArrayList<Any>()
        activeMotions.removeAll { motion ->
            val finished = (now - motion.startTimeMs > MOTION_MAX_LIFETIME_MS) || (motion.isReleased && motion.isAtRest(now))
            if (finished) {
                finishedEntities.add(motion.entity)
            }
            finished
        }

        for (entity in finishedEntities) {
            provider.requestRedraw(view, entity)
        }

        if (activeMotions.isEmpty()) {
            if (hadMotions && finishedEntities.isEmpty()) {
                provider.requestRedraw(view, null)
            }
            if (hadMotions) {
                (view.parent as? View)?.invalidate()
            }
            motionRedrawFailureCount = 0
            return@Runnable
        }

        val motions = activeMotions.toList()
        var anySuccess = false
        for (motion in motions) {
            if (provider.requestRedraw(view, motion.entity)) {
                anySuccess = true
            }
        }
        if (!anySuccess) {
            motionRedrawFailureCount++
            view.invalidate()
            if (motionRedrawFailureCount < MAX_MOTION_REDRAW_FAILURES) {
                scheduleMotionFrame(view)
            } else {
                activeMotions.clear()
                motionRedrawFailureCount = 0
            }
            return@Runnable
        }
        motionRedrawFailureCount = 0
        scheduleMotionFrame(view)
    }

    /**
     * FOOT_PRINT icon bitmap cache: `(iconKey, size, bitmap)`.
     *
     * Samsung calls `createScaledBitmap` on every press; here one entry per `(icon, size)`
     * is kept and reused across redraws. A failed render is not cached (fail-closed).
     * iconKey is part of the cache key, so switching icons invalidates the entry.
     */
    private var footPrintCache: Triple<String, Int, Bitmap>? = null

    /**
     * Per-key key-face provider. Returns the key rect in keyboardView local coordinates, or
     * null when it cannot be resolved (not ready / empty hit / invalid coordinate frame);
     * callers then fail closed. Injected after symbol resolution in the scan/install phase;
     * callbacks run no DexKit queries and no reflection scans.
     */
    fun interface PerKeyBoundsProvider {
        fun boundsFor(keyboardView: View, localX: Float, localY: Float): RectF?
        fun targetFor(keyboardView: View, localX: Float, localY: Float): OplusPerKeyTarget? = null
        /**
         * Checks whether a host draw-pass entity represents the same key geometry.
         *
         * The host can expose different entity instances to the key renderer and the
         * keyboard-level shadow passes. Identity-only matching leaves the shadow bitmap
         * at its original position in that case.
         */
        fun matchesBounds(entity: Any, bounds: RectF): Boolean = false
        /**
         * The live key entity at this geometry; null when it cannot be determined. Callers
         * treat null as "unknown" and keep the motion.
         *
         * The host rebuilds the whole `softKeyboard` key list on CN/EN or number/symbol
         * switches, invalidating old entity objects. The geometry fallback ([matchesBounds])
         * still matches a new entity at the same position to the old motion target, so the
         * bake skips it and leaves a hole; the host's `D(entity)` finds no old entity in the
         * new list, the per-key redraw becomes a no-op, and the hole persists.
         * Call this before each bake frame and drop the motion when the target is gone.
         */
        fun entityAt(keyboardView: View, localX: Float, localY: Float): Any? = null
        fun supportsMotion(): Boolean = false
        fun requestRedraw(keyboardView: View): Boolean = false
        fun requestRedraw(keyboardView: View, entity: Any?): Boolean = requestRedraw(keyboardView)
    }

    private var perKeyBoundsProvider: PerKeyBoundsProvider? = null

    /**
     * Sets the per-key key-face provider; null means unavailable (fail-closed, key blending disabled).
     */
    fun setPerKeyBoundsProvider(provider: PerKeyBoundsProvider?) {
        perKeyBoundsProvider = provider
    }

    fun beginKeyboardDrawFrame(keyboardView: View? = null) {
        motionDrawFrameTimeMs = SystemClock.uptimeMillis()
        if (keyboardView != null) {
            pruneStaleMotions(keyboardView)
        }
    }

    /**
     * Drops stale motions: key entity rebuilt by the host, or past the absolute lifetime.
     *
     * Runs **before** the keyboard `onDraw` bake (this class executes ahead of
     * `chain.proceed()` in the hook), so the same frame's full redraw paints those keys
     * back at rest and leaves no hole.
     *
     * One hit query per active motion, independent of key count. A null entity (not
     * determinable) keeps the motion instead of treating it as stale.
     *
     * A dropped motion whose **entity is still valid** (typically ACTION_UP not delivered,
     * spring left displaced) gets one extra per-key redraw to paint the key back at rest.
     */
    private fun pruneStaleMotions(keyboardView: View) {
        if (activeMotions.isEmpty()) return
        val provider = perKeyBoundsProvider ?: return
        val now = motionDrawFrameTimeMs ?: SystemClock.uptimeMillis()
        val needsRepair = ArrayList<Any>()
        val drop = ArrayList<MotionEffect>()
        for (motion in activeMotions) {
            if (!isMotionLive(motion, now)) {
                // Past the absolute lifetime (typically ACTION_UP not delivered, spring left
                // displaced). The entity may still be valid, so the drop repaints it at rest.
                drop.add(motion)
                needsRepair.add(motion.entity)
                continue
            }
            val current = runCatching {
                provider.entityAt(
                    keyboardView,
                    motion.keyBounds.centerX(),
                    motion.keyBounds.centerY()
                )
            }.getOrNull()
            if (current != null && current !== motion.entity) {
                // Entity rebuilt by the host: the old object no longer belongs to the current
                // layout and would draw a corrupted key. One per-key redraw with the **current
                // new entity** re-bakes this position at rest and fills the skipped hole.
                drop.add(motion)
                needsRepair.add(current)
            }
        }
        if (drop.isEmpty()) return
        activeMotions.removeAll(drop)
        for (entity in needsRepair) {
            runCatching { provider.requestRedraw(keyboardView, entity) }
        }
    }

    fun endKeyboardDrawFrame() {
        motionDrawFrameTimeMs = null
    }

    fun partitionMotionEntities(
        source: List<*>,
        inactive: MutableList<Any?>,
        active: MutableList<Any?>
    ): Boolean {
        inactive.clear()
        active.clear()
        if (activeMotions.isEmpty()) return false
        val now = motionDrawFrameTimeMs ?: SystemClock.uptimeMillis()
        for (entity in source) {
            if (entity != null && activeMotions.any { isSameMotionEntity(it, entity) && isMotionLive(it, now) }) {
                active.add(entity)
            } else {
                inactive.add(entity)
            }
        }
        return active.isNotEmpty()
    }

    fun isMotionActive(entity: Any): Boolean {
        if (activeMotions.isEmpty()) return false
        val now = motionDrawFrameTimeMs ?: SystemClock.uptimeMillis()
        return activeMotions.any { isSameMotionEntity(it, entity) && isMotionLive(it, now) }
    }

    /**
     * Whether a motion is still in effect (**with an absolute lifetime fallback**).
     *
     * Animation frames are not guaranteed: `motionFramePosted` stuck at true, the view
     * detached from the window, or a dropped `postOnAnimation` callback all stop
     * [motionFrameRunnable]. `activeMotions` is then never cleaned while the "skip bake"
     * path only consults this method, leaving that key out of the host buffer permanently
     * (it disappears until the layout is rebuilt).
     *
     * The check ignores whether cleanup ran and uses `startTimeMs`: any motion older than
     * [MOTION_MAX_LIFETIME_MS] counts as stale. This is the single criterion for bake
     * skipping, overlay partitioning, and canvas transforms; the three stay consistent.
     */
    private fun isMotionLive(motion: MotionEffect, now: Long): Boolean {
        if (now - motion.startTimeMs > MOTION_MAX_LIFETIME_MS) return false
        return !motion.isReleased || !motion.isAtRest(now)
    }

    fun beginKeyDraw(canvas: Canvas, entity: Any): Int? {
        val now = motionDrawFrameTimeMs ?: SystemClock.uptimeMillis()
        val motion = activeMotions.firstOrNull {
            isSameMotionEntity(it, entity) && isMotionLive(it, now)
        } ?: return null

        val save = canvas.save()
        return try {
            applyMotionTransform(canvas, motion, motion.keyBounds, now)
            save
        } catch (t: Throwable) {
            canvas.restoreToCount(save)
            throw t
        }
    }

    private fun applyMotionTransform(
        canvas: Canvas,
        motion: MotionEffect,
        bounds: RectF,
        now: Long
    ) {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val sx = motion.scaleX.update(now)
        val sy = motion.scaleY.update(now)
        val tx = motion.translationX.update(now)
        val ty = motion.translationY.update(now)
        val rotation = motion.rotation.update(now)

        canvas.translate(tx, ty)
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        canvas.scale(sx, sy)
        canvas.translate(-cx, -cy)
    }

    private fun isSameMotionEntity(motion: MotionEffect, entity: Any): Boolean {
        return motion.entity === entity ||
            perKeyBoundsProvider?.matchesBounds(entity, motion.keyBounds) == true
    }

    fun endKeyDraw(canvas: Canvas, saveCount: Int?) {
        if (saveCount != null) {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun scheduleMotionFrame(view: View) {
        if (motionFramePosted) return
        motionFramePosted = true
        view.postOnAnimation(motionFrameRunnable)
    }

    private fun startMotion(
        keyboardView: View,
        pointerId: Int,
        type: MotionEffectType,
        target: OplusPerKeyTarget,
        localX: Float,
        localY: Float,
        now: Long
    ) {
        if (type == MotionEffectType.NONE || perKeyBoundsProvider?.supportsMotion() != true) return
        activeMotions.removeAll { it.entity === target.entity }
        motionRedrawFailureCount = 0

        val motion = MotionEffect(pointerId, target.entity, type, target.localBounds)
        when (type) {
            MotionEffectType.SPRING_DOWN -> {
                motion.scaleX.setTarget(0.7f, now)
                motion.scaleY.setTarget(0.7f, now)
            }
            MotionEffectType.SPRING_UP -> {
                motion.scaleX.setTarget(1.3f, now)
                motion.scaleY.setTarget(1.3f, now)
            }
            MotionEffectType.BOUNCE_UP -> {
                motion.translationY.setTarget(-20f, now)
            }
            MotionEffectType.BOUNCE_TOUCH -> {
                val dx = target.localBounds.centerX() - localX
                val dy = target.localBounds.centerY() - localY
                val distance = hypot(dx, dy)
                if (distance > 0.001f) {
                    val factor = 20f / distance
                    motion.translationX.setTarget(dx * factor, now)
                    motion.translationY.setTarget(dy * factor, now)
                }
            }
            MotionEffectType.TILT -> {
                motion.translationY.setTarget(-9f, now)
                motion.rotation.setTarget(9f, now)
            }
            MotionEffectType.NONE -> return
        }
        activeMotions.add(motion)
        viewRef = WeakReference(keyboardView)
        scheduleMotionFrame(keyboardView)
    }

    private fun releaseMotion(pointerId: Int?, now: Long) {
        val targets = if (pointerId == null) {
            activeMotions.toList()
        } else {
            activeMotions.filter { it.pointerId == pointerId }
        }
        if (targets.isEmpty()) return
        for (motion in targets) {
            motion.isReleased = true
            motion.scaleX.setTarget(1f, now)
            motion.scaleY.setTarget(1f, now)
            motion.translationX.setTarget(0f, now)
            motion.translationY.setTarget(0f, now)
            motion.rotation.setTarget(0f, now)
        }
        viewRef?.get()?.let { scheduleMotionFrame(it) }
        scheduleSafetyRedraw(targets)
    }

    /**
     * Fallback redraw after release (repairs the "key disappears after press" case).
     *
     * Key Motion **skips** moving keys at bake time (`perkeyDrawMethod` returns null); the
     * overlay repaints them on the screen canvas, and [motionFrameRunnable] calls
     * `requestRedraw` once per entity at animation end to bake the key back into the host
     * buffer bitmap. Two breaks in that chain:
     *  - the animation frame is cleared early when `view` detaches or has size 0 (run =
     *    clears activeMotions with no redraw);
     *  - the host's `D(entity)` request lands on a frame where the motion is still active.
     * Either break leaves a permanent **hole** for that key in the host buffer, which is
     * rebuilt only when `drawRefresh` is set; the key stays gone until a layout rebuild.
     *
     * This waits one animation lifetime plus a bit after release, then issues one per-key
     * redraw for entities with no remaining motion (one `D(entity)`, released keys only).
     */
    private fun scheduleSafetyRedraw(motions: List<MotionEffect>) {
        val view = viewRef?.get() ?: return
        val provider = perKeyBoundsProvider ?: return
        for (motion in motions) {
            val entity = motion.entity
            view.postDelayed({
                if (!isMotionActive(entity)) {
                    provider.requestRedraw(view, entity)
                }
            }, MOTION_MAX_LIFETIME_MS + 50L)
        }
    }

    // ── Internal animation representation classes ──

    private class BoardEffect(
        val type: BoardEffectType,
        val originX: Float,
        val originY: Float,
        val color: Int,
        val keyBounds: RectF,
        val startTime: Long,
        val duration: Long,
        val density: Float = 1f,
        val alphaRatio: Float = 1f,
        val rangeRatio: Float = 1f,
        /**
         * OUTLINE stroke target: the real key rect of **the pressed key** (rootView space).
         *
         * Samsung `HoneyTeaKeyboardViewHolder.dispatchTouchEvent` runs
         * `new BoardOutlineEffector(this, pressedKeyView)`, so outlineTargetView is the pressed
         * key and drawTargetView is the whole keyboard container. The effector builds its
         * roundRect (corner 8dp) from `outlineTargetView.getLeft()/Top()` plus the key-row
         * parent offset, which lands on that key's rect inside the container. Null when the real
         * key bounds do not resolve; OUTLINE then fail-closes and draws nothing.
         */
        val outlineTargetBounds: RectF? = null,
        /** BLOB_SPREAD / BLOB Brownian state (blob count + prebuilt shader); null for other types. */
        val boardBlobState: BoardBlobState? = null,
        /** SPARK / SPARK_SPIN: angle base of the two orbits. SPARK=`randomRange(0,90)`; SPARK_SPIN=`randomRange(0,360)`. */
        val sparkAngleDelta: Float = 0f,
        /** SPARK / SPARK_SPIN: per-particle rotation base `fromRotateZ = randomRange(0, 90)`. */
        val sparkFromRotateZ: Float = 0f,
        /** OUTLINE: LinearGradient built from two random effect colors. */
        val outlineColors: IntArray? = null,
        /** PAINT_GUN: `desSize = min(w,h) * randomFloat(min,max)` as the bitmap base side (px). */
        val paintGunSize: Float = 0f,
        /** PAINT_GUN: whole-bitmap rotation from `randomRange(-100, 100)` (degrees). */
        val paintGunRotation: Float = 0f,
        /** PAINT_GUN: one of the four original Samsung splash assets picked at press time. */
        val paintGunBitmap: Bitmap? = null,
        /** FIRE_CRACKER: color pool currently used by Samsung EffectColorProvider. */
        val fireCrackerPalette: IntArray? = null,
        /** FIRE_CRACKER: rasterized bitmap of Samsung's 6dp oval drawable. */
        val fireCrackerBitmap: Bitmap? = null
    )

    /**
     * Board BLOB_SPREAD runtime state (1:1 port of Samsung `BoardBlobSpreadEffector`).
     *
     * - `toDotRadius = min(width, height) * dotScale(0.3)`
     * - 3 blobs, motion scale `(w*0.7, h*0.7)` (motionScale=0.7), frequency 0.2
     * - canvas translated to `(downX - toDotRadius/4, downY - toDotRadius/3)`
     * - shader = `RadialGradient(toR/2, toR/2, max(1,toR), [randomColor, 0])`
     */
    private class BoardBlobState(
        val dotCount: Int,
        val toDotRadius: Float,
        val motions: Array<BrownianMotion>,
        var blobShader: Shader? = null
    ) {
        fun update() {
            for (m in motions) m.update()
        }
    }

    private class KeyEffect(
        val type: KeyEffectType,
        val keyBounds: RectF,
        val color: Int,
        val startTime: Long,
        val duration: Long,
        val density: Float,
        val alphaRatio: Float = 1f,
        val rangeRatio: Float = 1f,
        /** Motion target resolved from the same touch event, if available. */
        val motionEntity: Any? = null,
        /** BLOB_SPREAD Brownian translation point used for the touch (view space). */
        val originX: Float = 0f,
        val originY: Float = 0f,
        /** BLOB / BLOB_SPREAD state of the 5 blobs + Brownian motion; null for other types. */
        val blobState: KeyBlobState? = null,
        /**
         * SPARK_SPIN (= `KeySparkEffector`):
         * - `sparkAngleDelta` = `randomRange(0, 150)` (`iS`)
         * - `sparkFromRotateZ` = `randomRange(0, 90)` (particle rotation base)
         * - `sparkFromDotRadius` = `max(keyW, keyH) * 0.3` (particle half-side start)
         * - `sparkOrbitRadius` = `hypot(keyW, keyH) / 2` (animation value cap / `currentAnimRadius` end)
         * - `sparkShaderRadius` = `hypot(keyW, keyH)` (background RadialGradient radius)
         */
        val sparkAngleDelta: Float = 0f,
        val sparkFromRotateZ: Float = 0f,
        val sparkFromDotRadius: Float = 0f,
        val sparkOrbitRadius: Float = 0f,
        val sparkShaderRadius: Float = 0f,
        /** SPARK_SPIN background layer color `lighter(color, 0.3)`, computed at creation. */
        val sparkBgColor: Int = 0,
        /**
         * FOOT_PRINT (= `KeyFootPrintEffector`):
         * - `footPrintRotation` = `randomRange(-30, 30)`
         * - `footPrintSize` = `(int)(min(keyW, keyH) * 0.75f)`
         */
        val footPrintRotation: Float = 0f,
        val footPrintSize: Int = 0
    )

    /**
     * Key BLOB / BLOB_SPREAD runtime state (1:1 port of Samsung `KeyBlobEffector` /
     * `KeyBlobSpreadEffector`).
     *
     * - `fMin = min(width, height) * dotScale` (BLOB dotScale=0.8, BLOB_SPREAD dotScale=1.5)
     * - BLOB: 5 blobs jitter around a random pivot (width [w/4,w/3], height [h/4,h/3]) with radius `(1-af)*fMin`.
     * - BLOB_SPREAD: canvas translated to the touch point, blob radius `af*fMin`.
     * - Both draw a full `ColorUtil.lighter(randomColor, 0.3)` background first.
     * - shader = `RadialGradient(fMin/2, fMin/2, max(1,fMin), [randomColor, 0])`.
     */
    private class KeyBlobState(
        val dotCount: Int,
        val fMin: Float,
        val pivotX: Float,
        val pivotY: Float,
        val motions: Array<BrownianMotion>
    ) {
        /**
         * Radial gradient shader of the blobs. Samsung builds one
         * `RadialGradient(fMin/2, fMin/2, max(1,fMin), [color, 0])` at effect creation and
         * reuses it every frame; this does the same at effect creation.
         */
        var blobShader: Shader? = null

        /** Advances the Brownian timeline of every blob; called once per frame. */
        fun update() {
            for (m in motions) m.update()
        }
    }

    /**
     * 1:1 port of Samsung `BrownianMotion` (including its embedded `Perlin`).
     *
     * - constructor: `positionScale=(scaleX, scaleY, 1000)`; `positionFrequency=0.25` (overridden to 0.2 by the effector);
     *   `positionAmplitude=0.3`; `positionFractalLevel=3`; `fbmNorm=1.3333334f`.
     * - `time[6]` starts at `randomRange(-10000, 0)` (integer range); `rehash()` is followed by one `timeStep()`.
     * - `update()`: `dt = min(0.021, (uptime-last)/1000)`; `time[i] += frequency*dt` (i=0..2);
     *   `pos = fbm(time[i], 3) * scale[i] * (amplitude * fbmNorm)`.
     * - `Perlin`: fixed `perm` table of 256+1; `noise(x)=lerp(fade(f), grad(perm[i],f), grad(perm[i+1],f-1))*2`;
     *   `grad(hash,x) = (hash&1)==0 ? x : -x`; `fbm(x, octave)` sums octaves at half amplitude.
     */
    private class BrownianMotion(
        private val scaleX: Float,
        private val scaleY: Float,
        positionFrequency: Float
    ) {
        private val time = FloatArray(6) { randomRangeInt(-10000f, 0f) }
        private var lastTimeMs = 0L

        var x: Float = 0f
            private set
        var y: Float = 0f
            private set
        var z: Float = 0f
            private set

        private var frequency = positionFrequency

        init {
            // Samsung's constructor ends with frameRateCounter.timeStep(); this sets lastTimeMs to "initialized".
            lastTimeMs = SystemClock.uptimeMillis()
        }

        fun update() {
            val now = SystemClock.uptimeMillis()
            val dt = if (lastTimeMs > 0L) ((now - lastTimeMs) / 1000f).coerceAtMost(0.021f) else 0f
            lastTimeMs = now

            for (i in 0 until 3) {
                time[i] += frequency * dt
            }
            val norm = POSITION_AMPLITUDE * FBM_NORM
            x = fbm(time[0], FRACTAL_LEVEL) * scaleX * norm
            y = fbm(time[1], FRACTAL_LEVEL) * scaleY * norm
            z = fbm(time[2], FRACTAL_LEVEL) * 1000f * norm
        }

        /** `Perlin.fbm(x, octave)`: `sum += noise(x) * amp; x *= 2; amp *= 0.5`. */
        private fun fbm(v: Float, octave: Int): Float {
            var sum = 0f
            var amp = 0.5f
            var xv = v
            repeat(octave) {
                sum += noise1D(xv) * amp
                xv *= 2f
                amp *= 0.5f
            }
            return sum
        }

        /**
         * `Perlin.noise(x)`:
         * `i = (int)floor(x) & 255; f = x - floor(x);`
         * `return lerp(fade(f), grad(perm[i], f), grad(perm[i+1], f-1)) * 2`
         */
        private fun noise1D(v: Float): Float {
            val i = floor(v).toInt() and 255
            val f = v - floor(v)
            val a = grad(perm[i], f)
            val b = grad(perm[i + 1], f - 1f)
            val t = fade(f)
            // lerp(t, a, b) = a + (b - a) * t
            return (a + (b - a) * t) * 2f
        }

        private fun fade(t: Float): Float = ((((6f * t) - 15f) * t) + 10f) * t * t * t

        /** `Perlin.grad(hash, x) = (hash & 1) == 0 ? x : -x`. */
        private fun grad(hash: Int, xv: Float): Float = if ((hash and 1) == 0) xv else -xv

        companion object {
            private const val POSITION_AMPLITUDE = 0.3f
            private const val FBM_NORM = 1.3333334f
            private const val FRACTAL_LEVEL = 3

            /** Samsung `BrownianMotion.Perlin.perm` (256 entries + the first entry 151 repeated at the end). */
            private val perm = intArrayOf(
                151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225,
                140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23, 190, 6, 148,
                247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32,
                57, 177, 33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175,
                74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83, 111, 229, 122,
                60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54,
                65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169,
                200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64,
                52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212,
                207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223, 183, 170, 213,
                119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
                129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104,
                218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241,
                81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157,
                184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254, 138, 236, 205, 93,
                222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180,
                151
            )
        }
    }

    private class SpringValue(
        initial: Float,
        private val dampingRatio: Float,
        private val stiffness: Float
    ) {
        var value: Float = initial
            private set
        var velocity: Float = 0f
            private set

        private var target: Float = initial
        private var startValue: Float = initial
        private var startVelocity: Float = 0f
        private var startTimeMs: Long = SystemClock.uptimeMillis()

        fun setTarget(newTarget: Float, nowMs: Long) {
            update(nowMs)
            startValue = value
            startVelocity = velocity
            target = newTarget
            startTimeMs = nowMs
        }

        fun update(nowMs: Long): Float {
            val elapsed = (nowMs - startTimeMs).coerceAtLeast(0L) / 1000f
            val omega0 = kotlin.math.sqrt(stiffness)
            val decay = dampingRatio * omega0
            val omegaD = omega0 * kotlin.math.sqrt(1f - dampingRatio * dampingRatio)
            val displacement = startValue - target
            val sineCoefficient = (startVelocity + decay * displacement) / omegaD
            val expTerm = exp((-decay * elapsed).toDouble()).toFloat()
            val cosTerm = cos((omegaD * elapsed).toDouble()).toFloat()
            val sinTerm = sin((omegaD * elapsed).toDouble()).toFloat()
            val inner = displacement * cosTerm + sineCoefficient * sinTerm
            value = target + expTerm * inner
            velocity = expTerm * (
                -decay * inner -
                    displacement * omegaD * sinTerm +
                    sineCoefficient * omegaD * cosTerm
                )
            return value
        }

        fun isAtRest(nowMs: Long): Boolean {
            update(nowMs)
            return kotlin.math.abs(value - target) < 0.001f &&
                kotlin.math.abs(velocity) < 0.01f
        }
    }

    private class MotionEffect(
        val pointerId: Int,
        val entity: Any,
        val type: MotionEffectType,
        val keyBounds: RectF,
        val scaleX: SpringValue = SpringValue(1f, 0.5f, 1500f),
        val scaleY: SpringValue = SpringValue(1f, 0.5f, 1500f),
        val translationX: SpringValue = SpringValue(0f, 0.5f, 1500f),
        val translationY: SpringValue = SpringValue(0f, 0.5f, 1500f),
        val rotation: SpringValue = SpringValue(0f, 0.2f, 1500f),
        var isReleased: Boolean = false,
        val startTimeMs: Long = SystemClock.uptimeMillis()
    ) {
        fun isAtRest(nowMs: Long): Boolean {
            return scaleX.isAtRest(nowMs) &&
                scaleY.isAtRest(nowMs) &&
                translationX.isAtRest(nowMs) &&
                translationY.isAtRest(nowMs) &&
                rotation.isAtRest(nowMs)
        }
    }

    /**
     * Dispatch touch event from keyboard View with coordinate offsets relative to hostView (rootView).
     *
     * @param keyboardView The source keyboard View where touch occurs.
     * @param offsetX Horizontal translation offset from keyboardView to hostView.
     * @param offsetY Vertical translation offset from keyboardView to hostView.
     * @param event The motion event.
     * @param isCharacterInputMode True if the keyboard is in character input mode (not emoji mode).
     */
    fun onTouch(
        keyboardView: View,
        offsetX: Float,
        offsetY: Float,
        event: MotionEvent,
        isCharacterInputMode: Boolean
    ) {
        viewRef = WeakReference(keyboardView)
        val now = SystemClock.uptimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val context = keyboardView.context
                val boardType = BoardEffectType.fromKey(OplusInputPrefs.getEffectsBoard(context))
                val keyType = KeyEffectType.fromKey(OplusInputPrefs.getEffectsKey(context))
                val motionType = MotionEffectType.fromKey(OplusInputPrefs.getEffectsMotion(context))
                val colorPreset = EffectColorPreset.fromKey(OplusInputPrefs.getEffectsColorPreset(context))
                val customPalette = if (colorPreset == EffectColorPreset.CUSTOM) {
                    OplusInputPrefs.getCustomPalette(context)
                } else {
                    null
                }
                val effectiveColors = customPalette?.toIntArray() ?: colorPreset.colors
                val alphaRatio = OplusInputPrefs.getEffectsAlphaRatio(context).coerceIn(0.0f, 1.0f)
                val rangeRatio = OplusInputPrefs.getEffectsRangeRatio(context).coerceIn(0.5f, 2.0f)
                val speedRatio = OplusInputPrefs.getEffectsSpeedRatio(context).coerceIn(0.5f, 2.0f)
                val density = keyboardView.resources.displayMetrics.density

                val index = event.actionIndex
                val localX = event.getX(index)
                val localY = event.getY(index)

                val rootX = localX + offsetX
                val rootY = localY + offsetY

                val effectTarget = if (
                    motionType != MotionEffectType.NONE ||
                    (isCharacterInputMode && keyType != KeyEffectType.NONE)
                ) {
                    perKeyBoundsProvider?.targetFor(keyboardView, localX, localY)
                } else {
                    null
                }

                if (motionType != MotionEffectType.NONE) {
                    if (effectTarget != null) {
                        startMotion(
                            keyboardView = keyboardView,
                            pointerId = event.getPointerId(index),
                            type = motionType,
                            target = effectTarget,
                            localX = localX,
                            localY = localY,
                            now = now
                        )
                    }
                }

                val color = colorPreset.pickColor(colorIndexCounter.getAndIncrement(), effectiveColors)
                val boardColor = when (boardType) {
                    BoardEffectType.PAINT_GUN -> if (effectiveColors.isNotEmpty()) {
                        effectiveColors[Random.nextInt(effectiveColors.size)]
                    } else {
                        color
                    }
                    BoardEffectType.FIRE_CRACKER -> pickSamsungEffectColor(effectiveColors)
                    else -> color
                }

                // Board effect uses the estimated whole-keyboard box.
                val boardBounds = estimateKeyBounds(
                    keyboardView.width.toFloat(),
                    keyboardView.height.toFloat(),
                    localX,
                    localY
                )
                boardBounds.offset(offsetX, offsetY)

                // 1. Board light effect (Board Blending): triggers on every touch event.
                if (boardType != BoardEffectType.NONE) {
                    val duration = ((when (boardType) {
                        BoardEffectType.RING_SPREAD -> DURATION_BOARD_RING
                        BoardEffectType.BLOB_SPREAD -> DURATION_BOARD_BLOB
                        BoardEffectType.SPARK -> DURATION_BOARD_SPARK
                        BoardEffectType.OUTLINE -> DURATION_BOARD_OUTLINE
                        BoardEffectType.BLOB -> DURATION_BOARD_BLOB_STATIC
                        BoardEffectType.SPARK_SPIN -> DURATION_BOARD_SPARK_SPIN
                        BoardEffectType.PAINT_GUN -> DURATION_BOARD_PAINT_GUN
                        BoardEffectType.FIRE_CRACKER -> DURATION_BOARD_FIRE_CRACKER
                        BoardEffectType.NONE -> 0L
                    }) / speedRatio).toLong()
                    // BLOB / BLOB_SPREAD share the Brownian state; only dotScale / motionScale / dotCount differ.
                    val boardBlobState = when (boardType) {
                        BoardEffectType.BLOB_SPREAD -> buildBoardBlobState(
                            keyboardView.width.toFloat(), keyboardView.height.toFloat(), boardColor,
                            BOARD_BLOB_DOT_SCALE * rangeRatio, BOARD_BLOB_MOTION_SCALE * rangeRatio, BOARD_BLOB_DOT_COUNT
                        )
                        BoardEffectType.BLOB -> buildBoardBlobState(
                            keyboardView.width.toFloat(), keyboardView.height.toFloat(), boardColor,
                            BOARD_BLOB_STATIC_DOT_SCALE * rangeRatio, BOARD_BLOB_STATIC_MOTION_SCALE * rangeRatio, BOARD_BLOB_STATIC_DOT_COUNT
                        )
                        else -> null
                    }
                    // SPARK: iS = randomRange(0, 90); SPARK_SPIN: iS = randomRange(0, 360).
                    // Both use fromRotateZ = randomRange(0, 90) at construction.
                    val isSpin = boardType == BoardEffectType.SPARK_SPIN
                    val sparkAngleDelta = when (boardType) {
                        BoardEffectType.SPARK -> randomRangeInt(0f, 90f)
                        BoardEffectType.SPARK_SPIN -> randomRangeInt(0f, BOARD_SPARK_SPIN_ANGLE_MAX)
                        else -> 0f
                    }
                    val sparkFromRotateZ = if (boardType == BoardEffectType.SPARK || isSpin) {
                        randomRangeInt(0f, 90f)
                    } else {
                        0f
                    }
                    // BoardOutlineEffector: LinearGradient uses two random effect colors.
                    val outlineColors = if (boardType == BoardEffectType.OUTLINE) {
                        intArrayOf(color, colorPreset.pickColor(colorIndexCounter.getAndIncrement(), effectiveColors))
                    } else {
                        null
                    }
                    // Board OUTLINE: target = the pressed key's real key rect (Samsung pressedKeyView).
                    // Null when no real key bounds resolve; that OUTLINE fail-closes and draws nothing.
                    val outlineTarget = if (boardType == BoardEffectType.OUTLINE) {
                        resolvePerKeyBounds(keyboardView, localX, localY, offsetX, offsetY)
                    } else {
                        null
                    }
                    // BoardPaintGunEffector: desSize = min(w,h) * randomFloat(minScale, maxScale);
                    // whole-bitmap rotation = randomRange(-100, 100).
                    val paintGunSize = if (boardType == BoardEffectType.PAINT_GUN) {
                        val scale = BOARD_PAINT_GUN_MIN_SCALE +
                            Random.nextFloat() * (BOARD_PAINT_GUN_MAX_SCALE - BOARD_PAINT_GUN_MIN_SCALE)
                        min(keyboardView.width, keyboardView.height).toFloat() * scale * rangeRatio
                    } else {
                        0f
                    }
                    val paintGunRotation = if (boardType == BoardEffectType.PAINT_GUN) {
                        randomRangeInt(-BOARD_PAINT_GUN_ROTATE_MAX, BOARD_PAINT_GUN_ROTATE_MAX)
                    } else {
                        0f
                    }
                    val paintGunBitmap = if (boardType == BoardEffectType.PAINT_GUN) {
                        paintGunBitmap(Random.nextInt(BOARD_PAINT_GUN_DRAWABLES.size))
                    } else {
                        null
                    }
                    // BoardFireCrackerEffector: the initial fuse color is fixed at touch time; the
                    // burst phase calls Samsung EffectColorProvider.getRandomColor() per frame and direction.
                    val fireCrackerPalette = if (boardType == BoardEffectType.FIRE_CRACKER) {
                        effectiveColors
                    } else {
                        null
                    }
                    val fireCrackerBitmap = if (boardType == BoardEffectType.FIRE_CRACKER) {
                        getFireCrackerBitmap()
                    } else {
                        null
                    }
                    activeBoardEffects.add(
                        BoardEffect(
                            type = boardType,
                            originX = rootX,
                            originY = rootY,
                            color = boardColor,
                            keyBounds = boardBounds,
                            startTime = now,
                            duration = duration,
                            density = density,
                            alphaRatio = alphaRatio,
                            rangeRatio = rangeRatio,
                            outlineTargetBounds = outlineTarget,
                            boardBlobState = boardBlobState,
                            sparkAngleDelta = sparkAngleDelta,
                            sparkFromRotateZ = sparkFromRotateZ,
                            outlineColors = outlineColors,
                            paintGunSize = paintGunSize,
                            paintGunRotation = paintGunRotation,
                            paintGunBitmap = paintGunBitmap,
                            fireCrackerPalette = fireCrackerPalette,
                            fireCrackerBitmap = fireCrackerBitmap
                        )
                    )
                }

                // 2. Key light effect (Key Blending): fires only in character input mode (fixed,
                // non-emoji). Uses the per-key entity rect; fail-closes when no key face resolves.
                if (isCharacterInputMode && keyType != KeyEffectType.NONE) {
                    // Note: resolvePerKeyBounds already offsets the rect from keyboardView local
                    // space to rootView space (kb.offset(offsetX, offsetY) inside). Callers must
                    // not offset again; a second offset shifts the key rect by a whole
                    // keyboardView->rootView delta (regression from commit 9fbe11f).
                    val keyLocalBounds = effectTarget?.localBounds?.let { RectF(it) }?.apply {
                        offset(offsetX, offsetY)
                    } ?: resolvePerKeyBounds(keyboardView, localX, localY, offsetX, offsetY)

                    if (keyLocalBounds != null && keyLocalBounds.width() > 0f && keyLocalBounds.height() > 0f) {
                        val duration = ((when (keyType) {
                            KeyEffectType.COLOR_FLASH -> DURATION_KEY_FLASH
                            KeyEffectType.OUTLINE -> DURATION_KEY_OUTLINE
                            KeyEffectType.BLOB -> DURATION_KEY_BLOB
                            KeyEffectType.BLOB_SPREAD -> DURATION_KEY_SPREAD
                            KeyEffectType.SPARK_SPIN -> DURATION_KEY_SPARK_SPIN
                            KeyEffectType.FOOT_PRINT -> DURATION_KEY_FOOT_PRINT
                            KeyEffectType.NONE -> 0L
                        }) / speedRatio).toLong()
                        val blobState = if (keyType == KeyEffectType.BLOB || keyType == KeyEffectType.BLOB_SPREAD) {
                            buildBlobState(keyType, keyLocalBounds, color, rangeRatio)
                        } else {
                            null
                        }
                        // KeySparkEffector: fromDotRadius=max(w,h)*0.3; animation value 0..hypot(w,h)/2;
                        // background shader radius hypot(w,h); iS=randomRange(0,150); fromRotateZ=randomRange(0,90).
                        val isKeySpark = keyType == KeyEffectType.SPARK_SPIN
                        val keyW = keyLocalBounds.width()
                        val keyH = keyLocalBounds.height()
                        val keySparkFromDotRadius = if (isKeySpark) {
                            max(keyW, keyH) * KEY_SPARK_FROM_SCALE * rangeRatio
                        } else {
                            0f
                        }
                        val keySparkDiag = if (isKeySpark) hypot(keyW, keyH) * rangeRatio else 0f
                        val keySparkAngleDelta = if (isKeySpark) {
                            randomRangeInt(0f, KEY_SPARK_ANGLE_DELTA_MAX)
                        } else {
                            0f
                        }
                        val keySparkFromRotateZ = if (isKeySpark) {
                            randomRangeInt(0f, KEY_SPARK_FROM_ROTATE_Z_MAX)
                        } else {
                            0f
                        }
                        // KeyFootPrintEffector: icon side = (int)(min(w,h)*0.75); rotation randomRange(-30,30).
                        val isFootPrint = keyType == KeyEffectType.FOOT_PRINT
                        val footPrintSize = if (isFootPrint) {
                            (min(keyW, keyH) * KEY_FOOT_PRINT_SCALE * rangeRatio).toInt()
                        } else {
                            0
                        }
                        val footPrintRotation = if (isFootPrint) {
                            randomRangeInt(-KEY_FOOT_PRINT_ROTATE_MAX, KEY_FOOT_PRINT_ROTATE_MAX)
                        } else {
                            0f
                        }
                        activeKeyEffects.add(
                            KeyEffect(
                                type = keyType,
                                keyBounds = keyLocalBounds,
                                color = color,
                                startTime = now,
                                duration = duration,
                                density = density,
                                alphaRatio = alphaRatio,
                                rangeRatio = rangeRatio,
                                motionEntity = effectTarget?.entity,
                                originX = offsetX + localX,
                                originY = offsetY + localY,
                                blobState = blobState,
                                sparkAngleDelta = keySparkAngleDelta,
                                sparkFromRotateZ = keySparkFromRotateZ,
                                sparkFromDotRadius = keySparkFromDotRadius,
                                sparkOrbitRadius = keySparkDiag / 2f,
                                sparkShaderRadius = keySparkDiag,
                                sparkBgColor = if (isKeySpark) lighter(color, KEY_BLOB_BG_LIGHTER) else 0,
                                footPrintRotation = footPrintRotation,
                                footPrintSize = footPrintSize
                            )
                        )
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                releaseMotion(event.getPointerId(event.actionIndex), now)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                releaseMotion(null, now)
            }
        }
    }

    /**
     * Builds key BLOB / BLOB_SPREAD runtime state (ported from Samsung `KeyBlobEffector` /
     * `KeyBlobSpreadEffector`), generating 5 Brownian motion states and a random pivot from
     * the key rect size.
     *
     * - BLOB: `fMin = min(w,h)*0.8`, pivot = `randomRange(w/4, w/3)` x `randomRange(h/4, h/3)`.
     * - BLOB_SPREAD: `fMin = min(w,h)*1.5` (pivot unused).
     * - Both use motion scale `(w*1.5, h*1.5)`, frequency 0.2.
     *
     * @param color builds the blob RadialGradient, constructed once at effect creation.
     */
    private fun buildBlobState(type: KeyEffectType, keyBounds: RectF, color: Int, rangeRatio: Float = 1f): KeyBlobState {
        val w = keyBounds.width()
        val h = keyBounds.height()
        val dotScale = (if (type == KeyEffectType.BLOB_SPREAD) KEY_BLOB_SPREAD_DOT_SCALE else KEY_BLOB_DOT_SCALE) * rangeRatio
        val fMin = min(w, h) * dotScale

        val pivotX = if (type == KeyEffectType.BLOB) randomRangeInt(w / 4f, w / 3f) else 0f
        val pivotY = if (type == KeyEffectType.BLOB) randomRangeInt(h / 4f, h / 3f) else 0f

        val motions = Array(KEY_BLOB_DOT_COUNT) {
            BrownianMotion(
                scaleX = w * KEY_BLOB_MOTION_SCALE * rangeRatio,
                scaleY = h * KEY_BLOB_MOTION_SCALE * rangeRatio,
                positionFrequency = KEY_BLOB_MOTION_FREQUENCY
            )
        }
        return KeyBlobState(
            dotCount = KEY_BLOB_DOT_COUNT,
            fMin = fMin,
            pivotX = pivotX,
            pivotY = pivotY,
            motions = motions
        ).also { state ->
            state.blobShader = RadialGradient(
                fMin / 2f,
                fMin / 2f,
                max(1f, fMin),
                intArrayOf(color, 0x00000000),
                null,
                Shader.TileMode.CLAMP
            )
        }
    }

    /**
     * Resolves the pressed key's real key rect (rootView space). Shared by key blending and
     * board OUTLINE; any failure returns null (fail-closed).
     */
    private fun resolvePerKeyBounds(
        keyboardView: View,
        localX: Float,
        localY: Float,
        offsetX: Float,
        offsetY: Float
    ): RectF? {
        val provider = perKeyBoundsProvider ?: return null
        val kb = try {
            provider.boundsFor(keyboardView, localX, localY)
        } catch (t: Throwable) {
            Log.w("OplusKeyboardEffects", "Per-key bounds provider failed; falling open for this event.", t)
            return null
        }
        if (kb == null || kb.width() <= 0f || kb.height() <= 0f) return null
        kb.offset(offsetX, offsetY)
        return kb
    }

    /**
     * Builds board BLOB / BLOB_SPREAD runtime state.
     *
     * The two share one structure (Samsung `BoardBlobEffector` and `BoardBlobSpreadEffector`
     * use the same BrownianMotion + RadialGradient skeleton) and differ only in `dotScale` /
     * `motionScale` / `dotCount`:
     * - BLOB_SPREAD: dotScale=0.3, motionScale=0.7, dotCount=3
     * - BLOB:        dotScale=0.3, motionScale=0.5, dotCount=5
     *
     * Prebuilds `RadialGradient(toDotRadius/2, toDotRadius/2, max(1,toDotRadius), [color, 0])`.
     */
    private fun buildBoardBlobState(
        width: Float,
        height: Float,
        color: Int,
        dotScale: Float,
        motionScale: Float,
        dotCount: Int
    ): BoardBlobState {
        val toDotRadius = min(width, height) * dotScale
        val motions = Array(dotCount) {
            BrownianMotion(
                scaleX = width * motionScale,
                scaleY = height * motionScale,
                positionFrequency = BOARD_BLOB_MOTION_FREQUENCY
            )
        }
        return BoardBlobState(
            dotCount = dotCount,
            toDotRadius = toDotRadius,
            motions = motions
        ).also { state ->
            state.blobShader = RadialGradient(
                toDotRadius / 2f,
                toDotRadius / 2f,
                max(1f, toDotRadius),
                intArrayOf(color, 0x00000000),
                null,
                Shader.TileMode.CLAMP
            )
        }
    }

    /**
     * Check if there are active board or key effects requiring animation frames.
     */
    fun hasActiveEffects(): Boolean {
        val now = SystemClock.uptimeMillis()
        return activeBoardEffects.any { (now - it.startTime) < it.duration } ||
            activeKeyEffects.any { (now - it.startTime) < it.duration }
    }

    /**
     * Draw active effects overlay strictly within IME bounds Canvas.
     */
    fun draw(view: View, imeBounds: RectF, canvas: Canvas) {
        if (activeBoardEffects.isEmpty() && activeKeyEffects.isEmpty()) {
            return
        }

        val now = SystemClock.uptimeMillis()

        // ── Step 1: Render Board Blending Effects ──
        if (activeBoardEffects.isNotEmpty()) {
            val toRemove = ArrayList<BoardEffect>()
            for (effect in activeBoardEffects) {
                val elapsed = now - effect.startTime
                if (elapsed >= effect.duration) {
                    toRemove.add(effect)
                    continue
                }
                val progress = (elapsed.toFloat() / effect.duration).coerceIn(0f, 1f)
                drawBoardEffect(imeBounds, canvas, effect, progress)
            }
            if (toRemove.isNotEmpty()) {
                activeBoardEffects.removeAll(toRemove)
            }
        }

        // ── Step 2: Render Key Blending Effects ──
        if (activeKeyEffects.isNotEmpty()) {
            val toRemove = ArrayList<KeyEffect>()
            for (effect in activeKeyEffects) {
                val elapsed = now - effect.startTime
                if (elapsed >= effect.duration) {
                    toRemove.add(effect)
                    continue
                }
                val progress = (elapsed.toFloat() / effect.duration).coerceIn(0f, 1f)
                drawKeyEffect(canvas, effect, progress, now)
            }
            if (toRemove.isNotEmpty()) {
                activeKeyEffects.removeAll(toRemove)
            }
        }
    }

    // ── Board Effect Renderers ──
    //
    // Coordinate space (critical): do not enter "view space" again.
    // Samsung's board effector lives on the keyboard container view (`HoneyTeaKeyboardViewHolder extends
    // ConstraintLayout`, **toolbar and bottom bar included**), so the blender's canvas is that container's
    // own canvas (driven by `view.invalidate()` -> `onDraw`), origin = container top-left; downX/downY are
    // touch points in that space. Our overlay canvas lives on rootView with origin at its top-left, and
    // effect.originX/Y = localX + offsetX is already a rootView-space touch point -- the two are directly
    // equivalent, so every effect reads effect.originX/originY as-is and must not translate(imeBounds.*),
    // which would push shader centers and particle orbits out of the clip region.
    // Coverage rect: RING uses imeBounds (= whole keyboard container area, matching Samsung's
    // drawRect(0,0,view.getWidth(),view.getHeight()), **toolbar and bottom bar included**).

    private fun drawBoardEffect(
        imeBounds: RectF,
        canvas: Canvas,
        effect: BoardEffect,
        progress: Float
    ) {
        // Samsung animationFraction = ValueAnimator.getAnimatedFraction() = interpolated fraction.
        if (effect.alphaRatio <= 0.001f) return
        val af = afSine(progress)

        when (effect.type) {
            BoardEffectType.RING_SPREAD -> {
                // BoardRingSpreadEffector (1800ms, SINE_IN_OUT80):
                //   animatedValue f = -0.64 + 1.28 * af          // ValueAnimator.ofFloat(-0.64,0.64)
                //   radius = hypot(view.getWidth(), view.getHeight()) / 2
                //   shader = RadialGradient(downX, downY, radius, [0, color, 0],
                //                           [0, 0.35+f, 0.64+f])
                //   alpha  = LinearDecreaseAlpha(af, 255)
                //   covers the whole view Rect
                val f = -0.64f + 1.28f * af
                // Samsung: hypot(view.getWidth(), view.getHeight()) / 2,
                // view = the keyboard container (toolbar + bottom bar) -> our imeBounds.
                val radius = ((hypot(imeBounds.width(), imeBounds.height()) / 2f) * effect.rangeRatio).coerceAtLeast(1f)

                val stop2 = 0.35f + f
                val stop3 = f + 0.64f
                val alpha = (linearDecreaseAlpha(af, 255) * effect.alphaRatio).toInt().coerceIn(0, 255)

                boardPaint.style = Paint.Style.FILL
                boardPaint.shader = RadialGradient(
                    effect.originX,
                    effect.originY,
                    radius,
                    intArrayOf(0x00000000, effect.color, 0x00000000),
                    floatArrayOf(0f, stop2, stop3),
                    Shader.TileMode.CLAMP
                )
                boardPaint.alpha = alpha

                // Coordinate space: overlay canvas origin = rootView top-left and effect.originX/Y
                // is already a rootView-space touch point (= Samsung's container-space downX/downY).
                // Samsung's blender sits on the keyboard container whose canvas is that space, so no
                // further translation here; origin is equivalent. Draw rect = the whole IME container
                // area (toolbar + bottom bar) = Samsung's drawRect(0,0,view.getWidth(),view.getHeight()).
                canvas.drawRect(imeBounds, boardPaint)
                boardPaint.shader = null
                boardPaint.alpha = 255
            }

            BoardEffectType.BLOB -> {
                // BoardBlobEffector (1500ms, SINE_IN_OUT80, dotScale=0.3, motionScale=0.5, dotCount=5):
                //   currentDotRadius = (1 - af) * toDotRadius          <- shrinks (opposite of BLOB_SPREAD)
                //   alpha = LinearIncreaseAlpha(af, 255)               <- fade in (not BLOB_SPREAD's V shape)
                //   translate(downX - toR/4, downY - toR/4)            <- /4 here, not BLOB_SPREAD's /3
                //   shader = RadialGradient(toR/2, toR/2, max(1,toR), [color, 0])
                //   5 blobs with Brownian added, drawCircle(r/2, r/2, r, paint)
                val state = effect.boardBlobState
                if (state != null) {
                    state.update()

                    val r = state.toDotRadius
                    val currentDotRadius = (1f - af) * r
                    val alpha = (linearIncreaseAlpha(af, BOARD_BLOB_STATIC_ALPHA) * effect.alphaRatio).toInt().coerceIn(0, 255)

                    // Coordinate space: canvas origin = rootView top-left, originX/Y already a
                    // rootView-space touch point (= Samsung container-space downX/downY); no more translation.
                    val saved = canvas.save()
                    canvas.translate(effect.originX - r / 4f, effect.originY - r / 4f)

                    boardPaint.style = Paint.Style.FILL
                    boardPaint.shader = state.blobShader
                    boardPaint.alpha = alpha
                    for (i in 0 until state.dotCount) {
                        val m = state.motions[i]
                        canvas.save()
                        canvas.translate(m.x, m.y)
                        canvas.drawCircle(currentDotRadius / 2f, currentDotRadius / 2f, currentDotRadius, boardPaint)
                        canvas.restore()
                    }

                    canvas.restoreToCount(saved)
                    boardPaint.shader = null
                    boardPaint.alpha = 255
                }
            }

            BoardEffectType.BLOB_SPREAD -> {
                // BoardBlobSpreadEffector (1500ms, SINE_IN_OUT80, dotScale=0.3, motionScale=0.7):
                //   toDotRadius = min(view.w, view.h) * 0.3
                //   currentDotRadius = ValueAnimator.ofFloat(toR/3, toR) through SINE_IN_OUT80 -> r
                //   alpha = BlobSpreadAlpha(af) (coefficient ignored)
                //   translate(downX - toR/4, downY - toR/3)
                //   shader = RadialGradient(toR/2, toR/2, max(1,toR), [color, 0])
                //   3 blobs with Brownian added, drawCircle(r/2, r/2, r, paint)
                val state = effect.boardBlobState
                if (state != null) {
                    state.update()

                    val r = state.toDotRadius
                    val currentDotRadius = r / 3f + (r - r / 3f) * af
                    val alpha = (blobSpreadAlpha(af) * effect.alphaRatio).toInt().coerceIn(0, 255)

                    // Coordinate space: canvas origin = rootView top-left, originX/Y already a
                    // rootView-space touch point (= Samsung view-space downX/downY), so translate
                    // directly to (downX-.., downY-..) with **no extra imeBounds translation**.
                    val saved = canvas.save()
                    canvas.translate(effect.originX - r / 4f, effect.originY - r / 3f)

                    boardPaint.style = Paint.Style.FILL
                    boardPaint.shader = state.blobShader
                    boardPaint.alpha = alpha
                    for (i in 0 until state.dotCount) {
                        val m = state.motions[i]
                        canvas.save()
                        canvas.translate(m.x, m.y)
                        canvas.drawCircle(currentDotRadius / 2f, currentDotRadius / 2f, currentDotRadius, boardPaint)
                        canvas.restore()
                    }

                    canvas.restoreToCount(saved)
                    boardPaint.shader = null
                    boardPaint.alpha = 255
                }
            }

            BoardEffectType.SPARK -> {
                // BoardSparkEffector (2000ms, SINE_IN_OUT80):
                //   paint.color = randomColor
                //   paint.shader = RadialGradient(downX, downY, TO_RADIUS=100dp, [color, 0])  <- only shader
                //   alpha = LinearDecreaseAlpha(af, 125)
                //   currentAnimRadius = ValueAnimator.ofFloat(0, 100dp) through SINE_IN_OUT80
                //   drawDots(center=(downX,downY), angleDelta=iS,          radius=currentAnimRadius)
                //   drawDots(center=(downX,downY), angleDelta=36+iS,       radius=currentAnimRadius/2)
                //   each particle inside drawDots:
                //     x = centerX + cos(72*i - angleDelta) * radius
                //     y = centerY + sin(72*i - angleDelta) * radius
                //     halfSide = lerp(fromDotRadius=30dp, toDotRadius=15dp, af)
                //     camera.rotateZ(af*90 + fromRotateZ) -> canvas.setMatrix
                //     drawRoundRect(x-halfSide, y-halfSide, x+halfSide, y+halfSide, 10dp, 10dp)
                val maxOrbit = dp(BOARD_SPARK_ORBIT_DP, effect.density) * effect.rangeRatio
                val fromDot = dp(BOARD_SPARK_DOT_START_DP, effect.density)
                val toDot = dp(BOARD_SPARK_DOT_END_DP, effect.density)
                val corner = dp(BOARD_SPARK_CORNER_DP, effect.density)
                val currentAnimRadius = maxOrbit * af
                val halfSide = fromDot + (toDot - fromDot) * af

                val alpha = (linearDecreaseAlpha(af, BOARD_SPARK_ALPHA) * effect.alphaRatio).toInt().coerceIn(0, 255)

                boardPaint.style = Paint.Style.FILL
                boardPaint.color = effect.color
                boardPaint.alpha = alpha
                boardPaint.shader = RadialGradient(
                    effect.originX,
                    effect.originY,
                    max(1f, maxOrbit),
                    intArrayOf(effect.color, 0x00000000),
                    null,
                    Shader.TileMode.CLAMP
                )

                val saved = canvas.save()

                // Coordinate space: canvas origin = rootView top-left, originX/Y already a
                // rootView-space touch point (= Samsung view-space downX/downY); no more translation.
                val iS = effect.sparkAngleDelta
                drawSparkDots(canvas, effect, currentAnimRadius, iS, halfSide, corner, af)
                drawSparkDots(canvas, effect, currentAnimRadius / 2f, BOARD_SPARK_DOT_ANGLE / 2f + iS, halfSide, corner, af)

                canvas.restoreToCount(saved)
                boardPaint.shader = null
                boardPaint.alpha = 255
            }

            BoardEffectType.SPARK_SPIN -> {
                // BoardSparkSpinEffector (2000ms, SINE_IN_OUT80) - same frame as SPARK, only orbit angle differs:
                //   iS = randomRange(0, 360) (SPARK uses 0..90)
                //   iX = round(af * 360) (integer degrees)
                //   drawDots(center, angleDelta = iS - iX,        radius = currentAnimRadius)   <- reverse spin
                //   drawDots(center, angleDelta = 36 + iS + iX,   radius = currentAnimRadius/2) <- forward spin
                //   everything else (single shader@touch, radius 0..100dp, halfSide lerp(30dp,15dp,af),
                //   corner 10dp, alpha = LinearDecreaseAlpha(af, 125), particle spin af*90+fromRotateZ) matches SPARK.
                val maxOrbit = dp(BOARD_SPARK_ORBIT_DP, effect.density) * effect.rangeRatio
                val fromDot = dp(BOARD_SPARK_DOT_START_DP, effect.density)
                val toDot = dp(BOARD_SPARK_DOT_END_DP, effect.density)
                val corner = dp(BOARD_SPARK_CORNER_DP, effect.density)
                val currentAnimRadius = maxOrbit * af
                val halfSide = fromDot + (toDot - fromDot) * af

                val alpha = (linearDecreaseAlpha(af, BOARD_SPARK_ALPHA) * effect.alphaRatio).toInt().coerceIn(0, 255)

                boardPaint.style = Paint.Style.FILL
                boardPaint.color = effect.color
                boardPaint.alpha = alpha
                boardPaint.shader = RadialGradient(
                    effect.originX,
                    effect.originY,
                    max(1f, maxOrbit),
                    intArrayOf(effect.color, 0x00000000),
                    null,
                    Shader.TileMode.CLAMP
                )

                val saved = canvas.save()

                // Coordinate space: canvas origin = rootView top-left, originX/Y already a rootView-space touch point; no more translation.
                val iS = effect.sparkAngleDelta
                val iX = Math.round(af * 360f).toFloat()
                drawSparkDots(canvas, effect, currentAnimRadius, iS - iX, halfSide, corner, af)
                drawSparkDots(
                    canvas, effect, currentAnimRadius / 2f,
                    BOARD_SPARK_DOT_ANGLE / 2f + iS + iX, halfSide, corner, af
                )

                canvas.restoreToCount(saved)
                boardPaint.shader = null
                boardPaint.alpha = 255
            }

            BoardEffectType.OUTLINE -> {
                // BoardOutlineEffector (1000ms, SINE_IN_OUT80):
                //   origPath = roundRect(target rect, honey_tea_corner_radius=8dp)
                //   shader = LinearGradient(0, 0, 0, drawTargetView.getWidth(), [color1, color2])
                //   pathProgress = ValueAnimator.ofFloat(0, pathMeasure.getLength()) through SINE_IN_OUT80
                //   alpha = LinearDecreaseAlpha(af, 255)
                //   strokeWidth = af * 200
                //   shaderMatrix.setRotate(af * 360)
                //   currentPath = getSegment(0, pathProgress, true)
                //
                // Target geometry: Samsung's target is `outlineTargetView` = **the pressed key**
                // (`new BoardOutlineEffector(this, pressedKeyView)`), rect = that key's
                // left/top/right/bottom inside the keyboard container after the key-row parent
                // offset. Our `outlineTargetBounds` is already that key's real rootView-space rect.
                // Null when no real key bounds resolve (outlineTargetBounds == null) -> fail-closed.
                val target = effect.outlineTargetBounds ?: return
                val corner = dp(KEY_OUTLINE_CORNER_DP, effect.density)

                tempPath.reset()
                tempPath.addRoundRect(target, corner, corner, Path.Direction.CW)
                pathMeasure.setPath(tempPath, false)
                val totalLen = pathMeasure.length

                val alpha = (linearDecreaseAlpha(af, 255) * effect.alphaRatio).toInt().coerceIn(0, 255)

                boardPaint.style = Paint.Style.STROKE
                boardPaint.strokeJoin = Paint.Join.ROUND
                boardPaint.strokeCap = Paint.Cap.ROUND
                boardPaint.strokeWidth = (af * 200f * effect.rangeRatio).coerceAtLeast(0f)
                boardPaint.alpha = alpha

                val colors = effect.outlineColors
                // Samsung: LinearGradient(0, 0, 0, drawTargetView.getWidth(), [c1,c2]),
                // drawTargetView = the whole keyboard container -> imeBounds width is the gradient length (vertical).
                boardPaint.shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    imeBounds.width().coerceAtLeast(1f),
                    intArrayOf(
                        colors?.getOrNull(0) ?: effect.color,
                        colors?.getOrNull(1) ?: effect.color
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                tempMatrix.reset()
                tempMatrix.setRotate(af * 360f)
                boardPaint.shader?.setLocalMatrix(tempMatrix)

                tempPath.reset()
                pathMeasure.getSegment(0f, totalLen * af, tempPath, true)
                canvas.drawPath(tempPath, boardPaint)
                boardPaint.shader = null
                boardPaint.alpha = 255
            }

            BoardEffectType.PAINT_GUN -> {
                // BoardPaintGunEffector (2000ms, SINE_IN_OUT80):
                //   onTouchDown: returns immediately when the view size is invalid;
                //                paint.setStyle(FILL); paint.setColorFilter(PorterDuff(SRC_IN, randomColor));
                //                desSize = min(w,h) * randomFloat(minScale=0.3, maxScale=0.7)
                //                rotate = randomRange(-100, 100)
                //   onBlending:  canvas.translate(downX, downY); canvas.rotate(rotate);
                //                paint.alpha = PaintGunAlpha(af)
                //                canvas.drawBitmap(bitmap, null, getRect(desSize), paint)
                //   getRect(desSize): t = min(af*5, 1); s = t*desSize; return RectF(-s/2, -s/2, s/2, s/2)
                //     -> square centered on the touch point, side growing from 0 to desSize.
                //
                // Assets match Samsung: one of the 100dp vectors ic_paint_gun_1..4 picked at each press,
                // rasterized at intrinsic size and tinted SRC_IN with this effect's random color.
                val t = min(af * 5f, 1f)
                val side = t * effect.paintGunSize
                if (side <= 0f) return
                val half = side / 2f
                val alpha = (paintGunAlpha(af) * effect.alphaRatio).toInt().coerceIn(0, 255)
                val bitmap = effect.paintGunBitmap ?: return

                val saved = canvas.save()
                canvas.translate(effect.originX, effect.originY)
                canvas.rotate(effect.paintGunRotation)

                boardPaint.style = Paint.Style.FILL
                boardPaint.alpha = alpha
                boardPaint.shader = null
                boardPaint.colorFilter = PorterDuffColorFilter(effect.color, PorterDuff.Mode.SRC_IN)
                tempRect.set(-half, -half, half, half)
                canvas.drawBitmap(bitmap, null, tempRect, boardPaint)

                canvas.restoreToCount(saved)
                boardPaint.colorFilter = null
                boardPaint.alpha = 255
            }

            BoardEffectType.FIRE_CRACKER -> {
                // BoardFireCrackerEffector (1500ms, SINE_IN_OUT80, `ofFloat(0.1f, 1.0f)`):
                //   paint = new Paint(ANTI_ALIAS); paint.style = FILL;
                //   onTouchDown: colorFilter = PorterDuffColorFilter(randomColor, SRC_IN)
                //   onBlending:
                //     alpha = LinearDecreaseAlpha(af, 175)
                //     f2 = af * 80                       <- fuse/burst radius (raw px, density not applied)
                //     canvas.translate(downX, downY)
                //     if (f2 < 15) canvas.drawCircle(0, 0, f2, paint)        <- fuse phase: solid center circle
                //     else for (angle of [0,45,...,315]) {                    <- burst in 8 directions
                //       paint.setColorFilter(randomColor, SRC_IN)
                //       matrix.reset()
                //       matrix.postRotate(angle, bitmap.w/2, -f2)     <- rotate around (bitmap.w/2, -f2)
                //       matrix.postTranslate(-bitmap.w/2, f2)          <- then translate (-w/2, +f2)
                //       canvas.drawBitmap(bitmap, matrix, paint)        <- 6dp solid oval particle
                //     }
                //
                val fuseRadius = af * BOARD_FIRE_CRACKER_FUSE_RADIUS * effect.rangeRatio
                val alpha = (linearDecreaseAlpha(af, BOARD_FIRE_CRACKER_ALPHA) * effect.alphaRatio).toInt().coerceIn(0, 255)
                val bitmap = effect.fireCrackerBitmap ?: return
                val palette = effect.fireCrackerPalette ?: return

                val saved = canvas.save()
                // Coordinate space: canvas origin = rootView top-left, originX/Y already a
                // rootView-space touch point (= Samsung view-space downX/downY); no imeBounds translation.
                canvas.translate(effect.originX, effect.originY)

                boardPaint.style = Paint.Style.FILL
                boardPaint.shader = null
                boardPaint.alpha = alpha

                if (fuseRadius < BOARD_FIRE_CRACKER_FUSE_THRESHOLD) {
                    // Fuse phase: solid center circle (color = the random color set in onTouchDown).
                    boardPaint.colorFilter = PorterDuffColorFilter(effect.color, PorterDuff.Mode.SRC_IN)
                    canvas.drawCircle(0f, 0f, fuseRadius.coerceAtLeast(0f), boardPaint)
                } else {
                    // Samsung's original logic: re-pick the color per direction and frame, and apply
                    // the same postRotate/postTranslate transform to the source bitmap.
                    for (angle in BOARD_FIRE_CRACKER_ANGLES) {
                        boardPaint.colorFilter = PorterDuffColorFilter(
                            pickSamsungEffectColor(palette),
                            PorterDuff.Mode.SRC_IN
                        )
                        tempMatrix.reset()
                        tempMatrix.postRotate(
                            angle,
                            bitmap.width / 2f,
                            -fuseRadius
                        )
                        tempMatrix.postTranslate(
                            -bitmap.width / 2f,
                            fuseRadius
                        )
                        canvas.drawBitmap(bitmap, tempMatrix, boardPaint)
                    }
                }

                canvas.restoreToCount(saved)
                boardPaint.colorFilter = null
                boardPaint.alpha = 255
            }

            BoardEffectType.NONE -> {
                // No-op
            }
        }
    }

    /**
     * Samsung `PaintGunBitmapData.get()` counterpart: random index set at touch time; vector
     * rasterized at 100dp intrinsic size on first hit only. Unavailable resource → fail closed,
     * no fallback to an approximate programmatic shape.
     */
    private fun paintGunBitmap(index: Int): Bitmap? {
        paintGunBitmaps[index]?.let { return it }
        if (paintGunResourcesUnavailable) return null
        val view = viewRef?.get() ?: return null
        return try {
            val resources = OplusWorkshopUiText.getModuleResources(view.context) ?: run {
                paintGunResourcesUnavailable = true
                Log.w("OplusKeyboardEffects", "PAINT_GUN module resources unavailable")
                return null
            }
            val drawable = resources.getDrawable(BOARD_PAINT_GUN_DRAWABLES[index], null) ?: return null
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val bitmapCanvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(bitmapCanvas)
            paintGunBitmaps[index] = bitmap
            bitmap
        } catch (t: Throwable) {
            paintGunResourcesUnavailable = true
            Log.w("OplusKeyboardEffects", "PAINT_GUN resource render failed", t)
            null
        }
    }

    /**
     * Samsung `FireCrackerBitmapData` counterpart: rasterizes the 6dp oval drawable at the
     * current density once; every later FIRE_CRACKER effect reuses that bitmap.
     */
    private fun getFireCrackerBitmap(): Bitmap? {
        fireCrackerBitmap?.let { return it }
        if (fireCrackerResourcesUnavailable) return null
        val view = viewRef?.get() ?: return null
        return try {
            val resources = OplusWorkshopUiText.getModuleResources(view.context) ?: run {
                fireCrackerResourcesUnavailable = true
                Log.w("OplusKeyboardEffects", "FIRE_CRACKER module resources unavailable")
                return null
            }
            val drawable = resources.getDrawable(R.drawable.fire_cracker, null) ?: return null
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val bitmapCanvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(bitmapCanvas)
            fireCrackerBitmap = bitmap
            bitmap
        } catch (t: Throwable) {
            fireCrackerResourcesUnavailable = true
            Log.w("OplusKeyboardEffects", "FIRE_CRACKER resource render failed", t)
            null
        }
    }

    /**
     * `BoardSparkEffector.drawDots`: 5 rounded-rectangle particles, 72° angular spacing.
     *
     * `for i in 0..4: x = centerX + cos(72*i - angleDelta)*radius; y = centerY + sin(72*i - angleDelta)*radius;
     *  canvas.save(); updateCanvasMatrix(canvas, x, y); drawRoundRect(x-h, y-h, x+h, y+h, corner, corner, paint); canvas.restore()`
     *
     * `updateCanvasMatrix` = `camera.rotateZ(af*90 + fromRotateZ)` then pre/postTranslate around the particle center.
     */
    private fun drawSparkDots(
        canvas: Canvas,
        effect: BoardEffect,
        radius: Float,
        angleDelta: Float,
        halfSide: Float,
        corner: Float,
        af: Float
    ) {
        val centerX = effect.originX
        val centerY = effect.originY
        for (i in 0 until BOARD_SPARK_DOTS_COUNT) {
            val angleDeg = BOARD_SPARK_DOT_ANGLE * i.toFloat() - angleDelta
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val x = centerX + (radius * cos(angleRad)).toFloat()
            val y = centerY + (radius * sin(angleRad)).toFloat()

            canvas.save()
            // Samsung: camera.rotateZ(af*90 + fromRotateZ) then preTranslate/postTranslate around (x,y).
            // 2D equivalent of a pure Z rotation: rotate (af*90 + fromRotateZ) around (x,y).
            canvas.rotate(af * 90f + effect.sparkFromRotateZ, x, y)
            tempRect.set(x - halfSide, y - halfSide, x + halfSide, y + halfSide)
            canvas.drawRoundRect(tempRect, corner, corner, boardPaint)
            canvas.restore()
        }
    }

    // ── Key Effect Renderers ──

    /**
     * Draws a single key effect; the draw region is clipped to the key's rounded rectangle.
     *
     * Samsung `HoneyTeaKeyView` calls `setClipToOutline(blendingEffect != NONE)` on construct
     * and settings change; key blending draws are then clipped by the key background outline
     * (rounded rectangle). This overlay has no host key View and clips explicitly to the same
     * rounded rectangle. Corner radius = Samsung constant
     * `honey_tea_key_outline_effect_corner_radius = 8dp`.
     *
     * Every effect draws inside `bounds` (OUTLINE insets its stroke by halfStroke); clipping
     * to `bounds` cuts no valid drawing. An effect drawing outward requires re-evaluating the
     * clip region.
     */
    private fun drawKeyEffect(
        canvas: Canvas,
        effect: KeyEffect,
        progress: Float,
        now: Long
    ) {
        if (effect.alphaRatio <= 0.001f) return
        val bounds = effect.keyBounds

        val saved = canvas.save()
        val motion = effect.motionEntity?.let { entity ->
            activeMotions.firstOrNull { isSameMotionEntity(it, entity) }
        }
        if (motion != null) {
            applyMotionTransform(canvas, motion, bounds, now)
        }
        keyClipPath.reset()
        val radius = KEY_CLIP_CORNER_DP * effect.density
        keyClipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        canvas.clipPath(keyClipPath)

        try {
            drawKeyEffectContent(canvas, effect, progress)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun drawKeyEffectContent(canvas: Canvas, effect: KeyEffect, progress: Float) {
        val bounds = effect.keyBounds

        when (effect.type) {
            KeyEffectType.COLOR_FLASH -> {
                // Samsung KeyColorFlashEffector (1000ms, DecelerateInterpolator):
                //   af = getAnimatedFraction() = DecelerateInterpolator(linear)
                //   alpha = LinearDecreaseAlpha(af, 255)
                //   drawRect(0, 0, view.getWidth(), view.getHeight(), paint)   ← full-key fill in base color
                val af = afDecel(progress)
                val alpha = (linearDecreaseAlpha(af, 255) * effect.alphaRatio).toInt().coerceIn(0, 255)

                keyPaint.style = Paint.Style.FILL
                keyPaint.shader = null
                keyPaint.color = effect.color
                keyPaint.alpha = alpha

                canvas.drawRect(bounds, keyPaint)
            }

            KeyEffectType.OUTLINE -> {
                // Samsung KeyOutlineEffector (1200ms, SINE_IN_OUT80):
                //   outlineDrawable = honeytea_key_effect_outline (GradientDrawable)
                //   strokeWidth = honey_tea_key_outline_effect_stroke_width = 6dp
                //   setBounds(-stroke/2, -stroke/2, w + stroke/2, h + stroke/2)  ← stroke centerline on the key edge
                //   setStroke(strokePx, randomColor)
                //   alpha = LinearDecreaseAlpha(af, 255)
                //
                // The host has no key View outline clip; the stroke rect insets by halfStroke,
                // keeping the whole stroke inside the key rectangle (the rounded clip would
                // cut its outer half).
                val af = afSine(progress)
                val alpha = (linearDecreaseAlpha(af, 255) * effect.alphaRatio).toInt().coerceIn(0, 255)
                // Samsung: dimensionPixelOffset = getDimensionPixelOffset(...), i.e. (int)(6*density).
                val strokeWidth = dp(KEY_OUTLINE_STROKE_DP, effect.density) * effect.rangeRatio
                val cornerRadius = dp(KEY_OUTLINE_CORNER_DP, effect.density)
                val halfStroke = strokeWidth * 0.5f

                keyPaint.style = Paint.Style.STROKE
                keyPaint.strokeWidth = strokeWidth
                keyPaint.shader = null
                keyPaint.color = effect.color
                keyPaint.alpha = alpha

                tempRect.set(
                    bounds.left + halfStroke,
                    bounds.top + halfStroke,
                    bounds.right - halfStroke,
                    bounds.bottom - halfStroke
                )
                canvas.drawRoundRect(tempRect, cornerRadius, cornerRadius, keyPaint)
            }

            KeyEffectType.BLOB -> {
                // Samsung KeyBlobEffector: 5 blobs jitter with Brownian motion around a random pivot.
                // Background = full rect of lighter(color, 0.3), blob radius (1-f)*fMin, alpha coefficient 255.
                val blobState = effect.blobState
                if (blobState != null) {
                    drawBlobFamily(
                        canvas = canvas,
                        effect = effect,
                        progress = progress,
                        blobState = blobState,
                        alphaCoef = KEY_BLOB_ALPHA,
                        spread = false
                    )
                }
            }

            KeyEffectType.BLOB_SPREAD -> {
                // Samsung KeyBlobSpreadEffector: canvas translates to the touch point, blob radius f*fMin;
                // background also lighter(color, 0.3), alpha coefficient 125.
                val blobState = effect.blobState
                if (blobState != null) {
                    drawBlobFamily(
                        canvas = canvas,
                        effect = effect,
                        progress = progress,
                        blobState = blobState,
                        alphaCoef = KEY_BLOB_SPREAD_ALPHA,
                        spread = true
                    )
                }
            }

            KeyEffectType.SPARK_SPIN -> {
                // Samsung KeySparkEffector (1200ms, SINE_IN_OUT80), **key-size based**:
                //   drawBgPaint.color = lighter(color, 0.3)
                //   drawBgPaint.alpha = LinearDecreaseAlpha(af, 255)
                //   drawRect(0, 0, keyW, keyH, drawBgPaint)                    ← full-key light background
                //   drawPaint.shader = RadialGradient(downX, downY, hypot(keyW,keyH), [color, 0])
                //   drawPaint.alpha  = round(lerp(255, 0, af))
                //   anim = ofFloat(0, hypot(keyW,keyH)/2) → currentAnimRadius
                //   iX = round(MAX_DOT_LOC_ROTATION(45) * af)
                //   drawDots(center, iS - iX, currentAnimRadius)
                //   drawDots(center, DOT_ANGLE(120)/2 + iS + iX, currentAnimRadius/2)
                // Particles: x = cos(DOT_ANGLE*i - angleDelta)*radius + centerX (y uses sin);
                //   halfSide = lerp(fromDotRadius, toDotRadius, af) (toDotRadius = fromDotRadius*0.5);
                //   camera.rotateZ(af*90 + fromRotateZ); drawRoundRect(x±halfSide, y±halfSide, dp(5))
                val af = afSine(progress)
                val bgAlpha = (linearDecreaseAlpha(af, 255) * effect.alphaRatio).toInt().coerceIn(0, 255)
                val maxOrbit = effect.sparkOrbitRadius
                val currentAnimRadius = maxOrbit * af
                val fromDot = effect.sparkFromDotRadius
                val toDot = fromDot * KEY_SPARK_TO_RELATIVE_SCALE
                val halfSide = fromDot + (toDot - fromDot) * af
                val corner = dp(KEY_SPARK_CORNER_DP, effect.density)
                val iX = Math.round(KEY_SPARK_MAX_DOT_LOC_ROTATION * af).toFloat()

                // 1. Full-key light background (alpha = LinearDecreaseAlpha(af,255), same as Samsung).
                keyPaint.style = Paint.Style.FILL
                keyPaint.shader = null
                keyPaint.color = effect.sparkBgColor
                keyPaint.alpha = bgAlpha
                canvas.drawRect(bounds, keyPaint)

                // 2. Radial gradient at the touch point (alpha = round(lerp(255, 0, af))).
                val sparkAlpha = (Math.round(255f + (0f - 255f) * af) * effect.alphaRatio).toInt().coerceIn(0, 255)
                keyPaint.style = Paint.Style.FILL
                keyPaint.color = effect.color
                keyPaint.alpha = sparkAlpha
                keyPaint.shader = RadialGradient(
                    effect.originX,
                    effect.originY,
                    max(1f, effect.sparkShaderRadius),
                    intArrayOf(effect.color, 0x00000000),
                    null,
                    Shader.TileMode.CLAMP
                )

                val saved = canvas.save()
                // Coordinate space: originX/Y are rootView-space touch points (equivalent to Samsung key-view downX/downY).
                val iS = effect.sparkAngleDelta
                drawKeySparkDots(canvas, effect, currentAnimRadius, iS - iX, halfSide, corner, af)
                drawKeySparkDots(
                    canvas, effect, currentAnimRadius / 2f,
                    KEY_SPARK_DOT_ANGLE / 2f + iS + iX, halfSide, corner, af
                )
                canvas.restoreToCount(saved)

                keyPaint.shader = null
                keyPaint.alpha = 255
            }

            KeyEffectType.FOOT_PRINT -> {
                // Samsung KeyFootPrintEffector (2000ms, SINE_IN_OUT80):
                //   when isCustomIcon()==false: paint.setColorFilter(PorterDuffColorFilter(color, SRC_IN))
                //     ← built-in icons tinted; **custom icons not tinted** (drawn as-is)
                //   fS = randomRange(-30, 30)
                //   iMin = (int)(min(keyW, keyH) * 0.75f)
                //   bmp = createScaledBitmap(iconBitmap, iMin, iMin, false)      ← scaled once on press
                //   onBlending: alpha = LinearDecreaseAlpha(af, 175);
                //     translate(downX, downY); rotate(fS); drawBitmap(bmp, -iMin/2, -iMin/2, paint)
                val af = afSine(progress)
                val size = effect.footPrintSize
                if (size > 0) {
                    val icon = FootPrintIcon.fromKey(
                        viewRef?.get()?.context?.let { OplusInputPrefs.getFootPrintIcon(it) } ?: "CAT"
                    )
                    val bmp = footPrintBitmap(icon, size)
                    if (bmp != null) {
                        val saved = canvas.save()
                        canvas.translate(effect.originX, effect.originY)
                        canvas.rotate(effect.footPrintRotation)

                        keyPaint.style = Paint.Style.FILL
                        keyPaint.shader = null
                        // Built-in monochrome vector: SRC_IN tinting fills with the effect color;
                        // custom PNG stays untinted per Samsung semantics (original colors kept).
                        keyPaint.colorFilter = if (icon.isCustom) {
                            null
                        } else {
                            PorterDuffColorFilter(effect.color, PorterDuff.Mode.SRC_IN)
                        }
                        keyPaint.alpha = (linearDecreaseAlpha(af, KEY_FOOT_PRINT_ALPHA) * effect.alphaRatio).toInt().coerceIn(0, 255)
                        val half = size / 2f
                        tempRect.set(-half, -half, half, half)
                        canvas.drawBitmap(bmp, null, tempRect, keyPaint)

                        keyPaint.colorFilter = null
                        keyPaint.alpha = 255
                        canvas.restoreToCount(saved)
                    }
                }
            }

            KeyEffectType.NONE -> {
                // No-op
            }
        }
    }

    /**
     * Shared rendering for Key BLOB / BLOB_SPREAD (1:1 port of Samsung `KeyBlobEffector` /
     * `KeyBlobSpreadEffector`, both 1200ms + SINE_IN_OUT80).
     *
     * `af = getAnimatedFraction() = SINE_IN_OUT80(linear)`; the whole effect derives from `af`.
     *
     * Per frame (mirrors onBlending in the source file):
     * 1. `alpha = LinearDecreaseAlpha(af, coef)` (BLOB 255 / BLOB_SPREAD 125)
     * 2. Background: `drawBgPaint.color = lighter(randomColor, 0.3)`; `setAlpha(alpha)`;
     *    `drawRect(0, 0, w, h, drawBgPaint)`
     * 3. `r = (1 - af) * fMin` (BLOB, shrinks) or `af * fMin` (BLOB_SPREAD, expands)
     * 4. `canvas.translate(pivotX, pivotY)` (BLOB) or `(downX, downY)` (BLOB_SPREAD)
     * 5. 5 blobs: `m.update(); paint.setAlpha(alpha); canvas.translate(m.x, m.y);
     *    drawCircle(r/2, r/2, r, paint)`, paint.shader = `RadialGradient(fMin/2, fMin/2, max(1,fMin), [color,0])`
     *
     * **Coordinate space**: Samsung's key effector canvas = the key itself (origin at key
     * top-left); `pivotX/Y` (`randomRange(w/4,w/3)`) are **key-local** coordinates. This
     * overlay's canvas origin = rootView top-left, so BLOB adds `keyBounds.left/top` on the
     * pivot translate; BLOB_SPREAD's `originX/Y` are already rootView-space touch points and
     * are used directly.
     */
    private fun drawBlobFamily(
        canvas: Canvas,
        effect: KeyEffect,
        progress: Float,
        blobState: KeyBlobState,
        alphaCoef: Int,
        spread: Boolean
    ) {
        // Samsung animationFraction = getAnimatedFraction() = interpolated fraction.
        val af = afSine(progress)
        val alpha = (linearDecreaseAlpha(af, alphaCoef) * effect.alphaRatio).toInt().coerceIn(0, 255)

        // Full background rect (drawBgPaint): lighter(randomColor, 0.3), alpha same as blobs.
        // Samsung draws drawRect(0,0,w,h) on the key view; the key rectangle stands in here (both key-outline clipped).
        keyPaint.style = Paint.Style.FILL
        keyPaint.shader = null
        keyPaint.color = lighter(effect.color, KEY_BLOB_BG_LIGHTER)
        keyPaint.alpha = alpha
        canvas.drawRect(effect.keyBounds, keyPaint)
        keyPaint.alpha = 255

        // Advances the Brownian timeline (once per frame, shared across the key's blobs).
        blobState.update()

        val fMin = blobState.fMin
        val r = if (spread) af * fMin else (1f - af) * fMin

        // Translates to pivot (BLOB) or to the touch point (BLOB_SPREAD).
        // Coordinate space: BLOB_SPREAD originX/Y are rootView-space touch points, same
        // semantics as Samsung key-view downX/downY (canvas origin = rootView top-left).
        // BLOB pivotX/Y = `randomRange(w/4,w/3)` taken on the **key's own canvas**, i.e.
        // **key-local**; the key's rootView top-left offset is added, or blobs land at the
        // rootView top-left instead of on the key.
        val saved = canvas.save()
        val tx = if (spread) effect.originX else effect.keyBounds.left + blobState.pivotX
        val ty = if (spread) effect.originY else effect.keyBounds.top + blobState.pivotY
        canvas.translate(tx, ty)

        // 5 blobs (shader built at effect creation, reused every frame).
        keyPaint.style = Paint.Style.FILL
        keyPaint.shader = blobState.blobShader
        keyPaint.alpha = alpha

        for (i in 0 until blobState.dotCount) {
            val m = blobState.motions[i]
            canvas.save()
            canvas.translate(m.x, m.y)
            canvas.drawCircle(r / 2f, r / 2f, r, keyPaint)
            canvas.restore()
        }

        canvas.restoreToCount(saved)
        keyPaint.shader = null
        keyPaint.alpha = 255
    }

    /**
     * `KeySparkEffector.drawDots`: 3 rounded-rectangle particles, `DOT_ANGLE = 120°` angular spacing.
     *
     * `for i in 0 until 3: x = cos(DOT_ANGLE*i - angleDelta)*radius + centerX` (y uses sin);
     * `halfSide = lerp(fromDotRadius, toDotRadius, af)`;
     * `camera.rotateZ(af*90 + fromRotateZ)` rotates around the particle center → 2D equivalent `canvas.rotate(af*90 + fromRotateZ, x, y)`;
     * `drawRoundRect(x-halfSide, y-halfSide, x+halfSide, y+halfSide, dp(5))`.
     */
    private fun drawKeySparkDots(
        canvas: Canvas,
        effect: KeyEffect,
        radius: Float,
        angleDelta: Float,
        halfSide: Float,
        corner: Float,
        af: Float
    ) {
        for (i in 0 until KEY_SPARK_DOTS_COUNT) {
            val angleDeg = KEY_SPARK_DOT_ANGLE * i.toFloat() - angleDelta
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val x = effect.originX + (radius * cos(angleRad)).toFloat()
            val y = effect.originY + (radius * sin(angleRad)).toFloat()

            canvas.save()
            canvas.rotate(af * KEY_SPARK_MAX_DOT_ITSELF_ROTATION + effect.sparkFromRotateZ, x, y)
            tempRect.set(x - halfSide, y - halfSide, x + halfSide, y + halfSide)
            canvas.drawRoundRect(tempRect, corner, corner, keyPaint)
            canvas.restore()
        }
    }

    /**
     * Returns the FOOT_PRINT icon bitmap (scaled for the current icon + `size`, cached).
     *
     * Samsung uses `FootPrintIconProvider`: 9 built-in vector icons (default `foot_print_cat`)
     * or a user PNG. On press: `createScaledBitmap(iconBitmap, size, size, false)`.
     *
     * - Built-in icon: rendered to Bitmap via `res.getDrawable(drawableRes)` (**module APK** resources).
     * - `CUSTOM`: PNG from `OplusInputPrefs.getCustomFootPrintFile`; missing file → CAT.
     *
     * Fail-closed: any failure returns null (caller draws nothing).
     */
    private fun footPrintBitmap(icon: FootPrintIcon, size: Int): Bitmap? {
        footPrintCache?.let { if (it.first == icon.key && it.second == size) return it.third }
        val view = viewRef?.get() ?: return null
        return try {
            val bmp = if (icon.isCustom && OplusInputPrefs.hasCustomFootPrint(view.context)) {
                // Custom icon: decode the user PNG directly (Samsung does not tint it; scale only here).
                val f = OplusInputPrefs.getCustomFootPrintFile(view.context)
                val src = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: return null
                val scaled = Bitmap.createScaledBitmap(src, size, size, true)
                if (scaled !== src) src.recycle()
                scaled
            } else {
                // Built-in vector: module APK resources, resolved in the host process via OplusWorkshopUiText.
                // view.resources is wrong here — it is the host keyboard's own resource table and lacks module R.drawable.*.
                val res = OplusWorkshopUiText.getModuleResources(view.context) ?: return null
                val drawable = res.getDrawable(icon.drawableRes, null) ?: return null
                val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(c)
                b
            }
            footPrintCache = Triple(icon.key, size, bmp)
            bmp
        } catch (t: Throwable) {
            Log.w("OplusKeyboardEffects", "FootPrint icon render failed", t)
            null
        }
    }

    /**
     * O(1) Geometry estimation for soft key bounding box on keyboard View.
     *
     * Full-board reference frame for background blending only; **never** a key-face fallback
     * for key blending. Real key faces come from [PerKeyBoundsProvider] (entity rectangles);
     * resolution failure → no key effect drawn, and this estimate never stands in for a key
     * face (see AGENTS.md: fail closed).
     */
    private fun estimateKeyBounds(
        viewWidth: Float,
        viewHeight: Float,
        touchX: Float,
        touchY: Float
    ): RectF {
        val rowHeight = (viewHeight / 4.15f).coerceAtLeast(36f)
        val row = (touchY / rowHeight).toInt().coerceIn(0, 3)
        val rowTop = row * rowHeight + 4f
        val rowBottom = (row + 1) * rowHeight - 4f

        val keyWidth = (viewWidth / 10f).coerceAtLeast(30f)
        var left: Float
        var right: Float

        when (row) {
            0 -> {
                // Top row: Q W E R T Y U I O P (10 keys)
                val col = (touchX / keyWidth).toInt().coerceIn(0, 9)
                left = col * keyWidth + 3f
                right = (col + 1) * keyWidth - 3f
            }

            1 -> {
                // Second row: A S D F G H J K L (9 keys, half key indent)
                val indent = keyWidth * 0.5f
                val col = ((touchX - indent) / keyWidth).toInt().coerceIn(0, 8)
                left = indent + col * keyWidth + 3f
                right = indent + (col + 1) * keyWidth - 3f
            }

            2 -> {
                // Third row: Shift (1.4x) + 7 keys + Del (1.4x)
                val specialW = keyWidth * 1.4f
                if (touchX < specialW) {
                    left = 3f
                    right = specialW - 3f
                } else if (touchX > viewWidth - specialW) {
                    left = viewWidth - specialW + 3f
                    right = viewWidth - 3f
                } else {
                    val col = ((touchX - specialW) / keyWidth).toInt().coerceIn(0, 6)
                    left = specialW + col * keyWidth + 3f
                    right = specialW + (col + 1) * keyWidth - 3f
                }
            }

            else -> {
                // Bottom row: Space bar in the middle
                val sideW = keyWidth * 1.5f
                if (touchX < sideW) {
                    left = 3f
                    right = sideW - 3f
                } else if (touchX > viewWidth - sideW) {
                    left = viewWidth - sideW + 3f
                    right = viewWidth - 3f
                } else {
                    val spaceLeft = sideW + 6f
                    val spaceRight = viewWidth - sideW - 6f
                    if (touchX in (spaceLeft + keyWidth)..(spaceRight - keyWidth)) {
                        left = spaceLeft + keyWidth
                        right = spaceRight - keyWidth
                    } else if (touchX < spaceLeft + keyWidth) {
                        left = spaceLeft
                        right = spaceLeft + keyWidth - 3f
                    } else {
                        left = spaceRight - keyWidth + 3f
                        right = spaceRight
                    }
                }
            }
        }

        return RectF(
            left.coerceAtLeast(0f),
            rowTop.coerceAtLeast(0f),
            right.coerceAtMost(viewWidth),
            rowBottom.coerceAtMost(viewHeight)
        )
    }
}
