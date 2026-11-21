package io.github.ra1nify.realos.xposed.hooks.navigation

import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Resolves the button View currently associated with the host `ButtonDispatcher`.
 *
 * Prefers a usable View (attached + visible + size > 0), then falls back to any
 * non-null candidate. Reflection handles are cached per class: `getCurrentView`,
 * `mCurrentView` and `mViews` are each resolved once.
 */
internal object NavButtonDispatcherViews {

    private val methodCache = java.util.concurrent.ConcurrentHashMap<String, Method?>()
    private val fieldCache = java.util.concurrent.ConcurrentHashMap<String, Field?>()

    private fun methodOf(cls: Class<*>, name: String): Method? =
        methodCache.getOrPut(cls.name + "#" + name) {
            runCatching { cls.getMethod(name).apply { isAccessible = true } }.getOrNull()
        }

    private fun fieldOf(cls: Class<*>, name: String): Field? =
        fieldCache.getOrPut(cls.name + "#" + name) {
            var found: Field? = null
            var c: Class<*>? = cls
            while (c != null && found == null) {
                found = runCatching { c.getDeclaredField(name) }.getOrNull()
                c = c.superclass
            }
            runCatching { found?.isAccessible = true }
            found
        }

    private fun usable(v: View?): Boolean =
        v != null && v.isAttachedToWindow && v.visibility == View.VISIBLE &&
            v.width > 0 && v.height > 0

    /**
     * @param preferred Candidate already held by the caller, from a weak cache; nullable
     * @param dispatcher Host button dispatcher; nullable
     */
    fun resolve(preferred: View?, dispatcher: Any?): View? {
        if (usable(preferred)) return preferred
        if (dispatcher is View) return dispatcher
        val d = dispatcher ?: return preferred
        val dClass = d.javaClass

        methodOf(dClass, "getCurrentView")?.let { m ->
            val v = runCatching { m.invoke(d) as? View }.getOrNull()
            if (usable(v)) return v
            if (v != null && preferred == null) return v
        }

        fieldOf(dClass, "mCurrentView")?.let { f ->
            val v = runCatching { f.get(d) as? View }.getOrNull()
            if (usable(v)) return v
            if (v != null && preferred == null) return v
        }

        fieldOf(dClass, "mViews")?.let { f ->
            val views = runCatching { (f.get(d) as? List<*>)?.filterIsInstance<View>() }.getOrNull()
            views?.firstOrNull { usable(it) }?.let { return it }
            if (preferred == null) views?.firstOrNull()?.let { return it }
        }

        return preferred
    }

    /**
     * All Views associated with the dispatcher (`mViews` + `mCurrentView`).
     *
     * Unlike [resolve], this path syncs a drawable to every reusable copy and skips
     * the attached/visible filter; callers filter on their own.
     */
    fun allViews(dispatcher: Any?): List<View> {
        val d = dispatcher ?: return emptyList()
        if (d is View) return listOf(d)
        val dClass = d.javaClass
        val out = ArrayList<View>(4)
        fieldOf(dClass, "mViews")?.let { f ->
            runCatching { (f.get(d) as? List<*>)?.filterIsInstance<View>() }
                .getOrNull()?.let { out.addAll(it) }
        }
        fieldOf(dClass, "mCurrentView")?.let { f ->
            runCatching { f.get(d) as? View }.getOrNull()?.let { if (it !in out) out.add(it) }
        }
        return out
    }

    /** Drops the caches on hot reload, when the host class view instance changed. */
    fun clearCache() {
        methodCache.clear()
        fieldCache.clear()
    }
}
