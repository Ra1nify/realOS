package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs

/**
 * Injects customizable liquid glass blur parameters into ColorOS Material Blur engine
 * and supports real-time dynamic refresh while keyboard is active.
 *
 * Background overlay is attached directly in front of the actual keyboard View (thisObject of keyboardDrawMethod)
 * under its direct parent composite (M0 / FrameLayout).
 */
object OplusLiquidGlassHook {


    /** Minimum gap between full view-tree walks while no renderer has been captured yet. */
    private const val COLLECT_RETRY_INTERVAL_MS = 500L
    private const val TAG = "OplusLiquidGlassHook"

    /**
     * Background image overlay master switch.
     *
     * Disabled: the overlay cannot be confined to the IME area in the host hierarchy.
     * Overlay attach/layer/resize, background node clearing, and lifecycle mounting are skipped.
     * The implementation is retained and gated by this flag.
     */
    private const val BG_IMAGE_FEATURE_ENABLED = false

    /** Matches the radius SeekBar min/max in OplusWorkshopView. */
    private const val BLUR_RADIUS_MIN = 30
    private const val BLUR_RADIUS_MAX = 300

    /** Docked keyboard top-corner slider range in dp; matches OplusWorkshopView Card 1. */
    private const val CORNER_RADIUS_MIN_DP = 0
    private const val CORNER_RADIUS_MAX_DP = 40

    /** Drag-time log throttle: one entry per 30px blur step or 4dp corner step. */
    private const val BLUR_LOG_STEP = 30
    private const val CORNER_LOG_STEP_DP = 4

