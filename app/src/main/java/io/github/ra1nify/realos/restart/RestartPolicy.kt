package io.github.ra1nify.realos.restart

/**
 * Declarative restart policy for one host page.
 *
 * [packages] holds one or more package names; the settings page restarts both
 * Settings and Wallpapers. [relaunchHome] is only used by the launcher, where
 * force-stop alone leaves the user without a home surface.
 *
 * [successMessageRes] is the host-specific success toast; failure uses the
 * shared root-required message.
 */
data class RestartPolicy(
    val mode: RestartMode,
    val packages: List<String> = emptyList(),
    val relaunchHome: Boolean = false,
    val successMessageRes: Int? = null
)
