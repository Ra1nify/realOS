package io.github.ra1nify.realos.clockfont

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Tests the SystemUI variant against stock ColorOS fonts. */
class ClockFontSystemVariantTest {

    private val flexFont = File(
        "C:/R1p0/_analysis/oplus-clock/ext/" +
            "KeyguardPersonalityClocks.apk__assets__fonts__tunable__OPPODigit00.ttf"
    )

    private val fixedFont = File(
        "C:/R1p0/_analysis/oplus-clock/ext/" +
            "KeyguardPersonalityClocks.apk__assets__fonts__fixed__OPPODigit01.ttf"
    )

    private val profile = ClockFontSystemVariant.Profile(
        ascentRatio = 0.841f,
        descentRatio = 0.277f,
        lineGapRatio = 0f,
        advanceAt1000 = buildMap {
            for (ch in "0123456789") put(ch, 593)
            put(':', 309)
            put('.', 250)
            put(' ', 250)
        }
    )

    @Test
    fun normalizesVerticalMetricsAndDigitAdvances() {
        assumeTrue(flexFont.isFile)
        val source = flexFont.readBytes()
        val before = ClockFontSystemVariant.metricsOf(source)!!
        val patched = ClockFontSystemVariant.normalize(source, profile)!!
        val after = ClockFontSystemVariant.metricsOf(patched)!!

        assertEquals(1000, after.unitsPerEm)
        assertEquals(841, after.ascent)
        assertEquals(-277, after.descent)
        assertEquals(0, after.lineGap)
        assertTrue("stock flex descent should be pathological", before.descent < -1000)

        assertEquals(593, ClockFontSystemVariant.advanceOf(patched, '0'))
        assertEquals(593, ClockFontSystemVariant.advanceOf(patched, '9'))
        assertEquals(309, ClockFontSystemVariant.advanceOf(patched, ':'))
        // Shared tail metric covers period and colon.
        assertEquals(309, ClockFontSystemVariant.advanceOf(patched, '.'))
    }

    @Test
    fun normalizesFixedFontToTheSameStockProfile() {
        assumeTrue(fixedFont.isFile)
        val source = fixedFont.readBytes()
        val before = ClockFontSystemVariant.metricsOf(source)!!
        val patched = ClockFontSystemVariant.normalize(source, profile)!!
        val after = ClockFontSystemVariant.metricsOf(patched)!!

        assertEquals(841, after.ascent)
        assertEquals(-277, after.descent)
        assertTrue("Fixed stock ascent should differ", before.ascent != after.ascent)
        assertEquals(593, ClockFontSystemVariant.advanceOf(patched, '0'))
    }

    @Test
    fun rejectsNonFontBytes() {
        assertNull(
            ClockFontSystemVariant.normalize(ByteArray(2048) { 0x41 }, profile)
        )
    }
}
