package io.github.ra1nify.realos.xposed.hooks.navigation

import android.graphics.Paint
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger
import io.github.ra1nify.realos.navigation.SystemUiConfig

/**
 * Side gesture back arrow customization hook (Layer 3).
 */
object SystemUiGestureBackHook {

    private const val TAG = "RealOS-SysUiGestureBack"
    private val callbackCount = AtomicInteger(0)

    fun install(module: XposedModule, classLoader: ClassLoader, handles: SystemUiHandles) {
        module.log(Log.INFO, TAG, "install() enter (SystemUI)")
        SystemUiConfig.prime(module)

        if (!handles.canHookGestureBack) {
            module.log(Log.WARN, TAG, "fail closed: Gesture back symbols missing, skipping hook")
            return
        }

        SystemUiGestureVibrateHook.install(module, classLoader, handles)
        installCreateIconHook(module, handles)
        SystemUiGestureAppearanceHook.install(module, classLoader, handles)
        installExclusionZoneHook(module, handles)

        val enabled = SystemUiConfig.gestureBackEnabled()
        val alpha = SystemUiConfig.gestureBackIconAlpha()
        module.log(
            Log.INFO, TAG,
            "Gesture back config: enabled=$enabled alpha=$alpha"
        )

        if (!enabled) {
            module.log(Log.INFO, TAG, "Gesture back master switch disabled, skipping alpha hook")
            return
        }

        val setBackIconMethod = handles.setBackIconMethod ?: return
        val iconPaintField = handles.iconPaintField ?: return

        runCatching {
            module.hook(setBackIconMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val nowEnabled = SystemUiConfig.gestureBackEnabled()
                    val nowAlpha = SystemUiConfig.gestureBackIconAlpha()
                    val paint = iconPaintField.get(chain.thisObject) as? Paint
                    if (nowEnabled && nowAlpha != SystemUiConfig.DEFAULT_ALPHA) {
                        paint?.alpha = nowAlpha
                    }
                    // Records only the first hit; setBackIcon fires repeatedly during the gesture.
                    if (callbackCount.incrementAndGet() == 1) {
                        module.log(
                            Log.INFO, TAG,
                            "setBackIcon intercepted (first hit): enabled=$nowEnabled alpha=${paint?.alpha}"
                        )
                    }
                } catch (t: Throwable) {
                    module.log(Log.WARN, TAG, "Failed to apply gesture back icon alpha", t)
                }
                result
            }
            module.log(
                Log.INFO, TAG,
                "Hooked setBackIcon on ${handles.gestureNavViewClass?.name}"
            )
        }.onFailure {
            module.log(Log.ERROR, TAG, "fail closed: Hook setBackIcon failed", it)
        }

        installProbes(module, handles)
    }

    private fun installCreateIconHook(
        module: XposedModule,
        handles: SystemUiHandles
    ) {
        val createIcon = handles.managerCreateIconMethod
        if (createIcon == null) {
            module.log(Log.WARN, TAG, "createIcon() method missing -> appearance/alpha hook disabled")
            return
        }
        if (handles.managerViewFields.isEmpty()) {
            module.log(Log.WARN, TAG, "SideGestureNavView fields in manager missing -> appearance hook disabled")
            return
        }
        val iconPaintField = handles.iconPaintField ?: return

        runCatching {
            module.hook(createIcon).intercept { chain ->
                val r = chain.proceed()
                try {
                    val appearanceApplied =
                        SystemUiGestureAppearanceHook.applyTo(module, handles, chain.thisObject)

                    if (!appearanceApplied && SystemUiConfig.gestureBackEnabled()) {
                        val a = SystemUiConfig.gestureBackIconAlpha()
                        if (a != SystemUiConfig.DEFAULT_ALPHA) {
                            var applied = 0
                            for (f in handles.managerViewFields) {
                                val view = f.get(chain.thisObject) ?: continue
                                (iconPaintField.get(view) as? Paint)?.alpha = a
                                applied++
                            }
                            module.log(
                                Log.INFO, TAG,
                                "createIcon: applied iconPaint.alpha=$a ($applied views)"
                            )
                        }
                    }
                } catch (t: Throwable) {
                    module.log(Log.WARN, TAG, "Post-createIcon handling failed", t)
                }
                r
            }
            module.log(Log.INFO, TAG, "Hooked SideGestureViewManager#createIcon")
        }.onFailure { module.log(Log.ERROR, TAG, "createIcon hook failed", it) }
    }

    /**
     * Diagnostic probe: observation only, **installed in debug builds only**.
     *
     * Default log cap is 3 records.
     */
    private fun installProbes(module: XposedModule, handles: SystemUiHandles) {
        if (!io.github.ra1nify.realos.BuildConfig.DEBUG) return

        fun probe(label: String, method: Method?, cap: Int = 3) {
            if (method == null) {
                module.log(Log.WARN, TAG, "Probe target missing: $label")
                return
            }
            val hits = AtomicInteger(0)
            runCatching {
                module.hook(method).intercept { chain ->
                    val r = chain.proceed()
                    val n = hits.incrementAndGet()
                    if (n <= cap) {
                        module.log(Log.INFO, TAG, "Probe hit: $label (#$n/$cap)")
                    }
                    r
                }
                module.log(Log.INFO, TAG, "Probe installed: $label")
            }.onFailure { module.log(Log.ERROR, TAG, "Probe install failed: $label", it) }
        }

        probe("SideGestureViewManager#createView", handles.managerCreateViewMethod)
        probe("SideGestureNavView#onDraw", handles.navViewOnDrawMethod, cap = 3)
    }

    private fun installExclusionZoneHook(
        module: XposedModule,
        handles: SystemUiHandles
    ) {
        val managerOnDown = handles.managerOnDownEventMethod ?: run {
            module.log(Log.WARN, TAG, "SideGestureViewManager#onDownEvent missing -> exclusion zone disabled")
            return
        }

        runCatching {
            module.hook(managerOnDown).intercept { chain ->
                val view = handles.managerViewFields.firstNotNullOfOrNull { f ->
                    runCatching { f.get(chain.thisObject) as? android.view.View }.getOrNull()
                }
                val ctx = view?.context
                val minRatio = SystemUiConfig.gestureActiveMinRatio(ctx)
                val maxRatio = SystemUiConfig.gestureActiveMaxRatio(ctx)

                if (minRatio > 0 || maxRatio < 100) {
                    val event = chain.args.getOrNull(1) as? android.view.MotionEvent
                    if (event != null) {
                        val rawY = event.rawY
                        val screenH = ctx?.resources?.displayMetrics?.heightPixels ?: 2400
                        val touchPctFromBottom = ((screenH - rawY) / screenH.toFloat()) * 100f

                        if (touchPctFromBottom < minRatio || touchPctFromBottom > maxRatio) {
                            module.log(
                                Log.INFO, TAG,
                                "Side gesture intercepted outside active zone: touchPctFromBottom=$touchPctFromBottom%"
                            )
                            return@intercept null
                        }
                    }
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "Hooked SideGestureViewManager#onDownEvent (exclusion zone)")
        }.onFailure {
            module.log(Log.ERROR, TAG, "Exclusion zone hook failed", it)
        }
    }
}
