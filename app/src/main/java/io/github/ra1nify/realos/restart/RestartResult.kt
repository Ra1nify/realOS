package io.github.ra1nify.realos.restart

import io.github.ra1nify.realos.R

/** Outcome of one restart attempt. */
sealed interface RestartResult {

    data object Success : RestartResult

    /** Some packages restarted, some failed. */
    data class Partial(val failedPackages: List<String>) : RestartResult

    data class Failed(val reason: String) : RestartResult

    /** The page declares [RestartMode.NONE]; the button should not be shown. */
    data object Unsupported : RestartResult
}

/** Toast text for a completed restart attempt. */
fun RestartResult.toastMessageRes(policy: RestartPolicy): Int = when (this) {
    RestartResult.Success -> policy.successMessageRes ?: R.string.action_restart
    else -> R.string.restart_failed_need_root
}
