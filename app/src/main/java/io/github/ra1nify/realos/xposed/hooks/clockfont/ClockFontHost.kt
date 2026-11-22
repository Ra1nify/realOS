package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.database.ContentObserver
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.ra1nify.realos.clockfont.ClockFontContract
import io.github.ra1nify.realos.clockfont.ClockFontType
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host-process client for the realOS clock-font provider.
 *
 * All calls are read-only and designed to avoid blocking the UI thread:
 * - Maintains an in-memory snapshot of font entries.
 * - Registers a ContentObserver on the provider URI for automatic background refresh.
 * - Typefaces are cached in-memory with variable font axis settings.
 */
internal object ClockFontHost {

    private const val TAG = "RealOS-ClockFontHost"
    private const val MAX_TYPEFACE_CACHE = 64

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedEntries: List<Entry> = emptyList()

    private val isObserving = AtomicBoolean(false)
    private val isRefreshing = AtomicBoolean(false)
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val warned = ConcurrentHashMap.newKeySet<String>()

    data class Entry(
        val id: String,
        val displayName: String,
        val type: ClockFontType,
        val dynamicWeight: Boolean = true
    ) {
        val value: String get() = ClockFontContract.valueFor(id)
    }

    fun attach(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        ensureObserver(app)
        refreshAsync(app)
    }

    fun context(): Context? = appContext

    fun listValues(context: Context? = null): List<String> {
        val ctx = context ?: appContext
        if (cachedEntries.isEmpty() && ctx != null) {
            refreshSync(ctx)
        }
        return cachedEntries.map { it.value }
    }

    fun isCustom(value: String?): Boolean = ClockFontContract.idFromValue(value) != null

    fun isFlex(value: String?): Boolean {
        val id = ClockFontContract.idFromValue(value) ?: return false
        val entry = cachedEntries.firstOrNull { it.id == id }
        if (entry != null) return entry.type == ClockFontType.FLEX
        val ctx = appContext ?: return false
        refreshSync(ctx)
        return cachedEntries.firstOrNull { it.id == id }?.type == ClockFontType.FLEX
    }

    fun isDynamicWeight(value: String?): Boolean {
        val id = ClockFontContract.idFromValue(value) ?: return true
        val entry = cachedEntries.firstOrNull { it.id == id }
        if (entry != null) return entry.dynamicWeight
        val ctx = appContext ?: return true
        refreshSync(ctx)
        return cachedEntries.firstOrNull { it.id == id }?.dynamicWeight ?: true
    }

    fun buildTypeface(
        context: Context?,
        value: String?,
        weight: Int,
        height: Int
    ): Typeface? {
        val id = ClockFontContract.idFromValue(value) ?: return null
        val ctx = context ?: appContext ?: return null
        var entry = cachedEntries.firstOrNull { it.id == id }
        if (entry == null) {
            refreshSync(ctx)
            entry = cachedEntries.firstOrNull { it.id == id } ?: return null
        }

        val safeWeight = if (entry.type == ClockFontType.FLEX) {
            weight.coerceIn(100, 1000)
        } else {
            weight.coerceIn(100, 700)
        }
        val safeHeight = height.coerceIn(1, 1000)
        val cacheKey = "${entry.id}|${entry.type.wireValue}|$safeWeight|$safeHeight"
        typefaceCache[cacheKey]?.let { return it }

        val resolver = ctx.contentResolver
        val uri = ClockFontContract.fontUri(entry.id)
        val typeface = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val settings = if (entry.type == ClockFontType.FLEX) {
                    "'wght' $safeWeight, 'HGHT' $safeHeight"
                } else {
                    "'wght' $safeWeight"
                }
                Typeface.Builder(pfd.fileDescriptor)
                    .setWeight(safeWeight)
                    .setItalic(false)
                    .setFontVariationSettings(settings)
                    .build()
            }
        }.onFailure {
            warnOnce("load:${entry.id}", "failed to load ${entry.displayName}: ${it.message}")
        }.getOrNull()

        if (typeface != null) {
            if (typefaceCache.size >= MAX_TYPEFACE_CACHE) typefaceCache.clear()
            typefaceCache[cacheKey] = typeface
            Log.d(TAG, "built typeface for ${entry.displayName} (id=${entry.id}, type=${entry.type}, weight=$safeWeight, height=$safeHeight)")
        } else {
            Log.w(TAG, "Typeface.Builder returned null for ${entry.displayName} (id=${entry.id})")
        }
        return typeface
    }

    fun invalidate() {
        cachedEntries = emptyList()
        typefaceCache.clear()
        appContext?.let { refreshAsync(it) }
    }

    private fun ensureObserver(context: Context) {
        if (!isObserving.compareAndSet(false, true)) return
        runCatching {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    Log.i(TAG, "clock-font provider changed, refreshing cache")
                    typefaceCache.clear()
                    refreshAsync(context)
                }
            }
            context.contentResolver.registerContentObserver(
                ClockFontContract.FONTS_URI,
                true,
                observer
            )
        }.onFailure {
            Log.w(TAG, "failed to register ContentObserver: ${it.message}")
        }
    }

    private fun refreshAsync(context: Context) {
        if (!isRefreshing.compareAndSet(false, true)) return
        Thread({
            try {
                val result = queryEntries(context)
                cachedEntries = result
                Log.d(TAG, "refreshed ${result.size} clock font(s) in background")
            } finally {
                isRefreshing.set(false)
            }
        }, "RealOS-ClockFontRefresh").start()
    }

    private fun refreshSync(context: Context) {
        val result = queryEntries(context)
        if (result.isNotEmpty() || cachedEntries.isEmpty()) {
            cachedEntries = result
        }
    }

    private fun queryEntries(context: Context): List<Entry> {
        val uri = ClockFontContract.FONTS_URI
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ClockFontContract.COLUMN_ID,
                    ClockFontContract.COLUMN_DISPLAY_NAME,
                    ClockFontContract.COLUMN_TYPE,
                    ClockFontContract.COLUMN_DYNAMIC_WEIGHT
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_ID)
                val nameIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_DISPLAY_NAME)
                val typeIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_TYPE)
                val weightIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_DYNAMIC_WEIGHT)
                if (idIndex < 0) return@use emptyList()
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex) ?: continue
                        val dynamicWeight = if (weightIndex >= 0) cursor.getInt(weightIndex) != 0 else true
                        add(
                            Entry(
                                id = id,
                                displayName = if (nameIndex >= 0) cursor.getString(nameIndex) ?: id else id,
                                type = if (typeIndex >= 0) {
                                    ClockFontType.fromWire(cursor.getString(typeIndex))
                                } else {
                                    ClockFontType.FIXED
                                },
                                dynamicWeight = dynamicWeight
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.onFailure {
            warnOnce("query", "clock-font provider unavailable: ${it.message}")
        }.getOrDefault(emptyList())
    }

    private fun warnOnce(key: String, message: String) {
        if (warned.add(key)) Log.w(TAG, message)
    }
}
