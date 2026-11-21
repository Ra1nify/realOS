package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

/**
 * Custom List wrapper that makes whitelist verification unconditionally evaluate to true.
 *
 * Inherits from [ArrayList] to ensure full runtime type-compatibility with host
 * type checks and casts (e.g. ArrayList / List / Collection).
 *
 * Calling [contains] returns true for any input package name, fulfilling the requirement
 * of making internal blur decision `z2` evaluate to true across all target applications,
 * with zero hook invocation or reflection overhead during active typing.
 */
class UniversalWhitelistList(
    original: Collection<String>? = null
) : ArrayList<String>(original ?: emptyList()) {

    override fun contains(element: String): Boolean = true
}

/**
 * Custom Set wrapper fallback for versions where the whitelist collection is typed as Set.
 */
class UniversalWhitelistSet(
    original: Collection<String>? = null
) : HashSet<String>(original ?: emptySet()) {

    override fun contains(element: String): Boolean = true
}
