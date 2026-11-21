package io.github.ra1nify.realos.xposed.hooks.navigation

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.os.Vibrator
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import io.github.ra1nify.realos.navigation.GestureVibrationConfig
import io.github.ra1nify.realos.navigation.SystemUiConfig

/**
 * Two-stage back gesture vibration: weak stage and strong stage.
 */
object SystemUiGestureVibrateHook {

    private const val TAG = "RealOS-SysUiVibrate"

    private const val EFFECT_TYPE = 1
    private const val ERM_WEAK_MS = 10L
    private const val ERM_STRONG_MS = 65L
    private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
    private const val SUPPRESS_TTL_MS = 1000L

    private const val OBSERVER_CLASS =
        "com.oplus.systemui.navigationbar.observer.SwipeSideGestureBackVibrateObserver"

    private const val STATE_CANCEL = 1
    private const val STATE_THRESHOLD = 2

    @Volatile
    private var module: XposedModule? = null

    private fun log(level: Int, msg: String) {
        module?.log(level, TAG, msg)
    }

    private fun log(level: Int, msg: String, t: Throwable) {
        module?.log(level, TAG, msg, t)
    }

    private class ViewState {
        var active = false
        var lastState = Int.MIN_VALUE
        var lastWeakX = Float.NaN
        var lastWeakTs = 0L
        var enabled = false
        var hapticOn = false
        var cfg: GestureVibrationConfig = GestureVibrationConfig()
        var loggedFirst = false
    }

    private val states: MutableMap<Any, ViewState> =
        Collections.synchronizedMap(WeakHashMap<Any, ViewState>())

    private var builderCtor: Constructor<*>? = null
    private var setEffectTypeMethod: Method? = null
    private var setEffectStrengthMethod: Method? = null
    private var setStrengthSettingEnabledMethod: Method? = null
    private var setAsynchronousMethod: Method? = null
    private var buildMethod: Method? = null
    private var linearMotorVibrateMethod: Method? = null

    fun install(
        module: XposedModule,
        classLoader: ClassLoader,
        handles: SystemUiHandles
    ) {
        this.module = module

        val hookPoint = handles.onUpdateGestureViewMethod
        val stateMethod = handles.containerStateMethod
        val pointMethod = handles.containerPointMethod
        val hasDownField = handles.hasDownEventField
        val lastGestureStateField = handles.lastGestureStateField
        if (hookPoint == null || stateMethod == null || pointMethod == null || hasDownField == null) {
            module.log(
                Log.WARN, TAG,
                "fail closed: vibration symbols incomplete (hook=${hookPoint != null} state=${stateMethod != null} point=${pointMethod != null} hasDown=${hasDownField != null}) -> not installing"
            )
            return
        }

        if (!prepareVibrationApi(classLoader)) {
            module.log(Log.WARN, TAG, "fail closed: vibration API unavailable -> not installing")
            return
        }

        installStockSuppressor(module, classLoader)

        runCatching {
            module.hook(hookPoint).intercept { chain ->
                val result = chain.proceed()
                try {
                    onGestureEvent(
                        chain.thisObject,
                        chain.getArg(0),
                        stateMethod,
                        pointMethod,
                        hasDownField,
                        lastGestureStateField
                    )
                } catch (t: Throwable) {
                    log(Log.WARN, "Vibration callback threw (swallowed)", t)
                }
                result
            }
            module.log(
                Log.INFO, TAG,
                "Installed: SideGestureNavView#onUpdateGestureView (two-stage vibration)"
            )
        }.onFailure {
            log(Log.ERROR, "fail closed: hook install failed", it)
        }
    }

