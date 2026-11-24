package io.github.ra1nify.realos.clockfont

/** Builds the stock-metrics SystemUI clock font variant. */
object ClockFontSystemVariant {

    private const val SFNT_TRUETYPE = 0x00010000
    private const val SFNT_TRUE = 0x74727565

    private const val GLYPH_CHARS = "0123456789:. "

    /** Stock metrics used to patch the variant. */
    data class Profile(
        val ascentRatio: Float,
        val descentRatio: Float,
        val lineGapRatio: Float = 0f,
        val advanceAt1000: Map<Char, Int> = emptyMap()
    )

    data class VerticalMetrics(
        val unitsPerEm: Int,
        val ascent: Int,
        val descent: Int,
        val lineGap: Int
    )

    /** Applies [profile]; returns null for unsupported fonts. */
    fun normalize(source: ByteArray, profile: Profile): ByteArray? =
        runCatching { FontFile(source).normalize(profile) }.getOrNull()

    /** Returns the advance width of [ch] in font units. */
    fun advanceOf(source: ByteArray, ch: Char): Int? =
        runCatching { FontFile(source).advanceOf(ch) }.getOrNull()

    /** Returns current hhea metrics. */
    fun metricsOf(source: ByteArray): VerticalMetrics? =
        runCatching { FontFile(source).metricsOf() }.getOrNull()

    private class FontFile(private val bytes: ByteArray) {

        private lateinit var sfnt: SfntTables
        private var unitsPerEm = 0
        private var numGlyphs = 0
        private var numberOfHMetrics = 0

        fun normalize(profile: Profile): ByteArray? {
            parse()
            val hhea = sfnt.table("hhea")?.offset ?: error("missing hhea")
            val hmtx = sfnt.table("hmtx")?.offset ?: error("missing hmtx")
            val cmapRange = sfnt.table("cmap") ?: error("missing cmap")
            val cmap = cmapRange.offset
            val cmapLength = cmapRange.length

            val ascent = (profile.ascentRatio * unitsPerEm).toInt()
            val descent = -(profile.descentRatio * unitsPerEm).toInt()
            val lineGap = (profile.lineGapRatio * unitsPerEm).toInt()

            val copy = bytes.copyOf()
            writeI16(copy, hhea, 4, ascent)
            writeI16(copy, hhea, 6, descent)
            writeI16(copy, hhea, 8, lineGap)

            val os2 = sfnt.table("OS/2")
            if (os2 != null && os2.length >= 78) {
                writeI16(copy, os2.offset, 68, ascent)
                writeI16(copy, os2.offset, 70, descent)
                writeI16(copy, os2.offset, 72, lineGap)
                writeU16(copy, os2.offset, 74, ascent.coerceAtLeast(0))
                writeU16(copy, os2.offset, 76, (-descent).coerceAtLeast(0))
            }

            if (profile.advanceAt1000.isNotEmpty()) {
                val lastMetricIndex = numberOfHMetrics - 1
                var lastMetricTarget: Int? = null
                var sharedTarget: Int? = null

                for (ch in GLYPH_CHARS) {
                    val targetAt1000 = profile.advanceAt1000[ch] ?: continue
                    val gid = SfntCmap.glyphForChar(
                        bytes,
                        cmap,
                        cmapLength,
                        ch.code,
                        numGlyphs
                    ) ?: continue
                    if (gid <= 0 || gid >= numGlyphs) continue
                    val target = (targetAt1000 * unitsPerEm / 1000f).toInt().coerceAtLeast(1)
                    if (gid < numberOfHMetrics) {
                        writeU16(copy, hmtx, gid * 4, target)
                        if (gid == lastMetricIndex) {
                            lastMetricTarget = maxOf(lastMetricTarget ?: 0, target)
                        }
                    } else {
                        // Shared tail glyphs use the largest target advance.
                        sharedTarget = maxOf(sharedTarget ?: 0, target)
                    }
                }

                if (sharedTarget != null) {
                    writeU16(
                        copy,
                        hmtx,
                        lastMetricIndex * 4,
                        maxOf(lastMetricTarget ?: 0, sharedTarget)
                    )
                }
            }

            return copy
        }

        fun advanceOf(ch: Char): Int? {
            parse()
            val hmtx = sfnt.table("hmtx")?.offset ?: return null
            val cmapRange = sfnt.table("cmap") ?: return null
            val gid = SfntCmap.glyphForChar(
                bytes,
                cmapRange.offset,
                cmapRange.length,
                ch.code,
                numGlyphs
            ) ?: return null
            if (gid <= 0 || gid >= numGlyphs) return null
            return if (gid < numberOfHMetrics) {
                sfnt.u16(hmtx + gid * 4)
            } else {
                sfnt.u16(hmtx + (numberOfHMetrics - 1) * 4)
            }
        }

        fun metricsOf(): VerticalMetrics {
            parse()
            val hhea = sfnt.table("hhea")?.offset ?: error("missing hhea")
            return VerticalMetrics(
                unitsPerEm = unitsPerEm,
                ascent = sfnt.i16(hhea + 4),
                descent = sfnt.i16(hhea + 6),
                lineGap = sfnt.i16(hhea + 8)
            )
        }

        private fun parse() {
            val version = SfntTables.versionOf(bytes)
            require(version == SFNT_TRUETYPE.toLong() || version == SFNT_TRUE.toLong()) {
                "unsupported sfnt version"
            }
            sfnt = SfntTables.parse(bytes)

            val headRange = sfnt.table("head") ?: error("missing head")
            require(headRange.length >= 54) { "head truncated" }
            unitsPerEm = sfnt.u16(headRange.offset + 18)
            require(unitsPerEm in 16..16384) { "unitsPerEm" }

            val maxpRange = sfnt.table("maxp") ?: error("missing maxp")
            require(maxpRange.length >= 6) { "maxp truncated" }
            numGlyphs = sfnt.u16(maxpRange.offset + 4)
            require(numGlyphs in 1..0xFFFF) { "numGlyphs" }

            val hheaRange = sfnt.table("hhea") ?: error("missing hhea")
            require(hheaRange.length >= 36) { "hhea truncated" }
            numberOfHMetrics = sfnt.u16(hheaRange.offset + 34)
            require(numberOfHMetrics in 1..numGlyphs) { "numberOfHMetrics" }

            val hmtxRange = sfnt.table("hmtx") ?: error("missing hmtx")
            val required = numberOfHMetrics * 4 + (numGlyphs - numberOfHMetrics) * 2
            require(hmtxRange.length >= required) { "hmtx truncated" }

            val cmapRange = sfnt.table("cmap") ?: error("missing cmap")
            require(cmapRange.length >= 4) { "cmap truncated" }
        }

        private fun writeU16(target: ByteArray, table: Int, relative: Int, value: Int) {
            val offset = table + relative
            require(offset >= 0 && offset + 2 <= target.size)
            target[offset] = (value ushr 8).toByte()
            target[offset + 1] = value.toByte()
        }

        private fun writeI16(target: ByteArray, table: Int, relative: Int, value: Int) {
            writeU16(target, table, relative, value and 0xFFFF)
        }
    }
}