    private val activeViewRootManagers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val activeKeyboardViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    /**
     * One overlay per host, keyed by host.
     * Weak keys: entries drop out when the host is collected.
     */
    private val activeOverlayMap = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, WeakReference<RealOSBgOverlayView>>()
    )
    private val attachPostedSet = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )

    @Volatile
    private var activeInputView: WeakReference<View>? = null
    @Volatile
    private var activeDecorView: WeakReference<View>? = null
    @Volatile
    private var activeImsRef: WeakReference<android.inputmethodservice.InputMethodService>? = null
    @Volatile
    private var lastKnownContext: WeakReference<Context>? = null
    @Volatile
    private var activeBlurDelegate: WeakReference<Any>? = null
    @Volatile
    private var blurDelegateMethodH: Method? = null
    @Volatile
    private var blurDelegateMethodE: Method? = null
    private val staticRefreshFields = Collections.synchronizedList(ArrayList<Field>())

    @Volatile
    private var vrmSetRadiusMethod: Method? = null
    @Volatile
    private var vrmSetParamsMethod: Method? = null
    @Volatile
    private var blurParamCreateMethodRef: Method? = null

    // ── Docked keyboard corner path (runtime reflection hook on ViewRootManager.setCornerRadius) ──
    @Volatile
    private var vrmSetCornerRadiusMethod: Method? = null
    /**
     * Bottom-corner radii (px) from the last host setCornerRadius call per docked VRM,
     * passed through unchanged on live refresh. Only docked (bottom==0) non-handwriting (top>0) calls are recorded.
     */
    private val dockedBottomByVrm = Collections.synchronizedMap(
        WeakHashMap<Any, FloatArray>()
    )

    // ── Keyboard panel edge outline (COUI AGSL stroke) corner path ──
    /** Callback class names of the host keyboard panel drawables live under this package. */
    private const val KEYBOARD_PKG_PREFIX = "com.oplus.keyboard"
    @Volatile
    private var edgeSetCornerRadiusMethod: Method? = null
    /** Recognized keyboard panel edge drawables (callback under com.oplus.keyboard). */
    private val keyboardPanelEdgeDrawables = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Drawable, Boolean>())
    )
    /** Bottom-corner radii (px) of keyboard panel edge drawables, passed through on live refresh. */
    private val edgeBottomByDrawable = Collections.synchronizedMap(
        WeakHashMap<Drawable, FloatArray>()
    )

    // ── Blur radius path (runtime reflection hook on ViewRootManager.setBlurRadius) ──
    @Volatile
    private var vrmHelperClassLoader: ClassLoader? = null
    private val appliedRadiusByVrm = Collections.synchronizedMap(
        WeakHashMap<Any, Int>()
    )

    /**
     * Key alpha cache (0f..1f).
     *
     * The host reads the getter on every key draw (40..60 per frame); the hook returns this
     * volatile field and never reads prefs. Written at install time and by [updateLiveKeyAlpha].
     */
    @Volatile
    private var cachedKeyAlphaFloat: Float = OplusInputPrefs.DEFAULT_KEY_ALPHA
    @Volatile
    private var cachedBypassAllLimits: Boolean = false

    /**
     * Buffered key renderers (main keyboard body + theme preview) captured from the input view
     * tree, plus the resolved `setDrawRefresh(boolean)` setters that rebuild their key bitmap.
     *
     * Key backgrounds are painted into a cached Bitmap; without setting the refresh flag the
     * alpha change only shows up after an IME restart. See [updateLiveKeyAlpha].
     */
    private val keyBufferRefreshMethods = Collections.synchronizedList(mutableListOf<Method>())
    private val activeKeyBufferedViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )


    @Volatile
    private var lastCollectAttemptAt: Long = 0L

    /** Bumped on every key-alpha change; renderers compare it to decide on a rebuild. */
    @Volatile
    private var keyAlphaVersion: Int = 0

    /** Last key-alpha version each renderer instance has rebuilt for. */
    private val appliedKeyAlphaVersion =
        Collections.synchronizedMap(WeakHashMap<View, Int>())

    /**
     * Per-call authoritative values used during live refresh.
     *
     * The `setBlurRadius(int)` / `setCornerRadius(float×4)` rewrite hooks replace caller
     * arguments with persisted prefs, and live refresh re-invokes the same methods to take
     * effect. During a drag the ThreadLocal holds the value the rewrite hooks read first.
     *
     * Precondition (confirmed by oplus-framework.jar disassembly): both
     * `ViewRootManager.setBlurRadius(I)V` and `setCornerRadius(FFFF)V` forward directly to
     * `BackgroundBlurDrawable` on the same thread, synchronously, with no cache and no post.
     * The ThreadLocal is therefore visible inside the hook.
     *
     * Set only around the live-refresh invoke and cleared in `finally`.
     */
    private val liveBlurRadiusOverride = ThreadLocal<Int?>()
    private val liveCornerPxOverride = ThreadLocal<Float?>()

    /** Last logged values for drag-time log throttling. */
    @Volatile
    private var lastLoggedBlurRadius: Int = -1000
    @Volatile
    private var lastLoggedCornerDp: Int = -1000

    fun getActiveIms(): android.inputmethodservice.InputMethodService? = activeImsRef?.get()

    fun cleanup() {
        val mapSnapshot = synchronized(activeOverlayMap) { HashMap(activeOverlayMap) }
        for ((_, overlayRef) in mapSnapshot) {
            val overlay = overlayRef.get()
            if (overlay != null) {
                runCatching {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                    overlay.clearCache()
                }
            }
        }
        activeOverlayMap.clear()
        attachPostedSet.clear()
        restoreSavedBackgroundNodes()
        restoreSavedHostBackgrounds()
        hostBoundsStamp.clear()
        lifecycleWatched.clear()
        activeViewRootManagers.clear()
        activeKeyboardViews.clear()
        activeInputView = null
        activeDecorView = null
        activeImsRef = null
        lastKnownContext = null
        RealOSImeBounds.clear()
        activeBlurDelegate = null
        blurDelegateMethodH = null
        blurDelegateMethodE = null
        staticRefreshFields.clear()
        vrmSetRadiusMethod = null
        vrmSetParamsMethod = null
        blurParamCreateMethodRef = null
        appliedRadiusByVrm.clear()
        vrmSetCornerRadiusMethod = null
        dockedBottomByVrm.clear()
        edgeSetCornerRadiusMethod = null
        keyboardPanelEdgeDrawables.clear()
        edgeBottomByDrawable.clear()
        keyBufferRefreshMethods.clear()
        activeKeyBufferedViews.clear()
    }

    fun install(module: XposedModule, app: Application, symbols: OplusInputSymbols) {
        // ClassLoader used to load the framework ViewRootManager at runtime.
        vrmHelperClassLoader = app.classLoader

        cachedKeyAlphaFloat = OplusInputPrefs.getKeyAlpha(app).coerceIn(0f, 1f)
        cachedBypassAllLimits = OplusInputPrefs.isBypassAllLimits(app)

        // ── 1. Bypass Day/Night Follow System restriction ──
        // Gated by the merged bypass-all-limits switch.
        if (symbols.isReadyForDayNightBypass()) {
            try {
                val method = symbols.dayNightCheckMethod!!.restore(app.classLoader)
                if (method != null) {
                    module.hook(method).intercept { chain ->
                        if (cachedBypassAllLimits) {
                            val isInner = ThemeManagerHelper.isInnerTheme(app)
                            if (isInner) {
                                return@intercept true
                            }
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Installed DayNight check bypass hook on ${symbols.dayNightCheckMethod.className}.${symbols.dayNightCheckMethod.methodName}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error installing DayNight check bypass hook", t)
            }
        }

        // ── 2. Inject Custom Blur Parameters (Alpha & Color Tint) ──
        if (symbols.blurParamCreateMethod != null) {
            try {
                val method = symbols.blurParamCreateMethod.restore(app.classLoader)
                if (method != null) {
                    blurParamCreateMethodRef = method
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (result != null) {
                            applyMaterialParams(app, result)
                        }
                        result
                    }
                    Log.i(TAG, "Installed custom blur params hook on ${symbols.blurParamCreateMethod.className}.${symbols.blurParamCreateMethod.methodName}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error installing custom blur params hook", t)
            }
        }

        // ── 3. Inject Custom Blur Apply ──
        if (symbols.blurApplyMethod != null) {
            try {
                val method = symbols.blurApplyMethod.restore(app.classLoader)
                if (method != null) {
                    module.hook(method).intercept { chain ->
                        val res = chain.proceed()
                        // [Background image feature disabled] overlay attach is not triggered
                        // activeInputView?.get()?.let { root ->
                        //     scheduleBackgroundOverlayAttach(root)
                        // }
                        res
                    }
                    Log.i(TAG, "Installed custom blur apply hook on ${symbols.blurApplyMethod.className}.${symbols.blurApplyMethod.methodName}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error installing custom blur apply hook", t)
            }
        }

        // ── 3.5 Inject Custom Blur Radius & Capture Active ViewRootManager ──
        // ViewRootManager belongs to the Oplus framework, not the host APK dex, so DexKit
        //   cannot scan it. It is loaded at runtime via the host ClassLoader and
        //   setBlurRadius(int) is hooked: the value Oplus writes (blur.b.f13838h = 150 or 0
        //   from c()) is replaced with the user value. thisObject captures the VRM instance
        //   into a weak set for live refresh.
        try {
            val loader = vrmHelperClassLoader ?: app.classLoader
            val vrmClass = runCatching { loader.loadClass("com.oplus.view.ViewRootManager") }.getOrNull()
            if (vrmClass == null) {
                Log.w(TAG, "[BlurRadius] disabled: ViewRootManager class not resolvable")
            } else {
                // Exact signature match: setBlurRadius(int) -> void
                val setRadiusMethod = vrmClass.declaredMethods.firstOrNull {
                    it.name == "setBlurRadius" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        it.returnType == Void.TYPE
                }
                if (setRadiusMethod == null) {
                    Log.w(TAG, "[BlurRadius] disabled: setBlurRadius(int) descriptor missing on ${vrmClass.name}")
                } else {
                    setRadiusMethod.isAccessible = true
                    vrmSetRadiusMethod = setRadiusMethod

                    // setBlurParams(...) is optional
                    vrmClass.declaredMethods.firstOrNull {
                        it.name == "setBlurParams" && it.parameterTypes.size == 1
                    }?.let {
                        it.isAccessible = true
                        vrmSetParamsMethod = it
                    }

                    module.hook(setRadiusMethod).intercept { chain ->
                        // Captures the VRM instance; the object Oplus calls setBlurRadius on is the VRM.
                        chain.thisObject?.let { vrm ->
                            activeViewRootManagers.add(vrm)
                        }
                        // Live refresh: this call uses the ThreadLocal value, not the pref.
                        val live = liveBlurRadiusOverride.get()
                        if (live != null) {
                            return@intercept chain.proceed(
                                arrayOf<Any?>(live.coerceIn(BLUR_RADIUS_MIN, BLUR_RADIUS_MAX))
                            )
                        }
                        if (OplusInputPrefs.isLiquidGlassEnabled(app)) {
                            val customRadius = OplusInputPrefs.getBlurRadius(app)
                                .coerceIn(BLUR_RADIUS_MIN, BLUR_RADIUS_MAX)
                            // Rewrites the argument with the user value.
                            return@intercept chain.proceed(arrayOf<Any?>(customRadius))
                        }
                        // Feature off: the original Oplus logic runs unchanged.
                        chain.proceed()
                    }
                    Log.i(TAG, "[BlurRadius] installed setBlurRadius(int) parameter-rewrite hook on ${vrmClass.name}")
                }

                // ── 3.6 Docked keyboard top corners: rewrite setCornerRadius(float×4) top arguments ──
                //   Host com.heytap.common.bean.d#a() calls
                //   ViewRootManager.setCornerRadius(topLeft, topRight, bottomRight, bottomLeft):
                //     docked regular = (28dp, 28dp, 0, 0); docked handwriting = (0, 0, 0, 0); floating = (14dp x4).
                //   Only docked calls (bottom == 0) with a host top corner > 0 have their top corners
                //   rewritten to the user value; bottom corners pass through unchanged.
                val setCornerMethod = vrmClass.declaredMethods.firstOrNull {
                    it.name == "setCornerRadius" &&
                        it.parameterTypes.size == 4 &&
                        it.parameterTypes.all { p -> p == Float::class.javaPrimitiveType } &&
                        it.returnType == Void.TYPE
                }
                if (setCornerMethod == null) {
                    Log.w(TAG, "[CornerRadius] disabled: setCornerRadius(float x4) descriptor missing on ${vrmClass.name}")
                } else {
                    setCornerMethod.isAccessible = true
                    vrmSetCornerRadiusMethod = setCornerMethod

                    module.hook(setCornerMethod).intercept { chain ->
                        val vrm = chain.thisObject
                        val topLeft = chain.args.getOrNull(0) as? Float ?: 0f
                        val topRight = chain.args.getOrNull(1) as? Float ?: 0f
                        val bottomRight = chain.args.getOrNull(2) as? Float ?: 0f
                        val bottomLeft = chain.args.getOrNull(3) as? Float ?: 0f

                        // Docked check: bottom corners are 0 (floating and voice panels use 14dp).
                        val isDocked = bottomRight == 0f && bottomLeft == 0f
                        // Rewrite only when the host top corner is > 0; handwriting (host passes 0) stays square.
                        val shouldRewrite = isDocked && topLeft > 0f

                        if (vrm != null && shouldRewrite) {
                            dockedBottomByVrm[vrm] = floatArrayOf(bottomRight, bottomLeft)
                        }

                        // Live refresh: this call uses the ThreadLocal px.
                        val livePx = liveCornerPxOverride.get()
                        if (shouldRewrite && livePx != null) {
                            return@intercept chain.proceed(
                                arrayOf<Any?>(livePx, livePx, bottomRight, bottomLeft)
                            )
                        }

                        if (shouldRewrite && OplusInputPrefs.isLiquidGlassEnabled(app)) {
                            val dp = OplusInputPrefs.getCornerRadius(app)
                                .coerceIn(CORNER_RADIUS_MIN_DP, CORNER_RADIUS_MAX_DP)
                            val px = dp * app.resources.displayMetrics.density
                            return@intercept chain.proceed(arrayOf<Any?>(px, px, bottomRight, bottomLeft))
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "[CornerRadius] installed setCornerRadius(float x4) parameter-rewrite hook on ${vrmClass.name}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[BlurRadius] failed to install setBlurRadius hook", t)
        }

        // ── 3.7 Keyboard panel edge outline (COUI AGSL stroke) corner adaptation ──
        //   Host wraps com.coui.appcompat.shadowedge.COUIShadowEdgeDrawable as
        //   com.oplus.keyboard.base.util.C0880w and sets it as the keyboard panel foreground.
        //   Its corners come from setCornerRadius(lt, rt, rb, lb), the same source as the VRM.
        //   Gate without symbol resolution: drawable.callback class names starting with
        //   com.oplus.keyboard identify the keyboard panel; COUI dialogs and static soft-key
        //   strokes (callback null) are excluded, and other keyboard strokes use the
        //   1-argument overload.
        try {
            val edgeClass = runCatching {
                app.classLoader.loadClass("com.coui.appcompat.shadowedge.COUIShadowEdgeDrawable")
            }.getOrNull()
            if (edgeClass == null) {
                Log.w(TAG, "[EdgeOutline] disabled: COUIShadowEdgeDrawable not resolvable")
            } else {
                // Single 4-argument overload: setCornerRadius(float, float, float, float), returns the drawable.
                val setEdgeCornerMethod = edgeClass.declaredMethods.firstOrNull {
                    it.name == "setCornerRadius" &&
                        it.parameterTypes.size == 4 &&
                        it.parameterTypes.all { p -> p == Float::class.javaPrimitiveType }
                }
                if (setEdgeCornerMethod == null) {
                    Log.w(TAG, "[EdgeOutline] disabled: setCornerRadius(float x4) descriptor missing on ${edgeClass.name}")
                } else {
                    setEdgeCornerMethod.isAccessible = true
                    edgeSetCornerRadiusMethod = setEdgeCornerMethod

                    module.hook(setEdgeCornerMethod).intercept { chain ->
                        val drawable = chain.thisObject as? Drawable
                        val callback = drawable?.callback
                        val isKeyboardPanel =
                            callback != null && callback.javaClass.name.startsWith(KEYBOARD_PKG_PREFIX)

                        val topLeft = chain.args.getOrNull(0) as? Float ?: 0f
                        val topRight = chain.args.getOrNull(1) as? Float ?: 0f
                        val bottomRight = chain.args.getOrNull(2) as? Float ?: 0f
                        val bottomLeft = chain.args.getOrNull(3) as? Float ?: 0f

                        // Same gate as the VRM path: docked (bottom == 0) with a host top corner > 0.
                        val isDocked = bottomRight == 0f && bottomLeft == 0f
                        val shouldRewrite = isKeyboardPanel && isDocked && topLeft > 0f

                        if (drawable != null && isKeyboardPanel) {
                            keyboardPanelEdgeDrawables.add(drawable)
                            if (shouldRewrite) {
                                edgeBottomByDrawable[drawable] = floatArrayOf(bottomRight, bottomLeft)
                            }
                        }

                        // Live refresh: this call uses the ThreadLocal px.
                        val livePx = liveCornerPxOverride.get()
                        if (shouldRewrite && livePx != null) {
                            return@intercept chain.proceed(
                                arrayOf<Any?>(livePx, livePx, bottomRight, bottomLeft)
                            )
                        }

                        if (shouldRewrite && OplusInputPrefs.isLiquidGlassEnabled(app)) {
                            val dp = OplusInputPrefs.getCornerRadius(app)
                                .coerceIn(CORNER_RADIUS_MIN_DP, CORNER_RADIUS_MAX_DP)
                            val px = dp * app.resources.displayMetrics.density
                            return@intercept chain.proceed(arrayOf<Any?>(px, px, bottomRight, bottomLeft))
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "[EdgeOutline] installed setCornerRadius(float x4) rewrite hook on ${edgeClass.name}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[EdgeOutline] failed to install edge outline corner hook", t)
        }

        // ── 4. Inject Theme Key Alpha (key translucency) ──
        //
        // Every key draw calls the no-arg Float getter on the theme work object and uses it:
        //   Float fT = <work>.t();
        //   drawable.setAlpha(fT != null ? clamp((int)(fT * 255), 0, 255) : 255);
        // The facade com.oplus.keyboard.base.theme.b#t() forwards only; the draw path does not
        // go through it, so every resolved work implementation gets a getter returning the cache.
        //
        // The former path that changed alpha on the keyBackgroundMethod drawable was removed:
        // the host setAlpha call overwrites it in the same frame, and g() is static and pools
        // its drawables, so mutate() copies are not pooled and would grow
        // activeKeyDrawables without bound.
        // New-version path: the skin group precomputes the 0..255 key background alpha.
        symbols.keyBgAlpha255Method?.let { descriptor ->
            runCatching {
                val method = descriptor.restore(app.classLoader)
                if (method == null) {
                    Log.w(TAG, "[KeyAlpha] new getter restore failed: ${descriptor.className}.${descriptor.methodName}")
                    return@runCatching
                }
                module.hook(method).intercept { chain ->
                    val base = (chain.proceed() as? Number)?.toInt() ?: 0
                    (base * cachedKeyAlphaFloat).roundToInt().coerceIn(0, 255)
                }
                Log.i(TAG, "[KeyAlpha] installed new getter hook on ${descriptor.className}.${descriptor.methodName}")
            }.onFailure {
                Log.e(TAG, "[KeyAlpha] new getter install failed: ${descriptor.className}.${descriptor.methodName}", it)
            }
        }

        if (symbols.keyAlphaMethods.isEmpty() && symbols.keyBgAlpha255Method == null) {
            Log.w(TAG, "[KeyAlpha] fail closed: key alpha getter not resolved; key frost opacity is not adjustable")
        }
        for (descriptor in symbols.keyAlphaMethods) {
            runCatching {
                val method = descriptor.restore(app.classLoader)
                if (method == null) {
                    Log.w(TAG, "[KeyAlpha] restore failed: ${descriptor.className}.${descriptor.methodName}")
                    return@runCatching
                }
                module.hook(method).intercept { cachedKeyAlphaFloat }
                Log.i(TAG, "[KeyAlpha] installed on ${descriptor.className}.${descriptor.methodName}")
            }.onFailure {
                Log.e(TAG, "[KeyAlpha] install failed: ${descriptor.className}.${descriptor.methodName}", it)
            }
        }

        // Key alpha is painted into a cached key bitmap, so a cache write alone is not visible
        // until the renderer rebuilds. Resolve the renderers refresh setters once here.
        keyBufferRefreshMethods.clear()
        for (descriptor in symbols.keyBufferRefreshMethods) {
            runCatching {
                val setter = descriptor.restore(app.classLoader)
                if (setter == null) {
                    Log.w(
                        TAG,
                        "[KeyAlpha] refresh setter restore failed: " +
                            "${descriptor.className}.${descriptor.methodName}"
                    )
                    return@runCatching
                }
                keyBufferRefreshMethods.add(setter)
            }.onFailure {
                Log.e(TAG, "[KeyAlpha] refresh setter resolve failed", it)
            }
        }
        if (keyBufferRefreshMethods.isNotEmpty()) {
            Log.i(
                TAG,
                "[KeyAlpha] key buffer refresh ready: ${keyBufferRefreshMethods.size} setter(s)"
            )
        } else {
            Log.w(TAG, "[KeyAlpha] no refresh setter; key alpha applies on next IME restart only")
        }

        installKeyRendererDrawHooks(module)

        // ── 5. Capture Keyboard View for Live Invalidation & Overlay Attachment ──
        if (BG_IMAGE_FEATURE_ENABLED && symbols.keyboardDrawMethod != null) {
            runCatching {
                val drawMethod = symbols.keyboardDrawMethod.restore(app.classLoader)
                if (drawMethod != null) {
                    module.hook(drawMethod).intercept { chain ->
                        val keyboardView = chain.thisObject as? View
                        if (keyboardView != null) {
                            RealOSImeBounds.bindKeyboardView(keyboardView)
                            val firstSeen = activeKeyboardViews.add(keyboardView)
                            if (firstSeen) {
                                watchKeyboardViewLifecycle(keyboardView)
                            }
                            scheduleBackgroundOverlayAttach(keyboardView)
                        }
                        chain.proceed()
                    }
                }
            }
        }

        // ── 6. Capture InputMethodService inputView and decorView ──
        try {
            val imsClass = android.inputmethodservice.InputMethodService::class.java
            val onCreateInputViewMethod = imsClass.getDeclaredMethod("onCreateInputView")
            module.hook(onCreateInputViewMethod).intercept { chain ->
                val view = chain.proceed() as? View
                if (view != null) {
                    activeKeyboardViews.add(view)
                    captureActiveInputView(view, chain.thisObject)
                    collectBufferedRenderers(view)
                }
                view
            }

            val onWindowShownMethod = imsClass.getDeclaredMethod("onWindowShown")
            module.hook(onWindowShownMethod).intercept { chain ->
                val res = chain.proceed()
                // The slider is dragged while the IME is hidden, so the key views are
                // detached and cannot be refreshed then. Re-apply on every show: the
                // renderer rebuilds its cached key bitmap on the next draw.
                runCatching { refreshBufferedRenderers() }
                (chain.thisObject as? android.inputmethodservice.InputMethodService)?.let { ims ->
                    captureActiveImeService(ims)
                }
                res
            }
            Log.i(TAG, "Installed InputMethodService lifecycle hooks")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook InputMethodService: ${t.message}")
        }

        // ── 7. Dynamically resolve static refresh fields from AbstractC0864f ──
        if (symbols.dayNightCheckMethod != null) {
            runCatching {
                val dayNightClass = app.classLoader.loadClass(symbols.dayNightCheckMethod.className)
                for (field in dayNightClass.declaredFields) {
                    if (Modifier.isStatic(field.modifiers)) {
                        val type = field.type
                        if (type.name.startsWith("com.oplus.keyboard") ||
                            type.name.startsWith("kotlin.jvm.functions") ||
                            type.interfaces.any { it.name.startsWith("kotlin.jvm.functions") }
                        ) {
                            field.isAccessible = true
                            staticRefreshFields.add(field)
                            Log.i(TAG, "Captured static refresh field: ${dayNightClass.name}.${field.name}")
                        }
                    }
                }
            }
        }

        // Restores View/VRM references from the live Window after hot reload or late injection.
        recoverActiveViewsAndDelegates(app)
    }

    private fun captureActiveInputView(view: View, serviceObj: Any?) {
        activeInputView = WeakReference(view)
        // Records the input view; it defines the IME visible area used as the overlay clip bounds.
        RealOSImeBounds.bindInputView(view)
        view.context?.let { lastKnownContext = WeakReference(it) }
        (serviceObj as? android.inputmethodservice.InputMethodService)?.let { ims ->
            activeImsRef = WeakReference(ims)
            ims.window?.window?.decorView?.let {
                activeDecorView = WeakReference(it)
            }
        }
        findAndSetBlurDelegate(view)
    }

    private fun captureActiveImeService(ims: android.inputmethodservice.InputMethodService) {
        activeImsRef = WeakReference(ims)
        ims.window?.window?.decorView?.let {
            activeDecorView = WeakReference(it)
        }
    }

    private const val TAG_BG_OVERLAY = "realos_bg_overlay"

    /** Hosts whose background was cleared, with the original drawable. */
    private val savedHostBackgrounds = Collections.synchronizedMap(
        WeakHashMap<View, WeakReference<Drawable>>()
    )

    /** Last bounds signature per host, used to detect layout changes. */
    private val hostBoundsStamp = Collections.synchronizedMap(
        WeakHashMap<View, Long>()
    )

    /** Keyboard Views with lifecycle listeners registered (weak set). */
    private val lifecycleWatched = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )

    /**
     * Registers one-time lifecycle listeners on a keyboard View:
     *  - detach: removes the overlay from the View's host and clears state;
     *  - global layout: re-attaches when host bounds change.
     */
    private fun watchKeyboardViewLifecycle(keyboardView: View) {
        if (!BG_IMAGE_FEATURE_ENABLED) return
        if (!lifecycleWatched.add(keyboardView)) return
        runCatching {
            keyboardView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    scheduleBackgroundOverlayAttach(v)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    detachOverlayForSource(v)
                }
            })
            keyboardView.viewTreeObserver.addOnGlobalLayoutListener {
                val kb = keyboardView
                if (kb.isAttachedToWindow) {
                    scheduleBackgroundOverlayAttach(kb)
                }
            }
        }.onFailure {
            Log.w(TAG, "[Overlay] lifecycle watch failed: ${it.message}")
        }
    }

    /** Returns the host currently used for a source View (keyboard draw View or IME root). */
    private fun resolveHostFor(source: View): ViewGroup? {
        val selection = RealOSOverlayHostProbe.selectCommonHost(source)
        return selection.host ?: (source.parent as? ViewGroup)
    }

    /**
     * Removes the overlay from a source View's host and clears state.
     * The overlay is removed only when no other active source resolves to that host.
     */
    private fun detachOverlayForSource(source: View) {
        if (!BG_IMAGE_FEATURE_ENABLED) return
        val host = resolveHostFor(source) ?: return
        val overlay = activeOverlayMap.remove(host)?.get() ?: return
        // The overlay is kept while another attached source still resolves to this host.
        val stillReferenced = synchronized(activeKeyboardViews) {
            activeKeyboardViews.any { it !== source && it.isAttachedToWindow && resolveHostFor(it) === host }
        }
        if (stillReferenced) {
            activeOverlayMap[host] = WeakReference(overlay)
            return
        }
        runCatching {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            overlay.clearCache()
        }.onFailure {
            Log.w(TAG, "[Overlay] detach failed: ${it.message}")
        }
        hostBoundsStamp.remove(host)
        Log.i(TAG, "[Overlay] detached overlay from host=${host.javaClass.simpleName}")
    }

    private fun scheduleBackgroundOverlayAttach(source: View) {
        if (!BG_IMAGE_FEATURE_ENABLED) return
        source.parent as? ViewGroup ?: return

        // Skip when a live overlay already sits on the host and the host bounds are unchanged.
        val host = resolveHostFor(source)
        if (host != null) {
            val existing = activeOverlayMap[host]?.get()
            if (existing != null && existing.parent === host) {
                val last = hostBoundsStamp[host]
                if (last != null && last == boundsStamp(host)) return
            }
        }

        if (attachPostedSet.contains(source)) return
        attachPostedSet.add(source)

        source.post {
            attachPostedSet.remove(source)
            attachBackgroundOverlayToHost(source)
        }
    }

    /** Host measurement signature: width, height, and screen position. */
    private fun boundsStamp(host: View): Long {
        val r = android.graphics.Rect()
        host.getGlobalVisibleRect(r)
        return (host.measuredWidth.toLong() shl 40) xor
            (host.measuredHeight.toLong() shl 20) xor
            ((r.left.toLong() and 0xFFFF) shl 10) xor
            (r.top.toLong() and 0xFFFF)
    }

    private fun attachBackgroundOverlayToHost(source: View) {
        // 1. Select the common host; falls back to the source View's direct parent when null.
        val selection = RealOSOverlayHostProbe.selectCommonHost(source)
        val host = selection.host ?: (source.parent as? ViewGroup) ?: return

        // 2. Host must be measured and visible; otherwise the attach is skipped.
        if (host.measuredWidth <= 0 || host.measuredHeight <= 0 || host.visibility != View.VISIBLE) {
            Log.i(TAG, "[Overlay] skip attach: host not ready (${selection.reason})")
            return
        }

        // 3. One overlay per host, keyed by host.
        var overlay = activeOverlayMap[host]?.get()
        if (overlay == null) {
            overlay = RealOSBgOverlayView(host.context).apply {
                tag = TAG_BG_OVERLAY
                hostOverlay = true
                // Drawing range is clipped to the IME visible area on any host;
                // host containers taller than the IME do not overflow.
                clipToIme = true
            }
            activeOverlayMap[host] = WeakReference(overlay)
        }

        // 4. Insert index: after pure background nodes and before any interactive foreground.
        val insertIndex = RealOSOverlayHostProbe.computeBackgroundInsertIndex(host)
        val alreadyInHost = overlay.parent === host
        if (!alreadyInHost) {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            val params = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            host.addView(overlay, insertIndex.coerceIn(0, host.childCount), params)
            Log.i(TAG, "[Overlay] attached to host ${host.javaClass.simpleName} at index $insertIndex " +
                "(measured=${host.measuredWidth}x${host.measuredHeight}, ${selection.reason})")
        } else {
            val cur = host.indexOfChild(overlay)
            if (cur != insertIndex - 1 && cur != insertIndex) {
                host.removeView(overlay)
                val fresh = RealOSOverlayHostProbe.computeBackgroundInsertIndex(host)
                host.addView(
                    overlay,
                    fresh.coerceIn(0, host.childCount),
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }

        // 5. Clear the host background and verified pure background nodes; original state is saved.
        neutralizeHostBackground(host)
        neutralizePureBackgroundChildren(host)

        hostBoundsStamp[host] = boundsStamp(host)

        // Forces a re-measure so the overlay picks up the current host width and height.
        overlay.requestLayout()
        overlay.clearCache()
        overlay.postInvalidate()
    }

    /**
     * Clears the host background so the overlay is visible.
     *
     * Conditions (all required):
     *  - the host background is non-null;
     *  - the host is a ViewGroup that carries the keyboard foreground;
     *  - the background was not set by the overlay itself;
     * The original background is kept in a weak reference and restored on cleanup.
     */
    private fun neutralizeHostBackground(host: ViewGroup) {
        runCatching {
            val bg = host.background ?: return
            val clazzName = bg.javaClass.name
            // Already transparent.
            if (bg.alpha == 0) return
            // Saves the original background once.
            if (!savedHostBackgrounds.containsKey(host)) {
                savedHostBackgrounds[host] = WeakReference(bg)
            }
            host.background = null
            Log.i(TAG, "[Overlay] host bg cleared: ${host.javaClass.simpleName} bg=${clazzName}")
        }
    }

    /**
     * Clears verified pure background nodes in the host (for example the full-size Oplus
     * background ImageView). Image and background are saved for restore.
     * Sibling Views, interactive controls, and popups are not touched.
     */
    private fun neutralizePureBackgroundChildren(host: ViewGroup) {
        val nodes = runCatching { RealOSOverlayHostProbe.findPureBackgroundNodes(host) }
            .getOrDefault(emptyList())
        for (node in nodes) {
            runCatching {
                if (node.alpha == 0f && node.background == null) return@runCatching
                if (!savedNodeStates.containsKey(node)) {
                    savedNodeStates[node] = NodeSavedState(
                        background = node.background,
                        alpha = node.alpha
                    )
                }
                if (node is android.widget.ImageView && node.drawable != null) {
                    node.setImageDrawable(null)
                }
                node.background = null
                node.alpha = 0f
            }
        }
    }

    /** Saved state of a background node: drawable and alpha. */
    private data class NodeSavedState(val background: Drawable?, val alpha: Float)

    /** Background nodes cleared by this hook, with their original state. */
    private val savedNodeStates = Collections.synchronizedMap(
        WeakHashMap<View, NodeSavedState>()
    )

    /** Restores every cleared background node. */
    private fun restoreSavedBackgroundNodes() {
        val snapshot = synchronized(savedNodeStates) { HashMap(savedNodeStates) }
        for ((node, state) in snapshot) {
            runCatching {
                node.alpha = state.alpha
                if (node.background == null && state.background != null) {
                    node.background = state.background
                }
            }
        }
        savedNodeStates.clear()
    }

    /** Restores every cleared host background on cleanup. */
    private fun restoreSavedHostBackgrounds() {
        val snapshot = synchronized(savedHostBackgrounds) { HashMap(savedHostBackgrounds) }
        for ((host, ref) in snapshot) {
            runCatching {
                val bg = ref.get()
                if (bg != null && host.background == null) {
                    host.background = bg
                }
            }
        }
        savedHostBackgrounds.clear()
    }

    private fun findAndSetBlurDelegate(containerView: View) {
        runCatching {
            var current: Class<*>? = containerView.javaClass
            while (current != null && current != Any::class.java) {
                for (field in current.declaredFields) {
                    field.isAccessible = true
                    val obj = field.get(containerView) ?: continue
                    if (obj === containerView) continue

                    val methods = obj.javaClass.methods
                    val hasE = methods.any { it.name == "e" && it.parameterTypes.isEmpty() }
                    val hasI = methods.any { it.name == "i" && it.parameterTypes.isEmpty() }
                    val hMethod = methods.firstOrNull {
                        it.name == "h" && it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == Boolean::class.javaPrimitiveType
                    }

                    if ((hasE || hasI) && hMethod != null) {
                        activeBlurDelegate = WeakReference(obj)
                        blurDelegateMethodH = hMethod
                        blurDelegateMethodE = methods.firstOrNull { it.name == "e" && it.parameterTypes.isEmpty() }
                        Log.i(TAG, "Found active blur delegate in containerView field ${field.name} (${obj.javaClass.name})")
                        return
                    }
                }
                current = current.superclass
            }
        }
    }

    private fun applyMaterialParams(
        context: Context,
        paramObj: Any,
        explicitLightAlpha: Float? = null,
        explicitDarkAlpha: Float? = null
    ) {
        val isNight = CouiComponentFactory.isNightMode(context)
        val alpha = if (isNight) {
            explicitDarkAlpha ?: OplusInputPrefs.getDarkMaskAlpha(context)
        } else {
            explicitLightAlpha ?: OplusInputPrefs.getLightMaskAlpha(context)
        }
        val hexColor = if (isNight) {
            OplusInputPrefs.getDarkMaskColor(context)
        } else {
            OplusInputPrefs.getLightMaskColor(context)
        }
        val baseColor = runCatching { Color.parseColor(hexColor) }.getOrDefault(
            if (isNight) Color.parseColor("#0D0D0D") else Color.parseColor("#E1E1E1")
        )

        val r = Color.red(baseColor) / 255.0f
        val g = Color.green(baseColor) / 255.0f
        val b = Color.blue(baseColor) / 255.0f

        runCatching {
            val setMatParamsMethod = paramObj.javaClass.getMethod(
                "setMaterialParams",
                Int::class.javaPrimitiveType,
                FloatArray::class.java,
                FloatArray::class.java
            )
            setMatParamsMethod.invoke(
                paramObj,
                1,
                floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f),
                floatArrayOf(r, g, b, alpha)
            )
        }
    }

    /**
     * Called on module hot reload and on cross-process config change broadcasts.
     * Walks the live Window / View tree from the process WindowManagerGlobal and
     * recovers InputMethodService, activeInputView, activeDecorView, activeBlurDelegate,
     * ViewRootManager instances, and COUIShadowEdgeDrawable instances.
     * The recovered references replace ones lost during hot reload.
     */
    fun recoverActiveViewsAndDelegates(context: Context) {
        runCatching {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val getInstance = wmgClass.getMethod("getInstance")
            val wmg = getInstance.invoke(null) ?: return@runCatching
            val mViewsField = wmgClass.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val views = (mViewsField.get(wmg) as? List<View>)?.toList() ?: return@runCatching

            val vrmClass = runCatching {
                (vrmHelperClassLoader ?: context.classLoader).loadClass("com.oplus.view.ViewRootManager")
            }.getOrNull()

            if (vrmSetRadiusMethod == null && vrmClass != null) {
                vrmClass.declaredMethods.firstOrNull {
                    it.name == "setBlurRadius" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
                }?.let {
                    it.isAccessible = true
                    vrmSetRadiusMethod = it
                }
            }
            if (vrmSetParamsMethod == null && vrmClass != null) {
                vrmClass.declaredMethods.firstOrNull {
                    it.name == "setBlurParams" && it.parameterTypes.size == 1
                }?.let {
                    it.isAccessible = true
                    vrmSetParamsMethod = it
                }
            }

            for (rootView in views) {
                // 1. Walks rootView Context wrappers to find the InputMethodService.
                var ctx: Context? = rootView.context
                var imsInstance: android.inputmethodservice.InputMethodService? = null
                while (ctx is android.content.ContextWrapper) {
                    if (ctx is android.inputmethodservice.InputMethodService) {
                        imsInstance = ctx
                        break
                    }
                    ctx = ctx.baseContext
                }

                if (imsInstance != null) {
                    captureActiveImeService(imsInstance)
                }

                // 2. Walks the live View tree.
                traverseViewTree(rootView) { view ->
                    val className = view.javaClass.name
                    if (className.contains("KeyboardView", ignoreCase = true) ||
                        className.contains("SkbContainer", ignoreCase = true) ||
                        className.contains("PanelContainer", ignoreCase = true)
                    ) {
                        activeKeyboardViews.add(view)
                    }

                    if (activeInputView?.get() == null || activeBlurDelegate?.get() == null) {
                        findAndSetBlurDelegate(view)
                        if (activeBlurDelegate?.get() != null) {
                            activeInputView = WeakReference(view)
                            RealOSImeBounds.bindInputView(view)
                            lastKnownContext = WeakReference(view.context ?: context)
                        }
                    }

                    if (vrmClass != null) {
                        captureVrmFromFields(view, vrmClass)
                    }

                    val bg = view.background
                    if (bg != null && bg.javaClass.name.contains("ShadowEdge", ignoreCase = true)) {
                        keyboardPanelEdgeDrawables.add(bg)
                    }
                }
            }
            Log.i(
                TAG,
                "recoverActiveViewsAndDelegates completed: vrms=${activeViewRootManagers.size}, " +
                    "delegate=${activeBlurDelegate?.get() != null}, inputView=${activeInputView?.get() != null}, " +
                    "decorView=${activeDecorView?.get() != null}"
            )
        }.onFailure {
            Log.w(TAG, "recoverActiveViewsAndDelegates failed", it)
        }
    }

    private fun traverseViewTree(root: View, visitor: (View) -> Unit) {
        visitor(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child != null) {
                    traverseViewTree(child, visitor)
                }
            }
        }
    }

    private fun captureVrmFromFields(obj: Any, vrmClass: Class<*>) {
        var cls: Class<*>? = obj.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (f.name.startsWith("shadow$") || Modifier.isStatic(f.modifiers)) continue
                f.isAccessible = true
                val value = runCatching { f.get(obj) }.getOrNull() ?: continue
                if (vrmClass.isInstance(value)) {
                    activeViewRootManagers.add(value)
                } else if (value.javaClass.name.contains("blur.b") || value.javaClass.name.contains("container.r")) {
                    for (subF in value.javaClass.declaredFields) {
                        subF.isAccessible = true
                        val subVal = runCatching { subF.get(value) }.getOrNull() ?: continue
                        if (vrmClass.isInstance(subVal)) {
                            activeViewRootManagers.add(subVal)
                        }
                    }
                }
            }
            cls = cls.superclass
        }
    }

    /**
     * Runs one live refresh. Callers pass the just-adjusted value through [explicitRadius] /
     * [explicitKeyAlpha]; without them the value is read from prefs.
     */
    fun triggerLiveRefresh(
        context: Context,
        explicitRadius: Int? = null,
        explicitKeyAlpha: Float? = null,
        refreshDelegates: Boolean = true,
        explicitLightAlpha: Float? = null,
        explicitDarkAlpha: Float? = null
    ) {
        if (activeViewRootManagers.isEmpty() || activeBlurDelegate?.get() == null || activeDecorView?.get() == null) {
            recoverActiveViewsAndDelegates(context)
        }

        // 1. Update blur radius directly on all active ViewRootManager instances
        applyRadiusToAllActiveVrms(resolveEffectiveBlurRadius(context, explicitRadius))

        // 2. Update blur params directly on all active ViewRootManager instances
        blurParamCreateMethodRef?.let { createMethod ->
            vrmSetParamsMethod?.let { setParams ->
                runCatching {
                    val paramObj = createMethod.invoke(null)
                    if (paramObj != null) {
                        applyMaterialParams(context, paramObj, explicitLightAlpha, explicitDarkAlpha)
                        val vrms = synchronized(activeViewRootManagers) { activeViewRootManagers.toList() }
                        for (vrm in vrms) {
                            runCatching { setParams.invoke(vrm, paramObj) }
                        }
                    }
                }
            }
        }

        // 3. Refresh the key alpha cache.
        //
        // The per-key draw path reads the work object getter (see the keyAlphaMethods hook),
        // so refreshing the cache is enough; drawable alpha is overwritten in the same frame.
        val keyAlpha = explicitKeyAlpha ?: OplusInputPrefs.getKeyAlpha(context)
        cachedKeyAlphaFloat = keyAlpha.coerceIn(0f, 1f)
        cachedBypassAllLimits = OplusInputPrefs.isBypassAllLimits(context)

        // 4-6. delegate / static callbacks / overlay refresh
        if (refreshDelegates) {
            refreshBlurDelegates()
        }

        requestLiveInvalidate()
    }

    /**
     * Refreshes the blur delegate, static callbacks, and background overlays;
     * called after a radius change.
     */
    private fun refreshBlurDelegates() {
        // 4. Trigger blur delegate refresh
        activeBlurDelegate?.get()?.let { delegate ->
            runCatching {
                val hMethod = blurDelegateMethodH
                if (hMethod != null) {
                    hMethod.invoke(delegate, true)
                } else {
                    blurDelegateMethodE?.invoke(delegate)
                }
            }
        }

        // 5. Trigger static refresh callbacks in AbstractC0864f
        val fields = synchronized(staticRefreshFields) { staticRefreshFields.toList() }
        for (field in fields) {
            runCatching {
                val fn = field.get(null)
                if (fn != null) {
                    val invokeMethod = fn.javaClass.methods.firstOrNull {
                        it.name == "invoke" && it.parameterTypes.isEmpty()
                    }
                    invokeMethod?.invoke(fn)
                }
            }
        }

        // 6. Refresh background overlays on all active keyboard views
        // [Background image feature disabled] overlays are not refreshed
        if (BG_IMAGE_FEATURE_ENABLED) {
            val kbViews = synchronized(activeKeyboardViews) { activeKeyboardViews.toList() }
            for (kbView in kbViews) {
                scheduleBackgroundOverlayAttach(kbView)
                val host = resolveHostFor(kbView) ?: continue
                val overlay = activeOverlayMap[host]?.get()
                overlay?.requestLayout()
                overlay?.clearCache()
                overlay?.postInvalidate()
                kbView.postInvalidate()
            }
        }
    }

    /**
     * Runs [action] on a snapshot of the captured keyboard ViewRootManager instances.
     *
     * Live UI updates use this instead of [triggerLiveRefresh]: no view-tree recovery, no delegate
     * refresh, no preference reads beyond the caller's value. A failing instance is dropped so the
     * other windows keep updating.
     */
    private inline fun forEachActiveVrm(action: (Any) -> Unit): Int {
        val vrms = synchronized(activeViewRootManagers) { activeViewRootManagers.toList() }
        var applied = 0
        for (vrm in vrms) {
            val ok = runCatching { action(vrm) }.isSuccess
            if (ok) {
                applied++
            } else {
                runCatching { activeViewRootManagers.remove(vrm) }
            }
        }
        return applied
    }

    /** One redraw request for the IME window; no recovery and no host method calls. */
    /**
     * Collects buffered key renderers from an input view subtree.
     *
     * Runs once per captured input view (and whenever a new one appears), never on a draw
     * callback, so the walk cost does not matter.
     */
    /**
     * Captures the buffered key renderers from their own `onDraw` and forces a rebuild when the
     * key alpha changed.
     *
     * Hooked here rather than by walking the input view tree: the key body is added after
     * `onCreateInputView` returns, so a tree walk at creation time finds nothing, and the walk
     * cannot be repeated on the slider path without costing a full traversal per drag tick.
     * `onDraw` is guaranteed to see a live, attached, measured instance.
     */
    private fun installKeyRendererDrawHooks(module: XposedModule) {
        val canvasClass = android.graphics.Canvas::class.java
        for (setter in keyBufferRefreshMethods.toList()) {
            runCatching {
                // getDeclaredMethod: only the renderer that declares onDraw itself is hooked.
                // An inherited onDraw resolves to a framework method, which is not ours to hook.
                val onDraw = setter.declaringClass.getDeclaredMethod("onDraw", canvasClass)
                module.hook(onDraw).intercept { chain ->
                    // Runs on every frame of the key body, so the steady state does a
                    // single map read: only a version change touches the renderer.
                    val self = chain.thisObject as? View
                    if (self != null) {
                        val version = keyAlphaVersion
                        val applied = appliedKeyAlphaVersion[self]
                        if (applied == null) {
                            // First draw: the buffer being built already uses the current
                            // alpha, so capture the renderer without forcing a rebuild.
                            activeKeyBufferedViews.add(self)
                            appliedKeyAlphaVersion[self] = version
                        } else if (applied != version) {
                            runCatching { setter.invoke(self, true) }
                            appliedKeyAlphaVersion[self] = version
                        }
                    }
                    chain.proceed()
                }
                Log.i(
                    TAG,
                    "[KeyAlpha] draw hook installed on ${setter.declaringClass.name}"
                )
            }.onFailure {
                Log.w(
                    TAG,
                    "[KeyAlpha] draw hook skipped on ${setter.declaringClass.name}: ${it.message}"
                )
            }
        }
    }

    private fun collectBufferedRenderers(root: View) {
        val setters = synchronized(keyBufferRefreshMethods) { keyBufferRefreshMethods.toList() }
        if (setters.isEmpty()) return
        var visited = 0
        var matched = 0
        fun visit(view: View) {
            visited++
            if (setters.any { it.declaringClass.isInstance(view) }) {
                activeKeyBufferedViews.add(view)
                matched++
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        runCatching { visit(root) }
    }

    /**
     * Forces the captured key renderers to rebuild their cached key bitmap.
     *
     * The renderers only repaint keys when their refresh flag is set (or the buffer is gone),
     * which is why a plain invalidate left the old alpha on screen until the next IME restart.
     */
    private fun refreshBufferedRenderers() {
        var views = synchronized(activeKeyBufferedViews) { activeKeyBufferedViews.toList() }
        val setters = synchronized(keyBufferRefreshMethods) { keyBufferRefreshMethods.toList() }
        val attached = views.count { it.isAttachedToWindow }
        if (views.isEmpty()) {
            // The key body is often added after onCreateInputView returns, so retry from the
            // captured roots. Throttled: the slider fires per drag tick and a full view-tree
            // walk on every tick is too expensive for a draw-adjacent path.
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastCollectAttemptAt >= COLLECT_RETRY_INTERVAL_MS) {
                lastCollectAttemptAt = now
                val roots = synchronized(activeKeyboardViews) { activeKeyboardViews.toList() }
                for (root in roots) collectBufferedRenderers(root)
                views = synchronized(activeKeyBufferedViews) { activeKeyBufferedViews.toList() }
            }
        }
        for (view in views) {
            if (!view.isAttachedToWindow) continue
            for (setter in setters) {
                if (!setter.declaringClass.isInstance(view)) continue
                runCatching { setter.invoke(view, true) }
                break
            }
            runCatching { view.postInvalidate() }
        }
    }


    private fun requestLiveInvalidate() {
        activeInputView?.get()?.postInvalidate()
        activeDecorView?.get()?.postInvalidate()
        val views = synchronized(activeKeyboardViews) { activeKeyboardViews.toList() }
        for (view in views) {
            runCatching { view.postInvalidate() }
        }
    }

    /**
     * Calls setBlurRadius(int) on every captured ViewRootManager with the target radius.
     *
     * The primary path is the parameter rewrite on `setBlurRadius(int)` (see install 3.5).
     * Existing VRMs are not called again by Oplus after a slider drag, so this method
     * applies the radius there.
     */
    private fun applyRadiusToAllActiveVrms(radius: Int) {
        val setRadius = vrmSetRadiusMethod ?: return
        val total = synchronized(activeViewRootManagers) { activeViewRootManagers.size }
        var applied = 0
        forEachActiveVrm { vrm ->
            setRadius.invoke(vrm, radius)
            appliedRadiusByVrm[vrm] = radius
            applied++
        }
        if (total > 0) {
            val failed = total - applied
            // Called once per drag tick; logging every call costs 60 entries per second.
            // Entries are written on threshold steps and on failures only.
            if (failed > 0 || abs(radius - lastLoggedBlurRadius) >= BLUR_LOG_STEP) {
                lastLoggedBlurRadius = radius
                Log.i(TAG, "[BlurRadius] vrmCount=$total applied=$applied failed=$failed radius=$radius")
            }
        }
    }

    /**
     * Computes the blur radius to apply.
     *   feature off            -> 0
     *   on, explicit radius    -> clamp(explicitRadius)
     *   on, no explicit radius -> clamp(prefsRadius)
     */
    fun resolveEffectiveBlurRadius(context: Context, explicitRadius: Int? = null): Int {
        if (!OplusInputPrefs.isLiquidGlassEnabled(context)) return 0
        val raw = explicitRadius ?: OplusInputPrefs.getBlurRadius(context)
        return raw.coerceIn(BLUR_RADIUS_MIN, BLUR_RADIUS_MAX)
    }

    /**
     * Real-time live update for blur radius without collapsing keyboard.
     * context only gates the feature switch; the radius comes from the explicit value.
     */
    fun updateLiveBlurRadius(radius: Int, context: Context? = null, refreshDelegates: Boolean = false) {
        val target = if (context != null) {
            resolveEffectiveBlurRadius(context, radius)
        } else {
            radius.coerceIn(BLUR_RADIUS_MIN, BLUR_RADIUS_MAX)
        }
        // Sets the authoritative value for this call; the rewrite hook reads it first.
        liveBlurRadiusOverride.set(target)
        try {
            applyRadiusToAllActiveVrms(target)
        } finally {
            liveBlurRadiusOverride.remove()
        }
        if (refreshDelegates) {
            refreshBlurDelegates()
        }
        requestLiveInvalidate()
    }

    /**
     * Real-time live update for the docked keyboard's top-corner radius without collapsing the keyboard.
     *
     * Applies through three channels:
     *  1. invoke setCornerRadius(newTop, newTop, cachedBottom...) on captured docked VRMs;
     *  2. write the same top corner to recognized keyboard panel drawables (COUIShadowEdgeDrawable);
     *  3. [refreshBlurDelegates] re-runs the host blur delegate, which forwards the rewritten values.
     */
    fun updateLiveCornerRadius(radiusDp: Int, context: Context? = null, refreshDelegates: Boolean = false) {
        val target = radiusDp.coerceIn(CORNER_RADIUS_MIN_DP, CORNER_RADIUS_MAX_DP)
        // Same density source as the apply* helpers. The px value becomes the authoritative
        // value read by both rewrite hooks.
        val density = (context ?: lastKnownContext?.get())?.resources?.displayMetrics?.density
        if (density != null) {
            liveCornerPxOverride.set(target * density)
        }
        try {
            applyCornerRadiusToAllActiveVrms(target, context)
            applyCornerRadiusToAllActiveEdgeDrawables(target, context)
        } finally {
            liveCornerPxOverride.remove()
        }
        if (refreshDelegates) {
            refreshBlurDelegates()
        }
        requestLiveInvalidate()
    }

    /**
     * Writes the new top-corner radius to every captured docked VRM.
     * Only VRMs with recorded docked calls are touched; bottom corners keep the host values.
     */
    private fun applyCornerRadiusToAllActiveVrms(radiusDp: Int, context: Context?) {
        val setCorner = vrmSetCornerRadiusMethod ?: return
        // Prefers the live-refresh px so both rewrite hooks see the same value.
        val px = liveCornerPxOverride.get() ?: run {
            val density = (context ?: lastKnownContext?.get())?.resources?.displayMetrics?.density ?: return
            radiusDp * density
        }
        var applied = 0
        forEachActiveVrm { vrm ->
            val bottoms = dockedBottomByVrm[vrm] ?: return@forEachActiveVrm
            setCorner.invoke(vrm, px, px, bottoms[0], bottoms[1])
            applied++
        }
        if (applied > 0) {
            // Same drag-time log throttle as above.
            if (abs(radiusDp - lastLoggedCornerDp) >= CORNER_LOG_STEP_DP) {
                lastLoggedCornerDp = radiusDp
                Log.i(TAG, "[CornerRadius] live-applied topCorner=${radiusDp}dp to $applied docked VRM(s)")
            }
        }
    }

    /**
     * Writes the new top-corner radius to every recognized keyboard panel drawable.
     * Only drawables with recorded docked calls are touched; bottom corners keep the host values.
     */
    private fun applyCornerRadiusToAllActiveEdgeDrawables(radiusDp: Int, context: Context?) {
        val setCorner = edgeSetCornerRadiusMethod ?: return
        // Same source as the VRM path: the live-refresh px.
        val px = liveCornerPxOverride.get() ?: run {
            val density = (context ?: lastKnownContext?.get())?.resources?.displayMetrics?.density ?: return
            radiusDp * density
        }
        val drawables = synchronized(keyboardPanelEdgeDrawables) { keyboardPanelEdgeDrawables.toList() }
        var applied = 0
        for (d in drawables) {
            val bottoms = edgeBottomByDrawable[d] ?: continue
            val ok = runCatching {
                setCorner.invoke(d, px, px, bottoms[0], bottoms[1])
                true
            }.getOrDefault(false)
            if (ok) applied++
        }
        if (applied > 0) {
            Log.i(TAG, "[EdgeOutline] live-applied topCorner=${radiusDp}dp to $applied panel drawable(s)")
        }
    }

    /**
     * Real-time light/dark mask alpha and tint update.
     *
     * Per-frame path: builds one material param object and pushes it to the captured VRMs.
     * No view-tree recovery, radius/corner writes or delegate refresh. If no VRM is captured
     * yet this is a no-op; the next keyboard open applies the prefs through the param hook.
     */
    fun updateLiveMaskAlpha(
        context: Context,
        explicitLightAlpha: Float? = null,
        explicitDarkAlpha: Float? = null
    ) {
        blurParamCreateMethodRef?.let { createMethod ->
            vrmSetParamsMethod?.let { setParams ->
                val paramObj = runCatching { createMethod.invoke(null) }.getOrNull()
                if (paramObj != null) {
                    applyMaterialParams(context, paramObj, explicitLightAlpha, explicitDarkAlpha)
                    forEachActiveVrm { vrm ->
                        setParams.invoke(vrm, paramObj)
                    }
                }
            }
        }
        requestLiveInvalidate()
    }

    /**
     * Real-time key background opacity update.
     *
     * The per-key draw path reads the hooked getter, so the cache plus one redraw is the whole
     * update. No window recovery and no host method calls: this runs on every slider frame.
     */
    fun updateLiveKeyAlpha(keyAlpha: Float) {
        cachedKeyAlphaFloat = keyAlpha.coerceIn(0f, 1f)
        keyAlphaVersion++
        // Key backgrounds live in a cached bitmap: flag the renderers before invalidating,
        // otherwise the new alpha only shows up after the next IME restart.
        refreshBufferedRenderers()
        requestLiveInvalidate()
    }

    /** Updates the volatile bypass cache without running a live refresh (switch path). */
    fun updateBypassAllLimits(enabled: Boolean) {
        cachedBypassAllLimits = enabled
    }
}
