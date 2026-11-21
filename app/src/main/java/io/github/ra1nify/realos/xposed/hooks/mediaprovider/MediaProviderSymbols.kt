package io.github.ra1nify.realos.xposed.hooks.mediaprovider

import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.ClassSymbol
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldSymbol
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import io.github.ra1nify.realos.xposed.symbol.MethodSymbol
import io.github.ra1nify.realos.xposed.symbol.SymbolDescriptor

data class MediaProviderSymbols(
    val ruleVersion: Int = 1,
    val hostFingerprint: String = "",
    val databaseHelperClass: ClassDescriptor? = null,
    val notifyInsertMethod: MethodDescriptor? = null,
    val notifyUpdateMethod: MethodDescriptor? = null,
    val notifyDeleteMethod: MethodDescriptor? = null,
    val databaseHelperContextField: FieldDescriptor? = null,
    val pickerNotificationSenderClass: ClassDescriptor? = null,
    val notifyMediaChangeMethod: MethodDescriptor? = null,
    val notifyMediaDeleteChangeMethod: MethodDescriptor? = null,
    val pickerSyncControllerClass: ClassDescriptor? = null,
    val getSyncControllerMethod: MethodDescriptor? = null,
    val syncAllMediaFromLocalProviderMethod: MethodDescriptor? = null,
    val statusMap: Map<String, HookPointState> = emptyMap(),
    val scanErrors: List<String> = emptyList()
) {
    val canNotifyChange: Boolean
        get() = databaseHelperClass != null &&
            notifyInsertMethod != null &&
            notifyUpdateMethod != null &&
            databaseHelperContextField != null &&
            pickerNotificationSenderClass != null &&
            notifyMediaChangeMethod != null

    val canNotifyDelete: Boolean
        get() = databaseHelperClass != null &&
            notifyDeleteMethod != null &&
            databaseHelperContextField != null &&
            pickerNotificationSenderClass != null &&
            notifyMediaDeleteChangeMethod != null

    val canTriggerSync: Boolean
        get() = pickerSyncControllerClass != null &&
            getSyncControllerMethod != null &&
            syncAllMediaFromLocalProviderMethod != null

    val isFullyResolved: Boolean
        get() = canNotifyChange && canNotifyDelete && canTriggerSync

    fun toCachePayload(fingerprint: String): AntiTearingCache.CachePayload {
        val map = buildMap<String, SymbolDescriptor> {
            databaseHelperClass?.let { put(KEY_DATABASE_HELPER, ClassSymbol(it)) }
            notifyInsertMethod?.let { put(KEY_NOTIFY_INSERT, MethodSymbol(it)) }
            notifyUpdateMethod?.let { put(KEY_NOTIFY_UPDATE, MethodSymbol(it)) }
            notifyDeleteMethod?.let { put(KEY_NOTIFY_DELETE, MethodSymbol(it)) }
            databaseHelperContextField?.let { put(KEY_CONTEXT_FIELD, FieldSymbol(it)) }
            pickerNotificationSenderClass?.let { put(KEY_PICKER_SENDER, ClassSymbol(it)) }
            notifyMediaChangeMethod?.let { put(KEY_NOTIFY_MEDIA_CHANGE, MethodSymbol(it)) }
            notifyMediaDeleteChangeMethod?.let { put(KEY_NOTIFY_MEDIA_DELETE, MethodSymbol(it)) }
            pickerSyncControllerClass?.let { put(KEY_PICKER_SYNC_CONTROLLER, ClassSymbol(it)) }
            getSyncControllerMethod?.let { put(KEY_GET_SYNC_CONTROLLER, MethodSymbol(it)) }
            syncAllMediaFromLocalProviderMethod?.let { put(KEY_SYNC_LOCAL_MEDIA, MethodSymbol(it)) }
        }
        return AntiTearingCache.CachePayload(fingerprint, ruleVersion, map)
    }

    companion object {
        const val KEY_DATABASE_HELPER = "media_provider_database_helper"
        const val KEY_NOTIFY_INSERT = "media_provider_notify_insert"
        const val KEY_NOTIFY_UPDATE = "media_provider_notify_update"
        const val KEY_NOTIFY_DELETE = "media_provider_notify_delete"
        const val KEY_CONTEXT_FIELD = "media_provider_context_field"
        const val KEY_PICKER_SENDER = "media_provider_picker_sender"
        const val KEY_NOTIFY_MEDIA_CHANGE = "media_provider_notify_media_change"
        const val KEY_NOTIFY_MEDIA_DELETE = "media_provider_notify_media_delete"
        const val KEY_PICKER_SYNC_CONTROLLER = "media_provider_picker_sync_controller"
        const val KEY_GET_SYNC_CONTROLLER = "media_provider_get_sync_controller"
        const val KEY_SYNC_LOCAL_MEDIA = "media_provider_sync_local_media"

        val ALL_HOOK_KEYS: List<String> = listOf(
            KEY_DATABASE_HELPER,
            KEY_NOTIFY_INSERT,
            KEY_NOTIFY_UPDATE,
            KEY_NOTIFY_DELETE,
            KEY_CONTEXT_FIELD,
            KEY_PICKER_SENDER,
            KEY_NOTIFY_MEDIA_CHANGE,
            KEY_NOTIFY_MEDIA_DELETE,
            KEY_PICKER_SYNC_CONTROLLER,
            KEY_GET_SYNC_CONTROLLER,
            KEY_SYNC_LOCAL_MEDIA
        )

        fun fromCachePayload(payload: AntiTearingCache.CachePayload): MediaProviderSymbols {
            val symbols = payload.symbols
            val databaseHelper = (symbols[KEY_DATABASE_HELPER] as? ClassSymbol)?.descriptor
            val notifyInsert = (symbols[KEY_NOTIFY_INSERT] as? MethodSymbol)?.descriptor
            val notifyUpdate = (symbols[KEY_NOTIFY_UPDATE] as? MethodSymbol)?.descriptor
            val notifyDelete = (symbols[KEY_NOTIFY_DELETE] as? MethodSymbol)?.descriptor
            val contextField = (symbols[KEY_CONTEXT_FIELD] as? FieldSymbol)?.descriptor
            val pickerSender = (symbols[KEY_PICKER_SENDER] as? ClassSymbol)?.descriptor
            val notifyMediaChange = (symbols[KEY_NOTIFY_MEDIA_CHANGE] as? MethodSymbol)?.descriptor
            val notifyMediaDelete = (symbols[KEY_NOTIFY_MEDIA_DELETE] as? MethodSymbol)?.descriptor
            val pickerSyncController = (symbols[KEY_PICKER_SYNC_CONTROLLER] as? ClassSymbol)?.descriptor
            val getSyncController = (symbols[KEY_GET_SYNC_CONTROLLER] as? MethodSymbol)?.descriptor
            val syncLocalMedia = (symbols[KEY_SYNC_LOCAL_MEDIA] as? MethodSymbol)?.descriptor

            val statusMap = mutableMapOf<String, HookPointState>()
            statusMap[KEY_DATABASE_HELPER] = stateOf(databaseHelper != null)
            statusMap[KEY_NOTIFY_INSERT] = stateOf(notifyInsert != null)
            statusMap[KEY_NOTIFY_UPDATE] = stateOf(notifyUpdate != null)
            statusMap[KEY_NOTIFY_DELETE] = stateOf(notifyDelete != null)
            statusMap[KEY_CONTEXT_FIELD] = stateOf(contextField != null)
            statusMap[KEY_PICKER_SENDER] = stateOf(pickerSender != null)
            statusMap[KEY_NOTIFY_MEDIA_CHANGE] = stateOf(notifyMediaChange != null)
            statusMap[KEY_NOTIFY_MEDIA_DELETE] = stateOf(notifyMediaDelete != null)
            statusMap[KEY_PICKER_SYNC_CONTROLLER] = stateOf(pickerSyncController != null)
            statusMap[KEY_GET_SYNC_CONTROLLER] = stateOf(getSyncController != null)
            statusMap[KEY_SYNC_LOCAL_MEDIA] = stateOf(syncLocalMedia != null)

            return MediaProviderSymbols(
                ruleVersion = payload.ruleVersion,
                hostFingerprint = payload.fingerprint,
                databaseHelperClass = databaseHelper,
                notifyInsertMethod = notifyInsert,
                notifyUpdateMethod = notifyUpdate,
                notifyDeleteMethod = notifyDelete,
                databaseHelperContextField = contextField,
                pickerNotificationSenderClass = pickerSender,
                notifyMediaChangeMethod = notifyMediaChange,
                notifyMediaDeleteChangeMethod = notifyMediaDelete,
                pickerSyncControllerClass = pickerSyncController,
                getSyncControllerMethod = getSyncController,
                syncAllMediaFromLocalProviderMethod = syncLocalMedia,
                statusMap = statusMap
            )
        }

        private fun stateOf(resolved: Boolean): HookPointState =
            if (resolved) HookPointState.FOUND else HookPointState.MISSING
    }
}
