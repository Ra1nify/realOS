package io.github.ra1nify.realos.xposed.hooks.clockfont

import io.github.ra1nify.realos.clockfont.ClockFontType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests Flex height clamping. */
class ClockFontHeightTest {

    @Test
    fun flexZeroClampsToAxisMinimum() {
        assertEquals(1, ClockFontHost.effectiveHeight(ClockFontType.FLEX, 0))
        assertEquals(1, ClockFontHost.effectiveHeight(ClockFontType.FLEX, -10))
    }

    @Test
    fun flexKeepsNormalRange() {
        assertEquals(50, ClockFontHost.effectiveHeight(ClockFontType.FLEX, 50))
        assertEquals(1000, ClockFontHost.effectiveHeight(ClockFontType.FLEX, 2000))
    }

    @Test
    fun fixedKeepsTheHostValue() {
        assertEquals(0, ClockFontHost.effectiveHeight(ClockFontType.FIXED, 0))
        assertEquals(50, ClockFontHost.effectiveHeight(ClockFontType.FIXED, 50))
        assertEquals(1000, ClockFontHost.effectiveHeight(ClockFontType.FIXED, 2000))
    }
}
