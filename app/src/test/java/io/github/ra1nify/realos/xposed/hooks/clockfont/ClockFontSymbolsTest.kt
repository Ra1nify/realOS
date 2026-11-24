package io.github.ra1nify.realos.xposed.hooks.clockfont

import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests symbol cache payload round-trips. */
class ClockFontSymbolsTest {

    @Suppress("unused")
    private fun sample(value: String): Boolean = value.isNotEmpty()

    @Suppress("unused")
    private var sampleField: String = ""

    @Test
    fun pluginPayloadRoundTripPreservesDescriptors() {
        val method = MethodDescriptor.of(
            ClockFontSymbolsTest::class.java.getDeclaredMethod("sample", String::class.java)
        )
        val field = FieldDescriptor.of(
            ClockFontSymbolsTest::class.java.getDeclaredField("sampleField")
        )
        val symbols = ClockFontPluginSymbols(
            ruleVersion = 7,
            hostFingerprint = "fp",
            panelClass = ClassDescriptor("java.lang.String"),
            panelSetFontData = method,
            panelSetFontSelected = method,
            fontTypefaceCacheBuildTypeFace = method,
            textUtilsBuildTypeFace = method,
            textUtilsGetTextTypeFace = method,
            galleryStateClass = ClassDescriptor("java.lang.String"),
            gallerySetFontStyleMethods = listOf(method),
            clockLayoutPanelFontFamily = field
        )

        val restored = ClockFontPluginSymbols.fromCachePayload(
            symbols.toCachePayload("fp")
        )

        assertEquals(7, restored.ruleVersion)
        assertEquals("fp", restored.hostFingerprint)
        assertEquals("java.lang.String", restored.panelClass?.className)
        assertEquals(method, restored.panelSetFontData)
        assertEquals(method, restored.textUtilsBuildTypeFace)
        assertEquals(method, restored.textUtilsGetTextTypeFace)
        assertEquals("java.lang.String", restored.galleryStateClass?.className)
        assertEquals(listOf(method), restored.gallerySetFontStyleMethods)
        assertEquals(field, restored.clockLayoutPanelFontFamily)
        assertEquals(
            HookPointState.FOUND,
            restored.statusMap[ClockFontPluginSymbols.KEY_PANEL_SET_FONT_DATA]
        )
        assertTrue(restored.canRender)
        assertTrue(restored.canPanel)
    }

    @Test
    fun systemPayloadRoundTripPreservesStyleChangeMethodList() {
        val setTypeface = MethodDescriptor.of(
            java.io.PrintStream::class.java.getDeclaredMethod("println", String::class.java)
        )
        val styleMethod = MethodDescriptor.of(
            ClockFontSymbolsTest::class.java.getDeclaredMethod("sample", String::class.java)
        )
        val symbols = ClockFontSystemSymbols(
            ruleVersion = 3,
            hostFingerprint = "sys",
            clockClass = ClassDescriptor("java.lang.String"),
            clockSetTypeface = setTypeface,
            styleChangeMethods = listOf(styleMethod, setTypeface)
        )

        val restored = ClockFontSystemSymbols.fromCachePayload(
            symbols.toCachePayload("sys")
        )

        assertEquals(3, restored.ruleVersion)
        assertEquals("sys", restored.hostFingerprint)
        assertEquals(2, restored.styleChangeMethods.size)
        assertEquals(styleMethod, restored.styleChangeMethods[0])
        assertEquals(
            HookPointState.FOUND,
            restored.statusMap[ClockFontSystemSymbols.KEY_STYLE_CHANGE_METHODS]
        )
        assertTrue(restored.canApplyClocks)
    }
}
