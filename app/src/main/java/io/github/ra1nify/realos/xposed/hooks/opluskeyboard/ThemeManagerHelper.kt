package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Helper to inspect and switch themes in com.oplus.keyboard via ThemeManager reflection.
 * Uses structural signatures to maintain multi-version compatibility against obfuscation.
 */
object ThemeManagerHelper {

    private const val TAG = "ThemeManagerHelper"

    @Volatile
    private var cachedThemeManager: Any? = null
    @Volatile
    private var cachedQMethod: Method? = null
    @Volatile
    private var managerResolved: Boolean = false

    /**
     * Checks if the current theme is official built-in theme (ThemeType.INNER).
     *
     * Fail-closed: returns false on reflection failure.
     */
    fun isInnerTheme(context: Context): Boolean = runCatching {
        val (themeManager, qMethod) = getResolvedManagerAndMethod(context) ?: return false
        qMethod.invoke(themeManager) as? Boolean ?: false
    }.getOrDefault(false)

    private fun getResolvedManagerAndMethod(context: Context): Pair<Any, Method>? {
        val manager = cachedThemeManager
        val method = cachedQMethod
        if (manager != null && method != null) {
            return Pair(manager, method)
        }
        if (managerResolved) return null

        synchronized(this) {
            if (cachedThemeManager != null && cachedQMethod != null) {
                return Pair(cachedThemeManager!!, cachedQMethod!!)
            }
            if (managerResolved) return null
            managerResolved = true

            val tm = getThemeManager(context) ?: return null
            val q = tm.javaClass.declaredMethods.firstOrNull { m ->
                (m.returnType == java.lang.Boolean.TYPE || m.returnType == Boolean::class.javaObjectType) &&
                    m.parameterTypes.isEmpty()
            } ?: return null
            q.isAccessible = true
            cachedThemeManager = tm
            cachedQMethod = q
            return Pair(tm, q)
        }
    }

    /**
     * Checks if the day/night mode is currently following system.
     */
    fun isFollowSystem(context: Context): Boolean = runCatching {
        val sp = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val mode = sp.getString("key_day_night_mode", "MODE_FOLLOW_SYS")
        mode == "MODE_FOLLOW_SYS"
    }.getOrDefault(true)

    /**
     * One-click apply official default theme and set mode to follow system.
     */
    fun applyDefaultTheme(context: Context): Boolean = runCatching {
        Log.i(TAG, "Applying official default theme and MODE_FOLLOW_SYS...")

        // 1. Update SharedPreferences
        val sp = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        sp.edit().putString("key_day_night_mode", "MODE_FOLLOW_SYS").apply()

        // 2. Call ThemeManager.applyTheme("Theme.Keyboard.Light")
        val themeManager = getThemeManager(context) ?: return false
        val applyMethod = themeManager.javaClass.declaredMethods.firstOrNull { m ->
            m.parameterTypes.size == 1 && m.parameterTypes[0] == String::class.java
        } ?: return false
        applyMethod.isAccessible = true
        applyMethod.invoke(themeManager, "Theme.Keyboard.Light")
        Log.i(TAG, "Official default theme applied successfully.")
        true
    }.getOrElse { t ->
        Log.e(TAG, "Failed to apply official default theme", t)
        false
    }

    private fun getThemeManager(context: Context): Any? = runCatching {
        val classLoader = context.classLoader
        val pkg = "com.oplus.keyboard.base.theme"

        val candidateNames = listOf("$pkg.b", "$pkg.a", "$pkg.c", "$pkg.ThemeManager")
        val clazz = candidateNames.mapNotNull { name ->
            runCatching { classLoader.loadClass(name) }.getOrNull()
        }.firstOrNull { clz ->
            clz.declaredFields.any { Modifier.isStatic(it.modifiers) }
        }

        if (clazz != null) {
            val lazyField = clazz.declaredFields.firstOrNull { Modifier.isStatic(it.modifiers) }
            if (lazyField != null) {
                lazyField.isAccessible = true
                val lazyObj = lazyField.get(null) ?: return null
                val valueProp = lazyObj.javaClass.methods.firstOrNull {
                    it.name == "getValue" || (it.parameterTypes.isEmpty() && !it.returnType.name.startsWith("void"))
                }
                return valueProp?.invoke(lazyObj) ?: lazyObj
            }
        }
        null
    }.getOrNull()
}
