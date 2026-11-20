package io.github.ra1nify.realos.restart

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.root.ProcessRestart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes a [RestartPolicy] off the main thread.
 *
 * All modes require root; [ProcessRestart] uses `su -M -c` with a 15 second
 * timeout. Package names and pkill patterns are validated before reaching the
 * shell. Missing root surfaces as a failed result, not an exception.
 */
object RestartExecutor {

    private const val TAG = "RealOS-Restart"

    private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")
    private val PKILL_PATTERN = Regex("^[A-Za-z0-9._:/-]+$")

    suspend fun restart(context: Context, policy: RestartPolicy): RestartResult =
        withContext(Dispatchers.IO) {
            restartNow(context.applicationContext ?: context, policy)
        }

    private fun restartNow(context: Context, policy: RestartPolicy): RestartResult =
        when (policy.mode) {
            RestartMode.NONE -> RestartResult.Unsupported

            RestartMode.FORCE_STOP -> {
                if (policy.packages.isEmpty()) {
                    return RestartResult.Failed("no package")
                }
                val invalid = policy.packages.firstOrNull { !PACKAGE_NAME.matches(it) }
                if (invalid != null) {
                    Log.w(TAG, "Invalid package for force-stop: $invalid")
                    return RestartResult.Failed("invalid package")
                }

                val failed = policy.packages.filterNot { packageName ->
                    val ok = ProcessRestart.forceStop(packageName)
                    if (!ok) Log.w(TAG, "am force-stop failed: $packageName")
                    ok
                }

                if (policy.relaunchHome && failed.size < policy.packages.size) {
                    if (!ProcessRestart.startHome(context)) {
                        Log.w(TAG, "HOME launch failed after launcher force-stop")
                        return RestartResult.Failed("home launch failed")
                    }
                }

                when {
                    failed.isEmpty() -> RestartResult.Success
                    failed.size == policy.packages.size -> RestartResult.Failed("force-stop failed")
                    else -> RestartResult.Partial(failed)
                }
            }

            RestartMode.PKILL -> {
                val pattern = policy.packages.singleOrNull()
                    ?: return RestartResult.Failed("pkill requires one pattern")
                if (!PKILL_PATTERN.matches(pattern)) {
                    Log.w(TAG, "Invalid pkill pattern: $pattern")
                    return RestartResult.Failed("invalid pattern")
                }
                if (ProcessRestart.pkillProcess(pattern)) {
                    RestartResult.Success
                } else {
                    Log.w(TAG, "pkill failed: $pattern")
                    RestartResult.Failed("pkill failed")
                }
            }
        }
}
