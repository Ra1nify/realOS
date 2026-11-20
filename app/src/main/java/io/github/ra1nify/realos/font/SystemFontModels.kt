package io.github.ra1nify.realos.font

import android.net.Uri

/** Format of an imported system font. */
enum class SystemFontFormat(val wireValue: String, val extension: String) {
    TTF("ttf", "ttf"),
    OTF("otf", "otf");

    companion object {
        fun fromWire(value: String?): SystemFontFormat =
            if (value == OTF.wireValue) OTF else TTF
    }
}

data class SystemFontRecord(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val format: SystemFontFormat,
    val addedAt: Long
) {
    val contentUri: Uri get() = SystemFontContract.fontUri(id)
}

sealed interface SystemFontImportResult {
    data class Success(val record: SystemFontRecord) : SystemFontImportResult
    data class Duplicate(val displayName: String) : SystemFontImportResult
    data class Failure(
        val error: SystemFontError,
        val detail: String? = null
    ) : SystemFontImportResult
}

enum class SystemFontError {
    NOT_FONT,
    UNSUPPORTED_FORMAT,
    TOO_SMALL,
    TOO_LARGE,
    MALFORMED,
    MISSING_TABLES,
    IO_ERROR
}
