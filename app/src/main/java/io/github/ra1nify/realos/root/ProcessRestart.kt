package io.github.ra1nify.realos.root

import android.content.Context
import android.content.Intent

/**
 * Low-level host process restart commands.
 *
 * Restart targets live in `io.github.ra1nify.realos.restart.RestartPolicies`;
 * `RestartConfig` resolves the command and `RestartExecutor` maps a policy onto
 * these primitives.
 *
 * All commands run through `su -M -c` with a 15 second timeout.
 */
object ProcessRestart {

    /** `am force-stop <package>`: regular apps cold-start on the next launch. */
    fun forceStop(packageName: String): Boolean =
        RootManager.suExecRaw("am force-stop $packageName").isSuccess

    /** `pkill -f <pattern>`: persistent processes the system restarts at once. */
    fun pkillProcess(pattern: String): Boolean =
        RootManager.suExecRaw("pkill -f $pattern").isSuccess

    /** Launches the home activity after a launcher force-stop. */
    fun startHome(context: Context): Boolean = runCatching {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(home)
        true
    }.getOrDefault(false)
}
