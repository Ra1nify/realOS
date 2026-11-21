package io.github.ra1nify.realos.xposed.hooks.clockfont

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Mirrors `OplusKeyguardStyleFontSettingPanel.setFontData`:
 * the last array element is skipped when `showLast` is false.
 */
class ClockFontListAugmenterTest {

    private fun visible(entries: List<String>, showLast: Boolean): List<String> =
        entries.filterIndexed { index, _ -> index != entries.lastIndex || showLast }

    @Test
    fun showLastTrueAppendsCustomAtEnd() {
        val result = ClockFontListAugmenter.augment(
            original = listOf("a", "b"),
            custom = listOf("x"),
            showLast = true
        )
        assertEquals(listOf("a", "b", "x"), result)
        assertEquals(listOf("a", "b", "x"), visible(result, showLast = true))
    }

    @Test
    fun showLastFalseInsertsBeforeHiddenLast() {
        val result = ClockFontListAugmenter.augment(
            original = listOf("a", "b"),
            custom = listOf("x"),
            showLast = false
        )
        assertEquals(listOf("a", "x", "b"), result)
        assertEquals(listOf("a", "x"), visible(result, showLast = false))
    }

    @Test
    fun showLastFalseWithSingleOriginalKeepsCustomVisible() {
        val result = ClockFontListAugmenter.augment(
            original = listOf("a"),
            custom = listOf("x"),
            showLast = false
        )
        assertEquals(listOf("x", "a"), result)
        assertEquals(listOf("x"), visible(result, showLast = false))
    }

    @Test
    fun showLastFalseWithEmptyOriginalUsesHiddenDummy() {
        val result = ClockFontListAugmenter.augment(
            original = emptyList(),
            custom = listOf("x"),
            showLast = false
        )
        assertEquals(listOf("x", ""), result)
        assertEquals(listOf("x"), visible(result, showLast = false))
    }

    @Test
    fun multipleCustomFontsAllSurviveShowLastFalse() {
        val result = ClockFontListAugmenter.augment(
            original = listOf("a", "b", "c"),
            custom = listOf("x", "y"),
            showLast = false
        )
        assertEquals(listOf("a", "b", "x", "y", "c"), result)
        assertEquals(listOf("a", "b", "x", "y"), visible(result, showLast = false))
    }

    @Test
    fun multipleCustomFontsAllSurviveEmptyOriginalShowLastFalse() {
        val result = ClockFontListAugmenter.augment(
            original = emptyList(),
            custom = listOf("x", "y"),
            showLast = false
        )
        assertEquals(listOf("x", "y", ""), result)
        assertEquals(listOf("x", "y"), visible(result, showLast = false))
    }

    @Test
    fun duplicateCustomValuesAreNotAdded() {
        val result = ClockFontListAugmenter.augment(
            original = listOf("a", "x"),
            custom = listOf("x", "y"),
            showLast = true
        )
        assertEquals(listOf("a", "x", "y"), result)
    }

    @Test
    fun duplicateValuesInsideCustomAreCollapsed() {
        val result = ClockFontListAugmenter.augment(
            original = emptyList(),
            custom = listOf("x", "x"),
            showLast = true
        )
        assertEquals(listOf("x"), result)
    }

    @Test
    fun emptyCustomKeepsOriginal() {
        val original = listOf("a", "b")
        val result = ClockFontListAugmenter.augment(
            original = original,
            custom = emptyList(),
            showLast = false
        )
        assertSame(original, result)
    }

    @Test
    fun alreadyPresentCustomKeepsOriginal() {
        val original = listOf("a", "x")
        val result = ClockFontListAugmenter.augment(
            original = original,
            custom = listOf("x"),
            showLast = false
        )
        assertSame(original, result)
    }
}
