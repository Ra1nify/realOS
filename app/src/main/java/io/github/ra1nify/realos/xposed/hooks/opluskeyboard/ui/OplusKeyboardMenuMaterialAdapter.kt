package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import android.content.Context
import io.github.ra1nify.realos.ui.theme.MenuMaterial
import io.github.ra1nify.realos.ui.theme.MenuMaterialResolver
import io.github.ra1nify.realos.ui.theme.SystemMenuMaterialResolver

/**
 * Lite-mode adapter that feeds the menu material into the keyboard's injected Compose tree.
 *
 * A lite build packages only the keyboard business, so the keyboard supplies the resolver itself
 * instead of relying on a caller from another business. The value comes from the same ColorOS
 * material switches the full build uses, so both modes render the same material; the adapter exists
 * so the keyboard owns its own seam and a future keyboard-only source can be swapped in here without
 * touching the shared theme code.
 */
internal object OplusKeyboardMenuMaterialAdapter : MenuMaterialResolver {

    override fun resolve(context: Context): MenuMaterial = SystemMenuMaterialResolver.resolve(context)
}
