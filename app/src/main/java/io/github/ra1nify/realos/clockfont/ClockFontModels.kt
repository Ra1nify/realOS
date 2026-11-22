package io.github.ra1nify.realos.clockfont

import android.net.Uri

enum class ClockFontType(val wireValue: String) {
    FIXED(ClockFontContract.TYPE_FIXED),
    FLEX(ClockFontContract.TYPE_FLEX);

    companion object {
        fun fromWire(value: String?): ClockFontType =
            if (value == ClockFontContract.TYPE_FLEX) FLEX else FIXED
    }
}

data class ClockFontRecord(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sha256: String,
    val type: ClockFontType,
    val dynamicWeight: Boolean = true,
    val sizeBytes: Long,
    val hidden: Boolean,
    val addedAt: Long
) {
    val value: String get() = ClockFontContract.valueFor(id)
    val contentUri: Uri get() = ClockFontContract.fontUri(id)
}

sealed interface ClockFontImportResult {
    data class Success(val record: ClockFontRecord) : ClockFontImportResult
    data class Duplicate(val displayName: String) : ClockFontImportResult
    data class Failure(val error: ClockFontError, val detail: String? = null) : ClockFontImportResult
}

enum class ClockFontError {
    NOT_TTF,
    TOO_SMALL,
    TOO_LARGE,
    MALFORMED,
    UNSUPPORTED_FORMAT,
    MISSING_GLYPHS,
    NO_VARIATION,
    WEIGHT_RANGE,
    HEIGHT_RANGE,
    AVAR_UNSUPPORTED,
    IO_ERROR,
    DUPLICATE
}
