package io.github.ra1nify.realos.clockfont

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared sfnt table directory reader. */
internal class SfntTables private constructor(
    val bytes: ByteArray,
    val tables: Map<String, Table>
) {

    data class Table(val offset: Int, val length: Int)

    class FormatException(message: String) : Exception(message)

    private val buf: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

    fun table(tag: String): Table? = tables[tag]

    fun u16(offset: Int): Int {
        require(offset >= 0 && offset + 2 <= bytes.size)
        return buf.getShort(offset).toInt() and 0xFFFF
    }

    fun i16(offset: Int): Int {
        require(offset >= 0 && offset + 2 <= bytes.size)
        return buf.getShort(offset).toInt()
    }

    fun u32(offset: Int): Long {
        require(offset >= 0 && offset + 4 <= bytes.size)
        return buf.getInt(offset).toLong() and 0xFFFFFFFFL
    }

    companion object {

        fun versionOf(bytes: ByteArray): Long {
            require(bytes.size >= 4) { "sfnt truncated" }
            return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt(0).toLong() and
                0xFFFFFFFFL
        }

        fun parse(bytes: ByteArray): SfntTables {
            if (bytes.size < 12) throw FormatException("sfnt truncated")
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val numTables = buf.getShort(4).toInt() and 0xFFFF
            if (numTables !in 1..64) throw FormatException("numTables=$numTables")
            if (12L + numTables.toLong() * 16L > bytes.size) {
                throw FormatException("table directory truncated")
            }

            val tables = HashMap<String, Table>(numTables)
            for (i in 0 until numTables) {
                val record = 12 + i * 16
                val tag = tagAt(bytes, record)
                val offset = buf.getInt(record + 8).toLong() and 0xFFFFFFFFL
                val length = buf.getInt(record + 12).toLong() and 0xFFFFFFFFL
                if (offset < 12L || length < 0L || offset + length > bytes.size) {
                    throw FormatException("table $tag out of bounds")
                }
                if (tables.put(tag, Table(offset.toInt(), length.toInt())) != null) {
                    throw FormatException("duplicate table $tag")
                }
            }

            val ordered = tables.values.sortedBy { it.offset }
            for (i in 1 until ordered.size) {
                if (ordered[i].offset < ordered[i - 1].offset + ordered[i - 1].length) {
                    throw FormatException("overlapping tables")
                }
            }
            return SfntTables(bytes, tables)
        }

        private fun tagAt(bytes: ByteArray, offset: Int): String {
            require(offset + 4 <= bytes.size)
            return buildString(4) {
                for (i in 0 until 4) append((bytes[offset + i].toInt() and 0xFF).toChar())
            }
        }
    }
}
