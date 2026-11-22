package io.github.ra1nify.realos.clockfont

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Conservative structural validator for clock fonts.
 *
 * It is intentionally not a full OpenType sanitizer: it verifies the sfnt directory,
 * the required clock glyphs, the variable-font axes and enough of `gvar`/`loca` to
 * reject truncated or obviously malformed files before the host loads them.
 */
object ClockFontValidator {

    private const val SFNT_TRUETYPE = 0x00010000
    private const val SFNT_TRUE = 0x74727565 // 'true'
    private const val SFNT_OTTO = 0x4F54544F // 'OTTO'
    private const val SFNT_TTCF = 0x74746366 // 'ttcf'
    private const val SFNT_WOFF = 0x774F4646 // 'wOFF'
    private const val SFNT_WOFF2 = 0x774F4632 // 'wOF2'

    private const val MAX_AXES = 32
    private const val MAX_NAME_LENGTH = 64

    sealed interface Result {
        data class Success(
            val displayName: String,
            val type: ClockFontType,
            val dynamicWeight: Boolean = true,
            val weightMin: Int,
            val weightDefault: Int,
            val weightMax: Int,
            val heightMin: Int?,
            val heightDefault: Int?,
            val heightMax: Int?,
            val tabularDigits: Boolean,
            val warnings: List<String>
        ) : Result

        data class Failure(val error: ClockFontError, val detail: String? = null) : Result
    }

    private class InvalidFont(
        val error: ClockFontError = ClockFontError.MALFORMED,
        detail: String? = null
    ) : Exception(detail)

    private data class Table(val offset: Int, val length: Int)

    private data class Axis(
        val tag: String,
        val min: Float,
        val default: Float,
        val max: Float
    )

    fun validate(file: File, sourceName: String? = null): Result {
        if (!file.isFile) {
            return Result.Failure(ClockFontError.IO_ERROR, "file not found")
        }
        val size = file.length()
        if (size < ClockFontContract.MIN_FILE_BYTES) {
            return Result.Failure(ClockFontError.TOO_SMALL, "size=$size")
        }
        if (size > ClockFontContract.MAX_FILE_BYTES) {
            return Result.Failure(ClockFontError.TOO_LARGE, "size=$size")
        }

        return try {
            val bytes = file.readBytes()
            val font = FontFile(bytes)
            font.validate(sourceName)
        } catch (e: InvalidFont) {
            Result.Failure(e.error, e.message)
        } catch (t: Throwable) {
            Result.Failure(ClockFontError.MALFORMED, t.message)
        }
    }

    private class FontFile(private val bytes: ByteArray) {
        private val buf: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        private val tables = HashMap<String, Table>()
        private val warnings = mutableListOf<String>()
        private var numGlyphs = 0
        private var numberOfHMetrics = 0
        private var indexToLocFormat = 0
        private lateinit var axes: List<Axis>

