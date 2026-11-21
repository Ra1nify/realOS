package io.github.ra1nify.realos.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ColorOS material style, as declared by the system material switches.
 *
 * The surface material is expressed by two switches with a parent/child relation: blur is the master
 * and stroke is its sub-switch, so the stroke switch is only reachable while blur is on. Turning blur
 * off forces both off. That gives the three styles users see in Settings:
 *
 * | Style | `system_material_blur_enable` | `system_material_stroke_enable` |
 * |---|---|---|
 * | solid | 0 | 0 (forced) |
 * | blur | 1 | 0 |
 * | glass | 1 | 1 |
 *
 * A separate `Settings.Global` key, `disable_window_blurs`, is the global blur kill switch: while it
 * is non-zero the platform stops compositing window blur, so a blurred material would render as a
 * flat panel and solid is the only honest result.
 *
 * Two other blur switches on ColorOS are deliberately not read because they drive different effects:
 * `animationBlurrySwitch` blurs the launcher icons and wallpaper during app open/close transitions,
 * and `is_desktop_wallpaper_blur_enable` drives the desktop wallpaper blur. Neither changes the
 * material of a surface.
 *
 * All values come from the public `Settings` tables, which need no root and no Xposed hook.
 */
internal enum class MenuMaterial {
    /** Opaque panel: no blur behind the menu. */
    SOLID,

    /** Blurred backdrop, no stroke. */
    BLUR,

    /** Blurred backdrop with a stroke. */
    GLASS,
    ;

    /** Whether a menu should sample the page behind it. */
    val usesBackdrop: Boolean
        get() = this != SOLID
}

/**
 * Reads the ColorOS material style.
 *
 * The keys are absent on builds without the material switches, in which case [MenuMaterial.BLUR] is
 * used: that keeps the blurred menu this module shipped before the switch was honored, which is also
 * the ColorOS default.
 */
internal object MenuMaterialStyle {

    /** `Settings.System` switch enabling blur behind window surfaces. */
    private const val KEY_BLUR = "system_material_blur_enable"

    /** `Settings.System` switch enabling the material stroke. */
    private const val KEY_STROKE = "system_material_stroke_enable"

    /** `Settings.Global` switch that disables every window blur platform-wide. */
    private const val KEY_DISABLE_WINDOW_BLURS = "disable_window_blurs"

    private val listeners = CopyOnWriteArrayList<(MenuMaterial) -> Unit>()

    @Volatile
    private var observer: ContentObserver? = null

    /** Reads the current material. Never throws. */
    fun read(context: Context): MenuMaterial {
        val resolver = context.contentResolver
        val blurEnabled =
            runCatching { Settings.System.getInt(resolver, KEY_BLUR, 1) != 0 }.getOrDefault(true)
        val strokeEnabled =
            runCatching { Settings.System.getInt(resolver, KEY_STROKE, 0) != 0 }.getOrDefault(false)
        val blurKilled =
            runCatching { Settings.Global.getInt(resolver, KEY_DISABLE_WINDOW_BLURS, 0) != 0 }
                .getOrDefault(false)
        return when {
            !blurEnabled || blurKilled -> MenuMaterial.SOLID
            strokeEnabled -> MenuMaterial.GLASS
            else -> MenuMaterial.BLUR
        }
    }

    /**
     * Subscribes to material changes.
     *
     * One `ContentObserver` is shared by the whole process, for the same reason as
     * [DarkModeStyle]: a host may compose several realOS pages at once.
     *
     * @return a handle that unsubscribes when invoked; calling it twice is harmless.
     */
    fun subscribe(context: Context, onChange: (MenuMaterial) -> Unit): () -> Unit {
        val appContext = context.applicationContext ?: context
        ensureObserver(appContext)
        listeners.add(onChange)
        return {
            listeners.remove(onChange)
            Unit
        }
    }

    private fun ensureObserver(context: Context) {
        if (observer != null) return
        synchronized(this) {
            if (observer != null) return
            val created =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        val material = read(context)
                        for (listener in listeners) {
                            runCatching { listener(material) }
                        }
                    }
                }
            val registered =
                runCatching {
                    context.contentResolver.registerContentObserver(
                        Settings.System.getUriFor(KEY_BLUR),
                        false,
                        created,
                    )
                    context.contentResolver.registerContentObserver(
                        Settings.System.getUriFor(KEY_STROKE),
                        false,
                        created,
                    )
                    context.contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(KEY_DISABLE_WINDOW_BLURS),
                        false,
                        created,
                    )
                }.isSuccess
            if (registered) observer = created
        }
    }
}

/**
 * Resolves the menu material.
 *
 * The resolver is a seam: a full build lets realOS own the decision, while a lite build installs an
 * adapter from the business that is actually packaged (see the keyboard adapter). The default
 * implementation reads the ColorOS material switches, which is what every adapter is expected to
 * agree with so both modes render the same material.
 */
internal fun interface MenuMaterialResolver {
    fun resolve(context: Context): MenuMaterial
}

/** Default resolver: reads the ColorOS material switches. */
internal val SystemMenuMaterialResolver = MenuMaterialResolver { context -> MenuMaterialStyle.read(context) }

/** Replaces the resolver for the current subtree; unset means [SystemMenuMaterialResolver]. */
internal val LocalMenuMaterialResolver =
    staticCompositionLocalOf<MenuMaterialResolver> { SystemMenuMaterialResolver }

/**
 * Remembers the host material style and keeps it in sync with the system switches, so toggling the
 * material in Settings updates the next menu without a restart.
 *
 * The subscription is registered on the system switch URIs, so an adapter that resolves from the
 * same switches refreshes automatically; an adapter backed by another source would have to notify
 * through its own mechanism.
 */
@Composable
internal fun rememberMenuMaterial(): MenuMaterial {
    val context = LocalContext.current
    val resolver = LocalMenuMaterialResolver.current
    var material by remember(context, resolver) { mutableStateOf(resolver.resolve(context)) }

    DisposableEffect(context, resolver) {
        material = resolver.resolve(context)
        val unsubscribe = MenuMaterialStyle.subscribe(context) { material = resolver.resolve(context) }
        onDispose { unsubscribe() }
    }

    return material
}
