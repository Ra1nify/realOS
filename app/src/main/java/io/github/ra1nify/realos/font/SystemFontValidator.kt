package io.github.ra1nify.realos.font

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Conservative sfnt validator for system fonts.
 *
 * It is not a full OpenType sanitizer. It verifies the sfnt directory, the tables
 * required to render text, and extracts a display name so malformed files are
 * rejected before they reach the Settings font manager.
 */
object SystemFontValidator {


    private const val SFNT_TRUETYPE = 0x00010000
    private const val SFNT_TRUE = 0x74727565 // 'true'
    private const val SFNT_OTTO = 0x4F54544F // 'OTTO'
    private const val SFNT_TTCF = 0x74746366 // 'ttcf'
    private const val SFNT_WOFF = 0x774F4646 // 'wOFF'
    private const val SFNT_WOFF2 = 0x774F4632 // 'wOF2'

    const val MIN_FILE_BYTES = 1024L
    const val MAX_FILE_BYTES = 64L * 1024L * 1024L
    private const val MAX_NAME_LENGTH = 64

    sealed interface Result {
        data class Success(
            val displayName: String,
            val format: SystemFontFormat,
            val warnings: List<String>
        ) : Result

        data class Failure(
            val error: SystemFontError,
            val detail: String? = null
        ) : Result
    }

    private class InvalidFont(
        val error: SystemFontError = SystemFontError.MALFORMED,
        detail: String? = null
    ) : Exception(detail)

    private data class Table(val offset: Int, val length: Int)

    fun validate(file: File, sourceName: String? = null): Result {
        if (!file.isFile) return Result.Failure(SystemFontError.IO_ERROR, "file not found")
        val size = file.length()
        if (size < MIN_FILE_BYTES) return Result.Failure(SystemFontError.TOO_SMALL, "size=$size")
        if (size > MAX_FILE_BYTES) return Result.Failure(SystemFontError.TOO_LARGE, "size=$size")

        return try {
            val bytes = file.readBytes()
            FontFile(bytes).validate(sourceName)
        } catch (e: InvalidFont) {
            Result.Failure(e.error, e.message)
        } catch (t: Throwable) {
            Result.Failure(SystemFontError.MALFORMED, t.message)
        }
    }

    private class FontFile(private val bytes: ByteArray) {
        private val buf: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        private val tables = HashMap<String, Table>()
        private val warnings = mutableListOf<String>()
        private var numGlyphs = 0
        private var numberOfHMetrics = 0

        fun validate(sourceName: String?): Result {
            val size = bytes.size
            if (size < 12) throw InvalidFont(detail = "sfnt header truncated")

            val version = buf.intAt(0)
            val format = when (version) {
                SFNT_TTCF -> throw InvalidFont(SystemFontError.UNSUPPORTED_FORMAT, "TTC")
                SFNT_WOFF -> throw InvalidFont(SystemFontError.UNSUPPORTED_FORMAT, "WOFF")
                SFNT_WOFF2 -> throw InvalidFont(SystemFontError.UNSUPPORTED_FORMAT, "WOFF2")
                SFNT_OTTO -> SystemFontFormat.OTF
                SFNT_TRUETYPE, SFNT_TRUE -> SystemFontFormat.TTF
                else -> throw InvalidFont(
                    SystemFontError.NOT_FONT,
                    "sfnt=0x${version.toUInt().toString(16)}"
                )
            }

            val numTables = buf.u16(4)
            if (numTables !in 1..64) throw InvalidFont(detail = "numTables=$numTables")
            if (12L + numTables.toLong() * 16L > size) {
                throw InvalidFont(detail = "table directory truncated")
            }

            for (i in 0 until numTables) {
                val record = 12 + i * 16
                val tag = tagAt(record)
                val offset = buf.u32(record + 8)
                val length = buf.u32(record + 12)
                if (offset < 12L || offset + length > size) {
                    throw InvalidFont(detail = "table $tag out of bounds")
                }
                if (tables.put(tag, Table(offset.toInt(), length.toInt())) != null) {
                    throw InvalidFont(detail = "duplicate table $tag")
                }
            }

            for (tag in listOf("head", "maxp", "hhea", "hmtx", "cmap", "name")) {
                if (tables[tag] == null) {
                    throw InvalidFont(SystemFontError.MISSING_TABLES, "missing $tag")
                }
            }
            when (format) {
                SystemFontFormat.TTF -> {
                    if (tables["loca"] == null || tables["glyf"] == null) {
                        throw InvalidFont(SystemFontError.MISSING_TABLES, "missing loca/glyf")
                    }
                }
                SystemFontFormat.OTF -> {
                    if (tables["CFF "] == null && tables["CFF2"] == null) {
                        throw InvalidFont(SystemFontError.MISSING_TABLES, "missing CFF/CFF2")
                    }
                }
            }

            val head = table("head")
            if (head.length < 54) throw InvalidFont(detail = "head truncated")
            if (buf.u32(head.offset + 12) != 0x5F0F3CF5L) {
                throw InvalidFont(detail = "head magic mismatch")
            }
            val unitsPerEm = buf.u16(head.offset + 18)
            if (unitsPerEm !in 16..16384) throw InvalidFont(detail = "unitsPerEm=$unitsPerEm")

            val maxp = table("maxp")
            if (maxp.length < 6) throw InvalidFont(detail = "maxp truncated")
            numGlyphs = buf.u16(maxp.offset + 4)
            if (numGlyphs !in 1..0xFFFF) throw InvalidFont(detail = "numGlyphs=$numGlyphs")

            val hhea = table("hhea")
            if (hhea.length < 36) throw InvalidFont(detail = "hhea truncated")
            numberOfHMetrics = buf.u16(hhea.offset + 34)
            if (numberOfHMetrics !in 1..numGlyphs) {
                throw InvalidFont(detail = "numberOfHMetrics=$numberOfHMetrics")
            }

            val hmtx = table("hmtx")
            val requiredHmtx = numberOfHMetrics * 4 + (numGlyphs - numberOfHMetrics) * 2
            if (hmtx.length < requiredHmtx) throw InvalidFont(detail = "hmtx truncated")

            if (!hasUnicodeCmap(table("cmap"))) {
                throw InvalidFont(SystemFontError.MALFORMED, "no Unicode cmap")
            }

            if (tables["OS/2"] == null) warnings += "missing OS/2"
            if (format == SystemFontFormat.OTF) warnings += "CFF outline"

            val displayName = readDisplayName()
                ?: sourceName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: "Imported font"

            return Result.Success(
                displayName = displayName.take(MAX_NAME_LENGTH),
                format = format,
                warnings = warnings.toList()
            )
        }