        fun validate(sourceName: String?): Result {
            val size = bytes.size
            if (size < 12) throw InvalidFont(detail = "sfnt header truncated")

            val version = buf.intAt(0)
            when (version) {
                SFNT_TTCF -> throw InvalidFont(ClockFontError.UNSUPPORTED_FORMAT, "TTC")
                SFNT_OTTO -> throw InvalidFont(ClockFontError.UNSUPPORTED_FORMAT, "CFF/OTF")
                SFNT_WOFF -> throw InvalidFont(ClockFontError.UNSUPPORTED_FORMAT, "WOFF")
                SFNT_WOFF2 -> throw InvalidFont(ClockFontError.UNSUPPORTED_FORMAT, "WOFF2")
                SFNT_TRUETYPE, SFNT_TRUE -> Unit
                else -> throw InvalidFont(ClockFontError.NOT_TTF, "sfnt=0x${version.toUInt().toString(16)}")
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
                if (offset < 12L || length < 0L || offset + length > size) {
                    throw InvalidFont(detail = "table $tag out of bounds")
                }
                if (tables.put(tag, Table(offset.toInt(), length.toInt())) != null) {
                    throw InvalidFont(detail = "duplicate table $tag")
                }
            }

            val ordered = tables.values.sortedBy { it.offset }
            for (i in 1 until ordered.size) {
                if (ordered[i].offset < ordered[i - 1].offset + ordered[i - 1].length) {
                    throw InvalidFont(detail = "overlapping tables")
                }
            }

            for (tag in listOf("head", "hhea", "maxp", "hmtx", "cmap", "name", "loca", "glyf", "fvar", "gvar")) {
                if (tables[tag] == null) {
                    throw InvalidFont(ClockFontError.NO_VARIATION, "missing $tag")
                }
            }

            val head = table("head")
            if (head.length < 54) throw InvalidFont(detail = "head truncated")
            if (buf.u32(head.offset + 12) != 0x5F0F3CF5L) {
                throw InvalidFont(detail = "head magic mismatch")
            }
            val unitsPerEm = buf.u16(head.offset + 18)
            if (unitsPerEm !in 16..16384) throw InvalidFont(detail = "unitsPerEm=$unitsPerEm")
            indexToLocFormat = buf.i16(head.offset + 50)
            if (indexToLocFormat !in 0..1) throw InvalidFont(detail = "indexToLocFormat")

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
            val ascent = buf.i16(hhea.offset + 4)
            val descent = buf.i16(hhea.offset + 6)
            if (ascent <= 0 || descent >= 0) {
                warnings += "unusual hhea metrics"
            }

            validateHmtx()
            validateLocaAndGlyf()
            val cmap = table("cmap")
            val glyphIds = validateCmap(cmap)
            validateFvar()
            validateGvar()
            validateOs2()
            val tabularDigits = validateHmtxDigits(glyphIds, cmap)

            val wght = axes.firstOrNull { it.tag == "wght" }
                ?: throw InvalidFont(ClockFontError.WEIGHT_RANGE, "missing wght")
            val hght = axes.firstOrNull { it.tag == "HGHT" }

            if (hght != null) {
                val coversHeight = hght.min <= 1f && hght.max >= 1000f
                if (!coversHeight) {
                    throw InvalidFont(
                        ClockFontError.HEIGHT_RANGE,
                        "HGHT ${hght.min}..${hght.max}"
                    )
                }
                if (tables["avar"] != null) {
                    throw InvalidFont(ClockFontError.AVAR_UNSUPPORTED, "avar present")
                }
            }

            if (tables["avar"] != null) {
                warnings += "avar present; fixed weight mapping is remapped by the font"
            }
            if (tables["MVAR"] != null) warnings += "MVAR present"
            if (tables["HVAR"] != null) warnings += "HVAR present"

            val isFlex = hght != null
            val requiredWeightMax = if (isFlex) 1000f else 700f
            if (wght.min > 100f || wght.max < requiredWeightMax) {
                throw InvalidFont(
                    ClockFontError.WEIGHT_RANGE,
                    "wght ${wght.min}..${wght.max}, need <=100..>=$requiredWeightMax"
                )
            }
            if (wght.default !in wght.min..wght.max) {
                throw InvalidFont(ClockFontError.WEIGHT_RANGE, "wght default outside range")
            }
            if (isFlex && hght.default !in hght.min..hght.max) {
                throw InvalidFont(ClockFontError.HEIGHT_RANGE, "HGHT default outside range")
            }

            if (axes.size > 2) warnings += "extra axes=${axes.size}"

            val displayName = readDisplayName()
                ?: sourceName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: "Imported clock font"

            val type = if (isFlex) ClockFontType.FLEX else ClockFontType.FIXED
            return Result.Success(
                displayName = displayName.take(MAX_NAME_LENGTH),
                type = type,
                weightMin = wght.min.toInt(),
                weightDefault = wght.default.toInt(),
                weightMax = wght.max.toInt(),
                heightMin = hght?.min?.toInt(),
                heightDefault = hght?.default?.toInt(),
                heightMax = hght?.max?.toInt(),
                tabularDigits = tabularDigits,
                warnings = warnings.toList()
            )
        }

