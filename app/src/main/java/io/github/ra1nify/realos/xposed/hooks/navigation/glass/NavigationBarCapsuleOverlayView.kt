package io.github.ra1nify.realos.xposed.hooks.navigation.glass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.xposed.hooks.navigation.NavButtonDispatcherViews
import java.lang.ref.WeakReference

/**
 * Multi-view container for the navigation button appearance background capsules.
 */
class NavigationBarCapsuleOverlayView(context: Context) : FrameLayout(context) {

    var capsuleMode: Int = SystemUiConfig.DEFAULT_GLASS_CAPSULE_MODE
        set(value) {
            val coerced = value.coerceIn(0, 4)
            if (field != coerced) {
                field = coerced
                requestLayout()
            }
        }

    var glassOpacity: Int = SystemUiConfig.DEFAULT_GLASS_OPACITY
        set(value) {
            val coerced = value.coerceIn(0, 100)
            if (field != coerced) {
                field = coerced
                capsule1.glassOpacity = coerced
                capsule2.glassOpacity = coerced
                capsule3.glassOpacity = coerced
            }
        }

    var spotlightEnabled: Boolean = SystemUiConfig.DEFAULT_GLASS_SPOTLIGHT_ENABLED
        set(value) {
            if (field != value) {
                field = value
                capsule1.spotlightEnabled = value
                capsule2.spotlightEnabled = value
                capsule3.spotlightEnabled = value
            }
        }

    var lightColor: String = SystemUiConfig.DEFAULT_GLASS_LIGHT_COLOR
        set(value) {
            if (field != value) {
                field = value
                capsule1.lightColor = value
                capsule2.lightColor = value
                capsule3.lightColor = value
            }
        }

    var lightAlpha: Int = SystemUiConfig.DEFAULT_GLASS_LIGHT_ALPHA
        set(value) {
            val coerced = value.coerceIn(10, 100)
            if (field != coerced) {
                field = coerced
                capsule1.lightAlpha = coerced
                capsule2.lightAlpha = coerced
                capsule3.lightAlpha = coerced
            }
        }

    var darkColor: String = SystemUiConfig.DEFAULT_GLASS_DARK_COLOR
        set(value) {
            if (field != value) {
                field = value
                capsule1.darkColor = value
                capsule2.darkColor = value
                capsule3.darkColor = value
            }
        }

    var darkAlpha: Int = SystemUiConfig.DEFAULT_GLASS_DARK_ALPHA
        set(value) {
            val coerced = value.coerceIn(10, 100)
            if (field != coerced) {
                field = coerced
                capsule1.darkAlpha = coerced
                capsule2.darkAlpha = coerced
                capsule3.darkAlpha = coerced
            }
        }

    var darkIntensity: Float = 0f
        set(value) {
            val coerced = value.coerceIn(0f, 1f)
            if (Math.abs(field - coerced) > 0.01f) {
                field = coerced
                capsule1.darkIntensity = coerced
                capsule2.darkIntensity = coerced
                capsule3.darkIntensity = coerced
                postInvalidateOnAnimation()
            }
        }

    private var backViewRef: WeakReference<View>? = null
    private var homeViewRef: WeakReference<View>? = null
    private var recentViewRef: WeakReference<View>? = null

    private var backDispatcherRef: WeakReference<Any>? = null
    private var homeDispatcherRef: WeakReference<Any>? = null
    private var recentDispatcherRef: WeakReference<Any>? = null

    private val capsule1 = ColorOsCapsuleItemView(context)
    private val capsule2 = ColorOsCapsuleItemView(context)
    private val capsule3 = ColorOsCapsuleItemView(context)

    private val rBack = RectF()
    private val rHome = RectF()
    private val rRecent = RectF()
    private val locNav = IntArray(2)
    private val locButton = IntArray(2)

    private var hasLoggedSuccess = false
    private var lastDiagnosticLogTime = 0L

