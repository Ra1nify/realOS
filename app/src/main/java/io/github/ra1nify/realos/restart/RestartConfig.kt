package io.github.ra1nify.realos.restart

import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * Resolves the restart mode for one host package.
 *
 * The project default and per-host overrides live in app/build.gradle.kts
 * (`RESTART_PROJECT_MODE` / `RESTART_PROJECT_SCOPES`) and arrive through
 * BuildConfig. A default of 0 disables every restart action.
 * android/system are always NONE. SystemUI uses pkill because force-stop sets
 * the stopped flag and the persistent process may not return.
 */
object RestartConfig {

    private val defaultMode: RestartMode =
        RestartMode.fromValue(BuildConfig.RESTART_MODE) ?: RestartMode.FORCE_STOP

    private val overrides: Map<String, RestartMode> by lazy {
        BuildConfig.RESTART_SCOPE_MODES
            .split(',')
            .mapNotNull { entry ->
                val packageName = entry.substringBefore(':', "").trim()
                val mode = RestartMode.fromValue(
                    entry.substringAfter(':', "").trim().toIntOrNull() ?: -1
                )
                if (packageName.isEmpty() || mode == null) null else packageName to mode
            }
            .toMap()
    }

    fun modeFor(packageName: String): RestartMode {
        if (defaultMode == RestartMode.NONE) return RestartMode.NONE
        if (packageName == SpecPackages.ANDROID || packageName == SpecPackages.SYSTEM) {
            return RestartMode.NONE
        }
        overrides[packageName]?.let { return it }
        if (packageName == SpecPackages.SYSTEMUI) {
            return RestartMode.PKILL
        }
        return defaultMode
    }
}
