package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.clockfont.ClockFontContract
import io.github.ra1nify.realos.clockfont.ClockFontType
import io.github.ra1nify.realos.util.RealOSLog as Log
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private typealias Entry = ClockFontEntry

/** Host-side clock-font metadata. */
internal object ClockFontHost : ClockFontTypefaceHost {

    private const val TAG = "RealOS-ClockFontHost"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    private var remoteListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Volatile
    private var cachedEntries: List<Entry> = emptyList()

    @Volatile
    private var statusBarClockEnabled = false

    @Volatile
    private var systemClockChangeListener: (() -> Unit)? = null

    private val isObserving = AtomicBoolean(false)
    private val isRefreshing = AtomicBoolean(false)
    private val providerSeedAttempted = AtomicBoolean(false)
    private val warned = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var remoteSnapshotLogged = false

    override fun hostContext(): Context? = appContext

    override fun hostModule(): XposedModule? = module

    override fun hasRemotePrefs(): Boolean = remotePrefs != null

    override fun hostEntries(): List<ClockFontEntry> = cachedEntries

    override fun prepareSystemVariants() {
        appContext?.let { prepareSystemVariantsAsync(it) }
    }

    @Synchronized
    fun attach(module: XposedModule, context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        this.module = module
        ensureObserver(app)

        // Retry remote prefs on every attach.
        val prefs = remotePrefs ?: runCatching {
            module.getRemotePreferences(ClockFontContract.REMOTE_PREFS)
        }.onFailure {
            warnOnce("remote-prefs", "remote preferences unavailable: ${it.message}")
        }.getOrNull()

        if (prefs == null) {
            warnOnce(
                "remote-snapshot-unavailable",
                "HookPoint[clockfont_remote_snapshot] state=MISSING " +
                    "(remote preferences unavailable)"
            )
            if (!seedFromProviderOnce(app)) refreshAsync(app)
            if (statusBarClockEnabled) prepareSystemVariantsAsync(app)
            return
        }

        remotePrefs = prefs
        statusBarClockEnabled = prefs.getBoolean(
            ClockFontContract.REMOTE_KEY_STATUS_BAR_CLOCK,
            false
        )
        if (remoteListener == null) registerRemoteListener(prefs)

        val snapshot = parseRemote(prefs)
        if (snapshot != null) {
            if (cachedEntries != snapshot) {
                ClockFontTypefaceFactory.clearCache()
                cachedEntries = snapshot
            }
            if (!remoteSnapshotLogged) {
                remoteSnapshotLogged = true
                Log.i(
                    TAG,
                    "HookPoint[clockfont_remote_snapshot] state=FOUND " +
                        "entries=${snapshot.size} " +
                        "rev=${prefs.getLong(ClockFontContract.REMOTE_KEY_REVISION, 0)}"
                )
            }
            if (statusBarClockEnabled) prepareSystemVariantsAsync(app)
            return
        }

        // Remote metadata is authoritative.
        warnOnce(
            "remote-snapshot-incomplete",
            "HookPoint[clockfont_remote_snapshot] state=MISSING (incomplete remote metadata)"
        )
        seedFromProviderOnce(app)
        refreshAsync(app)
        if (statusBarClockEnabled) prepareSystemVariantsAsync(app)
    }

    /** Seeds provider entries once per process. */
    private fun seedFromProviderOnce(context: Context): Boolean {
        if (!providerSeedAttempted.compareAndSet(false, true)) return false
        val result = queryEntries(context)
        if (result == null) {
            Log.w(
                TAG,
                "HookPoint[clockfont_provider_snapshot] state=MISSING (provider unavailable)"
            )
            return false
        }
        mergeProviderEntries(result)
        Log.i(
            TAG,
            "HookPoint[clockfont_provider_snapshot] state=FOUND entries=${result.size}"
        )
        return true
    }

    fun context(): Context? = appContext

    fun isAttached(): Boolean = module != null

    fun isStatusBarClockEnabled(): Boolean = statusBarClockEnabled

    /** Notifies SystemUI to refresh live clocks. */
    fun setSystemClockChangeListener(listener: (() -> Unit)?) {
        systemClockChangeListener = listener
        if (listener != null) appContext?.let { prepareSystemVariantsAsync(it) }
    }

