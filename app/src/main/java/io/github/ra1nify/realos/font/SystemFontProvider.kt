package io.github.ra1nify.realos.font

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Read-only provider consumed by the Settings process when a realOS font is
 * previewed or applied. Import/delete stay in the realOS app.
 */
class SystemFontProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        if (uri.pathSegments.firstOrNull() != SystemFontContract.PATH_FONTS) return null

        val id = uri.pathSegments.getOrNull(1)
        val records = if (id != null) {
            listOfNotNull(SystemFontStore.find(context, id))
        } else {
            SystemFontStore.list(context)
        }

        val columns = projection?.map { it }?.toTypedArray()
            ?: arrayOf(
                SystemFontContract.COLUMN_ID,
                SystemFontContract.COLUMN_DISPLAY_NAME,
                SystemFontContract.COLUMN_SIZE_BYTES,
                SystemFontContract.COLUMN_SHA256
            )
        val cursor = MatrixCursor(columns)
        for (record in records) {
            val row = arrayOfNulls<Any>(columns.size)
            for ((index, column) in columns.withIndex()) {
                row[index] = when (column) {
                    SystemFontContract.COLUMN_ID -> record.id
                    SystemFontContract.COLUMN_DISPLAY_NAME -> record.displayName
                    SystemFontContract.COLUMN_SIZE_BYTES -> record.sizeBytes
                    SystemFontContract.COLUMN_SHA256 -> record.sha256
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    override fun getType(uri: Uri): String {
        val context = context ?: return SystemFontContract.MIME_TYPE
        val id = uri.pathSegments.getOrNull(1) ?: return SystemFontContract.MIME_TYPE
        val record = SystemFontStore.find(context, id) ?: return SystemFontContract.MIME_TYPE
        return when (record.format) {
            SystemFontFormat.OTF -> "font/otf"
            SystemFontFormat.TTF -> "font/ttf"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") return null
        val context = context ?: return null
        if (uri.pathSegments.firstOrNull() != SystemFontContract.PATH_FONTS) return null
        val id = uri.pathSegments.getOrNull(1) ?: return null
        val record = SystemFontStore.find(context, id) ?: return null
        val file: File = SystemFontStore.fontFile(context, record)
        if (!file.isFile) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    // Read-only provider: write operations are ignored.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
