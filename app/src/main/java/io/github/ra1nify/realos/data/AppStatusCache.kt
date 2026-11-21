package io.github.ra1nify.realos.data

import android.content.Context
import io.github.ra1nify.realos.root.RootFlavor
import io.github.ra1nify.realos.root.RootInfo
import io.github.ra1nify.realos.root.RootState

data class XposedCachedStatus(
    val connected: Boolean = false,
    val frameworkName: String? = null,
    val frameworkVersion: String? = null
)

data class RootCachedStatus(
    val state: RootState = RootState.UNKNOWN,
    val flavor: RootFlavor = RootFlavor.UNKNOWN,
    val version: String? = null
) {
    fun toRootInfo(): RootInfo = RootInfo(state = state, flavor = flavor, version = version)
}

object AppStatusCache {

    private const val PREFS_NAME = "realos_app_status_cache"

    private const val KEY_XPOSED_CONNECTED = "xposed_connected"
    private const val KEY_XPOSED_FRAMEWORK_NAME = "xposed_framework_name"
    private const val KEY_XPOSED_FRAMEWORK_VERSION = "xposed_framework_version"

    private const val KEY_ROOT_STATE = "root_state"
    private const val KEY_ROOT_FLAVOR = "root_flavor"
    private const val KEY_ROOT_VERSION = "root_version"

    fun loadXposedStatus(context: Context): XposedCachedStatus {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val connected = sp.getBoolean(KEY_XPOSED_CONNECTED, false)
        val name = sp.getString(KEY_XPOSED_FRAMEWORK_NAME, null)
        val version = sp.getString(KEY_XPOSED_FRAMEWORK_VERSION, null)
        return XposedCachedStatus(
            connected = connected,
            frameworkName = name,
            frameworkVersion = version
        )
    }

    fun saveXposedStatus(context: Context, status: XposedCachedStatus): Boolean {
        val current = loadXposedStatus(context)
        if (current == status) return false // Unchanged; skip the write.

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_XPOSED_CONNECTED, status.connected)
            .putString(KEY_XPOSED_FRAMEWORK_NAME, status.frameworkName)
            .putString(KEY_XPOSED_FRAMEWORK_VERSION, status.frameworkVersion)
            .apply()
        return true
    }

    fun loadRootStatus(context: Context): RootCachedStatus {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stateStr = sp.getString(KEY_ROOT_STATE, null)
        val flavorStr = sp.getString(KEY_ROOT_FLAVOR, null)
        val version = sp.getString(KEY_ROOT_VERSION, null)

        val state = try {
            if (stateStr != null) RootState.valueOf(stateStr) else RootState.UNKNOWN
        } catch (_: Throwable) {
            RootState.UNKNOWN
        }

        val flavor = try {
            if (flavorStr != null) RootFlavor.valueOf(flavorStr) else RootFlavor.UNKNOWN
        } catch (_: Throwable) {
            RootFlavor.UNKNOWN
        }

        return RootCachedStatus(
            state = state,
            flavor = flavor,
            version = version
        )
    }

    fun saveRootStatus(context: Context, status: RootCachedStatus): Boolean {
        val current = loadRootStatus(context)
        if (current == status) return false // Unchanged; skip the write.

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_STATE, status.state.name)
            .putString(KEY_ROOT_FLAVOR, status.flavor.name)
            .putString(KEY_ROOT_VERSION, status.version)
            .apply()
        return true
    }
}