    private fun installStockSuppressor(module: XposedModule, classLoader: ClassLoader) {
        val observerCls = runCatching { classLoader.loadClass(OBSERVER_CLASS) }.getOrNull()
        if (observerCls == null) {
            module.log(Log.WARN, TAG, "Takeover: $OBSERVER_CLASS not found -> skipped (double vibration possible)")
            return
        }
        // Requires a unique candidate; gives up when there is more than one.
        val getter = observerCls.declaredMethods.filter {
            it.parameterTypes.isEmpty() && it.returnType == Int::class.javaPrimitiveType &&
                it.name.contains("vibrate", ignoreCase = true)
        }.singleOrNull()
        if (getter == null) {
            module.log(
                Log.WARN, TAG,
                "Takeover: no getter found (candidates=${observerCls.declaredMethods.map { it.name }}) -> skipped"
            )
            return
        }
        val getContextMethod = observerCls.methods.firstOrNull {
            it.name == "getContext" && it.parameterTypes.isEmpty()
        }
        runCatching {
            getter.isAccessible = true
            getContextMethod?.isAccessible = true
            module.hook(getter).intercept { chain ->
                if (shouldSuppressStockVibration(chain.thisObject, getContextMethod)) 0
                else chain.proceed()
            }
            module.log(Log.INFO, TAG, "Takeover installed: ${getter.toGenericString()}")
        }.onFailure { log(Log.WARN, "Takeover install failed (double vibration possible)", it) }
    }

    @Volatile
    private var suppressCached: Boolean = false
    @Volatile
    private var suppressCachedTs: Long = 0L
    @Volatile
    private var observerContext: Context? = null

