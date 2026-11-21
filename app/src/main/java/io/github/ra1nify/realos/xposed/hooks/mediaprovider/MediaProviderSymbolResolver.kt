package io.github.ra1nify.realos.xposed.hooks.mediaprovider

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.DexKitRunner
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import org.luckypray.dexkit.result.MethodData
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object MediaProviderSymbolResolver {

    private const val TAG = "RealOS-MediaProviderResolver"
    const val RULE_VERSION = 3
    private const val CACHE_PREFS = "realos_mediaprovider_symbols"

    private const val DATABASE_HELPER =
        "com.android.providers.media.DatabaseHelper"
    private const val PICKER_NOTIFICATION_SENDER =
        "com.android.providers.media.photopicker.v2.PickerNotificationSender"
    private const val PICKER_SYNC_CONTROLLER =
        "com.android.providers.media.photopicker.PickerSyncController"

    fun resolve(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        apkPath: String?
    ): MediaProviderSymbols {
        val fingerprint = buildFingerprint(apkPath)
        val cache = AntiTearingCache(context, CACHE_PREFS, RULE_VERSION, tag = TAG)

        if (fingerprint.isNotBlank()) {
            val cachedPayload = cache.read(fingerprint)
            if (cachedPayload != null) {
                val cached = runCatching {
                    MediaProviderSymbols.fromCachePayload(cachedPayload)
                }.getOrNull()
                if (cached != null && verifyCached(cached, classLoader)) {
                    module.log(Log.INFO, TAG, "Symbol cache hit (MediaProvider, fingerprint=$fingerprint)")
                    return cached
                }
                module.log(Log.WARN, TAG, "Symbol cache verification failed, falling back to full scan")
            }
        }

        module.log(Log.INFO, TAG, "Cache miss; performing symbol scan for MediaProvider")
        val symbols = scan(module, classLoader, apkPath, fingerprint)

        if (fingerprint.isNotBlank() && symbols.isFullyResolved) {
            if (cache.write(symbols.toCachePayload(fingerprint))) {
                module.log(Log.INFO, TAG, "Persisted MediaProvider symbols to cache (ruleVersion=$RULE_VERSION)")
            }
        }

        return symbols
    }

    private fun verifyCached(symbols: MediaProviderSymbols, classLoader: ClassLoader): Boolean {
        if (!symbols.isFullyResolved) return false
        if (symbols.databaseHelperClass?.restore(classLoader) == null) return false
        if (symbols.pickerNotificationSenderClass?.restore(classLoader) == null) return false
        if (symbols.notifyInsertMethod?.restore(classLoader) == null) return false
        if (symbols.notifyUpdateMethod?.restore(classLoader) == null) return false
        if (symbols.notifyDeleteMethod?.restore(classLoader) == null) return false
        if (symbols.databaseHelperContextField?.restore(classLoader) == null) return false
        if (symbols.notifyMediaChangeMethod?.restore(classLoader) == null) return false
        if (symbols.notifyMediaDeleteChangeMethod?.restore(classLoader) == null) return false
        if (symbols.pickerSyncControllerClass?.restore(classLoader) == null) return false
        if (symbols.getSyncControllerMethod?.restore(classLoader) == null) return false
        if (symbols.syncAllMediaFromLocalProviderMethod?.restore(classLoader) == null) return false
        return true
    }

    private fun scan(
        module: XposedModule,
        classLoader: ClassLoader,
        apkPath: String?,
        fingerprint: String
    ): MediaProviderSymbols {
        var databaseHelperClass = safeLoadClass(classLoader, DATABASE_HELPER)
        var pickerSenderClass = safeLoadClass(classLoader, PICKER_NOTIFICATION_SENDER)
        var pickerSyncControllerClass = safeLoadClass(classLoader, PICKER_SYNC_CONTROLLER)

        var notifyInsert = findNotifyMethod(databaseHelperClass, "notifyInsert")
        var notifyUpdate = findNotifyMethod(databaseHelperClass, "notifyUpdate")
        var notifyDelete = findNotifyMethod(databaseHelperClass, "notifyDelete")
        var notifyMediaChange = findStaticContextMethod(pickerSenderClass, "notifyMediaChange")
        var notifyMediaDeleteChange = findStaticContextMethod(pickerSenderClass, "notifyMediaDeleteChange")
        var getSyncController = findGetSyncController(pickerSyncControllerClass)
        var syncLocalMedia = findSyncLocalMedia(pickerSyncControllerClass)

        val needsDexKit = databaseHelperClass == null ||
            pickerSenderClass == null ||
            pickerSyncControllerClass == null ||
            notifyInsert == null ||
            notifyUpdate == null ||
            notifyDelete == null ||
            notifyMediaChange == null ||
            notifyMediaDeleteChange == null ||
            getSyncController == null ||
            syncLocalMedia == null

        if (needsDexKit) {
            val located = locateClassesWithDexKit(module, apkPath)
            if (located == null) {
                module.log(Log.WARN, TAG, "DexKit fallback unavailable; keeping direct resolution only")
            } else {
                located.databaseHelper?.let { name ->
                    val candidate = safeLoadClass(classLoader, name)
                    val insert = findNotifyMethod(candidate, "notifyInsert")
                    val update = findNotifyMethod(candidate, "notifyUpdate")
                    val delete = findNotifyMethod(candidate, "notifyDelete")
                    if (candidate != null && insert != null && update != null && delete != null) {
                        databaseHelperClass = candidate
                        notifyInsert = insert
                        notifyUpdate = update
                        notifyDelete = delete
                        module.log(Log.INFO, TAG, "DexKit restored DatabaseHelper: $name")
                    }
                }

                located.pickerSender?.let { name ->
                    val candidate = safeLoadClass(classLoader, name)
                    val mediaChange = findStaticContextMethod(candidate, "notifyMediaChange")
                    val deleteChange = findStaticContextMethod(candidate, "notifyMediaDeleteChange")
                    if (candidate != null && mediaChange != null && deleteChange != null) {
                        pickerSenderClass = candidate
                        notifyMediaChange = mediaChange
                        notifyMediaDeleteChange = deleteChange
                        module.log(Log.INFO, TAG, "DexKit restored PickerNotificationSender: $name")
                    }
                }

                located.pickerSyncController?.let { name ->
                    val candidate = safeLoadClass(classLoader, name)
                    val controller = findGetSyncController(candidate)
                    val sync = findSyncLocalMedia(candidate)
                    if (candidate != null && controller != null && sync != null) {
                        pickerSyncControllerClass = candidate
                        getSyncController = controller
                        syncLocalMedia = sync
                        module.log(Log.INFO, TAG, "DexKit restored PickerSyncController: $name")
                    }
                }
            }
        }

        val contextFields = databaseHelperClass?.declaredFields?.filter {
            it.type == Context::class.java
        }.orEmpty()
        val contextField = contextFields.singleOrNull()

        val states = mutableMapOf<String, HookPointState>()
        val scanErrors = mutableListOf<String>()

        fun logState(key: String, resolved: Boolean, detail: String = "") {
            val state = if (resolved) HookPointState.FOUND else HookPointState.MISSING
            states[key] = state
            val msg = "HookPoint[$key] state=$state${if (detail.isNotEmpty()) " detail=$detail" else ""}"
            if (resolved) {
                module.log(Log.INFO, TAG, msg)
            } else {
                module.log(Log.WARN, TAG, msg)
            }
        }

        logState(MediaProviderSymbols.KEY_DATABASE_HELPER, databaseHelperClass != null)
        if (databaseHelperClass == null) {
            scanErrors += "Failed to load $DATABASE_HELPER"
        }

        val databaseHelperName = databaseHelperClass?.name ?: DATABASE_HELPER
        logState(MediaProviderSymbols.KEY_NOTIFY_INSERT, notifyInsert != null)
        if (databaseHelperClass != null && notifyInsert == null) {
            scanErrors += "$databaseHelperName#notifyInsert(Uri) not found"
        }

        logState(MediaProviderSymbols.KEY_NOTIFY_UPDATE, notifyUpdate != null)
        if (databaseHelperClass != null && notifyUpdate == null) {
            scanErrors += "$databaseHelperName#notifyUpdate(Uri) not found"
        }

        logState(MediaProviderSymbols.KEY_NOTIFY_DELETE, notifyDelete != null)
        if (databaseHelperClass != null && notifyDelete == null) {
            scanErrors += "$databaseHelperName#notifyDelete(Uri) not found"
        }

        logState(
            MediaProviderSymbols.KEY_CONTEXT_FIELD,
            contextField != null,
            if (contextFields.size > 1) "candidates=${contextFields.map { it.name }}" else ""
        )
        if (databaseHelperClass != null && contextField == null) {
            scanErrors += "$databaseHelperName Context field count=${contextFields.size}"
        }

        logState(MediaProviderSymbols.KEY_PICKER_SENDER, pickerSenderClass != null)
        if (pickerSenderClass == null) {
            scanErrors += "Failed to load $PICKER_NOTIFICATION_SENDER"
        }

        val pickerSenderName = pickerSenderClass?.name ?: PICKER_NOTIFICATION_SENDER
        logState(MediaProviderSymbols.KEY_NOTIFY_MEDIA_CHANGE, notifyMediaChange != null)
        if (pickerSenderClass != null && notifyMediaChange == null) {
            scanErrors += "$pickerSenderName#notifyMediaChange(Context) not found"
        }

        logState(MediaProviderSymbols.KEY_NOTIFY_MEDIA_DELETE, notifyMediaDeleteChange != null)
        if (pickerSenderClass != null && notifyMediaDeleteChange == null) {
            scanErrors += "$pickerSenderName#notifyMediaDeleteChange(Context) not found"
        }

        logState(MediaProviderSymbols.KEY_PICKER_SYNC_CONTROLLER, pickerSyncControllerClass != null)
        if (pickerSyncControllerClass == null) {
            scanErrors += "Failed to load $PICKER_SYNC_CONTROLLER"
        }

        val pickerSyncControllerName = pickerSyncControllerClass?.name ?: PICKER_SYNC_CONTROLLER
        logState(MediaProviderSymbols.KEY_GET_SYNC_CONTROLLER, getSyncController != null)
        if (pickerSyncControllerClass != null && getSyncController == null) {
            scanErrors += "$pickerSyncControllerName#getInstanceOrThrow() not found"
        }

        logState(MediaProviderSymbols.KEY_SYNC_LOCAL_MEDIA, syncLocalMedia != null)
        if (pickerSyncControllerClass != null && syncLocalMedia == null) {
            scanErrors += "$pickerSyncControllerName#syncAllMediaFromLocalProvider(CancellationSignal) not found"
        }

        return MediaProviderSymbols(
            ruleVersion = RULE_VERSION,
            hostFingerprint = fingerprint,
            databaseHelperClass = databaseHelperClass?.let { ClassDescriptor(it.name) },
            notifyInsertMethod = notifyInsert?.let { MethodDescriptor.of(it) },
            notifyUpdateMethod = notifyUpdate?.let { MethodDescriptor.of(it) },
            notifyDeleteMethod = notifyDelete?.let { MethodDescriptor.of(it) },
            databaseHelperContextField = contextField?.let { FieldDescriptor.of(it) },
            pickerNotificationSenderClass = pickerSenderClass?.let { ClassDescriptor(it.name) },
            notifyMediaChangeMethod = notifyMediaChange?.let { MethodDescriptor.of(it) },
            notifyMediaDeleteChangeMethod = notifyMediaDeleteChange?.let { MethodDescriptor.of(it) },
            pickerSyncControllerClass = pickerSyncControllerClass?.let { ClassDescriptor(it.name) },
            getSyncControllerMethod = getSyncController?.let { MethodDescriptor.of(it) },
            syncAllMediaFromLocalProviderMethod = syncLocalMedia?.let { MethodDescriptor.of(it) },
            statusMap = states,
            scanErrors = scanErrors
        )
    }

    // ── DexKit class-name fallback ─────────────────────────────────────────────
    //
    // MediaProvider is not obfuscated, but ColorOS can move the picker classes
    // between releases (for example `.photopicker` and `.photopicker.v2`). The
    // direct names stay the first choice; this fallback only runs when a class or
    // method is missing, and locates the declaring class by stable AOSP names and
    // signatures. Renamed methods fail closed as before.

    private data class LocatedClasses(
        val databaseHelper: String?,
        val pickerSender: String?,
        val pickerSyncController: String?
    )

    private fun locateClassesWithDexKit(module: XposedModule, apkPath: String?): LocatedClasses? {
        if (apkPath.isNullOrBlank()) return null
        return DexKitRunner.withBridge(module, apkPath) { bridge ->
            fun uniqueClass(lists: List<List<MethodData>>): String? {
                if (lists.any { it.isEmpty() }) return null
                val common = lists.map { list -> list.map { it.declaredClassName }.toSet() }
                    .reduce { acc, set -> acc intersect set }
                return common.singleOrNull()
            }

            val databaseHelper = uniqueClass(
                listOf(
                    bridge.findMethod {
                        matcher {
                            name("notifyInsert")
                            paramTypes("android.net.Uri")
                        }
                    }.filter { !Modifier.isStatic(it.modifiers) && it.returnsVoid() },
                    bridge.findMethod {
                        matcher {
                            name("notifyUpdate")
                            paramTypes("android.net.Uri")
                        }
                    }.filter { !Modifier.isStatic(it.modifiers) && it.returnsVoid() },
                    bridge.findMethod {
                        matcher {
                            name("notifyDelete")
                            paramTypes("android.net.Uri")
                        }
                    }.filter { !Modifier.isStatic(it.modifiers) && it.returnsVoid() }
                )
            )

            val pickerSender = uniqueClass(
                listOf(
                    bridge.findMethod {
                        matcher {
                            name("notifyMediaChange")
                            paramTypes("android.content.Context")
                        }
                    }.filter { Modifier.isStatic(it.modifiers) && it.returnsVoid() },
                    bridge.findMethod {
                        matcher {
                            name("notifyMediaDeleteChange")
                            paramTypes("android.content.Context")
                        }
                    }.filter { Modifier.isStatic(it.modifiers) && it.returnsVoid() }
                )
            )

            val pickerSyncController = uniqueClass(
                listOf(
                    bridge.findMethod {
                        matcher {
                            name("syncAllMediaFromLocalProvider")
                            paramTypes("android.os.CancellationSignal")
                        }
                    }.filter { !Modifier.isStatic(it.modifiers) && it.returnsVoid() }
                )
            )

            LocatedClasses(databaseHelper, pickerSender, pickerSyncController)
        }?.also { located ->
            module.log(
                Log.INFO,
                TAG,
                "DexKit fallback result: db=${located.databaseHelper ?: "none"}, " +
                    "sender=${located.pickerSender ?: "none"}, " +
                    "sync=${located.pickerSyncController ?: "none"}"
            )
        }
    }

    private fun MethodData.returnsVoid(): Boolean =
        returnTypeName == "void" || returnTypeName == "V"

    // ── Reflection helpers ─────────────────────────────────────────────────────

    private fun safeLoadClass(classLoader: ClassLoader, name: String): Class<*>? =
        runCatching { classLoader.loadClass(name) }.getOrNull()

    private fun findNotifyMethod(clazz: Class<*>?, name: String): Method? = clazz?.declaredMethods?.firstOrNull {
        it.name == name &&
            !Modifier.isStatic(it.modifiers) &&
            it.returnType == Void.TYPE &&
            it.parameterTypes.size == 1 &&
            it.parameterTypes[0] == Uri::class.java
    }

    private fun findStaticContextMethod(clazz: Class<*>?, name: String): Method? = clazz?.declaredMethods?.firstOrNull {
        it.name == name &&
            Modifier.isStatic(it.modifiers) &&
            it.returnType == Void.TYPE &&
            it.parameterTypes.size == 1 &&
            it.parameterTypes[0] == Context::class.java
    }

    private fun findGetSyncController(clazz: Class<*>?): Method? = clazz?.declaredMethods?.firstOrNull {
        it.name == "getInstanceOrThrow" &&
            Modifier.isStatic(it.modifiers) &&
            it.parameterTypes.isEmpty() &&
            it.returnType == clazz
    }

    private fun findSyncLocalMedia(clazz: Class<*>?): Method? = clazz?.declaredMethods?.firstOrNull {
        it.name == "syncAllMediaFromLocalProvider" &&
            !Modifier.isStatic(it.modifiers) &&
            it.returnType == Void.TYPE &&
            it.parameterTypes.size == 1 &&
            it.parameterTypes[0] == CancellationSignal::class.java
    }

    private fun buildFingerprint(apkPath: String?): String {
        if (apkPath.isNullOrBlank()) return ""
        val file = File(apkPath)
        return if (file.exists()) {
            "${file.absolutePath}|${file.lastModified()}|${file.length()}"
        } else {
            apkPath
        }
    }
}