    private val parentLayoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        requestLayout()
    }

    init {
        setWillNotDraw(false)
        addView(capsule1)
        addView(capsule2)
        addView(capsule3)
        capsule1.visibility = GONE
        capsule2.visibility = GONE
        capsule3.visibility = GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ColorOsMaterialBridge.init(context.classLoader)
        (parent as? View)?.addOnLayoutChangeListener(parentLayoutListener)
        requestLayout()
    }

    override fun onDetachedFromWindow() {
        (parent as? View)?.removeOnLayoutChangeListener(parentLayoutListener)
        super.onDetachedFromWindow()
    }

    fun setButtons(back: View?, home: View?, recent: View?) {
        backViewRef = if (back != null) WeakReference(back) else null
        homeViewRef = if (home != null) WeakReference(home) else null
        recentViewRef = if (recent != null) WeakReference(recent) else null
        requestLayout()
    }

    fun setDispatchers(back: Any?, home: Any?, recent: Any?) {
        backDispatcherRef = if (back != null) WeakReference(back) else null
        homeDispatcherRef = if (home != null) WeakReference(home) else null
        recentDispatcherRef = if (recent != null) WeakReference(recent) else null
        requestLayout()
    }

    /**
     * Resolves the button View through [NavButtonDispatcherViews].
     */
    private fun getButtonView(viewRef: WeakReference<View>?, dispatcherRef: WeakReference<Any>?): View? =
        NavButtonDispatcherViews.resolve(viewRef?.get(), dispatcherRef?.get())


    private fun computeRelativeBounds(v: View?, outRect: RectF): Boolean {
        if (v == null || !v.isAttachedToWindow || v.visibility != VISIBLE || v.width <= 0 || v.height <= 0) {
            return false
        }
        v.getLocationInWindow(locButton)
        getLocationInWindow(locNav)

        val l = (locButton[0] - locNav[0]).toFloat()
        val t = (locButton[1] - locNav[1]).toFloat()
        val r = l + v.width
        val b = t + v.height
        outRect.set(l, t, r, b)
        return true
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val totalW = r - l
        val totalH = b - t
        if (capsuleMode == SystemUiConfig.CAPSULE_MODE_NONE || totalW <= 0 || totalH <= 0) {
            capsule1.visibility = GONE
            capsule2.visibility = GONE
            capsule3.visibility = GONE
            return
        }

        val backView = getButtonView(backViewRef, backDispatcherRef)
        val homeView = getButtonView(homeViewRef, homeDispatcherRef)
        val recentView = getButtonView(recentViewRef, recentDispatcherRef)

        val hasBack = computeRelativeBounds(backView, rBack)
        val hasHome = computeRelativeBounds(homeView, rHome)
        val hasRecent = computeRelativeBounds(recentView, rRecent)

        if (!hasBack && !hasHome && !hasRecent) {
            capsule1.visibility = GONE
            capsule2.visibility = GONE
            capsule3.visibility = GONE
            logDiagnosticOnce("onLayout: no button view is ready back=${backView != null} home=${homeView != null} recent=${recentView != null}")
            return
        }

        val refHeight = when {
            hasHome -> rHome.height()
            hasBack -> rBack.height()
            hasRecent -> rRecent.height()
            else -> totalH.toFloat()
        }
        val padY = (refHeight * 0.10f).coerceAtLeast(6f)
        val refTop = when {
            hasHome -> rHome.top
            hasBack -> rBack.top
            hasRecent -> rRecent.top
            else -> 0f
        }
        val refBottom = when {
            hasHome -> rHome.bottom
            hasBack -> rBack.bottom
            hasRecent -> rRecent.bottom
            else -> totalH.toFloat()
        }
        val top = (refTop + padY).toInt()
        val bottom = (refBottom - padY).toInt()

        val refWidth = when {
            hasHome -> rHome.width()
            hasBack -> rBack.width()
            hasRecent -> rRecent.width()
            else -> (totalW / 3f)
        }
        val padX = (refWidth * 0.08f).coerceAtLeast(6f)

        when (capsuleMode) {
            SystemUiConfig.CAPSULE_MODE_SINGLE -> {
                var minL = Float.MAX_VALUE
                var maxR = Float.MIN_VALUE
                if (hasBack) {
                    if (rBack.left < minL) minL = rBack.left
                    if (rBack.right > maxR) maxR = rBack.right
                }
                if (hasHome) {
                    if (rHome.left < minL) minL = rHome.left
                    if (rHome.right > maxR) maxR = rHome.right
                }
                if (hasRecent) {
                    if (rRecent.left < minL) minL = rRecent.left
                    if (rRecent.right > maxR) maxR = rRecent.right
                }
                if (minL < maxR) {
                    capsule1.visibility = VISIBLE
                    capsule1.layout((minL + padX).toInt(), top, (maxR - padX).toInt(), bottom)
                } else {
                    capsule1.visibility = GONE
                }
                capsule2.visibility = GONE
                capsule3.visibility = GONE
            }
            SystemUiConfig.CAPSULE_MODE_TRIPLE -> {
                if (hasBack) {
                    capsule1.visibility = VISIBLE
                    capsule1.layout((rBack.left + padX).toInt(), top, (rBack.right - padX).toInt(), bottom)
                } else {
                    capsule1.visibility = GONE
                }
                if (hasHome) {
                    capsule2.visibility = VISIBLE
                    capsule2.layout((rHome.left + padX).toInt(), top, (rHome.right - padX).toInt(), bottom)
                } else {
                    capsule2.visibility = GONE
                }
                if (hasRecent) {
                    capsule3.visibility = VISIBLE
                    capsule3.layout((rRecent.left + padX).toInt(), top, (rRecent.right - padX).toInt(), bottom)
                } else {
                    capsule3.visibility = GONE
                }
            }
            SystemUiConfig.CAPSULE_MODE_PAIR_BACK_HOME -> {
                if (hasBack && hasHome) {
                    val minL = minOf(rBack.left, rHome.left) + padX
                    val maxR = maxOf(rBack.right, rHome.right) - padX
                    capsule1.visibility = VISIBLE
                    capsule1.layout(minL.toInt(), top, maxR.toInt(), bottom)
                } else if (hasBack) {
                    capsule1.visibility = VISIBLE
                    capsule1.layout((rBack.left + padX).toInt(), top, (rBack.right - padX).toInt(), bottom)
                } else if (hasHome) {
                    capsule1.visibility = VISIBLE
                    capsule1.layout((rHome.left + padX).toInt(), top, (rHome.right - padX).toInt(), bottom)
                } else {
                    capsule1.visibility = GONE
                }

                if (hasRecent) {
                    capsule2.visibility = VISIBLE
                    capsule2.layout((rRecent.left + padX).toInt(), top, (rRecent.right - padX).toInt(), bottom)
                } else {
                    capsule2.visibility = GONE
                }
                capsule3.visibility = GONE
            }
            SystemUiConfig.CAPSULE_MODE_PAIR_HOME_RECENT -> {
                if (hasBack) {
                    capsule1.visibility = VISIBLE
                    capsule1.layout((rBack.left + padX).toInt(), top, (rBack.right - padX).toInt(), bottom)
                } else {
                    capsule1.visibility = GONE
                }

                if (hasHome && hasRecent) {
                    val minL = minOf(rHome.left, rRecent.left) + padX
                    val maxR = maxOf(rHome.right, rRecent.right) - padX
                    capsule2.visibility = VISIBLE
                    capsule2.layout(minL.toInt(), top, maxR.toInt(), bottom)
                } else if (hasHome) {
                    capsule2.visibility = VISIBLE
                    capsule2.layout((rHome.left + padX).toInt(), top, (rHome.right - padX).toInt(), bottom)
                } else if (hasRecent) {
                    capsule2.visibility = VISIBLE
                    capsule2.layout((rRecent.left + padX).toInt(), top, (rRecent.right - padX).toInt(), bottom)
                } else {
                    capsule2.visibility = GONE
                }
                capsule3.visibility = GONE
            }
            else -> {
                capsule1.visibility = GONE
                capsule2.visibility = GONE
                capsule3.visibility = GONE
            }
        }

        logSuccessOnce("onLayout: laid out the native liquid glass capsule mode=$capsuleMode back=$hasBack home=$hasHome recent=$hasRecent")
    }

    fun onPointerEvent(event: MotionEvent) {
        if (capsuleMode == SystemUiConfig.CAPSULE_MODE_NONE || !spotlightEnabled) return

        val x = event.x
        val y = event.y

        val activeCapsules = listOf(capsule1, capsule2, capsule3).filter { it.visibility == VISIBLE && it.width > 0 && it.height > 0 }
        if (activeCapsules.isEmpty()) return

        val hitCapsule = activeCapsules.firstOrNull { it.left <= x && x <= it.right && it.top <= y && y <= it.bottom }
            ?: activeCapsules.minByOrNull {
                val cx = (it.left + it.right) / 2f
                val cy = (it.top + it.bottom) / 2f
                (x - cx) * (x - cx) + (y - cy) * (y - cy)
            }

        if (hitCapsule != null) {
            val localEvent = MotionEvent.obtain(event)
            localEvent.offsetLocation(-hitCapsule.left.toFloat(), -hitCapsule.top.toFloat())
            hitCapsule.onPointerEvent(localEvent)
            localEvent.recycle()
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activeCapsules.forEach { if (it != hitCapsule) it.extinguish() }
        }
    }

    private fun logSuccessOnce(msg: String) {
        if (!hasLoggedSuccess) {
            hasLoggedSuccess = true
            Log.i("RealOS-NavBarCapsule", msg)
        }
    }

    private fun logDiagnosticOnce(msg: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDiagnosticLogTime > 5000L) {
            lastDiagnosticLogTime = now
            Log.w("RealOS-NavBarCapsule", msg)
        }
    }

    class ColorOsCapsuleItemView(context: Context) : View(context) {

        var glassOpacity: Int = SystemUiConfig.DEFAULT_GLASS_OPACITY
            set(value) {
                field = value
                updatePaints()
                postInvalidateOnAnimation()
            }

        var spotlightEnabled: Boolean = SystemUiConfig.DEFAULT_GLASS_SPOTLIGHT_ENABLED
            set(value) {
                field = value
                postInvalidateOnAnimation()
            }

        var lightColor: String = SystemUiConfig.DEFAULT_GLASS_LIGHT_COLOR
            set(value) {
                field = value
                updatePaints()
                postInvalidateOnAnimation()
            }

        var lightAlpha: Int = SystemUiConfig.DEFAULT_GLASS_LIGHT_ALPHA
            set(value) {
                field = value
                updatePaints()
                postInvalidateOnAnimation()
            }

        var darkColor: String = SystemUiConfig.DEFAULT_GLASS_DARK_COLOR
            set(value) {
                field = value
                updatePaints()
                postInvalidateOnAnimation()
            }

        var darkAlpha: Int = SystemUiConfig.DEFAULT_GLASS_DARK_ALPHA
            set(value) {
                field = value
                updatePaints()
                postInvalidateOnAnimation()
            }

        var darkIntensity: Float = 0f
            set(value) {
                field = value
                updatePaints()
                updateFallbackSpotlightShader()
                postInvalidateOnAnimation()
            }

        private var cornerParams: Any? = null
        private var spotLightDrawable: Drawable? = null
        private var officialMaterialAttached = false
        private val capsulePath = Path()
        private val localRect = RectF()

        private val customMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val fallbackGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fallbackSpotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var fallbackPointerX = -1f
        private var fallbackPointerY = -1f
        private var fallbackGlowIntensity = 0f
        private var fallbackGlowAnimator: ValueAnimator? = null
        private val fallbackLightRadius = 180f * context.resources.displayMetrics.density

        init {
            setWillNotDraw(false)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, v.height / 2f)
                }
            }
            clipToOutline = true
            updatePaints()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            initOfficialMaterial()
            postInvalidateOnAnimation()
        }

        override fun onDetachedFromWindow() {
            ColorOsMaterialBridge.cancelSpotLight(spotLightDrawable)
            spotLightDrawable?.callback = null
            spotLightDrawable = null
            fallbackGlowAnimator?.cancel()
            fallbackGlowAnimator = null
            officialMaterialAttached = false
            cornerParams = null
            ColorOsMaterialBridge.clearCornerParams(this)
            super.onDetachedFromWindow()
        }

        private fun initOfficialMaterial() {
            officialMaterialAttached = false
            if (ColorOsMaterialBridge.init(context.classLoader)) {
                if (spotLightDrawable == null) {
                    spotLightDrawable = ColorOsMaterialBridge.createSpotLightDrawable(context)?.apply {
                        callback = this@ColorOsCapsuleItemView
                    }
                }
                officialMaterialAttached = true
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return

            invalidateOutline()

            localRect.set(0f, 0f, w.toFloat(), h.toFloat())
            val cornerRadius = (h / 2f).coerceAtLeast(4f)
            capsulePath.reset()
            capsulePath.addRoundRect(localRect, cornerRadius, cornerRadius, Path.Direction.CW)

            if (isAttachedToWindow && !officialMaterialAttached) initOfficialMaterial()

            if (officialMaterialAttached) {
                if (cornerParams == null) {
                    cornerParams = ColorOsMaterialBridge.createCornerParams(cornerRadius, 2.0f)
                } else {
                    cornerParams?.let { ColorOsMaterialBridge.updateCornerRadius(it, cornerRadius) }
                }
                ColorOsMaterialBridge.applyCornerParams(this, cornerParams)
            }

            spotLightDrawable?.setBounds(0, 0, w, h)
        }

        fun onPointerEvent(event: MotionEvent) {
            if (!spotlightEnabled) return
            if (officialMaterialAttached && spotLightDrawable != null) {
                ColorOsMaterialBridge.dispatchSpotLightTouchEvent(spotLightDrawable, event)
            } else {
                handleFallbackPointer(event)
            }
        }

        fun extinguish() {
            if (officialMaterialAttached && spotLightDrawable != null) {
                ColorOsMaterialBridge.extinguishSpotLight(spotLightDrawable)
            } else {
                startFallbackGlowAnimation(false)
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (width <= 0 || height <= 0 || capsulePath.isEmpty) return

            if (officialMaterialAttached) {
                drawCustomMask(canvas)

                if (spotlightEnabled && spotLightDrawable != null) {
                    val save = canvas.save()
                    try {
                        canvas.clipPath(capsulePath)
                        spotLightDrawable?.draw(canvas)
                    } finally {
                        canvas.restoreToCount(save)
                    }
                }
            } else {
                drawFallback(canvas)
            }
        }

        private fun drawCustomMask(canvas: Canvas) {
            if (customMaskPaint.alpha > 0) {
                canvas.drawPath(capsulePath, customMaskPaint)
            }
        }

        private fun updatePaints() {
            val lColor = runCatching { Color.parseColor(lightColor) }.getOrDefault(Color.parseColor("#E1E1E1"))
            val dColor = runCatching { Color.parseColor(darkColor) }.getOrDefault(Color.parseColor("#0D0D0D"))

            val r = (Color.red(lColor) * (1f - darkIntensity) + Color.red(dColor) * darkIntensity).toInt().coerceIn(0, 255)
            val g = (Color.green(lColor) * (1f - darkIntensity) + Color.green(dColor) * darkIntensity).toInt().coerceIn(0, 255)
            val b = (Color.blue(lColor) * (1f - darkIntensity) + Color.blue(dColor) * darkIntensity).toInt().coerceIn(0, 255)
            val currentAlphaPercent = (lightAlpha * (1f - darkIntensity) + darkAlpha * darkIntensity).coerceIn(0f, 100f)

            val maskAlpha = (currentAlphaPercent * 2.55f).toInt().coerceIn(0, 255)
            val maskColor = Color.argb(maskAlpha, r, g, b)
            customMaskPaint.color = maskColor
            fallbackGlassPaint.color = maskColor
        }

        private fun updateFallbackSpotlightShader() {
            if (fallbackPointerX < 0f || fallbackPointerY < 0f || fallbackGlowIntensity <= 0.001f || !spotlightEnabled) {
                fallbackSpotlightPaint.shader = null
                return
            }
            val maxAlpha = if (darkIntensity > 0.5f) 160 else 220
            val centerAlpha = (maxAlpha * fallbackGlowIntensity).toInt().coerceIn(0, 255)
            val midAlpha = (centerAlpha * 0.35f).toInt().coerceIn(0, 255)

            fallbackSpotlightPaint.shader = RadialGradient(
                fallbackPointerX, fallbackPointerY, fallbackLightRadius,
                intArrayOf(
                    Color.argb(centerAlpha, 255, 255, 255),
                    Color.argb(midAlpha, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.0f, 0.45f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }

        private fun handleFallbackPointer(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fallbackPointerX = event.x
                    fallbackPointerY = event.y
                    updateFallbackSpotlightShader()
                    startFallbackGlowAnimation(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    fallbackPointerX = event.x
                    fallbackPointerY = event.y
                    updateFallbackSpotlightShader()
                    postInvalidateOnAnimation()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    fallbackPointerX = event.x
                    fallbackPointerY = event.y
                    startFallbackGlowAnimation(false)
                }
            }
        }

        private fun startFallbackGlowAnimation(glow: Boolean) {
            fallbackGlowAnimator?.cancel()
            val target = if (glow) 1.0f else 0.0f
            val duration = if (glow) 140L else 260L
            val interpolator = if (glow) DecelerateInterpolator() else AccelerateDecelerateInterpolator()

            fallbackGlowAnimator = ValueAnimator.ofFloat(fallbackGlowIntensity, target).apply {
                this.duration = duration
                this.interpolator = interpolator
                addUpdateListener { anim ->
                    fallbackGlowIntensity = anim.animatedValue as Float
                    updateFallbackSpotlightShader()
                    postInvalidateOnAnimation()
                }
                start()
            }
        }

        private fun drawFallback(canvas: Canvas) {
            canvas.drawPath(capsulePath, fallbackGlassPaint)
            if (spotlightEnabled && fallbackGlowIntensity > 0.001f && fallbackSpotlightPaint.shader != null) {
                val saveCount = canvas.save()
                try {
                    canvas.clipPath(capsulePath)
                    canvas.drawPaint(fallbackSpotlightPaint)
                } finally {
                    canvas.restoreToCount(saveCount)
                }
            }
        }
    }
}