    private fun shouldSuppressStockVibration(observer: Any?, getContextMethod: Method?): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - suppressCachedTs < SUPPRESS_TTL_MS) return suppressCached
        val ctx = observerContext ?: runCatching {
            getContextMethod?.invoke(observer) as? Context
        }.getOrNull()?.also { observerContext = it }
        val v = SystemUiConfig.gestureBackVibrateEnabled(ctx) &&
            SystemUiConfig.gestureVibration(ctx).takeOverStrong
        suppressCached = v
        suppressCachedTs = now
        return v
    }

    private fun prepareVibrationApi(classLoader: ClassLoader): Boolean = runCatching {
        val wf = classLoader.loadClass("com.oplus.os.WaveformEffect")
        val builderCls = wf.declaredClasses.firstOrNull { it.simpleName == "Builder" } ?: return false
        builderCtor = builderCls.getDeclaredConstructor()
        builderCtor?.isAccessible = true
        setEffectTypeMethod = builderCls.getMethod("setEffectType", Int::class.javaPrimitiveType)
        setEffectStrengthMethod = builderCls.getMethod("setEffectStrength", Int::class.javaPrimitiveType)
        setStrengthSettingEnabledMethod =
            builderCls.getMethod("setStrengthSettingEnabled", Boolean::class.javaPrimitiveType)
        setAsynchronousMethod = runCatching {
            builderCls.getMethod("setAsynchronous", Boolean::class.javaPrimitiveType)
        }.getOrNull()
        buildMethod = builderCls.getMethod("build")
        linearMotorVibrateMethod = classLoader
            .loadClass("com.oplus.os.LinearmotorVibrator")
            .getMethod("vibrate", wf)
        module?.log(
            Log.INFO, TAG,
            "Vibration API resolved"
        )
        true
    }.getOrElse {
        log(Log.WARN, "Vibration API resolution failed", it)
        false
    }

    private fun onGestureEvent(
        view: Any,
        arg: Any?,
        stateMethod: Method,
        pointMethod: Method,
        hasDownField: Field,
        lastGestureStateField: Field?
    ) {
        val ctx = (view as? View)?.context ?: return
        val st = states[view] ?: ViewState().also { states[view] = it }

        val hasDown = hasDownField.getBoolean(view)
        if (!hasDown) {
            st.active = false
            st.lastState = Int.MIN_VALUE
            st.lastWeakX = Float.NaN
            return
        }

        val newGesture = if (lastGestureStateField != null) {
            lastGestureStateField.getInt(view) == -1
        } else {
            !st.active
        }
        if (newGesture) {
            st.active = true
            st.lastState = Int.MIN_VALUE
            st.lastWeakX = Float.NaN
            st.lastWeakTs = 0L
            st.loggedFirst = false
            st.enabled = SystemUiConfig.gestureBackVibrateEnabled(ctx)
            st.hapticOn = readSystemInt(ctx, KEY_HAPTIC_FEEDBACK, 0) != 0
            st.cfg = SystemUiConfig.gestureVibration(ctx)
            log(
                Log.INFO,
                "Gesture start: vibrationEnabled=${st.enabled} hapticFeedback=${st.hapticOn} weak=${st.cfg.weakStrengthClamped} strong=${st.cfg.strongStrengthClamped}"
            )
        }
        if (!st.enabled || !st.hapticOn) return

        val container = arg ?: return
        val state = stateMethod.invoke(container) as? Int ?: return
        if (state != STATE_CANCEL && state != STATE_THRESHOLD) {
            st.lastState = state
            return
        }
        val x = (pointMethod.invoke(container) as? PointF)?.x ?: return

        if (state == STATE_CANCEL) {
            val now = SystemClock.uptimeMillis()
            val dx = if (st.lastWeakX.isNaN()) Float.MAX_VALUE else abs(x - st.lastWeakX)
            val stepPx = st.cfg.stepDpClamped * ctx.resources.displayMetrics.density
            if (dx >= stepPx && now - st.lastWeakTs >= st.cfg.intervalMsClamped) {
                st.lastWeakX = x
                st.lastWeakTs = now
                vibrate(ctx, st.cfg.weakStrengthClamped, ERM_WEAK_MS)
                if (!st.loggedFirst) {
                    st.loggedFirst = true
                    log(Log.INFO, "First weak vibration: x=$x dx=$dx stepPx=$stepPx strength=${st.cfg.weakStrengthClamped}")
                }
            }
        } else {
            if (st.lastState != STATE_THRESHOLD) {
                vibrate(ctx, st.cfg.strongStrengthClamped, ERM_STRONG_MS)
                if (!st.loggedFirst) {
                    st.loggedFirst = true
                    log(Log.INFO, "First strong vibration (threshold crossed): x=$x strength=${st.cfg.strongStrengthClamped}")
                }
            }
            st.lastWeakX = x
        }
        st.lastState = state
    }

    private fun vibrate(ctx: Context, strength: Int?, ermMs: Long) {
        if (tryLinearVibrate(ctx, strength)) return
        tryErmVibrate(ctx, ermMs)
    }

    private fun tryLinearVibrate(ctx: Context, strength: Int?): Boolean {
        val vibrate = linearMotorVibrateMethod ?: return false
        val lmv = runCatching { ctx.getSystemService("linearmotor") }.getOrNull() ?: return false
        val effect = buildEffect(strength) ?: return false
        return runCatching {
            vibrate.invoke(lmv, effect)
            true
        }.getOrElse {
            log(Log.WARN, "linearmotor.vibrate failed; falling back", it)
            false
        }
    }

    private fun tryErmVibrate(ctx: Context, ms: Long) {
        runCatching {
            val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, ms), -1)
        }.onFailure { log(Log.WARN, "Fallback vibration failed", it) }
    }

    private fun buildEffect(strength: Int?): Any? = runCatching {
        val ctor = builderCtor ?: return null
        var builder: Any = ctor.newInstance()
        setEffectTypeMethod?.let { builder = it.invoke(builder, EFFECT_TYPE) ?: builder }
        if (strength != null) {
            setEffectStrengthMethod?.let { builder = it.invoke(builder, strength) ?: builder }
            setStrengthSettingEnabledMethod?.let { builder = it.invoke(builder, false) ?: builder }
        }
        setAsynchronousMethod?.let { builder = it.invoke(builder, true) ?: builder }
        buildMethod?.invoke(builder)
    }.getOrElse {
        log(Log.WARN, "Failed to build WaveformEffect", it)
        null
    }

    private fun readSystemInt(ctx: Context, key: String, def: Int): Int =
        runCatching { Settings.System.getInt(ctx.contentResolver, key, def) }.getOrDefault(def)
}
