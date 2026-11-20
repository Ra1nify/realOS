package io.github.ra1nify.realos.clockfont

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ClockFontValidatorTest {

    private val analysis = File("C:/R1p0/_analysis/oplus-clock")

    @Test
    fun cleanFixedFontIsAccepted() {
        val file = File(analysis, "pixel-aosp/fonts/RobotoFlexClockFixed-Clean.ttf")
        assumeTrue(file.isFile)
        val result = ClockFontValidator.validate(file, file.name)
        assertTrue("expected success, got $result", result is ClockFontValidator.Result.Success)
        result as ClockFontValidator.Result.Success
        assertTrue(result.type == ClockFontType.FIXED)
    }

    @Test
    fun colorOsFlexFontIsAcceptedAsFlex() {
        val file = File(analysis, "ext/KeyguardPersonalityClocks.apk__assets__fonts__tunable__OPPODigit00.ttf")
        assumeTrue(file.isFile)
        val result = ClockFontValidator.validate(file, file.name)
        assertTrue("expected success, got $result", result is ClockFontValidator.Result.Success)
        result as ClockFontValidator.Result.Success
        assertTrue(result.type == ClockFontType.FLEX)
    }

    @Test
    fun colorOsFixedFontIsAcceptedAsFixed() {
        val file = File(analysis, "ext/KeyguardPersonalityClocks.apk__assets__fonts__fixed__OPPODigit01.ttf")
        assumeTrue(file.isFile)
        val result = ClockFontValidator.validate(file, file.name)
        assertTrue("expected success, got $result", result is ClockFontValidator.Result.Success)
        result as ClockFontValidator.Result.Success
        assertTrue(result.type == ClockFontType.FIXED)
    }

    @Test
    fun nonFontIsRejected() {
        val file = File.createTempFile("not-a-font", ".ttf")
        try {
            file.writeBytes(ByteArray(2048) { 0x41 })
            val result = ClockFontValidator.validate(file, file.name)
            assertTrue(result is ClockFontValidator.Result.Failure)
        } finally {
            file.delete()
        }
    }
}
