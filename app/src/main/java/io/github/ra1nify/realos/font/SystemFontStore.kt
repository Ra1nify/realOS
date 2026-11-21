package io.github.ra1nify.realos.font

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.ra1nify.realos.util.RealOSLog as Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * App-private storage for imported system fonts plus the registry consumed by
 * [SystemFontProvider]. Keep this class free of Compose/UI references.
 */
object SystemFontStore {

    private const val TAG = "SystemFontStore"
    private const val DIR_NAME = "system-fonts"
    private const val REGISTRY_NAME = "registry.json"

    private val lock = Any()

    fun fontsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun registryFile(context: Context): File = File(fontsDir(context), REGISTRY_NAME)

    fun list(context: Context): List<SystemFontRecord> = synchronized(lock) {
        readRegistry(context)
    }

    fun find(context: Context, id: String): SystemFontRecord? = synchronized(lock) {
        readRegistry(context).firstOrNull { it.id == id }
    }

    fun fontFile(context: Context, record: SystemFontRecord): File =
        File(fontsDir(context), record.fileName)

    fun importFromUri(context: Context, uri: Uri): SystemFontImportResult {
        val dir = fontsDir(context)
        val temp = File(dir, "import-${UUID.randomUUID()}.tmp")
        return try {
            val copied = copyToTemp(context, uri, temp)
            if (!copied) {
                return SystemFontImportResult.Failure(SystemFontError.IO_ERROR, "copy failed")
            }
            val size = temp.length()
            val validation = SystemFontValidator.validate(temp, sourceName(context, uri))
            if (validation is SystemFontValidator.Result.Failure) {
                return SystemFontImportResult.Failure(validation.error, validation.detail)
            }
            val success = validation as SystemFontValidator.Result.Success

            val sha = sha256(temp)
            synchronized(lock) {
                val existing = readRegistry(context)
                val duplicate = existing.firstOrNull { it.sha256 == sha }
                if (duplicate != null) {
                    return SystemFontImportResult.Duplicate(duplicate.displayName)
                }

                val id = "sf_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val fileName = "$id.${success.format.extension}"
                val finalFile = File(dir, fileName)
                if (!temp.renameTo(finalFile)) {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }

                val record = SystemFontRecord(
                    id = id,
                    displayName = success.displayName,
                    fileName = fileName,
                    sha256 = sha,
                    sizeBytes = size,
                    format = success.format,
                    addedAt = System.currentTimeMillis()
                )
                writeRegistry(context, existing + record)
                Log.i(TAG, "imported ${record.displayName} (${record.id}) format=${record.format}")
                SystemFontImportResult.Success(record)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "import failed", t)
            SystemFontImportResult.Failure(SystemFontError.IO_ERROR, t.message)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun delete(context: Context, id: String): Boolean = synchronized(lock) {
        val records = readRegistry(context)
        val record = records.firstOrNull { it.id == id } ?: return false
        val file = fontFile(context, record)
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "failed to delete font file ${record.fileName}")
        }
        writeRegistry(context, records.filterNot { it.id == id })
        true
    }

    private fun copyToTemp(context: Context, uri: Uri, temp: File): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
    }.getOrDefault(false)

    private fun sourceName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readRegistry(context: Context): List<SystemFontRecord> {
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
                    add(
                        SystemFontRecord(
                            id = id,
                            displayName = item.optString("displayName", id),
                            fileName = fileName,
                            sha256 = item.optString("sha256"),
                            sizeBytes = item.optLong("sizeBytes"),
                            format = SystemFontFormat.fromWire(item.optString("format")),
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

    private fun writeRegistry(context: Context, records: List<SystemFontRecord>) {
        val dir = fontsDir(context)
        val target = registryFile(context)
        val temp = File(dir, "$REGISTRY_NAME.tmp")
        val root = JSONObject().apply {
            put("version", 1)
            put("fonts", JSONArray().apply {
                records.forEach { record ->
                    put(JSONObject().apply {
                        put("id", record.id)
                        put("displayName", record.displayName)
                        put("fileName", record.fileName)
                        put("sha256", record.sha256)
                        put("sizeBytes", record.sizeBytes)
                        put("format", record.format.wireValue)
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
    }
}