        private fun hasUnicodeCmap(cmap: Table): Boolean {
            if (cmap.length < 4) return false
            val count = buf.u16(cmap.offset + 2)
            if (count !in 1..64) return false
            for (i in 0 until count) {
                val record = cmap.offset + 4 + i * 8
                if (record + 8 > cmap.offset + cmap.length) break
                val platform = buf.u16(record)
                val encoding = buf.u16(record + 2)
                val relative = buf.u32(record + 4)
                if (relative < 0 || relative + 2 > cmap.length) continue
                if (platform == 0 || platform == 3 && encoding in 0..10) {
                    val sub = cmap.offset + relative.toInt()
                    val format = buf.u16(sub)
                    if (format == 4 || format == 12 || format == 13 || format == 6) {
                        return true
                    }
                }
            }
            return false
        }

        private fun readDisplayName(): String? {
            val name = tables["name"] ?: return null
            if (name.length < 6) return null
            val count = buf.u16(name.offset + 2)
            val stringOffset = buf.u16(name.offset + 4)
            if (count !in 1..1024) return null
            var best: String? = null
            for (i in 0 until count) {
                val record = name.offset + 6 + i * 12
                if (record + 12 > name.offset + name.length) break
                val platform = buf.u16(record)
                val encoding = buf.u16(record + 2)
                val nameId = buf.u16(record + 6)
                val length = buf.u16(record + 8)
                val offset = buf.u16(record + 10)
                if (nameId != 1 && nameId != 4) continue
                val start = name.offset + stringOffset + offset
                if (start < 0 || start + length > name.offset + name.length) continue
                val value = decodeName(start, length, platform, encoding) ?: continue
                val cleaned = value.filter { it.code in 0x20..0x7E || it.code in 0xA0..0xFFEF }
                    .trim()
                    .take(MAX_NAME_LENGTH)
                if (cleaned.isNotBlank()) {
                    if (nameId == 1) return cleaned
                    if (best == null) best = cleaned
                }
            }
            return best
        }

        private fun decodeName(offset: Int, length: Int, platform: Int, encoding: Int): String? {
            if (length <= 0) return null
            return runCatching {
                val bytes = ByteArray(length) { buf.get(offset + it) }
                when {
                    platform == 0 || platform == 3 && encoding in 0..1 || encoding == 10 ->
                        String(bytes, Charsets.UTF_16BE)
                    else -> String(bytes, Charsets.UTF_8)
                }
            }.getOrNull()
        }

        private fun table(tag: String): Table =
            tables[tag] ?: throw InvalidFont(SystemFontError.MISSING_TABLES, "missing $tag")

        private fun tagAt(offset: Int): String {
            if (offset + 4 > bytes.size) throw InvalidFont(detail = "tag out of bounds")
            return buildString(4) {
                for (i in 0 until 4) append((buf.get(offset + i).toInt() and 0xFF).toChar())
            }
        }

        private fun ByteBuffer.intAt(offset: Int): Int {
            if (offset < 0 || offset + 4 > limit()) throw InvalidFont(detail = "read out of bounds")
            return getInt(offset)
        }

        private fun ByteBuffer.u16(offset: Int): Int {
            if (offset < 0 || offset + 2 > limit()) throw InvalidFont(detail = "read out of bounds")
            return getShort(offset).toInt() and 0xFFFF
        }

        private fun ByteBuffer.u32(offset: Int): Long {
            if (offset < 0 || offset + 4 > limit()) throw InvalidFont(detail = "read out of bounds")
            return getInt(offset).toLong() and 0xFFFFFFFFL
        }
    }
}
