package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule

/**
 * Plugin runtime context. **Valid only while the plugin is active**; dropped after deactivation.
 */
class PluginContext(
    val module: XposedModule,
    /** Host recents view (`OplusRecentsViewImpl`) */
    val recentsView: View,
    val classLoader: ClassLoader
) {
    /** Recents panel container (`recent_container`); null while the view tree is not ready */
    val container: ViewGroup?
        get() = recentsView.parent as? ViewGroup

    val hostContext: Context
        get() = recentsView.context
}

/**
 * Recents layout plugin.
 *
 * Lifecycle is driven by [RecentsStyleGate]; the plugin **exists only while `key_recent_style` equals [id]**:
 * ```
 * not selected           → plugin not instantiated, wrapDelegate not called
 * just selected          → instantiate → onActivate(ctx)
 * stays selected         → wrapDelegate(base, ctx) on every currentDelegate()
 * switched to another    → onDeactivate() → never called again
 * ```
 *
 * Implementation contract (violations are bugs):
 * 1. [wrapDelegate] does **incremental wrapping** only on top of `base`; no full replacement implementation;
 * 2. exceptions from [onActivate] are caught by the Gate and rolled back (`onDeactivate` + the host delegate returned unchanged);
 * 3. [onDeactivate] fully reverts the view changes it made;
 * 4. a plugin never assumes it can be uninstalled — hooks cannot be uninstalled; only the active flag short-circuits.
 */
interface RecentsLayoutPlugin {

    /** Value written to `Settings.Secure key_recent_style`, with a uniform `realos.` prefix */
    val id: String

    /**
     * Wraps the host delegate.
     *
     * Called on every host delegate fetch; implementations do no heavy work there,
     * and cached values are prepared in [onActivate].
     */
    fun wrapDelegate(base: Any, ctx: PluginContext): Any

    /** Activation: install hooks, build views. Called once (until deactivation) */
    fun onActivate(ctx: PluginContext)

    /** Deactivation: revert every change */
    fun onDeactivate()
}
