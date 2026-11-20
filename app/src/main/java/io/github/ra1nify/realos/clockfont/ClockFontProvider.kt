package io.github.ra1nify.realos.clockfont

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Read-only font provider consumed by the clock hooks in Wallpapers/SystemUI/Launcher/AOD.
 *
 * Write operations are intentionally unsupported. Import/delete stays in the realOS app;
 * hosts only read metadata and the TTF file.
 */
class ClockFontProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        if (uri.pathSegments.firstOrNull() != ClockFontContract.PATH_FONTS) return null

        val id = uri.pathSegments.getOrNull(1)
        val records = if (id != null) {
            listOfNotNull(ClockFontStore.find(context, id, includeHidden = true))
        } else {
            ClockFontStore.list(context, includeHidden = false)
        }

        val columns = projection?.map { it }?.toTypedArray()
            ?: arrayOf(
                ClockFontContract.COLUMN_ID,
                ClockFontContract.COLUMN_DISPLAY_NAME,
                ClockFontContract.COLUMN_TYPE,
                ClockFontContract.COLUMN_SIZE_BYTES,
                ClockFontContract.COLUMN_SHA256
            )
        val cursor = MatrixCursor(columns)
        for (record in records) {
            val row = arrayOfNulls<Any>(columns.size)
            for ((index, column) in columns.withIndex()) {
                row[index] = when (column) {
                    ClockFontContract.COLUMN_ID -> record.id
                    ClockFontContract.COLUMN_DISPLAY_NAME -> record.displayName
                    ClockFontContract.COLUMN_TYPE -> record.type.wireValue
                    ClockFontContract.COLUMN_SIZE_BYTES -> record.sizeBytes
                    ClockFontContract.COLUMN_SHA256 -> record.sha256
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    override fun getType(uri: Uri): String = ClockFontContract.MIME_TYPE

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") return null
        val context = context ?: return null
        if (uri.pathSegments.firstOrNull() != ClockFontContract.PATH_FONTS) return null
        val id = uri.pathSegments.getOrNull(1) ?: return null
        val record = ClockFontStore.find(context, id, includeHidden = true) ?: return null
        val file: File = ClockFontStore.fontFile(context, record)
        if (!file.isFile) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("clock fonts are read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("clock fonts are read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("clock fonts are read-only")
}
