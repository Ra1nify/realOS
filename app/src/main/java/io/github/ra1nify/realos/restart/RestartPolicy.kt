package io.github.ra1nify.realos.restart

/**
 * Targets for one spec page's restart action.
 *
 * [packages] holds package names for force-stop and process patterns for pkill.
 * The command per target comes from [RestartConfig]. [successMessageRes] is the
 * host-specific success toast; failure uses the shared root-required message.
 */
data class RestartPolicy(
    val packages: List<String> = emptyList(),
    val relaunchHome: Boolean = false,
    val successMessageRes: Int? = null
)
