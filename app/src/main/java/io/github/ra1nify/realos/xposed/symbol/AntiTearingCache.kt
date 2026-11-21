package io.github.ra1nify.realos.xposed.symbol

import android.content.Context
import android.content.SharedPreferences
import io.github.ra1nify.realos.util.RealOSLog as Log
import org.json.JSONObject

/**
 * Anti-tearing persistent cache for symbol descriptors, valid across host versions.
 *
 * - **Three-key check**: `fingerprint` (host APK path + update time + size),
 *   `ruleVersion` and `schemaVersion` must all match; any mismatch marks the cache
 *   invalid and forces a rescan.
 * - **Anti-tearing write**: the scan result is serialized into one JSON string and
 *   committed in a single step instead of repeated `put` calls.
 * - **Post-commit check**: the stored value is read back and compared; a mismatch
 *   clears the cache for the next run.
 *
 * All methods are pure I/O and JSON operations, with no DexKit or reflection.
 */
class AntiTearingCache(
    context: Context?,
    private val prefsName: String,
    private val ruleVersion: Int,
    private val schemaVersion: Int = SCHEMA_VERSION,
    private val tag: String = "RealOS-AntiTearingCache"
) {

    private val prefs: SharedPreferences? = context?.let { ctx ->
        runCatching {
            ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        }.getOrElse { t ->
            Log.w(tag, "Symbol cache initialization failed; not persisting this run", t)
            null
        }
    }

    /**
     * Reads from the cache.
     *
     * @param fingerprint Current host fingerprint, must match the value at write time
     * @return The parsed [CachePayload] on a hit; null on miss, key mismatch or I/O error
     */
    fun read(fingerprint: String): CachePayload? {
        val store = prefs ?: return null
        return runCatching {
            if (store.getInt(KEY_SCHEMA, -1) != schemaVersion) return null
            if (store.getInt(KEY_RULE, -1) != ruleVersion) return null
            if (store.getString(KEY_FINGERPRINT, null) != fingerprint) return null

            val raw = store.getString(KEY_PAYLOAD, null) ?: return null
            val payload = CachePayload.fromJson(JSONObject(raw))
            if (payload.fingerprint != fingerprint) return null
            if (payload.ruleVersion != ruleVersion) return null
            payload
        }.getOrElse { t ->
            Log.w(tag, "Symbol cache read failed; rescanning", t)
            null
        }
    }

    /**
     * Writes a scan result.
     *
     * Serializes [symbols] into one JSON string, commits it with the version keys in a
     * single `commit()`, then reads it back; any failed step clears the cache.
     */
    fun write(payload: CachePayload): Boolean {
        val store = prefs ?: return false
        val raw = runCatching { payload.toJson().toString() }.getOrElse { t ->
            Log.w(tag, "Symbol cache serialization failed", t)
            return false
        }

        val committed = runCatching {
            store.edit()
                .putInt(KEY_SCHEMA, schemaVersion)
                .putInt(KEY_RULE, ruleVersion)
                .putString(KEY_FINGERPRINT, payload.fingerprint)
                .putString(KEY_PAYLOAD, raw)
                .commit()
        }.getOrDefault(false)

        if (!committed) {
            Log.w(tag, "Symbol cache commit failed; clearing the cache")
            clear()
            return false
        }

        // Reads back the committed value and compares it against the expected one.
        val verified = runCatching {
            store.getString(KEY_PAYLOAD, null) == raw &&
                store.getInt(KEY_RULE, -1) == ruleVersion &&
                store.getInt(KEY_SCHEMA, -1) == schemaVersion
        }.getOrDefault(false)

        if (!verified) {
            Log.w(tag, "Symbol cache read-back verification failed; clearing the cache")
            clear()
            return false
        }
        return true
    }

    fun clear() {
        runCatching { prefs?.edit()?.clear()?.commit() }
    }

    /**
     * Full cache content: fingerprint + rule version + all descriptor entries.
     *
     * `symbols` values are limited to [ClassDescriptor] / [MethodDescriptor] /
     * [FieldDescriptor]; the caller handles encode/decode.
     */
    class CachePayload(
        val fingerprint: String,
        val ruleVersion: Int,
        val symbols: Map<String, SymbolDescriptor>
    ) {

        fun toJson(): JSONObject = JSONObject().apply {
            put(KEY_FINGERPRINT, fingerprint)
            put(KEY_RULE_VERSION, ruleVersion)
            put(
                KEY_SYMBOLS,
                JSONObject().apply {
                    symbols.forEach { (key, descriptor) -> put(key, descriptor.toTaggedJson()) }
                }
            )
        }

        companion object {
            private const val KEY_FINGERPRINT = "fingerprint"
            private const val KEY_RULE_VERSION = "ruleVersion"
            private const val KEY_SYMBOLS = "symbols"

            /**
             * Deserializes.
             *
             * A descriptor that fails to parse is skipped; its restore marks it missing
             * and fails closed, while the other symbols in the payload are kept.
             */
            fun fromJson(json: JSONObject): CachePayload {
                val fingerprint = json.getString(KEY_FINGERPRINT)
                val ruleVersion = json.getInt(KEY_RULE_VERSION)
                val symbolsJson = json.optJSONObject(KEY_SYMBOLS) ?: JSONObject()
                val symbols = buildMap {
                    symbolsJson.keys().forEach { key ->
                        val obj = symbolsJson.optJSONObject(key) ?: return@forEach
                        SymbolDescriptor.fromJson(obj)?.let { put(key, it) }
                    }
                }
                return CachePayload(fingerprint, ruleVersion, symbols)
            }
        }
    }

    companion object {
        /** Cache structure version, bumped when descriptor fields or the serialization format change */
        private const val SCHEMA_VERSION = 1

        private const val KEY_SCHEMA = "schema_version"
        private const val KEY_RULE = "rule_version"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_PAYLOAD = "payload"

        /**
         * Builds a stable fingerprint from host APK info.
         *
         * Path + modification time + size: a host upgrade changes at least one of the
         * three, and a same-path overwrite install is still detected.
         */
        fun fingerprintOf(apkPath: String?, lastModified: Long, length: Long): String =
            "${apkPath ?: "?"}|$lastModified|$length"
    }
}

