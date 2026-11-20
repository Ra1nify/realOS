package io.github.ra1nify.realos.wallet

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Finshell wallet (com.finshell.wallet) configuration.
 *
 * Cross-process: the host process reads `wallet_config` through
 * [XposedModule.getRemotePreferences]. Remote preferences do not update on a normal
 * SharedPreferences write; [XposedServiceManager.getRemotePreferences] is written as well.
 *
 * Card-level properties (custom cover, stock cover, custom name, hidden name) use a
 * dedicated SharedPreferences file inside the wallet process.
 *
 * Hook callbacks (`getCardImg` / `getCardName` / `setTitleVisibility` and others) run on
 * the draw and bind paths. Card-level properties come from the [WalletCardOverrides]
 * in-memory snapshot: one full load on first read, then reload keyed by write version.
 */
object WalletConfig {

    private const val PREFS_NAME = "wallet_config"
    const val CARD_PREFS_NAME = "realos_wallet_card_overrides"

    private const val PREFIX_CUSTOM_COVER = "custom_cover_"
    private const val PREFIX_STOCK_COVER = "stock_cover_"
    private const val PREFIX_CUSTOM_NAME = "custom_name_"
    private const val PREFIX_HIDE_NAME = "hide_name_"

    /** Whether non-NFC emulated access card face customization is enabled. */
    const val KEY_NON_NFC_CARD_DIY_ENABLED = "wallet_non_nfc_card_diy_enabled"

    const val DEFAULT_NON_NFC_CARD_DIY_ENABLED = true

    // ========== Master switch ==========

