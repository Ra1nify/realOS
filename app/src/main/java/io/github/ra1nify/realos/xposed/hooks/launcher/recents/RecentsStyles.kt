package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import io.github.ra1nify.realos.xposed.hooks.launcher.ui.SlimRowsPreview

/**
 * Plugin registration entry.
 *
 * Registers **metadata + factory** only; no plugin is instantiated — the real object is created
 * when the user selects the style. Adding a layout plugin is one line here.
 */
object RecentsStyles {

    fun bootstrap() {
        RecentsStyleRegistry.register(
            StyleDescriptor(
                id = SlimLayoutPlugin.ID,
                label = RecentsStyleText::slimList,
                preview = { SlimRowsPreview() },
                factory = { SlimLayoutPlugin }
            )
        )
    }
}