/**
 * Cache carrier for a single symbol, hiding the three descriptor types.
 *
 * Serialization tags the type in the `kind` field and deserialization dispatches on
 * it; an unknown or missing `kind` returns null and the entry is skipped.
 */
sealed interface SymbolDescriptor {

    fun toJson(): JSONObject

    /** JSON form with a type tag, written by [AntiTearingCache.CachePayload] */
    fun toTaggedJson(): JSONObject = toJson().apply { put(KEY_KIND, kind) }

    /** Type tag value */
    val kind: String

    companion object {
        private const val KEY_KIND = "kind"

        private const val KIND_CLASS = "class"
        private const val KIND_METHOD = "method"
        private const val KIND_FIELD = "field"
        private const val KIND_CONSTRUCTOR = "constructor"
        private const val KIND_JSON = "json"

        fun fromJson(json: JSONObject): SymbolDescriptor? = runCatching {
            when (json.optString(KEY_KIND)) {
                KIND_CLASS -> ClassSymbol(ClassDescriptor.fromJson(json))
                KIND_METHOD -> MethodSymbol(MethodDescriptor.fromJson(json))
                KIND_FIELD -> FieldSymbol(FieldDescriptor.fromJson(json))
                KIND_CONSTRUCTOR -> ConstructorSymbol(ConstructorDescriptor.fromJson(json))
                KIND_JSON -> JsonSymbol(json.optString(JsonSymbol.KEY_RAW))
                else -> null
            }
        }.getOrNull()
    }
}

/** Cache carrier for [ClassDescriptor] */
data class ClassSymbol(val descriptor: ClassDescriptor) : SymbolDescriptor {
    override val kind: String get() = "class"
    override fun toJson(): JSONObject = descriptor.toJson()
}

/** Cache carrier for [MethodDescriptor] */
data class MethodSymbol(val descriptor: MethodDescriptor) : SymbolDescriptor {
    override val kind: String get() = "method"
    override fun toJson(): JSONObject = descriptor.toJson()
}

/** Cache carrier for [FieldDescriptor] */
data class FieldSymbol(val descriptor: FieldDescriptor) : SymbolDescriptor {
    override val kind: String get() = "field"
    override fun toJson(): JSONObject = descriptor.toJson()
}

/** Cache carrier for [ConstructorDescriptor] */
data class ConstructorSymbol(val descriptor: ConstructorDescriptor) : SymbolDescriptor {
    override val kind: String get() = "constructor"
    override fun toJson(): JSONObject = descriptor.toJson()
}

/**
 * Cache carrier that stores a raw JSON text.
 *
 * Used when the host symbol set already has toJson/fromJson: the whole snapshot goes
 * into the payload instead of a second hand-written field map.
 */
data class JsonSymbol(val raw: String) : SymbolDescriptor {
    override val kind: String get() = "json"
    override fun toJson(): JSONObject = JSONObject().put(KEY_RAW, raw)

    companion object {
        const val KEY_RAW = "raw"
    }
}
