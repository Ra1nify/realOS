package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedInterface
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusModuleRef
import java.lang.reflect.Field
import java.lang.reflect.Method
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import java.util.Collections

/**
 * Hot-reloadable controller of the liquid glass whitelist bypass.
 *
 * Two cross-version modes:
 * 1. New versions (1.9.17+): the whitelist inlines into the static predicate
 *    `c.a.c.i.obf.M.K(EditorInfo)`; the hook returns true unconditionally while the bypass is on.
 * 2. Old versions: the whitelist is a static collection field in `com.oplus.keyboard.input.utils.d`,
 *    swapped at runtime for a Universal container.
 */
object OplusInputWhitelistBypass {

    private const val TAG = "OplusWhitelistBypass"

    @Volatile
    private var cachedField: Field? = null
    @Volatile
    private var originalCollection: Any? = null
    @Volatile
    private var hookHandle: XposedInterface.HookHandle? = null

    private val lock = Any()

    /**
     * Registers and activates the whitelist bypass; called from `OplusInputHook.install`.
     *
     * @return whether initialization succeeded.
     */
    fun initialize(app: Context, symbols: OplusInputSymbols): Boolean {
        if (symbols.statusMap["whitelist"] != HookPointState.FOUND) return false

        // ── 1. New: inline decision method hook ──
        val methodDesc = symbols.blurDecisionMethod
        if (methodDesc != null) {
            synchronized(lock) {
                if (hookHandle == null) {
                    val method = methodDesc.restore(app.classLoader)
                    val module = OplusModuleRef.get()
                    if (method != null && module != null) {
                        try {
                            hookHandle = module.hook(method).intercept { chain ->
                                val enabled = runCatching { OplusInputPrefs.isBypassAllLimits(app) }.getOrDefault(false)
                                if (enabled) {
                                    true
                                } else {
                                    chain.proceed()
                                }
                            }
                            Log.i(TAG, "Installed whitelist blur decision hook on ${methodDesc.className}.${methodDesc.methodName}")
                            return true
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed to hook blurDecisionMethod", t)
                        }
                    } else {
                        Log.w(TAG, "blurDecisionMethod restore failed or module ref is null.")
                    }
                } else {
                    return true
                }
            }
        }

        // ── 2. Old: static collection field replacement ──
        val descriptor = symbols.whitelistField ?: return false
        synchronized(lock) {
            if (cachedField == null) {
                val field = descriptor.restore(app.classLoader) ?: run {
                    Log.w(TAG, "whitelistField restore failed; bypass disabled (fail closed).")
                    return false
                }
                val current = runCatching { field.get(null) }.getOrNull()
                if (current is Collection<*>) {
                    originalCollection = unwrapOriginal(current)
                } else {
                    originalCollection = current
                }
                cachedField = field
                Log.i(
                    TAG,
                    "Initialized whitelist field ${descriptor.className}.${descriptor.fieldName}, " +
                        "originalSize=${(originalCollection as? Collection<*>)?.size}"
                )
            }
        }

        refresh(app)
        return true
    }

    /**
     * Idempotently refreshes the old-version whitelist field from the current bypass switch
     * (the new version reads Prefs inside the hook).
     */
    fun refresh(context: Context) {
        val enabled = runCatching { OplusInputPrefs.isBypassAllLimits(context) }.getOrDefault(false)
        apply(enabled)
    }

    /**
     * Applies the old-version collection swap with the given enabled value.
     */
    fun apply(enabled: Boolean) {
        val field = cachedField ?: return
        synchronized(lock) {
            try {
                if (enabled) {
                    val original = originalCollection as? Collection<String> ?: emptyList()
                    val wrapped: Any = if (field.type == java.util.Set::class.java || Set::class.java.isAssignableFrom(field.type)) {
                        UniversalWhitelistSet(original)
                    } else {
                        UniversalWhitelistList(original)
                    }
                    field.set(null, wrapped)
                    val verified = field.get(null)
                    Log.i(TAG, "Whitelist bypass ENABLED (universal container applied, ok=${verified === wrapped})")
                } else {
                    val original = originalCollection
                        ?: Collections.emptyList<String>()
                    field.set(null, original)
                    val verified = field.get(null)
                    Log.i(TAG, "Whitelist bypass DISABLED (original collection restored, ok=${verified === original})")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to apply whitelist bypass (enabled=$enabled)", t)
            }
        }
    }

    /**
     * Clears the caches and unhooks; called from hot-reload cleanup.
     */
    fun reset() {
        synchronized(lock) {
            runCatching { hookHandle?.unhook() }
            hookHandle = null
            val field = cachedField
            val original = originalCollection
            if (field != null && original != null) {
                runCatching { field.set(null, original) }
                    .onSuccess { Log.i(TAG, "Whitelist field restored to original on reset.") }
                    .onFailure { Log.w(TAG, "Failed to restore whitelist field on reset: ${it.message}") }
            }
            cachedField = null
            originalCollection = null
        }
    }

    private fun unwrapOriginal(current: Collection<*>): Any {
        return when (current) {
            is UniversalWhitelistList -> ArrayList(current)
            is UniversalWhitelistSet -> HashSet(current)
            else -> current
        }
    }
}
