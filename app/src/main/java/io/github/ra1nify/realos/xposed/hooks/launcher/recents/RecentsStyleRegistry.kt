package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Registration entry: **static metadata** + lazy factory.
 *
 * [label] is a pure function (resolved by host locale at render time) and [preview] is fixed at
 * registration; neither instantiates a plugin — the selector lists every style at no cost for unselected ones.
 */
class StyleDescriptor(
    val id: String,
    /** Display name resolved by host locale; evaluated when the selector renders */
    val label: (Context) -> String,
    /** Custom preview (used when the host has no matching Lottie asset); null uses the official Lottie preview */
    val preview: (@Composable () -> Unit)? = null,
    val factory: () -> RecentsLayoutPlugin
)

/**
 * Style plugin registry.
 *
 * Two jobs: register metadata, create instances lazily by id.
 * Holds no host references, has no side effects, and is safe to call at install time.
 */
object RecentsStyleRegistry {

    /**
     * Written on the registration thread (install) and read on hook threads; both maps are locked:
     * concurrent `LinkedHashMap`/`HashMap` access can drop writes or read an inconsistent structure,
     * and [plugin]'s "instantiate when missing" creates duplicate plugins under concurrency.
     * One lock keeps metadata and instances consistent; the critical section holds only map operations.
     */
    private val lock = Any()

    private val descriptors = LinkedHashMap<String, StyleDescriptor>()
    private val instances = HashMap<String, RecentsLayoutPlugin>()

    fun register(descriptor: StyleDescriptor) {
        synchronized(lock) { descriptors[descriptor.id] = descriptor }
    }

    fun descriptor(id: String?): StyleDescriptor? =
        if (id == null) null else synchronized(lock) { descriptors[id] }

    /** All registered styles (registration order) for the selector */
    fun all(): List<StyleDescriptor> = synchronized(lock) { descriptors.values.toList() }

    /**
     * Returns the plugin instance for an id. **Instantiated on first call only** — the "do not initialize what is not needed" rule.
     */
    fun plugin(id: String): RecentsLayoutPlugin? = synchronized(lock) {
        descriptors[id] ?: return null
        instances[id]?.let { return it }
        val created = descriptors.getValue(id).factory()
        instances[id] = created
        created
    }

    /** For hot reload: drops instances (metadata stays; recreation is cheap) */
    fun clearInstances() {
        synchronized(lock) { instances.clear() }
    }
}
