package io.github.ra1nify.realos.xposed.hooks.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import io.github.ra1nify.realos.util.RealOSLog as Log

/**
 * Pluggable blur provider for the dock background layer.
 *
 * The stock implementation drives ColorOS' own blur controller. A future implementation can
 * supply the blur used by a floating navigation bar without changing the hook or config layers.
 */
interface DockerBlurBackend {
    val id: String

    /** Applies the blur drawable to [hotseat]; returns false when it could not be applied. */
    fun applyBlur(gates: DockerBgHandles, hotseat: Any, radiusPx: Float): Boolean
}

/** ColorOS stock hotseat blur, driven through `BlurBaseFactory` / `IBlurBaseController`. */
object StockDockerBlurBackend : DockerBlurBackend {

    override val id: String = "stock"

    override fun applyBlur(
        gates: DockerBgHandles,
        hotseat: Any,
        radiusPx: Float
    ): Boolean {
        val shortcutsField = gates.shortcutsField
        val blurPropField = gates.blurPropField
        val prepare = gates.prepareBlurBaseMethod
        val updateBlur = gates.updateBlurParamsMethod
        val updateBounds = gates.updateBoundsMethod
        if (shortcutsField == null || blurPropField == null || prepare == null ||
            updateBlur == null || updateBounds == null
        ) {
            Log.w(LauncherDockerBgHook.TAG, "fail closed: stock blur symbols missing")
            return false
        }

        val shortcuts = runCatching { shortcutsField.get(hotseat) as? View }.getOrNull()
            ?: return false
        if (shortcuts is ViewGroup && shortcuts.childCount == 0) return false
        val context = runCatching {
            hotseat.javaClass.getMethod("getContext").invoke(hotseat) as? Context
        }.getOrNull() ?: return false

        return runCatching {
            val controller = prepare.invoke(null, shortcuts, context, false, false, 4, true)
                ?: run {
                    Log.w(LauncherDockerBgHook.TAG, "Stock blur controller is null")
                    return false
                }
            blurPropField.set(hotseat, controller)
            val drawable = updateBlur.invoke(hotseat, radiusPx) as? Drawable
                ?: run {
                    Log.w(LauncherDockerBgHook.TAG, "Stock blur drawable is null")
                    return false
                }
            runCatching { drawable.callback = hotseat as? Drawable.Callback }
            gates.bgDrawableField?.set(hotseat, drawable)
            updateBounds.invoke(hotseat)
            runCatching { hotseat.javaClass.getMethod("invalidate").invoke(hotseat) }
            true
        }.getOrElse {
            Log.w(LauncherDockerBgHook.TAG, "Stock blur setup failed", it)
            false
        }
    }
}

/** Backend registry. Add a floating-navigation implementation here when needed. */
object DockerBlurBackends {
    const val NONE = "none"
    const val STOCK = "stock"

    fun resolve(id: String?): DockerBlurBackend = when (id) {
        STOCK, NONE, null -> StockDockerBlurBackend
        else -> StockDockerBlurBackend
    }
}
