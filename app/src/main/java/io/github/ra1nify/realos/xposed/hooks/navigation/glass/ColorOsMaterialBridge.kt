package io.github.ra1nify.realos.xposed.hooks.navigation.glass

import android.content.Context
import android.graphics.BlendMode
import android.graphics.drawable.Drawable
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.MotionEvent
import android.view.View
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Bridge facade over the ColorOS SpotLight touch light and CornerParams rounded corner.
 */
object ColorOsMaterialBridge {
    private const val TAG = "RealOS-MaterialBridge"

    @Volatile
    var isSupported: Boolean? = null
        private set

    private var setCornerParamsMethod: Method? = null
    private var spotLightTypeCapsule2: Any? = null
    private var cornerParamsConstructor: Constructor<*>? = null
    private var setRadiusMethod: Method? = null
    private var setWeightMethod: Method? = null

    private var spotLightDrawableClass: Class<*>? = null
    private var setSpotLightTypeMethod: Method? = null
    private var setPaintBlendModeMethod: Method? = null
    private var onTouchEventMethod: Method? = null
    private var extinguishMethod: Method? = null
    private var cancelMethod: Method? = null
    private var glowMethod: Method? = null

    fun init(classLoader: ClassLoader): Boolean {
        if (isSupported != null) return isSupported == true
        return try {
            val materialEffectClz = Class.forName("com.coui.appcompat.COUIMaterialEffect", false, classLoader)
            val cornerParamsClz = Class.forName("com.oplus.view.material.OplusMaterialCornerParams", false, classLoader)

            cornerParamsConstructor = cornerParamsClz.getConstructor(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            setRadiusMethod = cornerParamsClz.getMethod("setRadius", Float::class.javaPrimitiveType)
            setWeightMethod = cornerParamsClz.getMethod("setWeight", Float::class.javaPrimitiveType)
            setCornerParamsMethod = materialEffectClz.getMethod("setCornerParams", View::class.java, cornerParamsClz)

            runCatching {
                val spotLightClz = Class.forName("com.coui.appcompat.spotlight.COUISpotLightEffectDrawable", false, classLoader)
                val spotLightTypeClz = Class.forName("com.coui.appcompat.spotlight.COUISpotLightEffect\$SpotLightType", false, classLoader)
                spotLightDrawableClass = spotLightClz
                setSpotLightTypeMethod = spotLightClz.getMethod("setSpotLightType", Context::class.java, spotLightTypeClz)
                setPaintBlendModeMethod = spotLightClz.getMethod("setPaintBlendMode", BlendMode::class.java)
                onTouchEventMethod = spotLightClz.getMethod("onTouchEvent", MotionEvent::class.java)
                extinguishMethod = spotLightClz.getMethod("extinguish")
                cancelMethod = spotLightClz.getMethod("cancel")
                glowMethod = spotLightClz.getMethod("glow")

                @Suppress("UNCHECKED_CAST")
                val spotLightEnumClz = spotLightTypeClz as Class<out Enum<*>>
                spotLightTypeCapsule2 = runCatching { java.lang.Enum.valueOf(spotLightEnumClz, "TYPE_CAPSULE_2") }.getOrNull()
                    ?: runCatching { java.lang.Enum.valueOf(spotLightEnumClz, "TYPE_CAPSULE_1") }.getOrNull()
            }.onFailure {
                Log.w(TAG, "COUISpotLightEffectDrawable initialization limited: ${it.message}")
            }

            isSupported = true
            Log.i(TAG, "ColorOS official appearance material engine initialized (CornerParams and SpotLight)")
            true
        } catch (t: Throwable) {
            isSupported = false
            Log.w(TAG, "ColorOS official material engine unavailable: ${t.message}")
            false
        }
    }

    fun createCornerParams(radius: Float, weight: Float = 2.0f): Any? {
        if (isSupported != true) return null
        return runCatching { cornerParamsConstructor?.newInstance(radius, weight) }.getOrNull()
    }

    fun updateCornerRadius(params: Any, radius: Float) {
        runCatching { setRadiusMethod?.invoke(params, radius) }
    }

    fun applyCornerParams(view: View, params: Any?): Boolean {
        if (isSupported != true) return false
        val method = setCornerParamsMethod ?: return false
        return runCatching {
            method.invoke(null, view, params)
            true
        }.getOrDefault(false)
    }

    fun clearCornerParams(view: View) {
        if (isSupported != true) return
        val method = setCornerParamsMethod ?: return
        runCatching { method.invoke(null, view, null) }
    }

    fun createSpotLightDrawable(context: Context): Drawable? {
        if (isSupported != true) return null
        val clz = spotLightDrawableClass ?: return null
        return runCatching {
            val drawable = clz.getDeclaredConstructor().newInstance() as Drawable
            spotLightTypeCapsule2?.let { setSpotLightTypeMethod?.invoke(drawable, context, it) }
            setPaintBlendModeMethod?.invoke(drawable, BlendMode.OVERLAY)
            drawable
        }.getOrNull()
    }

    fun dispatchSpotLightTouchEvent(drawable: Drawable?, event: MotionEvent): Boolean {
        if (drawable == null) return false
        val method = onTouchEventMethod ?: return false
        return runCatching {
            method.invoke(drawable, event)
            true
        }.getOrDefault(false)
    }

    fun extinguishSpotLight(drawable: Drawable?) {
        if (drawable == null) return
        runCatching { extinguishMethod?.invoke(drawable) }
    }

    fun cancelSpotLight(drawable: Drawable?) {
        if (drawable == null) return
        runCatching { cancelMethod?.invoke(drawable) }
    }
}