    /** Host process read; returns the latest value on every call. */
    fun isNonNfcCardDiyEnabled(module: XposedModule): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME)
            .getBoolean(KEY_NON_NFC_CARD_DIY_ENABLED, DEFAULT_NON_NFC_CARD_DIY_ENABLED)
    }.getOrDefault(DEFAULT_NON_NFC_CARD_DIY_ENABLED)

    /** realOS app-side read. */
    fun isNonNfcCardDiyEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NON_NFC_CARD_DIY_ENABLED, DEFAULT_NON_NFC_CARD_DIY_ENABLED)
    }.getOrDefault(DEFAULT_NON_NFC_CARD_DIY_ENABLED)

    /** realOS app-side write: local and remote preferences. */
    fun setNonNfcCardDiyEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NON_NFC_CARD_DIY_ENABLED, enabled)
                .apply()
        }
        pushToRemote(enabled)
    }

    /**
     * Copies the local value into remote preferences.
     * Runs once on detail page entry.
     */
    fun syncToRemote(context: Context) {
        pushToRemote(isNonNfcCardDiyEnabled(context))
    }

    private fun pushToRemote(enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()
                ?.putBoolean(KEY_NON_NFC_CARD_DIY_ENABLED, enabled)
                ?.apply()
        }
    }

    // ========== Card-level configuration (AID bound) ==========

    /**
     * In-memory snapshot: package name, raw `Map` entries, and version number.
     * Used in the host UI process only (prefs resolve to the host package file).
     */
    class WalletCardOverrides internal constructor(
        val packageName: String,
        val values: Map<String, Any?>,
        val version: Int
    ) {
        fun customCover(aid: String): String? = values.str(PREFIX_CUSTOM_COVER + aid)
        fun stockCover(aid: String): String? = values.str(PREFIX_STOCK_COVER + aid)
        fun customName(aid: String): String? = values.str(PREFIX_CUSTOM_NAME + aid)
        fun hideName(aid: String): Boolean = values[PREFIX_HIDE_NAME + aid] as? Boolean ?: false

        /** Whether any custom state exists (cover / name / hidden name). */
        fun hasCustomState(aid: String): Boolean =
            customCover(aid) != null || customName(aid) != null || hideName(aid)

        /** All AIDs from prefixed keys, deduplicated. */
        fun knownAids(): Set<String> {
            val aids = HashSet<String>()
            for (key in values.keys) {
                when {
                    key.startsWith(PREFIX_CUSTOM_COVER) -> aids.add(key.removePrefix(PREFIX_CUSTOM_COVER))
                    key.startsWith(PREFIX_STOCK_COVER) -> aids.add(key.removePrefix(PREFIX_STOCK_COVER))
                    key.startsWith(PREFIX_CUSTOM_NAME) -> aids.add(key.removePrefix(PREFIX_CUSTOM_NAME))
                    key.startsWith(PREFIX_HIDE_NAME) -> aids.add(key.removePrefix(PREFIX_HIDE_NAME))
                }
            }
            aids.remove("")
            return aids
        }

        private fun Map<String, Any?>.str(key: String): String? =
            (this[key] as? String)?.takeIf { it.isNotEmpty() }

        companion object {
            val EMPTY = WalletCardOverrides("", emptyMap(), -1)
        }
    }

    private val overridesRef = AtomicReference(WalletCardOverrides.EMPTY)
    private val cardPrefsRef = AtomicReference<SharedPreferences?>(null)

    @Synchronized
    private fun cardPrefs(context: Context): SharedPreferences? {
        val cached = cardPrefsRef.get()
        if (cached != null) return cached
        val created = runCatching {
            context.getSharedPreferences(CARD_PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return null
        cardPrefsRef.set(created)
        return created
    }

    /**
     * Returns the card-level configuration snapshot.
     *
     * A full reload happens on the first call, an empty cache, or a version change.
     * Cache hits read and compare `version` only.
     */
    fun overrides(context: Context): WalletCardOverrides {
        val pkg = context.packageName
        val prefs = cardPrefs(context) ?: return WalletCardOverrides.EMPTY
        val current = overridesRef.get()
        val version = runCatching { prefs.getInt(KEY_OVERRIDES_VERSION, 0) }.getOrDefault(0)
        if (current.packageName == pkg && current.version == version) return current
        val snapshot = runCatching {
            WalletCardOverrides(pkg, prefs.all ?: emptyMap(), version)
        }.getOrElse {
            WalletCardOverrides(pkg, emptyMap(), version)
        }
        overridesRef.set(snapshot)
        return snapshot
    }

    /**
     * Single entry for write operations.
     *
     * Increments [KEY_OVERRIDES_VERSION] and uses `commit()`; readers in the same
     * process see the new value immediately.
     */
    private fun mutate(context: Context, block: SharedPreferences.Editor.() -> Unit): Boolean {
        val prefs = cardPrefs(context) ?: return false
        val currentVersion = runCatching { prefs.getInt(KEY_OVERRIDES_VERSION, 0) }.getOrDefault(0)
        val ok = runCatching {
            prefs.edit()
                .putInt(KEY_OVERRIDES_VERSION, currentVersion + 1)
                .apply(block)
                .commit()
        }.getOrDefault(false)
        if (ok) {
            // Invalidates the snapshot; the next read rebuilds it
            overridesRef.set(WalletCardOverrides.EMPTY)
        }
        return ok
    }

    fun getCustomCover(context: Context, aid: String): String? {
        if (aid.isEmpty()) return null
        return overrides(context).customCover(aid)
    }

    fun setCustomCover(context: Context, aid: String, url: String) {
        if (aid.isEmpty()) return
        val key = PREFIX_CUSTOM_COVER + aid
        mutate(context) {
            if (url.isEmpty()) remove(key) else putString(key, url)
        }
    }

    fun getStockCover(context: Context, aid: String): String? {
        if (aid.isEmpty()) return null
        return overrides(context).stockCover(aid)
    }

    fun setStockCover(context: Context, aid: String, url: String) {
        if (aid.isEmpty() || url.isEmpty()) return
        // Stock cover is recorded once and never overwritten
        if (getStockCover(context, aid).isNullOrEmpty()) {
            mutate(context) { putString(PREFIX_STOCK_COVER + aid, url) }
        }
    }

    fun getCustomName(context: Context, aid: String): String? {
        if (aid.isEmpty()) return null
        return overrides(context).customName(aid)
    }

    fun setCustomName(context: Context, aid: String, name: String) {
        if (aid.isEmpty()) return
        val key = PREFIX_CUSTOM_NAME + aid
        mutate(context) {
            if (name.isEmpty()) remove(key) else putString(key, name)
        }
    }

    fun isHideNameEnabled(context: Context, aid: String): Boolean {
        if (aid.isEmpty()) return false
        return overrides(context).hideName(aid)
    }

    fun setHideNameEnabled(context: Context, aid: String, hide: Boolean) {
        if (aid.isEmpty()) return
        val key = PREFIX_HIDE_NAME + aid
        mutate(context) {
            if (hide) putBoolean(key, true) else remove(key)
        }
    }

    // ========== Local cover pool ==========

    /** Cover pool directory under the host files dir; the path matches earlier versions. */
    fun getCoverPoolDir(context: Context): File {
        val dir = File(context.filesDir, COVER_DIR_NAME)
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        return dir
    }

    fun getCoverPool(context: Context): List<String> {
        val dir = getCoverPoolDir(context)
        val files = runCatching {
            dir.listFiles { f ->
                f.isFile && (f.name.endsWith(".png", ignoreCase = true) || f.name.endsWith(".jpg", ignoreCase = true))
            }
        }.getOrNull()
        return files?.sortedByDescending { it.lastModified() }?.map { it.absolutePath } ?: emptyList()
    }

    fun removeCoverFromPool(context: Context, path: String): Boolean {
        return runCatching {
            val f = File(path)
            if (f.exists()) f.delete() else false
        }.getOrDefault(false)
    }

    fun isPoolCoverPath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return path.contains("${File.separator}$COVER_DIR_NAME${File.separator}")
    }

    private const val KEY_OVERRIDES_VERSION = "overrides_version"
    private const val COVER_DIR_NAME = "realos_covers"

    /**
     * Checks whether an absolute path is inside the realOS cover pool and returns the
     * normalized path. Single check for "usable as a data source in this process".
     */
    fun resolvePoolCoverPath(context: Context, raw: String): String? {
        if (raw.isEmpty()) return null
        if (!isPoolCoverPath(raw)) return null
        val file = runCatching { File(raw) }.getOrNull() ?: return null
        if (!file.isAbsolute) return null
        if (!runCatching { file.isFile && file.length() > 0L }.getOrDefault(false)) return null
        return file.absolutePath
    }

    /**
     * Clears stale cover pool paths from host data when the file no longer exists.
     *
     * Only the module side performs this one-way orphan cleanup.
     *
     * @return number of cleared entries
     */
    fun clearStaleCoverReferences(context: Context, aids: Collection<String>): Int {
        if (aids.isEmpty()) return 0
        var cleared = 0
        for (aid in aids) {
            val cover = getCustomCover(context, aid) ?: continue
            if (resolvePoolCoverPath(context, cover) == null) {
                setCustomCover(context, aid, "")
                cleared++
            }
        }
        return cleared
    }
}
