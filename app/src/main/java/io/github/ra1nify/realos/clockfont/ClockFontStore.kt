package io.github.ra1nify.realos.clockfont

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.ra1nify.realos.util.RealOSLog as Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * App-private storage for imported clock fonts plus the registry consumed by
 * [ClockFontProvider]. Keep this class free of Compose/UI references.
 */
object ClockFontStore {

    private const val TAG = "ClockFontStore"
    private const val DIR_NAME = "clock-fonts"
    private const val REGISTRY_NAME = "registry.json"

    private val lock = Any()

    fun fontsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun registryFile(context: Context): File = File(fontsDir(context), REGISTRY_NAME)

    fun list(context: Context, includeHidden: Boolean = false): List<ClockFontRecord> {
        synchronized(lock) {
            val records = readRegistry(context)
            return if (includeHidden) records else records.filterNot { it.hidden }
        }
    }

    fun find(context: Context, id: String, includeHidden: Boolean = true): ClockFontRecord? {
        synchronized(lock) {
            return readRegistry(context).firstOrNull {
                it.id == id && (includeHidden || !it.hidden)
            }
        }
    }

    fun findByValue(context: Context, value: String?): ClockFontRecord? {
        val id = ClockFontContract.idFromValue(value) ?: return null
        return find(context, id, includeHidden = true)
    }

    fun fontFile(context: Context, record: ClockFontRecord): File =
        File(fontsDir(context), record.fileName)

    fun importFromUri(context: Context, uri: Uri): ClockFontImportResult {
        val dir = fontsDir(context)
        val temp = File(dir, "import-${UUID.randomUUID()}.ttf")
        return try {
            val copied = copyToTemp(context, uri, temp)
            if (!copied) {
                return ClockFontImportResult.Failure(ClockFontError.IO_ERROR, "copy failed")
            }
            val size = temp.length()
            if (size < ClockFontContract.MIN_FILE_BYTES) {
                return ClockFontImportResult.Failure(ClockFontError.TOO_SMALL, "size=$size")
            }
            if (size > ClockFontContract.MAX_FILE_BYTES) {
                return ClockFontImportResult.Failure(ClockFontError.TOO_LARGE, "size=$size")
            }

            val sha = sha256(temp)
            synchronized(lock) {
                val existing = readRegistry(context)
                val duplicate = existing.firstOrNull { it.sha256 == sha }
                if (duplicate != null) {
                    return ClockFontImportResult.Duplicate(duplicate.displayName)
                }

                val validation = ClockFontValidator.validate(temp, sourceName(context, uri))
                if (validation is ClockFontValidator.Result.Failure) {
                    return ClockFontImportResult.Failure(validation.error, validation.detail)
                }
                val success = validation as ClockFontValidator.Result.Success

                val id = "cf_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val fileName = "$id.ttf"
                val finalFile = File(dir, fileName)
                if (!temp.renameTo(finalFile)) {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }

                val record = ClockFontRecord(
                    id = id,
                    displayName = success.displayName,
                    fileName = fileName,
                    sha256 = sha,
                    type = success.type,
                    // User policy, not a font property: default on, toggled in the GUI.
                    dynamicWeight = true,
                    sizeBytes = size,
                    hidden = false,
                    addedAt = System.currentTimeMillis()
                )
                val updated = existing + record
                writeRegistry(context, updated)
                Log.i(TAG, "imported ${record.displayName} (${record.id}) type=${record.type}")
                ClockFontImportResult.Success(record)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "import failed", t)
            ClockFontImportResult.Failure(ClockFontError.IO_ERROR, t.message)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun setHidden(context: Context, id: String, hidden: Boolean): Boolean {
        synchronized(lock) {
            val records = readRegistry(context)
            val updated = records.map { if (it.id == id) it.copy(hidden = hidden) else it }
            if (updated == records) return false
            writeRegistry(context, updated)
            return true
        }
    }

    /** Per-font user policy: whether the host may show its weight slider. */
    fun setDynamicWeight(context: Context, id: String, dynamicWeight: Boolean): Boolean {
        synchronized(lock) {
            val records = readRegistry(context)
            val updated = records.map {
                if (it.id == id) it.copy(dynamicWeight = dynamicWeight) else it
            }
            if (updated == records) return false
            writeRegistry(context, updated)
            return true
        }
    }

    /**
     * Removes the font file and registry entry; selected clocks fall back to the default.
     */
    fun delete(context: Context, id: String): Boolean {
        synchronized(lock) {
            val records = readRegistry(context)
            val record = records.firstOrNull { it.id == id } ?: return false
            val file = fontFile(context, record)
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "failed to delete font file ${record.fileName}")
            }
            val updated = records.filterNot { it.id == id }
            writeRegistry(context, updated)
            return true
        }
    }

    private fun readRegistry(context: Context): List<ClockFontRecord> {
        val file = registryFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val fonts = root.optJSONArray("fonts") ?: JSONArray()
            buildList {
                for (i in 0 until fonts.length()) {
                    val item = fonts.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val fileName = item.optString("fileName").takeIf { it.isNotBlank() } ?: continue
                    val type = ClockFontType.fromWire(item.optString("type"))
                    add(
                        ClockFontRecord(
                            id = id,
                            displayName = item.optString("displayName", id),
                            fileName = fileName,
                            sha256 = item.optString("sha256"),
                            type = type,
                            dynamicWeight = item.optBoolean(
                                "dynamicWeight",
                                item.optBoolean("dynamic_weight", true)
                            ),
                            sizeBytes = item.optLong("sizeBytes"),
                            hidden = item.optBoolean("hidden", false),
                            addedAt = item.optLong("addedAt")
                        )
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "failed to read registry", it)
            emptyList()
        }
    }

    private fun writeRegistry(context: Context, records: List<ClockFontRecord>) {
        val dir = fontsDir(context)
        val target = registryFile(context)
        val temp = File(dir, "$REGISTRY_NAME.tmp")
        val root = JSONObject().apply {
            put("version", ClockFontContract.REGISTRY_VERSION)
            put("fonts", JSONArray().apply {
                records.forEach { record ->
                    put(JSONObject().apply {
                        put("id", record.id)
                        put("displayName", record.displayName)
                        put("fileName", record.fileName)
                        put("sha256", record.sha256)
                        put("type", record.type.wireValue)
                        put("dynamicWeight", record.dynamicWeight)
                        put("sizeBytes", record.sizeBytes)
                        put("hidden", record.hidden)
                        put("addedAt", record.addedAt)
                    })
                }
            })
        }
        temp.writeText(root.toString(), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        runCatching {
            context.contentResolver.notifyChange(ClockFontContract.FONTS_URI, null)
        }
    }

    private fun copyToTemp(context: Context, uri: Uri, target: File): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return false
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            true
        }.getOrElse {
            Log.e(TAG, "failed to copy $uri", it)
            false
        }
    }

    private fun sourceName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
