package io.github.ra1nify.realos.clockfont

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared Unicode cmap (format 4/12) lookup. */
internal object SfntCmap {

    /** Returns the Unicode subtable offset, preferring format 4. */
    fun findUnicodeSubtable(bytes: ByteArray, cmapOffset: Int, cmapLength: Int): Int? =
        runCatching {
            require(cmapOffset >= 0 && cmapLength >= 4 && cmapOffset + cmapLength <= bytes.size)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val cmapEnd = cmapOffset + cmapLength
            val count = u16(buf, cmapOffset + 2)
            require(count in 1..64)

            var format12 = -1
            for (i in 0 until count) {
                val record = cmapOffset + 4 + i * 8
                if (record + 8 > cmapEnd) break
                val platform = u16(buf, record)
                val encoding = u16(buf, record + 2)
                val relative = u32(buf, record + 4)
                if (relative < 0 || relative + 2 > cmapLength) continue
                val sub = cmapOffset + relative.toInt()
                if (sub + 2 > cmapEnd) continue
                when (u16(buf, sub)) {
                    4 -> if ((platform == 3 && encoding in 1..10) || platform == 0) {
                        return@runCatching sub
                    }
                    12 -> if (format12 < 0) format12 = sub
                }
            }
            format12.takeIf { it >= 0 }
        }.getOrNull()

    /** Returns the glyph id for [codePoint], or null when absent. */
    fun glyphFor(
        bytes: ByteArray,
        subtable: Int,
        cmapEnd: Int,
        codePoint: Int,
        numGlyphs: Int
    ): Int? = runCatching {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        when (u16(buf, subtable)) {
            4 -> glyphForFormat4(buf, subtable, cmapEnd, codePoint, numGlyphs)
            12 -> glyphForFormat12(buf, subtable, cmapEnd, codePoint, numGlyphs)
            else -> null
        }
    }.getOrNull()

    /** Returns the glyph id for [codePoint] using the Unicode subtable. */
    fun glyphForChar(
        bytes: ByteArray,
        cmapOffset: Int,
        cmapLength: Int,
        codePoint: Int,
        numGlyphs: Int
    ): Int? {
        val subtable = findUnicodeSubtable(bytes, cmapOffset, cmapLength) ?: return null
        return glyphFor(bytes, subtable, cmapOffset + cmapLength, codePoint, numGlyphs)
    }

    private fun glyphForFormat4(
        buf: ByteBuffer,
        subtable: Int,
        cmapEnd: Int,
        codePoint: Int,
        numGlyphs: Int
    ): Int? {
        val length = u16(buf, subtable + 2)
        if (length < 16) return null
        val subtableEnd = subtable + length
        if (subtableEnd > cmapEnd) return null
        val segCount = u16(buf, subtable + 6) / 2
        if (segCount <= 0) return null
        val endCodes = subtable + 14
        val startCodes = endCodes + segCount * 2 + 2
        val idDeltas = startCodes + segCount * 2
        val idRangeOffsets = idDeltas + segCount * 2
        if (idRangeOffsets + segCount * 2 > subtableEnd) return null

        for (i in 0 until segCount) {
            val end = u16(buf, endCodes + i * 2)
            if (codePoint > end) continue
            val start = u16(buf, startCodes + i * 2)
            if (codePoint < start) return null
            val delta = i16(buf, idDeltas + i * 2)
            val rangeOffset = u16(buf, idRangeOffsets + i * 2)
            val gid = if (rangeOffset == 0) {
                (codePoint + delta) and 0xFFFF
            } else {
                val glyphOffset =
                    idRangeOffsets + i * 2 + rangeOffset + (codePoint - start) * 2
                if (glyphOffset + 2 > subtableEnd) return null
                val raw = u16(buf, glyphOffset)
                if (raw == 0) return null
                (raw + delta) and 0xFFFF
            }
            return gid.takeIf { it in 1 until numGlyphs }
        }
        return null
    }

    private fun glyphForFormat12(
        buf: ByteBuffer,
        subtable: Int,
        cmapEnd: Int,
        codePoint: Int,
        numGlyphs: Int
    ): Int? {
        val length = u32(buf, subtable + 4)
        if (length < 16) return null
        val subtableEnd = subtable + length
        if (subtableEnd > cmapEnd) return null
        val groups = u32(buf, subtable + 12)
        if (groups < 0 || groups > 0x10000) return null
        for (i in 0 until groups.toInt()) {
            val record = subtable + 16 + i * 12
            if (record + 12 > subtableEnd) return null
            val start = u32(buf, record)
            val end = u32(buf, record + 4)
            if (codePoint < start || codePoint > end) continue
            val startGlyph = u32(buf, record + 8)
            val gid = startGlyph + (codePoint - start)
            return gid.toInt().takeIf { it in 1 until numGlyphs }
        }
        return null
    }

    private fun u16(buf: ByteBuffer, offset: Int): Int {
        require(offset >= 0 && offset + 2 <= buf.limit())
        return buf.getShort(offset).toInt() and 0xFFFF
    }

    private fun i16(buf: ByteBuffer, offset: Int): Int {
        require(offset >= 0 && offset + 2 <= buf.limit())
        return buf.getShort(offset).toInt()
    }

    private fun u32(buf: ByteBuffer, offset: Int): Long {
        require(offset >= 0 && offset + 4 <= buf.limit())
        return buf.getInt(offset).toLong() and 0xFFFFFFFFL
    }
}
