package io.github.ra1nify.realos.xposed.hooks.clockfont

/**
 * Pure list augmentation for `OplusKeyguardStyleFontSettingPanel.setFontData`.
 *
 * The host iterates the font array and skips the last element when `showLast`
 * is false:
 *
 *     for (i in array.indices) {
 *         if (i != array.lastIndex || showLast) add(array[i])
 *     }
 *
 * Appending custom values at the end therefore loses the last custom font when
 * the host passes `showLast = false`. This helper inserts them before the
 * hidden last element instead.
 *
 * No Android dependencies; covered by ClockFontListAugmenterTest.
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