        private fun validateHmtx() {
            val hmtx = table("hmtx")
            val required = numberOfHMetrics * 4 + (numGlyphs - numberOfHMetrics) * 2
            if (hmtx.length < required) throw InvalidFont(detail = "hmtx truncated")
        }

        private fun validateLocaAndGlyf() {
            val loca = table("loca")
            val glyf = table("glyf")
            val entrySize = if (indexToLocFormat == 0) 2 else 4
            if (loca.length < (numGlyphs + 1) * entrySize) {
                throw InvalidFont(detail = "loca truncated")
            }
            var previous = 0L
            for (i in 0..numGlyphs) {
                val value = if (entrySize == 2) {
                    buf.u16(loca.offset + i * 2).toLong()
                } else {
                    buf.u32(loca.offset + i * 4)
                }
                if (value < previous || value > glyf.length) {
                    throw InvalidFont(detail = "loca offset invalid")
                }
                previous = value
            }
        }

        private fun validateCmap(cmap: Table): Map<Char, Int> {
            if (cmap.length < 4) throw InvalidFont(detail = "cmap truncated")
            val count = buf.u16(cmap.offset + 2)
            if (count !in 1..64) throw InvalidFont(detail = "cmap count")
            var chosen = -1
            for (i in 0 until count) {
                val record = cmap.offset + 4 + i * 8
                if (record + 8 > cmap.offset + cmap.length) break
                val platform = buf.u16(record)
                val encoding = buf.u16(record + 2)
                val relative = buf.u32(record + 4)
                if (relative < 0 || relative + 2 > cmap.length) continue
                val sub = cmap.offset + relative.toInt()
                val format = buf.u16(sub)
                if (format == 4 && (platform == 3 && encoding in 1..10 || platform == 0)) {
                    chosen = sub
                    break
                }
            }
            if (chosen < 0) {
                for (i in 0 until count) {
                    val record = cmap.offset + 4 + i * 8
                    if (record + 8 > cmap.offset + cmap.length) break
                    val relative = buf.u32(record + 4)
                    if (relative < 0 || relative + 2 > cmap.length) continue
                    val sub = cmap.offset + relative.toInt()
                    if (buf.u16(sub) == 12) {
                        chosen = sub
                        break
                    }
                }
            }
            if (chosen < 0) throw InvalidFont(ClockFontError.MISSING_GLYPHS, "no Unicode cmap")

            val result = HashMap<Char, Int>()
            for (ch in "0123456789:") {
                val gid = glyphFor(chosen, ch.code, cmap)
                    ?: throw InvalidFont(ClockFontError.MISSING_GLYPHS, "missing '$ch'")
                result[ch] = gid
            }
            result['.'] = glyphFor(chosen, '.'.code, cmap) ?: -1
            if (result['.'] == -1) warnings += "missing period glyph"
            return result
        }

        private fun glyphFor(subtable: Int, codePoint: Int, cmap: Table): Int? {
            val format = buf.u16(subtable)
            return when (format) {
                4 -> glyphForFormat4(subtable, codePoint, cmap)
                12 -> glyphForFormat12(subtable, codePoint, cmap)
                else -> null
            }
        }

        private fun glyphForFormat4(subtable: Int, codePoint: Int, cmap: Table): Int? {
            val length = buf.u16(subtable + 2)
            if (length < 16 || subtable + length > cmap.offset + cmap.length) return null
            val segCount = buf.u16(subtable + 6) / 2
            if (segCount <= 0) return null
            val endCodes = subtable + 14
            val startCodes = endCodes + segCount * 2 + 2
            val idDeltas = startCodes + segCount * 2
            val idRangeOffsets = idDeltas + segCount * 2
            if (idRangeOffsets + segCount * 2 > subtable + length) return null

            for (i in 0 until segCount) {
                val end = buf.u16(endCodes + i * 2)
                if (codePoint > end) continue
                val start = buf.u16(startCodes + i * 2)
                if (codePoint < start) return null
                val delta = buf.i16(idDeltas + i * 2)
                val rangeOffset = buf.u16(idRangeOffsets + i * 2)
                val gid = if (rangeOffset == 0) {
                    (codePoint + delta) and 0xFFFF
                } else {
                    val glyphOffset = idRangeOffsets + i * 2 + rangeOffset + (codePoint - start) * 2
                    if (glyphOffset + 2 > subtable + length) return null
                    val raw = buf.u16(glyphOffset)
                    if (raw == 0) return null
                    (raw + delta) and 0xFFFF
                }
                return gid.takeIf { it in 1 until numGlyphs }
            }
            return null
        }

