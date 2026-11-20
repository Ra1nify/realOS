package io.github.ra1nify.realos.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * App icon switching.
 *
 * Icons are hosted by `<activity-alias>`; `application android:icon` is static.
 * Switching updates alias enabled states:
 * - exactly one alias stays enabled;
 * - enable the target first, then disable the others;
 * - PackageManager is the source of truth; SharedPreferences only restores picker state.
 *
 * ColorOS refreshes its icon cache after 1-2 seconds. DONT_KILL_APP is the only available signal.
 */
object AppIconManager {

    private const val PREFS_NAME = "app_icon"
    private const val KEY_ALIAS = "selected_alias"
    private const val KEY_HIDDEN = "icon_hidden"

    /** True when the user hid the launcher entry by disabling all aliases. */
    fun isHidden(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_HIDDEN, false) }.getOrDefault(false)

    /**
     * Hides or restores the launcher icon.
     *
     * Hide: disable all `activity-alias` entries.
     * Restore: enable the last selected alias. Exactly one alias stays enabled.
     */
    fun setHidden(context: Context, hidden: Boolean) {
        val pm = context.packageManager
        if (hidden) {
            AppIcon.entries.forEach { setEnabled(pm, context, it, false) }
        } else {
            // current() falls back to the local record when all aliases are disabled.
            setEnabled(pm, context, current(context), true)
        }
        runCatching { prefs(context).edit().putBoolean(KEY_HIDDEN, hidden).apply() }
    }

    /** Current icon: local record, then PackageManager, then default. */
    fun current(context: Context): AppIcon {
        val stored = prefs(context).getString(KEY_ALIAS, null)
        AppIcon.entries.firstOrNull { it.alias == stored }?.let { return it }

        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { isEnabled(pm, context, it) } ?: AppIcon.DEFAULT
    }

    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        setEnabled(pm, context, icon, true)
        AppIcon.entries.filter { it != icon }.forEach { setEnabled(pm, context, it, false) }
        prefs(context).edit().putString(KEY_ALIAS, icon.alias).apply()
    }

    /**
     * Startup fallback.
     *
     * After an upgrade, old shortcuts still target `MainActivity` and no alias may be enabled.
     * Enables at least the default icon.
     */
    fun ensureDefault(context: Context) {
        // Skip the fallback when the user hid the icon; a startup pass must not re-enable it.
        if (isHidden(context)) return
        val pm = context.packageManager
        if (AppIcon.entries.any { isEnabled(pm, context, it) }) return
        setEnabled(pm, context, AppIcon.DEFAULT, true)
        prefs(context).edit().putString(KEY_ALIAS, AppIcon.DEFAULT.alias).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun componentName(context: Context, icon: AppIcon): ComponentName =
        ComponentName(context.packageName, context.packageName + icon.alias)

    private fun isEnabled(pm: PackageManager, context: Context, icon: AppIcon): Boolean = runCatching {
        val component = componentName(context, icon)
        when (val state = pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> pm.getActivityInfo(component, 0).enabled
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
    }.getOrDefault(false)

    private fun setEnabled(pm: PackageManager, context: Context, icon: AppIcon, enabled: Boolean) {
        runCatching {
            pm.setComponentEnabledSetting(
                componentName(context, icon),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