    fun listValues(context: Context? = null): List<String> {
        val ctx = context ?: appContext
        // Panel-only fallback.
        if (cachedEntries.isEmpty() && ctx != null) {
            refreshSync(ctx)
        }
        return cachedEntries.filterNot { it.hidden }.map { it.value }
    }

    fun isCustom(value: String?): Boolean = ClockFontContract.idFromValue(value) != null

    fun isFlex(value: String?): Boolean {
        val id = ClockFontContract.idFromValue(value) ?: return false
        val entry = cachedEntries.firstOrNull { it.id == id }
        if (entry != null) return entry.type == ClockFontType.FLEX
        // Fail closed without a snapshot.
        appContext?.let { refreshAsync(it) }
        return false
    }

    fun isDynamicWeight(value: String?): Boolean {
        val id = ClockFontContract.idFromValue(value) ?: return false
        val entry = cachedEntries.firstOrNull { it.id == id }
        if (entry != null) return entry.dynamicWeight
        // Fail closed without a snapshot.
        appContext?.let { refreshAsync(it) }
        return false
    }

    fun buildTypeface(
        context: Context?,
        value: String?,
        weight: Int,
        height: Int,
        origin: String,
        normalizeMetrics: Boolean = false
    ): Typeface? = ClockFontTypefaceFactory.build(
        this,
        context,
        value,
        weight,
        height,
        origin,
        normalizeMetrics
    )

    internal fun effectiveHeight(type: ClockFontType, height: Int): Int =
        ClockFontTypefaceFactory.effectiveHeight(type, height)