        private fun glyphForFormat12(subtable: Int, codePoint: Int, cmap: Table): Int? {
            val length = buf.u32(subtable + 4)
            if (length < 16 || subtable + length > cmap.offset + cmap.length) return null
            val groups = buf.u32(subtable + 12)
            if (groups < 0 || groups > 0x10000) return null
            for (i in 0 until groups.toInt()) {
                val record = subtable + 16 + i * 12
                if (record + 12 > subtable + length) return null
                val start = buf.u32(record)
                val end = buf.u32(record + 4)
                if (codePoint < start || codePoint > end) continue
                val startGlyph = buf.u32(record + 8)
                val gid = startGlyph + (codePoint - start)
                return gid.toInt().takeIf { it in 1 until numGlyphs }
            }
            return null
        }

        private fun validateFvar() {
            val fvar = table("fvar")
            if (fvar.length < 16) throw InvalidFont(detail = "fvar truncated")
            val axesOffset = buf.u16(fvar.offset + 4)
            val axisCount = buf.u16(fvar.offset + 8)
            val axisSize = buf.u16(fvar.offset + 10)
            if (axisCount !in 1..MAX_AXES || axisSize < 20) {
                throw InvalidFont(detail = "fvar axes=$axisCount size=$axisSize")
            }
            val axesEnd = fvar.offset.toLong() + axesOffset + axisCount.toLong() * axisSize
            if (axesOffset < 16 || axesEnd > fvar.offset + fvar.length) {
                throw InvalidFont(detail = "fvar axis array out of bounds")
            }
            val parsed = ArrayList<Axis>(axisCount)
            for (i in 0 until axisCount) {
                val record = fvar.offset + axesOffset + i * axisSize
                val tag = tagAt(record)
                val min = fixedAt(record + 4)
                val default = fixedAt(record + 8)
                val max = fixedAt(record + 12)
                if (!min.isFinite() || !default.isFinite() || !max.isFinite() || min > default || default > max) {
                    throw InvalidFont(detail = "fvar axis $tag invalid")
                }
                if (parsed.any { it.tag == tag }) throw InvalidFont(detail = "duplicate axis $tag")
                parsed += Axis(tag, min, default, max)
            }
            if (parsed.none { it.tag == "wght" }) {
                throw InvalidFont(ClockFontError.WEIGHT_RANGE, "missing wght")
            }
            if (parsed.none { it.min < it.max }) {
                throw InvalidFont(ClockFontError.NO_VARIATION, "no varying axis")
            }
            axes = parsed
        }

