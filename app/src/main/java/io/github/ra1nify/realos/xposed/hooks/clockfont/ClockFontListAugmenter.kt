package io.github.ra1nify.realos.xposed.hooks.clockfont

/**
 * Inserts custom values before the host's hidden last font entry.
 * The host drops the last item when showLast is false.
 */
internal object ClockFontListAugmenter {

    fun augment(
        original: List<String>,
        custom: List<String>,
        showLast: Boolean
    ): List<String> {
        if (custom.isEmpty()) return original

        val additions = custom.distinct().filterNot { it in original }
        if (additions.isEmpty()) return original

        if (showLast) return original + additions
        if (original.isEmpty()) {
            // The host drops the last entry; keep a dummy so every custom value survives.
            return additions + ""
        }
        return original.dropLast(1) + additions + original.last()
    }
}
