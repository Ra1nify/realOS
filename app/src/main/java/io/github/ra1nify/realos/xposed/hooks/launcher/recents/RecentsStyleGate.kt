package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import android.os.SystemClock
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.hooks.launcher.LauncherHandles
import java.lang.reflect.Method

/**
 * Style plugin gate — **the only host hook point in the plugin system**.
 *
 * Hooks `OplusRecentsViewImpl#currentDelegate()`:
 * ```
 * base = chain.proceed()                  // host selection logic always runs first; its result is kept
 * id   = Settings.Secure[key_recent_style] (500ms cache)
 * plugin = Registry.descriptor(id)
 *   ├─ null      → deactivate (if any) → return base   ← official style, zero footprint
 *   └─ non-null  → activate/keep → plugin.wrapDelegate(base)
 * ```
 *
 * Design points:
 * - **does not touch** `gp.a.a()` / `ip.a.d()` / `CommonSettingsValueProxy` or other host global switches;
 * - on a non-matching plugin id: one map lookup + one "needs deactivation" check, no other work;
 * - a plugin activation failure always rolls back to `base`; the host never sees a half-built result.
 */
object RecentsStyleGate {

    private const val TAG = "RealOS-StyleGate"

    private const val SECURE_KEY_RECENT_STYLE = "key_recent_style"

    /** Style value cache duration */
    private const val STYLE_TTL_MS = 500L

    /** Style snapshot: value and timestamp are published together, so readers never see a new timestamp with an old value. */
    private data class StyleSnapshot(val id: String?, val at: Long)

    @Volatile
    private var styleSnapshot = StyleSnapshot(null, 0L)

    @Volatile
    private var activeId: String? = null

    @Volatile
    private var activePlugin: RecentsLayoutPlugin? = null

    /**
     * Context reused during the current activation.
     *
     * `currentDelegate()` runs dozens of times per frame; allocating a `PluginContext` each time
     * is pure garbage — one instance is reused while the view instance is unchanged.
     */
    @Volatile
    private var activeContext: PluginContext? = null

    /** Host style reader method static (Context) -> String; raw Settings.Secure read is the fallback when unresolved */
    @Volatile
    private var styleReaderMethod: Method? = null

    fun install(module: XposedModule, symbols: LauncherHandles) {
        val method = symbols.recentsCurrentDelegateMethod ?: run {
            module.log(Log.WARN, TAG, "fail closed: currentDelegate() not resolved; the plugin system stays disabled")
            return
        }
        if (!method.returnType.isInterface) {
            module.log(Log.WARN, TAG, "fail closed: currentDelegate() does not return an interface")
            return
        }

        styleReaderMethod = symbols.recentStyleReaderMethod.also {
            if (it == null) {
                module.log(
                    Log.WARN, TAG,
                    "Style reader not resolved; falling back to reading Settings.Secure[$SECURE_KEY_RECENT_STYLE] directly"
                )
            }
        }

        runCatching {
            module.hook(method).intercept { chain ->
                val base = chain.proceed() ?: return@intercept null

                val view = chain.thisObject as? View ?: return@intercept base
                val styleId = currentStyleId(view)
                val descriptor = RecentsStyleRegistry.descriptor(styleId)

                if (descriptor == null) {
                    // Official style (stacked / tiled / unknown value) — deactivates any plugin left over
                    deactivateIfAny(module)
                    return@intercept base
                }

                val ctx = contextFor(module, view)
                val plugin = ensureActive(module, descriptor, ctx) ?: return@intercept base

                runCatching { plugin.wrapDelegate(base, ctx) }.getOrElse { error ->
                    module.log(Log.WARN, TAG, "Plugin ${descriptor.id} wrapDelegate failed -> returning the delegate unchanged", error)
                    base
                }
            }
            module.log(
                Log.INFO, TAG,
                "Installed currentDelegate() (style plugin gate, registered=${RecentsStyleRegistry.all().map { it.id }})"
            )
        }.onFailure {
            module.log(Log.ERROR, TAG, "fail closed: style gate hook install failed", it)
        }
    }

    /** For hot reload */
    fun reset(module: XposedModule? = null) {
        deactivateIfAny(module)
        styleSnapshot = StyleSnapshot(null, 0L)
        activeContext = null
        styleReaderMethod = null
    }

    // ─────────────────────────────────────────────────────────────

    private fun currentStyleId(view: View): String? {
        val now = SystemClock.uptimeMillis()
        val cached = styleSnapshot
        if (now - cached.at <= STYLE_TTL_MS) return cached.id
        // The fresh value is computed before the snapshot is published, so other threads never see a new timestamp with an old style
        val fresh = runCatching {
            // The host's own reader runs first: with the key unset it backfills the stacked/tiled default
            // by OTA state, which matches host behavior closer than a raw Settings.Secure read (null)
            val reader = styleReaderMethod
            if (reader != null) {
                reader.invoke(null, view.context) as? String
            } else {
                Settings.Secure.getString(view.context.contentResolver, SECURE_KEY_RECENT_STYLE)
            }
        }.getOrNull()
        styleSnapshot = StyleSnapshot(fresh, now)
        return fresh
    }

    /** Reuses the same context while the view instance is unchanged */
    private fun contextFor(module: XposedModule, view: View): PluginContext {
        activeContext?.let { if (it.recentsView === view) return it }
        val loader = view.javaClass.classLoader ?: view.context.classLoader
        return PluginContext(module, view, loader).also { activeContext = it }
    }

    /**
     * Keeps the plugin for [descriptor] active.
     *
     * Returns null on activation failure; the caller falls back to the host delegate.
     */
    private fun ensureActive(
        module: XposedModule,
        descriptor: StyleDescriptor,
        ctx: PluginContext
    ): RecentsLayoutPlugin? {
        activePlugin?.let { if (activeId == descriptor.id) return it }

        // Style changed: tear down the previous plugin before enabling the new one
        deactivateIfAny(module)

        val plugin = runCatching { RecentsStyleRegistry.plugin(descriptor.id) }.getOrElse { error ->
            module.log(Log.ERROR, TAG, "Plugin ${descriptor.id} instantiation failed", error)
            return null
        } ?: return null

        val activated = runCatching { plugin.onActivate(ctx) }
        if (activated.isFailure) {
            module.log(Log.ERROR, TAG, "Plugin ${descriptor.id} activation failed -> rolled back", activated.exceptionOrNull())
            runCatching { plugin.onDeactivate() }
            return null
        }

        activeId = descriptor.id
        activePlugin = plugin
        module.log(Log.INFO, TAG, "Plugin activated: ${descriptor.id}")
        return plugin
    }

    private fun deactivateIfAny(module: XposedModule?) {
        val plugin = activePlugin ?: return
        val id = activeId
        // State is cleared before the callback; an exception from onDeactivate never leaves a "half-activated" state
        activePlugin = null
        activeId = null
        activeContext = null
        runCatching { plugin.onDeactivate() }.onFailure {
            module?.log(Log.WARN, TAG, "Plugin $id deactivation failed", it)
        }
        module?.log(Log.INFO, TAG, "Plugin deactivated: $id")
    }
}
