package io.github.ra1nify.realos.restart

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.root.ProcessRestart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes a [RestartPolicy] off the main thread.
 *
 * Each target resolves its command through [RestartConfig]. All modes require
 * root; [ProcessRestart] uses `su -M -c` with a 15 second timeout. Package
 * names and pkill patterns are validated before reaching the shell.
 */
object RestartExecutor {

    private const val TAG = "RealOS-Restart"

    private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")
    private val PKILL_PATTERN = Regex("^[A-Za-z0-9._:/-]+$")

    /** True when at least one target resolves to a real command. */
    fun supports(policy: RestartPolicy): Boolean =
        policy.packages.any { RestartConfig.modeFor(it) != RestartMode.NONE }

    suspend fun restart(context: Context, policy: RestartPolicy): RestartResult =
        withContext(Dispatchers.IO) {
            restartNow(context.applicationContext ?: context, policy)
        }

    private fun restartNow(context: Context, policy: RestartPolicy): RestartResult {
        val attempted = mutableListOf<String>()
        val failed = mutableListOf<String>()

        policy.packages.forEach { target ->
            when (RestartConfig.modeFor(target)) {
                RestartMode.NONE -> Unit

                RestartMode.FORCE_STOP -> {
                    attempted += target
                    if (!PACKAGE_NAME.matches(target)) {
                        Log.w(TAG, "Invalid package for force-stop: $target")
                        failed += target
                    } else if (!ProcessRestart.forceStop(target)) {
                        Log.w(TAG, "am force-stop failed: $target")
                        failed += target
                    }
                }

                RestartMode.PKILL -> {
                    attempted += target
                    if (!PKILL_PATTERN.matches(target)) {
                        Log.w(TAG, "Invalid pkill pattern: $target")
                        failed += target
                    } else if (!ProcessRestart.pkillProcess(target)) {
                        Log.w(TAG, "pkill failed: $target")
                        failed += target
                    }
                }
            }
        }

        if (attempted.isEmpty()) return RestartResult.Unsupported

        if (policy.relaunchHome && failed.size < attempted.size) {
            if (!ProcessRestart.startHome(context)) {
                Log.w(TAG, "HOME launch failed after launcher force-stop")
                return RestartResult.Failed("home launch failed")
            }
        }

        return when {
            failed.isEmpty() -> RestartResult.Success
            failed.size == attempted.size -> RestartResult.Failed("restart failed")
            else -> RestartResult.Partial(failed)
        }
    }
}