        private fun validateGvar() {
            val gvar = table("gvar")
            if (gvar.length < 20) throw InvalidFont(detail = "gvar truncated")
            if (buf.u32(gvar.offset) != 0x00010000L) {
                throw InvalidFont(detail = "gvar version")
            }
            val axisCount = buf.u16(gvar.offset + 4)
            val sharedTupleCount = buf.u16(gvar.offset + 6)
            val sharedTuplesOffset = buf.u32(gvar.offset + 8)
            val glyphCount = buf.u16(gvar.offset + 12)
            val flags = buf.u16(gvar.offset + 14)
            val glyphDataOffset = buf.u32(gvar.offset + 16)
            if (axisCount != axes.size || glyphCount != numGlyphs) {
                throw InvalidFont(detail = "gvar axis/glyph count mismatch")
            }
            val sharedBytes = sharedTupleCount.toLong() * axisCount * 2L
            if (sharedTuplesOffset + sharedBytes > gvar.length) {
                throw InvalidFont(detail = "gvar shared tuples")
            }
            if (glyphDataOffset > gvar.length) {
                throw InvalidFont(detail = "gvar glyph data offset")
            }
            val entrySize = if (flags and 1 != 0) 4 else 2
            val offsetsStart = 20
            val offsetsBytes = (numGlyphs + 1).toLong() * entrySize
            if (offsetsStart + offsetsBytes > gvar.length) {
                throw InvalidFont(detail = "gvar offsets truncated")
            }
            var previous = 0L
            for (i in 0..numGlyphs) {
                val pos = gvar.offset + offsetsStart + i * entrySize
                val value = if (entrySize == 2) buf.u16(pos).toLong() else buf.u32(pos)
                if (value < previous || value > gvar.length - glyphDataOffset) {
                    throw InvalidFont(detail = "gvar glyph offset invalid")
                }
                previous = value
            }
        }

        private fun validateOs2() {
            val os2 = tables["OS/2"] ?: return
            if (os2.length < 78) {
                warnings += "OS/2 truncated"
                return
            }
            val weightClass = buf.u16(os2.offset + 4)
            if (weightClass !in 1..1000) warnings += "OS/2 weightClass=$weightClass"
        }

        private fun validateHmtxDigits(glyphIds: Map<Char, Int>, cmap: Table): Boolean {
            val hmtx = table("hmtx")
            val advances = ArrayList<Int>(10)
            for (ch in "0123456789") {
                val gid = glyphIds[ch] ?: return false
                val advance = if (gid < numberOfHMetrics) {
                    buf.u16(hmtx.offset + gid * 4)
                } else {
                    buf.u16(hmtx.offset + (numberOfHMetrics - 1) * 4)
                }
                advances += advance
            }
            val equal = advances.all { it == advances.first() }
            if (!equal) warnings += "digits are not equal-width by default; relying on tnum"
            return equal
        }

        private fun readDisplayName(): String? {
            val name = tables["name"] ?: return null
            if (name.length < 6) return null
            val count = buf.u16(name.offset + 2)
            val stringOffset = buf.u16(name.offset + 4)
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
                    platform == 0 || platform == 3 && encoding in 0..1 || encoding == 10 -> {
                        val text = String(bytes, Charsets.UTF_16BE)
                        text
                    }
                    else -> String(bytes, Charsets.UTF_8)
                }
            }.getOrNull()
        }

        private fun table(tag: String): Table {
            val table = tables[tag] ?: throw InvalidFont(ClockFontError.NO_VARIATION, "missing $tag")
            return table
        }

        private fun tagAt(offset: Int): String {
            if (offset + 4 > bytes.size) throw InvalidFont(detail = "tag out of bounds")
            return buildString(4) {
                for (i in 0 until 4) append((buf.get(offset + i).toInt() and 0xFF).toChar())
            }
        }

        private fun fixedAt(offset: Int): Float = buf.intAt(offset) / 65536f

        private fun ByteBuffer.intAt(offset: Int): Int {
            if (offset < 0 || offset + 4 > limit()) {
                throw InvalidFont(detail = "read out of bounds")
            }
            return getInt(offset)
        }

        private fun ByteBuffer.u16(offset: Int): Int {
            if (offset < 0 || offset + 2 > limit()) {
                throw InvalidFont(detail = "read out of bounds")
            }
            return getShort(offset).toInt() and 0xFFFF
        }

        private fun ByteBuffer.i16(offset: Int): Int {
            if (offset < 0 || offset + 2 > limit()) {
                throw InvalidFont(detail = "read out of bounds")
            }
            return getShort(offset).toInt()
        }

        private fun ByteBuffer.u32(offset: Int): Long {
            if (offset < 0 || offset + 4 > limit()) {
                throw InvalidFont(detail = "read out of bounds")
            }
            return getInt(offset).toLong() and 0xFFFFFFFFL
        }
    }
}