    @Synchronized
    fun invalidate() {
        remoteListener?.let { listener ->
            runCatching { remotePrefs?.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        remoteListener = null
        remotePrefs = null
        module = null
        cachedEntries = emptyList()
        statusBarClockEnabled = false
        systemClockChangeListener = null
        remoteSnapshotLogged = false
        providerSeedAttempted.set(false)
        ClockFontSystemVariantPreparer.reset()
        ClockFontTypefaceFactory.clearCache()
    }

    private fun registerRemoteListener(prefs: SharedPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { updated, key ->
            val relevant = key == null ||
                key == ClockFontContract.REMOTE_KEY_VERSION ||
                key == ClockFontContract.REMOTE_KEY_REVISION ||
                key == ClockFontContract.REMOTE_KEY_ENTRIES ||
                key == ClockFontContract.REMOTE_KEY_STATUS_BAR_CLOCK
            if (relevant) {
                statusBarClockEnabled = updated.getBoolean(
                    ClockFontContract.REMOTE_KEY_STATUS_BAR_CLOCK,
                    statusBarClockEnabled
                )
                // Keep the previous snapshot on parse failure.
                val snapshot = parseRemote(updated)
                if (snapshot != null) {
                    ClockFontTypefaceFactory.clearCache()
                    cachedEntries = snapshot
                    Log.i(
                        TAG,
                        "remote metadata updated: ${snapshot.size} clock font(s), " +
                            "statusBarClock=$statusBarClockEnabled"
                    )
                }
                if (statusBarClockEnabled) {
                    appContext?.let { prepareSystemVariantsAsync(it) }
                }
                systemClockChangeListener?.invoke()
            }
        }
        remoteListener = listener
        runCatching { prefs.registerOnSharedPreferenceChangeListener(listener) }
            .onFailure {
                remoteListener = null
                Log.w(TAG, "failed to register remote metadata listener: ${it.message}")
            }
    }

    /** Returns null when no published snapshot exists. */
    private fun parseRemote(prefs: SharedPreferences): List<Entry>? {
        val version = prefs.getInt(ClockFontContract.REMOTE_KEY_VERSION, 0)
        if (version < ClockFontContract.REMOTE_CONFIG_VERSION) return null
        // Missing entries indicate an incomplete publish.
        val raw = prefs.getString(ClockFontContract.REMOTE_KEY_ENTRIES, null) ?: return null

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        Entry(
                            id = id,
                            displayName = item.optString("displayName", id),
                            type = ClockFontType.fromWire(item.optString("type")),
                            dynamicWeight = item.optBoolean("dynamicWeight", true),
                            hidden = item.optBoolean("hidden", false)
                        )
                    )
                }
            }
        }.onFailure {
            warnOnce("remote-parse", "invalid remote clock-font metadata: ${it.message}")
        }.getOrNull()
    }

    /** Prepares SystemUI variants off the render path. */
    private fun prepareSystemVariantsAsync(context: Context) {
        // SystemUI-only preparation.
        if (systemClockChangeListener == null) return
        if (!statusBarClockEnabled) return
        val entries = cachedEntries
        if (entries.isEmpty()) return

        val request: () -> Unit = { prepareSystemVariantsAsync(context) }
        ClockFontSystemVariantPreparer.prepare(
            context = context,
            entries = entries,
            profile = ClockFontTypefaceFactory.stockProfile,
            profileTag = ClockFontTypefaceFactory.profileTag,
            readBytes = { ClockFontTypefaceFactory.readEntryBytes(this, it) },
            onPrepared = { systemClockChangeListener?.invoke() },
            warn = ::warnOnce,
            retry = request
        )
    }

    private fun ensureObserver(context: Context) {
        if (!isObserving.compareAndSet(false, true)) return
        runCatching {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    Log.i(TAG, "clock-font provider changed, refreshing cache")
                    ClockFontTypefaceFactory.clearCache()
                    refreshAsync(context)
                }
            }
            context.contentResolver.registerContentObserver(
                ClockFontContract.FONTS_URI,
                true,
                observer
            )
        }.onFailure {
            isObserving.set(false)
            Log.w(TAG, "failed to register ContentObserver: ${it.message}")
        }
    }

    override fun refreshAsync(context: Context) {
        if (!isRefreshing.compareAndSet(false, true)) return
        Thread({
            try {
                val result = queryEntries(context)
                if (result != null) {
                    mergeProviderEntries(result)
                    Log.d(TAG, "refreshed ${result.size} clock font(s) from provider")
                }
            } finally {
                isRefreshing.set(false)
            }
        }, "RealOS-ClockFontRefresh").start()
    }

    private fun refreshSync(context: Context) {
        // Null means provider failure; empty is valid.
        val result = queryEntries(context) ?: return
        mergeProviderEntries(result)
    }

    /** Merges provider entries; remote ids win. */
    private fun mergeProviderEntries(provider: List<Entry>) {
        if (provider.isEmpty() && cachedEntries.isNotEmpty()) return
        val merged = LinkedHashMap<String, Entry>()
        cachedEntries.forEach { merged[it.id] = it }
        provider.forEach { merged.putIfAbsent(it.id, it) }
        cachedEntries = merged.values.toList()
    }

    private fun queryEntries(context: Context): List<Entry>? {
        val uri = ClockFontContract.FONTS_URI
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ClockFontContract.COLUMN_ID,
                    ClockFontContract.COLUMN_DISPLAY_NAME,
                    ClockFontContract.COLUMN_TYPE,
                    ClockFontContract.COLUMN_DYNAMIC_WEIGHT,
                    ClockFontContract.COLUMN_HIDDEN
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_ID)
                val nameIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_DISPLAY_NAME)
                val typeIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_TYPE)
                val weightIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_DYNAMIC_WEIGHT)
                val hiddenIndex = cursor.getColumnIndex(ClockFontContract.COLUMN_HIDDEN)
                if (idIndex < 0) return@use emptyList()
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex) ?: continue
                        val dynamicWeight = if (weightIndex >= 0) cursor.getInt(weightIndex) != 0 else true
                        val hidden = hiddenIndex >= 0 && cursor.getInt(hiddenIndex) != 0
                        add(
                            Entry(
                                id = id,
                                displayName = if (nameIndex >= 0) cursor.getString(nameIndex) ?: id else id,
                                type = if (typeIndex >= 0) {
                                    ClockFontType.fromWire(cursor.getString(typeIndex))
                                } else {
                                    ClockFontType.FIXED
                                },
                                dynamicWeight = dynamicWeight,
                                hidden = hidden
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.onFailure {
            warnOnce("query", "clock-font provider unavailable: ${it.message}")
        }.getOrNull()
    }

    override fun warnOnce(key: String, message: String) {
        if (warned.add(key)) Log.w(TAG, message)
    }
}
