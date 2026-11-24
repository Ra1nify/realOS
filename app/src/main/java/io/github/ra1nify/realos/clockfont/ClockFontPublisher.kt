package io.github.ra1nify.realos.clockfont

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.util.RealOSLog as Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Publishes clock fonts into LSPosed remote data. */
object ClockFontPublisher {

    private const val TAG = "ClockFontPublisher"
    private const val MARKER_NAME = "clockfont-remote.json"
    private const val SETTINGS_PREFS = "clockfont_settings"
    private const val SETTINGS_STATUS_BAR_CLOCK = "status_bar_clock_enabled"

    private val started = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val dirty = AtomicBoolean(false)
    private val publishLock = Any()

    fun start(context: Context) {
        val app = context.applicationContext ?: context
        if (!started.compareAndSet(false, true)) return
        XposedServiceManager.addOnServiceBoundListener { requestPublish(app) }
        requestPublish(app)
    }

    /** Status bar clock switch. */
    fun isStatusBarClockEnabled(context: Context): Boolean =
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(SETTINGS_STATUS_BAR_CLOCK, false)

    fun setStatusBarClockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SETTINGS_STATUS_BAR_CLOCK, enabled)
            .apply()
        requestPublish(context)
    }

    /** Coalesces republish requests. */
    fun requestPublish(context: Context?) {
        if (!BuildConfig.SCOPE_CLOCKFONT) return
        val app = context?.applicationContext ?: return
        dirty.set(true)
        if (!running.compareAndSet(false, true)) return

        Thread({
            try {
                while (dirty.getAndSet(false)) {
                    runCatching { publishOnce(app) }
                        .onFailure { Log.w(TAG, "remote publish failed", it) }
                }
            } finally {
                running.set(false)
                if (dirty.get()) requestPublish(app)
            }
        }, "RealOS-ClockFontPublish").apply { isDaemon = true }.start()
    }

    private fun publishOnce(context: Context): Boolean {
        // Probe the API; unsupported frameworks fail closed.
        val prefs = XposedServiceManager.getRemotePreferences(ClockFontContract.REMOTE_PREFS)
            ?: return false

        synchronized(publishLock) {
            val records = ClockFontStore.list(context, includeHidden = true)
            val statusBarClockEnabled = isStatusBarClockEnabled(context)
            val marker = readMarker(context)
            val remoteNames = XposedServiceManager.listRemoteFiles()
            val published = linkedMapOf<String, String>()
            val entries = mutableListOf<ClockFontRecord>()
            var writeFailures = 0

            for (record in records) {
                val fileName = ClockFontContract.remoteFileName(record.id)
                // Verify size when the remote file list is unavailable.
                val knownRemote = remoteNames?.contains(fileName) == true ||
                    (remoteNames == null && remoteFileMatches(fileName, record.sizeBytes))
                if (marker.files[record.id] == record.sha256 && knownRemote) {
                    entries += record
                    published[record.id] = record.sha256
                    continue
                }
                val source = ClockFontStore.fontFile(context, record)
                if (!source.isFile) {
                    Log.w(TAG, "font file missing for ${record.id}; skipped")
                    writeFailures++
                    continue
                }
                if (writeRemoteFile(fileName, source)) {
                    entries += record
                    published[record.id] = record.sha256
                } else {
                    writeFailures++
                }
            }

            val entriesJson = JSONArray().apply {
                entries.forEach { record ->
                    put(JSONObject().apply {
                        put("id", record.id)
                        put("displayName", record.displayName)
                        put("type", record.type.wireValue)
                        put("dynamicWeight", record.dynamicWeight)
                        put("hidden", record.hidden)
                        put("sizeBytes", record.sizeBytes)
                        put("sha256", record.sha256)
                    })
                }
            }.toString()

            // Keep files for existing records.
            val keep = records.map { ClockFontContract.remoteFileName(it.id) }.toSet()
            val stale = remoteNames
                ?.filter { it.startsWith(ClockFontContract.REMOTE_FILE_PREFIX) && it !in keep }
                .orEmpty()

            val unchanged = writeFailures == 0 &&
                stale.isEmpty() &&
                marker.revision > 0 &&
                prefs.getLong(ClockFontContract.REMOTE_KEY_REVISION, 0) == marker.revision &&
                prefs.getString(ClockFontContract.REMOTE_KEY_ENTRIES, null) == entriesJson &&
                prefs.getBoolean(ClockFontContract.REMOTE_KEY_STATUS_BAR_CLOCK, false) == statusBarClockEnabled
            if (unchanged) {
                Log.d(TAG, "remote clock fonts unchanged (${entries.size})")
                return true
            }

            val revision = marker.revision + 1
            val committed = runCatching {
                prefs.edit()
                    .putInt(ClockFontContract.REMOTE_KEY_VERSION, ClockFontContract.REMOTE_CONFIG_VERSION)
                    .putLong(ClockFontContract.REMOTE_KEY_REVISION, revision)
                    .putString(ClockFontContract.REMOTE_KEY_ENTRIES, entriesJson)
                    .putBoolean(
                        ClockFontContract.REMOTE_KEY_STATUS_BAR_CLOCK,
                        statusBarClockEnabled
                    )
                    .commit()
            }.onFailure {
                Log.w(TAG, "failed to commit remote metadata", it)
            }.getOrDefault(false)

            if (!committed) {
                Log.w(TAG, "remote metadata commit rejected")
                return false
            }

            writeMarker(context, revision, published)
            stale.forEach { name ->
                if (!XposedServiceManager.deleteRemoteFile(name)) {
                    Log.w(TAG, "failed to delete stale remote file $name")
                }
            }
            if (writeFailures > 0) {
                Log.w(TAG, "published ${entries.size}/${records.size} clock fonts; $writeFailures failed")
            } else {
                Log.i(TAG, "published ${entries.size} clock font(s), revision=$revision")
            }
            return true
        }
    }

    private fun remoteFileMatches(name: String, expectedSize: Long): Boolean {
        if (expectedSize <= 0L) return false
        val pfd = XposedServiceManager.openRemoteFile(name) ?: return false
        val matches = runCatching { pfd.statSize == expectedSize }.getOrDefault(false)
        runCatching { pfd.close() }
        return matches
    }

    private fun writeRemoteFile(name: String, source: File): Boolean {
        val pfd: ParcelFileDescriptor = XposedServiceManager.openRemoteFile(name) ?: return false
        val output = try {
            ParcelFileDescriptor.AutoCloseOutputStream(pfd)
        } catch (t: Throwable) {
            runCatching { pfd.close() }
            Log.w(TAG, "failed to wrap remote file $name", t)
            return false
        }
        return runCatching {
            output.use { out ->
                out.channel.truncate(0)
                source.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            }
            true
        }.onFailure {
            Log.w(TAG, "failed to write remote file $name", it)
        }.getOrDefault(false)
    }

    private class Marker(
        val revision: Long,
        val files: Map<String, String>
    )

    private fun readMarker(context: Context): Marker {
        val file = File(context.filesDir, MARKER_NAME)
        if (!file.isFile) return Marker(0, emptyMap())
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val filesJson = root.optJSONObject("files") ?: JSONObject()
            val files = buildMap {
                val keys = filesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val sha = filesJson.optString(key)
                    if (sha.isNotBlank()) put(key, sha)
                }
            }
            Marker(root.optLong("revision", 0), files)
        }.getOrElse {
            Log.w(TAG, "failed to read publish marker", it)
            Marker(0, emptyMap())
        }
    }

    private fun writeMarker(context: Context, revision: Long, files: Map<String, String>) {
        val target = File(context.filesDir, MARKER_NAME)
        val temp = File(context.filesDir, "$MARKER_NAME.tmp")
        runCatching {
            val root = JSONObject().apply {
                put("version", ClockFontContract.REMOTE_CONFIG_VERSION)
                put("revision", revision)
                put("files", JSONObject().apply {
                    files.forEach { (id, sha) -> put(id, sha) }
                })
            }
            temp.writeText(root.toString(), Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }.onFailure {
            Log.w(TAG, "failed to write publish marker", it)
        }
    }
}
