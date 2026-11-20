package io.github.ra1nify.realos.xposed.hooks.navigation.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.xposed.hooks.navigation.SystemUiHandles
import java.util.Collections
import java.util.WeakHashMap
import io.github.ra1nify.realos.navigation.GestureAppearance

/**
 * Scales the gesture handle bar (navigation handle) width.
 */
object SystemUiGestureHandleHook {

    private const val TAG = "RealOS-GestureHandle"

    private val activeViews: MutableSet<View> = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )

    private val lifecycleBoundViews: MutableSet<View> = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )

    private val unscaledWidths: MutableMap<View, Int> = Collections.synchronizedMap(WeakHashMap())

    @Volatile
    private var isReceiverRegistered = false

    /** Registered glass config receiver and its context, unregistered by cleanup. */
    private var glassReceiver: BroadcastReceiver? = null
    private var glassReceiverContext: java.lang.ref.WeakReference<Context>? = null

    @Volatile
    private var module: XposedModule? = null

    private fun log(level: Int, msg: String) {
        module?.log(level, TAG, msg)
    }

    private fun log(level: Int, msg: String, t: Throwable) {
        module?.log(level, TAG, msg, t)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun install(module: XposedModule, classLoader: ClassLoader, handles: SystemUiHandles) {
        this.module = module
        module.log(Log.INFO, TAG, "install() enter")

        if (!handles.canHookGestureHandle) {
            module.log(Log.WARN, TAG, "fail closed: GestureHandle symbols missing, skipping hook")
            return
        }

        val constructors = handles.handleConstructors
        constructors.forEach { ctor ->
            runCatching {
                module.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val view = chain.thisObject as? View
                        if (view != null) {
                            bindViewLifecycle(view)
                        }
                    } catch (t: Throwable) {
                        log(Log.WARN, "Constructor interception threw", t)
                    }
                    result
                }
            }.onFailure { log(Log.WARN, "Constructor hook install failed: $ctor", it) }
        }
        module.log(Log.INFO, TAG, "Installed: NavigationHandle constructors (${constructors.size})")

        val setVerticalMethod = handles.handleSetVerticalMethod
        if (setVerticalMethod != null) {
            runCatching {
                module.hook(setVerticalMethod).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val view = chain.thisObject as? View
                        if (view != null) {
                            bindViewLifecycle(view)
                            val ctx = view.context
                            val appearance = SystemUiConfig.gestureAppearance(ctx)
                            val isNight = isNightMode(ctx)
                            applyLengthScale(view, if (appearance.handleDiyEnabled) appearance.handleLengthScale else 100)
                            applyHandleMask(view, isNight, appearance)
                        }
                    } catch (t: Throwable) {
                        log(Log.WARN, "setVertical interception threw", t)
                    }
                    result
                }
                module.log(Log.INFO, TAG, "Installed: setVertical")
            }.onFailure { log(Log.WARN, "setVertical hook install failed", it) }
        }

        val onDrawMethod = handles.handleOnDrawMethod
        if (onDrawMethod != null) {
            runCatching {
                module.hook(onDrawMethod).intercept { chain ->
                    val view = chain.thisObject as? View
                    if (view != null) {
                        val ctx = view.context
                        val appearance = SystemUiConfig.gestureAppearance(ctx)
                        val isNight = isNightMode(ctx)
                        applyHandleMask(view, isNight, appearance)
                    }
                    chain.proceed()
                }
                module.log(Log.INFO, TAG, "Installed: NavigationHandle.onDraw")
            }.onFailure { log(Log.WARN, "onDraw hook install failed", it) }
        }
    }

    private fun bindViewLifecycle(view: View) {
        if (!lifecycleBoundViews.add(view)) return

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                handleViewAttached(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                handleViewDetached(v)
            }
        })

        if (view.isAttachedToWindow) {
            handleViewAttached(view)
        }
    }

    private fun applyLengthScale(view: View, scale: Int) {
        val lp = view.layoutParams ?: return

        val baseWidth = unscaledWidths.getOrPut(view) {
            if (lp.width > 0) lp.width else return
        }
        if (baseWidth <= 0) return

        val targetWidth = (baseWidth * (scale.coerceIn(50, 200) / 100f)).toInt().coerceAtLeast(1)
        if (lp.width != targetWidth) {
            lp.width = targetWidth
            view.layoutParams = lp
        }
    }

    private fun handleViewAttached(view: View) {
        activeViews.add(view)
        ensureReceiverRegistered(view.context)

        val ctx = view.context
        val appearance = SystemUiConfig.gestureAppearance(ctx)
        val isNight = isNightMode(ctx)
        applyLengthScale(view, if (appearance.handleDiyEnabled) appearance.handleLengthScale else 100)
        applyHandleMask(view, isNight, appearance)

        log(
            Log.INFO,
            "Gesture handle attached: view=$view lengthScale=${appearance.handleLengthScale}% diyEnabled=${appearance.handleDiyEnabled}"
        )
    }

    private fun handleViewDetached(view: View) {
        activeViews.remove(view)
    }

    private fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyHandleMask(view: View, isNight: Boolean, appearance: io.github.ra1nify.realos.navigation.GestureAppearance) {
        if (!appearance.handleDiyEnabled) {
            view.alpha = 1.0f
            return
        }
        val detail = if (isNight) appearance.night.handle else appearance.day.handle
        view.alpha = (detail.alpha / 255f).coerceIn(0f, 1f)

        runCatching {
            val field = view.javaClass.declaredFields.firstOrNull { it.type == android.graphics.Paint::class.java }
            if (field != null) {
                field.isAccessible = true
                val paint = field.get(view) as? android.graphics.Paint
                paint?.color = detail.color
                paint?.alpha = detail.alpha
            }
        }
        view.invalidate()
    }

    private fun ensureReceiverRegistered(context: Context) {
        if (isReceiverRegistered) return
        synchronized(this) {
            if (isReceiverRegistered) return
            runCatching {
                val filter = IntentFilter().apply {
                    addAction(SystemUiConfig.ACTION_GESTURE_GLASS_CHANGED)
                    addAction(SystemUiConfig.LEGACY_ACTION_GESTURE_GLASS_CHANGED)
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action == SystemUiConfig.ACTION_GESTURE_GLASS_CHANGED ||
                            intent?.action == SystemUiConfig.LEGACY_ACTION_GESTURE_GLASS_CHANGED
                        ) {
                            reloadAllActiveViews()
                        }
                    }
                }
                val appContext = context.applicationContext ?: context
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                glassReceiver = receiver
                glassReceiverContext = java.lang.ref.WeakReference(appContext)
                isReceiverRegistered = true
                log(Log.INFO, "Registered the ACTION_GESTURE_GLASS_CHANGED dynamic broadcast")
            }.onFailure {
                log(Log.WARN, "Failed to register the broadcast receiver", it)
            }
        }
    }

    private fun reloadAllActiveViews() {
        mainHandler.post {
            log(Log.INFO, "Gesture handle reloadAllActiveViews: activeViews=${activeViews.size}")
            for (view in activeViews.toList()) {
                if (!view.isAttachedToWindow) continue
                val ctx = view.context
                val appearance = SystemUiConfig.gestureAppearance(ctx)
                val isNight = isNightMode(ctx)
                applyLengthScale(view, if (appearance.handleDiyEnabled) appearance.handleLengthScale else 100)
                applyHandleMask(view, isNight, appearance)
            }
        }
    }

    fun cleanup() {

        // Unregisters the old receiver and resets the registration flag on hot reload.
        val receiver = glassReceiver
        val appContext = glassReceiverContext?.get()
        if (receiver != null && appContext != null) {
            runCatching { appContext.unregisterReceiver(receiver) }
                .onFailure { log(Log.WARN, "Failed to unregister the broadcast receiver", it) }
        }
        glassReceiver = null
        glassReceiverContext = null
        isReceiverRegistered = false

        activeViews.clear()

        lifecycleBoundViews.clear()

        unscaledWidths.clear()

    }

}
