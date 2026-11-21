package io.github.ra1nify.realos.xposed.hooks.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.hooks.navigation.pointer.SideGestureDynamicPointerRenderer
import java.util.Collections
import java.util.WeakHashMap
import io.github.ra1nify.realos.navigation.GestureAppearance
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.navigation.ArrowIcon
import io.github.ra1nify.realos.navigation.AppearanceDetail
import io.github.ra1nify.realos.navigation.ArrowDiy
import io.github.ra1nify.realos.navigation.buildArrowBitmap
import io.github.ra1nify.realos.navigation.buildArrowBitmapFrom
import io.github.ra1nify.realos.navigation.mirrorHorizontally

/**
 * Applies back gesture appearance configuration.
 */
object SystemUiGestureAppearanceHook {

    private const val TAG = "RealOS-SysUiAppearance"

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var currentConfig: GestureAppearance? = null

    private fun log(level: Int, msg: String) {
        module?.log(level, TAG, msg)
    }

    private fun log(level: Int, msg: String, t: Throwable) {
        module?.log(level, TAG, msg, t)
    }

    private var cachedKey: String? = null
    private var cachedNormal: Bitmap? = null
    private var cachedMirrored: Bitmap? = null

    @Volatile
    private var customIconKey: String? = null
    @Volatile
    private var customIconBitmap: Bitmap? = null

    @Volatile
    private var arrowCanvasPx: Int = 0
    @Volatile
    private var arrowIconPx: Int = 0

    private fun iconSizePx(density: Float): Int = (18f * density).toInt()

    private class HostOriginal(
        val bezierColor: Int,
        val bezierAlpha: Int,
        val borderColor: Int,
        val borderAlpha: Int,
        val borderWidth: Float,
        val iconAlpha: Int,
        val iconColorFilter: android.graphics.ColorFilter?
    )

    private val hostOriginals: MutableMap<View, HostOriginal> =
        Collections.synchronizedMap(WeakHashMap())

