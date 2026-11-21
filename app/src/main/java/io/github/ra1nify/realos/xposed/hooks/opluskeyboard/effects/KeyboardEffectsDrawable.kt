package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs

/**
 * Overlay Drawable attached to the window rootView via Android's native View.getOverlay().
 *
 * Draws transient effects on the overlay layer; key motion redraws the host keyboard buffer.
 * Drawing is clipped to the IME viewport with rounded top corners matching the toolbar.
 */
class KeyboardEffectsDrawable(
    private val hostView: View
) : Drawable() {

    private val controller = OplusKeyboardEffectController()
    private var isAnimating = false

    // Pre-allocated coordinate buffers, bounds, and path reused across touch events
    private val locKeyboard = IntArray(2)
    private val locHost = IntArray(2)
    private val locChild = IntArray(2)
    private val imeBounds = RectF()
    private val imeClipPath = Path()

    /**
     * Host superellipse exponent for smooth corners (= COUI `setWeight(3.0f)`); the corner
     * boundary satisfies |qx|^n + |qy|^n = R^n (host AGSL `sdRoundRect(p, b, corner, n = weight)`).
     */
    private val smoothCornerN = 3.0f

    /** Sample segments per corner (superellipse approximated by a polyline). */
    private val cornerSegments = 12

    /** Precomputed unit-corner sample table: (cos u)^(2/n), (sin u)^(2/n), u ∈ [0, π/2]. */
    private val cornerUnit = FloatArray((cornerSegments + 1) * 2).also { arr ->
        for (i in 0..cornerSegments) {
            val u = (Math.PI / 2) * i / cornerSegments
            arr[i * 2] = Math.pow(Math.cos(u), 2.0 / smoothCornerN).toFloat()
            arr[i * 2 + 1] = Math.pow(Math.sin(u), 2.0 / smoothCornerN).toFloat()
        }
    }

    private val frameCallback: Runnable = object : Runnable {
        override fun run() {
            if (!isAnimating) return
            ensureBounds()
            invalidateSelf()
            if (controller.hasActiveEffects()) {
                hostView.postOnAnimation(this)
            } else {
                isAnimating = false
            }
        }
    }

    init {
        ensureBounds()
    }

    private fun ensureBounds() {
        val w = hostView.width
        val h = hostView.height
        if (w > 0 && h > 0 && (bounds.width() != w || bounds.height() != h)) {
            setBounds(0, 0, w, h)
        }
    }

    override fun draw(canvas: Canvas) {
        if (imeBounds.isEmpty) return
        canvas.save()
        canvas.clipPath(imeClipPath)
        controller.draw(hostView, imeBounds, canvas)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private var lastKbW = -1
    private var lastKbH = -1
    private var lastHostW = -1
    private var lastHostH = -1
    private var lastKbLocX = -1
    private var lastKbLocY = -1
    private var lastHostLocX = -1
    private var lastHostLocY = -1
    private var cachedOffsetX = 0f
    private var cachedOffsetY = 0f

    fun onTouch(keyboardView: View, event: MotionEvent, isCharacterInputMode: Boolean) {
        ensureBounds()
        updateImeBounds(keyboardView)

        controller.onTouch(keyboardView, cachedOffsetX, cachedOffsetY, event, isCharacterInputMode)
        if (controller.hasActiveEffects()) {
            if (!isAnimating) {
                isAnimating = true
                hostView.postOnAnimation(frameCallback)
            }
        }
        invalidateSelf()
    }

    /**
     * Sets the per-key key-face provider. Pass null when unavailable; key blending fail-closes.
     */
    fun setPerKeyBoundsProvider(provider: OplusKeyboardEffectController.PerKeyBoundsProvider?) {
        controller.setPerKeyBoundsProvider(provider)
    }

    fun beginKeyboardDrawFrame(keyboardView: View? = null) {
        controller.beginKeyboardDrawFrame(keyboardView)
    }

    fun endKeyboardDrawFrame() {
        controller.endKeyboardDrawFrame()
    }

    fun partitionMotionEntities(
        source: List<*>,
        inactive: MutableList<Any?>,
        active: MutableList<Any?>
    ): Boolean = controller.partitionMotionEntities(source, inactive, active)

    fun isMotionActive(entity: Any): Boolean = controller.isMotionActive(entity)

    fun beginKeyDraw(canvas: Canvas, entity: Any): Int? = controller.beginKeyDraw(canvas, entity)

    fun endKeyDraw(canvas: Canvas, saveCount: Int?) {
        controller.endKeyDraw(canvas, saveCount)
    }

    /**
     * Determines the bounding box covering the toolbar, keyboard, and bottom insets.
     * Effects draw only inside that box, never over the application area above the keyboard.
     */
    private fun updateImeBounds(keyboardView: View) {
        val kbW = keyboardView.width
        val kbH = keyboardView.height
        val hostW = hostView.width
        val hostH = hostView.height
        if (hostW <= 0 || hostH <= 0 || kbW <= 0 || kbH <= 0) return

        keyboardView.getLocationInWindow(locKeyboard)
        hostView.getLocationInWindow(locHost)

        val kbLocX = locKeyboard[0]
        val kbLocY = locKeyboard[1]
        val hostLocX = locHost[0]
        val hostLocY = locHost[1]

        cachedOffsetX = (kbLocX - hostLocX).toFloat()
        cachedOffsetY = (kbLocY - hostLocY).toFloat()

        if (kbW == lastKbW && kbH == lastKbH &&
            hostW == lastHostW && hostH == lastHostH &&
            kbLocX == lastKbLocX && kbLocY == lastKbLocY &&
            hostLocX == lastHostLocX && hostLocY == lastHostLocY &&
            !imeBounds.isEmpty
        ) {
            return
        }

        lastKbW = kbW
        lastKbH = kbH
        lastHostW = hostW
        lastHostH = hostH
        lastKbLocX = kbLocX
        lastKbLocY = kbLocY
        lastHostLocX = hostLocX
        lastHostLocY = hostLocY

        val kbLeft = cachedOffsetX
        val kbTop = cachedOffsetY
        val kbRight = kbLeft + kbW.toFloat()
        val kbBottom = kbTop + kbH.toFloat()

        val density = keyboardView.resources.displayMetrics.density

        // 1. Traverse parent hierarchy upwards to detect the candidate toolbar view's top edge.
        //    Sets only the vertical clip range; the top corner radius comes from step 4.
        var toolbarTop = kbTop
        var current: View? = keyboardView

        while (current != null && current !== hostView) {
            val parent = current.parent as? ViewGroup ?: break
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child !== current && child.visibility == View.VISIBLE && child.height > 0) {
                    child.getLocationInWindow(locChild)
                    val childTop = (locChild[1] - locHost[1]).toFloat()
                    val diff = kbTop - childTop
                    // Candidate toolbar sits immediately above keyboard with realistic height (<= 65% of keyboard height)
                    if (diff > 0f && diff <= keyboardView.height * 0.65f && childTop < toolbarTop) {
                        toolbarTop = childTop
                    }
                }
            }
            current = parent
        }

        // Fallback: If no dedicated toolbar view was discovered, compensate with standard IME toolbar height (~46dp)
        if (toolbarTop >= kbTop) {
            toolbarTop = (kbTop - (46f * density)).coerceAtLeast(0f)
        }

        // 2. Bottom boundary: cover bottom elevation / gesture bar insets if docked near window bottom (within 120dp)
        val hostWFloat = hostW.toFloat()
        val hostHFloat = hostH.toFloat()
        val distToBottom = hostHFloat - kbBottom
        val isDockedBottom = distToBottom <= 120f * density
        val imeBottom = if (isDockedBottom) {
            hostHFloat
        } else {
            kbBottom
        }

        // 3. Horizontal boundary: full width for standard layout, aligned bounds for floating/one-handed mode
        val imeLeft: Float
        val imeRight: Float
        if (keyboardView.width.toFloat() >= hostWFloat * 0.92f) {
            imeLeft = 0f
            imeRight = hostWFloat
        } else {
            imeLeft = kbLeft.coerceAtLeast(0f)
            imeRight = kbRight.coerceAtMost(hostWFloat)
        }

        imeBounds.set(imeLeft, toolbarTop, imeRight, imeBottom)

        // 4. Construct rounded corner path (top-left & top-right rounded, bottom corners square if docked)
        //    Top radius comes from the keyboard panel corner radius (realOS custom corner dp -> px).
        //    The previous heuristic read the radius from any GradientDrawable background and picked up
        //    a 20dp chip radius (host h.a() uses widthPixels/360), leaving a clipped corner notch.
        val topRadius = OplusInputPrefs.getCornerRadius(hostView.context) * density
        val bottomRadius = if (isDockedBottom) 0f else topRadius

        // Same smooth corner as the host: Path.addRoundRect (circular arc) cuts a lens shape out of each corner.
        buildSmoothRoundRectPath(
            imeClipPath,
            imeBounds.left, imeBounds.top, imeBounds.right, imeBounds.bottom,
            topRadius, topRadius, bottomRadius, bottomRadius
        )
    }

    /**
     * Builds a rounded rect path with the host's smooth corner (L^n superellipse), clockwise.
     *
     * The host keyboard panel's blurred background and foreground stroke both use
     * cornerType=NEW_G2 + weight=3.0, whose corner boundary is |qx|^n + |qy|^n = R^n rather
     * than a circular arc. An arc clip cuts a lens shape out of each corner (thickest ~0.12R
     * at 45 degrees), which shows as a notch with no board blending in the corner.
     */
    private fun buildSmoothRoundRectPath(
        path: Path,
        l: Float, t: Float, r: Float, b: Float,
        rTL: Float, rTR: Float, rBR: Float, rBL: Float
    ) {
        path.reset()
        // Corner center (cx,cy); boundary point = center + (sx·R·cu, sy·R·su), (cu,su)=((cos u)^(2/n),(sin u)^(2/n)).
        // AGSL: q = abs(p) - halfSize + r with origin at the corner center; corner region |qx|^n+|qy|^n = R^n.
        fun corner(cx: Float, cy: Float, sx: Float, sy: Float, radius: Float, forward: Boolean) {
            if (radius <= 0.01f) {
                path.lineTo(cx, cy)
                return
            }
            for (i in 0..cornerSegments) {
                val idx = if (forward) i else cornerSegments - i
                path.lineTo(
                    cx + sx * radius * cornerUnit[idx * 2],
                    cy + sy * radius * cornerUnit[idx * 2 + 1]
                )
            }
        }
        path.moveTo(l + rTL, t)
        path.lineTo(r - rTR, t)
        corner(r - rTR, t + rTR, 1f, -1f, rTR, forward = false)   // top-right: (r-R,t) -> (r,t+R)
        path.lineTo(r, b - rBR)
        corner(r - rBR, b - rBR, 1f, 1f, rBR, forward = true)     // bottom-right: (r,b-R) -> (r-R,b)
        path.lineTo(l + rBL, b)
        corner(l + rBL, b - rBL, -1f, 1f, rBL, forward = false)   // bottom-left: (l+R,b) -> (l,b-R)
        path.lineTo(l, t + rTL)
        corner(l + rTL, t + rTL, -1f, -1f, rTL, forward = true)   // top-left: (l,t+R) -> (l+R,t)
        path.close()
    }
}