    private val arrowReplaced: MutableSet<View> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))

    @Volatile
    private var appliedKey: String? = null

    @Volatile
    private var appliedTs: Long = 0L

    @Volatile
    private var lastEnabled: Boolean? = null

    private const val KEY_DISABLED = "off"
    private const val REFRESH_TTL_MS = 500L

    fun install(module: XposedModule, classLoader: ClassLoader, handles: SystemUiHandles) {
        this.module = module

        var installed = false
        val managerHook = handles.managerOnUpdateGestureViewMethod
        if (managerHook != null) {
            installed = runCatching {
                module.hook(managerHook).intercept { chain ->
                    val r = chain.proceed()
                    try {
                        refreshIfChanged(chain.thisObject, handles)
                    } catch (t: Throwable) {
                        log(Log.WARN, "Live refresh failed (swallowed)", t)
                    }
                    r
                }
            }.onFailure { log(Log.WARN, "Manager-level live refresh install failed", it) }.isSuccess
            if (installed) {
                module.log(
                    Log.INFO, TAG,
                    "Installed: appearance live refresh (manager#onUpdateGestureView, both sides, TTL=${REFRESH_TTL_MS}ms)"
                )
            }
        } else {
            module.log(Log.WARN, TAG, "Manager-level onUpdateGestureView not resolved -> falling back to view level")
        }

        if (!installed) {
            val viewHook = handles.onUpdateGestureViewMethod
            if (viewHook == null) {
                module.log(
                    Log.WARN, TAG,
                    "Live refresh: neither manager nor view level onUpdateGestureView resolved -> skipped (a settings change needs a SystemUI restart)"
                )
            } else {
                runCatching {
                    module.hook(viewHook).intercept { chain ->
                        val r = chain.proceed()
                        try {
                            refreshIfChangedForView(chain.thisObject as? View, handles)
                        } catch (t: Throwable) {
                            log(Log.WARN, "Live refresh failed (swallowed)", t)
                        }
                        r
                    }
                    module.log(
                        Log.INFO, TAG,
                        "Installed: appearance live refresh (view#onUpdateGestureView fallback, current side only)"
                    )
                }.onFailure { log(Log.WARN, "View-level live refresh install failed", it) }
            }
        }

        val updateBackIcon = handles.updateBackIconMethod
        if (updateBackIcon == null) {
            module.log(Log.WARN, TAG, "updateBackIcon() not resolved -> appearance may be lost after a theme switch")
        } else {
            runCatching {
                module.hook(updateBackIcon).intercept { chain ->
                    val r = chain.proceed()
                    try {
                        val manager = chain.thisObject
                        if (!postToGestureThread(classLoader, manager, handles)) {
                            appliedKey = null
                            log(Log.WARN, "updateBackIcon: gesture thread unavailable -> will repaint on the next gesture")
                        }
                    } catch (t: Throwable) {
                        log(Log.WARN, "Repaint after updateBackIcon failed (swallowed)", t)
                    }
                    r
                }
                module.log(Log.INFO, TAG, "Installed: updateBackIcon (repaints appearance after a theme switch)")
            }.onFailure { log(Log.WARN, "updateBackIcon hook install failed", it) }
        }

        installMatrixCompensation(module, handles)
        installOnDrawHook(module, handles)
    }

    private fun installMatrixCompensation(
        module: XposedModule,
        handles: SystemUiHandles
    ) {
        val calcMethod = handles.calculateMatrixMethod
        val matrixGetter = handles.backIconMatrixMethod
        if (calcMethod == null || matrixGetter == null) {
            module.log(
                Log.WARN, TAG,
                "Arrow alignment compensation unavailable: calculateMatrix=${calcMethod != null} " +
                    "getBackIconMatrix=${matrixGetter != null}"
            )
            return
        }
        runCatching {
            module.hook(calcMethod).intercept { chain ->
                val r = chain.proceed()
                try {
                    val calc = chain.thisObject
                    val ctx = runCatching { calc.javaClass.getMethod("getContext").invoke(calc) as? Context }.getOrNull()
                    val cfg = currentConfig ?: ctx?.let { SystemUiConfig.gestureAppearance(it) }?.also { currentConfig = it }
                    val isDynamic = cfg != null && cfg.arrowDiyEnabled &&
                        cfg.arrowExperienceMode == GestureAppearance.ARROW_EXPERIENCE_DYNAMIC
                    if (!isDynamic) {
                        val canvasPx = arrowCanvasPx
                        val iconPx = arrowIconPx
                        if (canvasPx > 0 && iconPx > 0 && canvasPx != iconPx) {
                            val d = (canvasPx - iconPx) / 2f
                            (matrixGetter.invoke(chain.thisObject) as? Matrix)?.postTranslate(-d, -d)
                        }
                    }
                } catch (t: Throwable) {
                    log(Log.WARN, "Arrow alignment compensation failed (swallowed)", t)
                }
                r
            }
            module.log(Log.INFO, TAG, "Installed: arrow alignment compensation (corrects (bitmap - iconSize)/2 after calculateMatrix)")
        }.onFailure { log(Log.WARN, "Arrow alignment compensation install failed", it) }
    }

    private fun postToGestureThread(
        classLoader: ClassLoader,
        manager: Any?,
        handles: SystemUiHandles
    ): Boolean {
        if (manager == null) return false
        // Consumes the handles the resolver already validated and cached instead of
        // reflecting over constant class names again.
        val cls = handles.gestureUiThreadManagerClass ?: return false
        val getHandler = handles.getGestureUiHandlerMethod ?: return false
        val handler = runCatching {
            val inst = cls.getField("INSTANCE").get(null)
            getHandler.invoke(inst) as? Handler
        }.getOrNull() ?: return false
        return runCatching {
            handler.post { runCatching { forceReapply(manager, handles) } }
            true
        }.getOrDefault(false)
    }

    private fun refreshIfChanged(manager: Any?, handles: SystemUiHandles) {
        manager ?: return
        val now = SystemClock.uptimeMillis()
        if (now - appliedTs < REFRESH_TTL_MS) return
        appliedTs = now

        val views = viewsOf(handles, manager)
        if (views.isEmpty()) return
        val ctx = views.first().context ?: return
        val cfg = SystemUiConfig.gestureAppearance(ctx)
        currentConfig = cfg

        if (!cfg.anyDiyEnabled) {
            currentConfig = null
            if (lastEnabled == true && appliedKey != KEY_DISABLED) {
                restore(manager, handles, views, ctx)
                appliedKey = KEY_DISABLED
            }
            lastEnabled = false
            return
        }

        lastEnabled = true
        val isNight = isNightMode(ctx)
        val key = cfg.hashCode().toString() + "|" + isNight
        if (key == appliedKey) return
        appliedKey = key

        var applied = 0
        for (v in views) {
            if (runCatching { applyToView(cfg, ctx, v, handles, verbose = false) }.getOrDefault(false)) {
                applied++
            }
        }
        log(
            Log.INFO,
            "Appearance live refresh: views=$applied reverseLeft=${cfg.reverseLeft} ${describe(cfg.detailFor(isNight))}"
        )
    }

    private fun refreshIfChangedForView(view: View?, handles: SystemUiHandles) {
        view ?: return
        val now = SystemClock.uptimeMillis()
        if (now - appliedTs < REFRESH_TTL_MS) return
        appliedTs = now

        val ctx = view.context ?: return
        val cfg = SystemUiConfig.gestureAppearance(ctx)

        if (!cfg.anyDiyEnabled) {
            if (lastEnabled == true && appliedKey != KEY_DISABLED) {
                decodeHostBackIcon(ctx)?.let {
                    runCatching { handles.setBackIconMethod?.invoke(view, it) }
                    log(Log.INFO, "Restored the host stock icon (single-side fallback)")
                }
                appliedKey = KEY_DISABLED
            }
            lastEnabled = false
            return
        }

        lastEnabled = true
        runCatching { applyToView(cfg, ctx, view, handles, verbose = true) }
            .onFailure { log(Log.WARN, "Appearance live refresh (single-side fallback) failed", it) }
    }

    private fun forceReapply(manager: Any?, handles: SystemUiHandles) {
        manager ?: return
        val views = viewsOf(handles, manager)
        if (views.isEmpty()) return
        val ctx = views.first().context ?: return
        val cfg = SystemUiConfig.gestureAppearance(ctx)
        appliedTs = SystemClock.uptimeMillis()

        if (!cfg.anyDiyEnabled) {
            lastEnabled = false
            appliedKey = KEY_DISABLED
            return
        }
        lastEnabled = true
        var applied = 0
        for (v in views) {
            if (runCatching { applyToView(cfg, ctx, v, handles, verbose = false) }.getOrDefault(false)) {
                applied++
            }
        }
        appliedKey = cfg.hashCode().toString() + "|" + isNightMode(ctx)
        log(
            Log.INFO,
            "Repainted appearance after the theme switch: views=$applied reverseLeft=${cfg.reverseLeft} " +
                describe(cfg.detailFor(isNightMode(ctx)))
        )
    }

    fun applyTo(
        module: XposedModule,
        handles: SystemUiHandles,
        manager: Any
    ): Boolean {
        this.module = module

        val views = viewsOf(handles, manager)
        if (views.isEmpty()) {
            log(Log.WARN, "Appearance: no view obtained from the manager")
            return false
        }

        val ctx: Context = views.first().context ?: return false
        val cfg = SystemUiConfig.gestureAppearance(ctx)
        appliedTs = SystemClock.uptimeMillis()

        if (!cfg.anyDiyEnabled) {
            lastEnabled = false
            appliedKey = KEY_DISABLED
            return false
        }

        lastEnabled = true
        val isNight = isNightMode(ctx)
        var applied = 0
        for (v in views) {
            if (runCatching { applyToView(cfg, ctx, v, handles, verbose = false) }.getOrDefault(false)) {
                applied++
            }
        }
        appliedKey = cfg.hashCode().toString() + "|" + isNight
        log(
            Log.INFO,
            "Appearance applied: night=$isNight views=$applied reverseLeft=${cfg.reverseLeft} " +
                "switches[arrow=${cfg.arrowDiyEnabled} shadow=${cfg.shadowDiyEnabled} stroke=${cfg.strokeDiyEnabled}] " +
                describe(cfg.detailFor(isNight))
        )
        return cfg.arrowDiyEnabled
    }

    @Volatile
    private var isReceiverRegistered = false

    /** Registered config receiver and its context, unregistered by [reset]. */
    private var configReceiver: BroadcastReceiver? = null
    private var configReceiverContext: java.lang.ref.WeakReference<Context>? = null

    /**
     * Hot-reload cleanup: unregisters the broadcast receiver and resets the flag.
     */
    fun reset() {
        val receiver = configReceiver
        val ctx = configReceiverContext?.get()
        if (receiver != null && ctx != null) {
            runCatching { ctx.unregisterReceiver(receiver) }
                .onFailure { log(Log.WARN, "Failed to unregister the broadcast receiver", it) }
        }
        configReceiver = null
        configReceiverContext = null
        isReceiverRegistered = false
        appliedKey = null
        currentConfig = null
    }

    private fun ensureReceiverRegistered(ctx: Context) {
        if (isReceiverRegistered) return
        synchronized(this) {
            if (isReceiverRegistered) return
            runCatching {
                val filter = IntentFilter().apply {
                    addAction(SystemUiConfig.ACTION_GESTURE_GLASS_CHANGED)
                    addAction(SystemUiConfig.LEGACY_ACTION_GESTURE_GLASS_CHANGED)
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        if (intent?.action == SystemUiConfig.ACTION_GESTURE_GLASS_CHANGED ||
                            intent?.action == SystemUiConfig.LEGACY_ACTION_GESTURE_GLASS_CHANGED
                        ) {
                            appliedKey = null
                            currentConfig = null
                        }
                    }
                }
                val appContext = ctx.applicationContext ?: ctx
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    appContext.registerReceiver(receiver, filter)
                }
                isReceiverRegistered = true
                configReceiver = receiver
                configReceiverContext = java.lang.ref.WeakReference(appContext)
                log(Log.INFO, "Registered the ACTION_GESTURE_GLASS_CHANGED dynamic broadcast")
            }
        }
    }

    private fun applyToView(
        cfg: GestureAppearance,
        ctx: Context,
        view: View,
        handles: SystemUiHandles,
        verbose: Boolean
    ): Boolean {
        ensureReceiverRegistered(ctx)

        val isNight = isNightMode(ctx)
        val d = cfg.detailFor(isNight)
        val density = ctx.resources.displayMetrics.density
        val isLeft = handles.positionField?.let {
            runCatching { it.getInt(view) == 0 }.getOrNull()
        } ?: false
        val orig = hostOriginals[view] ?: captureOriginal(view, handles).also { hostOriginals[view] = it }

        currentConfig = cfg

        (handles.bezierPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint)?.let { paint ->
            if (cfg.shadowDiyEnabled) {
                paint.color = d.shadow.color
                paint.alpha = d.shadow.alpha
            } else {
                paint.color = orig.bezierColor
                paint.alpha = orig.bezierAlpha
            }
            paint.maskFilter = null
        }

        (handles.borderPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint)?.let { paint ->
            if (cfg.strokeDiyEnabled) {
                paint.color = d.stroke.color
                paint.alpha = d.stroke.alpha
                paint.strokeWidth = d.stroke.widthDp * density
            } else {
                paint.color = orig.borderColor
                paint.alpha = orig.borderAlpha
                paint.strokeWidth = orig.borderWidth
            }
        }

        if (cfg.arrowDiyEnabled) {
            val isDynamic = cfg.arrowExperienceMode == GestureAppearance.ARROW_EXPERIENCE_DYNAMIC
            val icon = ArrowIcon.fromKey(cfg.arrowIconKey)
            val mirrored = cfg.reverseLeft && isLeft
            val bmp = arrowBitmapFor(d.arrow, ctx, density, mirrored, icon)
            if (isDynamic) {
                arrowCanvasPx = 0
                arrowIconPx = 0
            }
            if (bmp != null) {
                runCatching { handles.setBackIconMethod?.invoke(view, bmp) }
                    .onFailure { log(Log.WARN, "setBackIcon failed", it) }
                arrowReplaced.add(view)
                (handles.iconPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint)?.let {
                    it.alpha = 255
                    it.colorFilter = null
                }
            }
        } else {
            if (cfg.reverseLeft && isLeft) {
                val host = decodeHostBackIcon(ctx)
                if (host != null) {
                    val mirrored = mirrorHorizontally(host)
                    runCatching { handles.setBackIconMethod?.invoke(view, mirrored) }
                        .onFailure { log(Log.WARN, "setBackIcon host bitmap mirror failed", it) }
                    arrowReplaced.add(view)
                    (handles.iconPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint)?.let {
                        it.alpha = 255
                        it.colorFilter = null
                    }
                }
            } else {
                restoreHostArrow(ctx, view, handles, orig)
            }
        }

        if (verbose) {
            log(
                Log.INFO,
                "Appearance live refresh: left=$isLeft reverseLeft=${cfg.reverseLeft} " +
                    "switches[arrow=${cfg.arrowDiyEnabled} shadow=${cfg.shadowDiyEnabled} stroke=${cfg.strokeDiyEnabled}] " +
                    describe(d)
            )
        }
        return true
    }

    private fun captureOriginal(
        view: View,
        handles: SystemUiHandles
    ): HostOriginal {
        val bezier = handles.bezierPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint
        val border = handles.borderPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint
        val icon = handles.iconPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint
        return HostOriginal(
            bezierColor = bezier?.color ?: GestureAppearance.DEFAULT_SHADOW_COLOR,
            bezierAlpha = bezier?.alpha ?: GestureAppearance.DEFAULT_SHADOW_ALPHA,
            borderColor = border?.color ?: Color.WHITE,
            borderAlpha = border?.alpha ?: GestureAppearance.DEFAULT_STROKE_ALPHA,
            borderWidth = border?.strokeWidth ?: 0f,
            iconAlpha = icon?.alpha ?: 255,
            iconColorFilter = icon?.colorFilter
        )
    }

    private fun restore(
        manager: Any?,
        handles: SystemUiHandles,
        views: List<View>,
        ctx: Context
    ) {
        val createIcon = handles.createIconMethod
        var bitmapRestored = false
        if (createIcon != null && manager != null) {
            bitmapRestored = runCatching { createIcon.invoke(manager); true }.getOrDefault(false)
            if (bitmapRestored) {
                log(Log.INFO, "Restored the host stock icon (via the host createIcon())")
            } else {
                log(Log.WARN, "Restore: createIcon() failed -> falling back to a bitmap fill")
            }
        }
        if (!bitmapRestored) {
            val bmp = decodeHostBackIcon(ctx)
            if (bmp == null) {
                log(Log.WARN, "Arrow restore failed: createIcon unavailable and navbar_side_gesture_back not found")
            } else {
                for (v in views) runCatching { handles.setBackIconMethod?.invoke(v, bmp) }
                log(Log.INFO, "Restored the host stock icon (bitmap fill ${bmp.width}x${bmp.height})")
            }
        }
        arrowReplaced.clear()

        var paints = 0
        for (v in views) {
            val orig = hostOriginals[v] ?: continue
            (handles.bezierPaintField?.let { runCatching { it.get(v) }.getOrNull() } as? Paint)?.let {
                it.color = orig.bezierColor
                it.alpha = orig.bezierAlpha
                it.maskFilter = null
                paints++
            }
            (handles.borderPaintField?.let { runCatching { it.get(v) }.getOrNull() } as? Paint)?.let {
                it.color = orig.borderColor
                it.alpha = orig.borderAlpha
                it.strokeWidth = orig.borderWidth
            }
            (handles.iconPaintField?.let { runCatching { it.get(v) }.getOrNull() } as? Paint)?.let {
                it.alpha = orig.iconAlpha
                it.colorFilter = orig.iconColorFilter
            }
        }
        if (paints > 0) log(Log.INFO, "Restored the host Paint from the snapshot ($paints views)")
    }

    private fun decodeHostBackIcon(ctx: Context): Bitmap? = runCatching {
        val res = ctx.resources
        val id = res.getIdentifier("navbar_side_gesture_back", "drawable", "com.android.systemui")
        if (id == 0) null else BitmapFactory.decodeResource(res, id)
    }.getOrNull()

    private fun viewsOf(
        handles: SystemUiHandles,
        manager: Any
    ): List<View> = handles.managerViewFields.mapNotNull { f ->
        runCatching { f.get(manager) as? View }.getOrNull()
    }

    private fun describe(d: AppearanceDetail): String =
        "shadow=#${Integer.toHexString(d.shadow.color)}/${d.shadow.alpha} " +
            "stroke=#${Integer.toHexString(d.stroke.color)}/${d.stroke.alpha}/${d.stroke.widthDp}dp " +
            "arrow=#${Integer.toHexString(d.arrow.color)}/${d.arrow.alpha}/${d.arrow.sizeDp}dp"

    private fun arrowBitmapFor(
        a: ArrowDiy,
        ctx: Context,
        density: Float,
        mirrored: Boolean,
        icon: ArrowIcon
    ): Bitmap? {
        if (icon == ArrowIcon.SYSTEM) {
            arrowCanvasPx = 0
            arrowIconPx = 0
            val host = decodeHostBackIcon(ctx) ?: return null
            return if (mirrored) mirrorHorizontally(host) else host
        }

        val userPx = (a.sizeDp * density).toInt().coerceIn(8, 256)
        val iconPx = iconSizePx(density)
        val canvasPx = maxOf(userPx, iconPx)
        arrowCanvasPx = canvasPx
        arrowIconPx = iconPx

        if (icon == ArrowIcon.CUSTOM) {
            val src = customIconBitmap(ctx)
            if (src == null) {
                log(Log.WARN, "Custom arrow drawable missing -> falling back to the system default")
                arrowCanvasPx = 0
                arrowIconPx = 0
                val host = decodeHostBackIcon(ctx) ?: return null
                return if (mirrored) mirrorHorizontally(host) else host
            }
            return buildArrowBitmapFrom(src, canvasPx, userPx, mirrored)
        }

        val key = "$canvasPx|$userPx|${a.color}|${a.alpha}|${icon.key}"
        if (cachedKey != key || cachedNormal == null || cachedMirrored == null) {
            cachedNormal = buildArrowBitmap(canvasPx, a.color, a.alpha, mirrored = false, contentSizePx = userPx, icon = icon)
            cachedMirrored = buildArrowBitmap(canvasPx, a.color, a.alpha, mirrored = true, contentSizePx = userPx, icon = icon)
            cachedKey = key
        }
        return if (mirrored) cachedMirrored else cachedNormal
    }

    private fun customIconBitmap(ctx: Context): Bitmap? {
        val b64 = SystemUiConfig.gestureBackCustomIconBase64(ctx)
        if (b64.isEmpty()) {
            customIconKey = null
            customIconBitmap = null
            return null
        }
        if (b64 == customIconKey && customIconBitmap != null) return customIconBitmap
        val bmp = runCatching {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
        customIconKey = b64
        customIconBitmap = bmp
        return bmp
    }

    private fun restoreHostArrow(
        ctx: Context,
        view: View,
        handles: SystemUiHandles,
        orig: HostOriginal
    ) {
        arrowCanvasPx = 0
        arrowIconPx = 0
        if (!arrowReplaced.remove(view)) return
        decodeHostBackIcon(ctx)?.let { host ->
            runCatching { handles.setBackIconMethod?.invoke(view, host) }
                .onFailure { log(Log.WARN, "Failed to restore the host arrow", it) }
        }
        (handles.iconPaintField?.let { runCatching { it.get(view) }.getOrNull() } as? Paint)?.let {
            it.alpha = orig.iconAlpha
            it.colorFilter = orig.iconColorFilter
        }
    }

    private fun isNightMode(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun installOnDrawHook(
        module: XposedModule,
        handles: SystemUiHandles
    ) {
        val onDrawMethod = handles.onDrawMethod
        if (onDrawMethod == null) {
            module.log(
                Log.WARN,
                TAG,
                "HookPoint[systemui_gesture_dynamic_pointer] state=MISSING onDraw=false"
            )
            return
        }
        runCatching {
            module.hook(onDrawMethod).intercept { chain ->
                val view = chain.thisObject as? View
                val canvas = chain.args[0] as? android.graphics.Canvas
                if (view != null && canvas != null) {
                    val cfg = currentConfig
                        ?: view.context?.let { SystemUiConfig.gestureAppearance(it) }?.also { currentConfig = it }
                    val isDynamic = cfg != null && cfg.arrowDiyEnabled &&
                        cfg.arrowExperienceMode == GestureAppearance.ARROW_EXPERIENCE_DYNAMIC
                    val iconPaint = handles.iconPaintField?.let { runCatching { it.get(view) as? Paint }.getOrNull() }
                    val origIconAlpha = iconPaint?.alpha ?: 255

                    if (isDynamic) {
                        iconPaint?.alpha = 0
                    }
                    try {
                        chain.proceed()
                    } finally {
                        if (isDynamic) {
                            runCatching {
                                renderDynamicPointer(canvas, view, cfg!!, handles)
                            }.onFailure {
                                log(Log.WARN, "Dynamic pointer render failed", it)
                            }
                            iconPaint?.alpha = origIconAlpha
                        }
                    }
                } else {
                    chain.proceed()
                }
            }
            module.log(
                Log.INFO,
                TAG,
                "HookPoint[systemui_gesture_dynamic_pointer] state=FOUND onDraw=${onDrawMethod.name}"
            )
        }.onFailure { log(Log.WARN, "onDraw hook install failed", it) }
    }

    private fun obtainPath(view: View, handles: SystemUiHandles): android.graphics.Path? {
        val calc = handles.getBezierCalculatorMethod?.let { runCatching { it.invoke(view) }.getOrNull() }
            ?: handles.bezierCalculatorField?.let { runCatching { it.get(view) }.getOrNull() }
            ?: return null
        return handles.getPathMethod?.let { runCatching { it.invoke(calc) as? android.graphics.Path }.getOrNull() }
    }

    private fun renderDynamicPointer(
        canvas: android.graphics.Canvas,
        view: View,
        cfg: GestureAppearance,
        handles: SystemUiHandles
    ) {
        val ctx = view.context ?: return
        val isNight = isNightMode(ctx)
        val d = cfg.detailFor(isNight)

        val gestureState = obtainGestureState(view, handles)
        if (gestureState != 1 && gestureState != 2) return

        val backIconMatrix = obtainBackIconMatrix(view, handles) ?: return
        val density = ctx.resources.displayMetrics.density
        val isLeft = handles.positionField?.let {
            runCatching { it.getInt(view) == 0 }.getOrNull()
        } ?: false

        val pullDistancePx = obtainPullDistance(view, handles, isLeft)

        SideGestureDynamicPointerRenderer.render(
            canvas = canvas,
            backIconMatrix = backIconMatrix,
            pullDistancePx = pullDistancePx,
            isLeft = isLeft,
            reverseLeft = cfg.reverseLeft,
            density = density,
            color = d.arrow.color,
            alpha = d.arrow.alpha,
            sizeDp = cfg.arrowSizeDp,
            style = cfg.dynamicPointerStyle
        )
    }

    private fun obtainPullDistance(
        view: View,
        handles: SystemUiHandles,
        isLeft: Boolean
    ): Float {
        val calc = handles.getBezierCalculatorMethod?.let { runCatching { it.invoke(view) }.getOrNull() }
            ?: handles.bezierCalculatorField?.let { runCatching { it.get(view) }.getOrNull() }

        if (calc != null) {
            val point = (handles.getCurrentPointMethod?.let { runCatching { it.invoke(calc) as? android.graphics.PointF }.getOrNull() }
                ?: handles.currentPointField?.let { runCatching { it.get(calc) as? android.graphics.PointF }.getOrNull() })

            if (point != null) {
                if (isLeft) {
                    return point.x.coerceAtLeast(0f)
                } else {
                    val screenWidth = handles.getScreenWidthMethod?.let {
                        runCatching { it.invoke(calc) as? Int }.getOrNull()
                    }?.toFloat() ?: view.resources.displayMetrics.widthPixels.toFloat()
                    return (screenWidth - point.x).coerceAtLeast(0f)
                }
            }
        }

        obtainPath(view, handles)?.let { path ->
            if (!path.isEmpty) {
                val bounds = android.graphics.RectF()
                path.computeBounds(bounds, true)
                return bounds.width().coerceAtLeast(0f)
            }
        }

        return 0f
    }

    private fun obtainGestureState(view: View, handles: SystemUiHandles): Int {
        handles.gestureStateField?.let { f ->
            runCatching {
                val state = f.getInt(view)
                if (state == 1 || state == 2) return state
            }
        }
        handles.lastGestureStateField?.let { f ->
            runCatching {
                val state = f.getInt(view)
                if (state == 1 || state == 2) return state
            }
        }
        if (view.visibility == View.VISIBLE) {
            return 1
        }
        return -1
    }

    private fun obtainBackIconMatrix(view: View, handles: SystemUiHandles): Matrix? {
        val calc = handles.getBezierCalculatorMethod?.let { runCatching { it.invoke(view) }.getOrNull() }
            ?: handles.bezierCalculatorField?.let { runCatching { it.get(view) }.getOrNull() }
            ?: return null
        return handles.backIconMatrixMethod?.let { runCatching { it.invoke(calc) as? Matrix }.getOrNull() }
    }
}
