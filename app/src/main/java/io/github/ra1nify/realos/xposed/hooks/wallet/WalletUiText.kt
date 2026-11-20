package io.github.ra1nify.realos.xposed.hooks.wallet

import android.content.Context
import android.content.res.Resources
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.util.ModuleResources

/**
 * Central text entry for wallet sub-pages (in-host UI).
 *
 * These pages run in the wallet process. Text comes from module resource IDs resolved through
 * the host Resources, with a read-only English fallback map for missing entries.
 */
object WalletUiText {

    const val CUSTOM_PAGE_TITLE = "wallet_custom_page_title"
    const val CUSTOM_PAGE_SAVE = "wallet_custom_page_save"
    const val CUSTOM_PAGE_COVER_LABEL = "wallet_custom_page_cover_label"
    const val CUSTOM_PAGE_COVER_ADD = "wallet_custom_page_cover_add"
    const val CUSTOM_PAGE_COVER_DELETE_TITLE = "wallet_custom_page_cover_delete_title"
    const val CUSTOM_PAGE_COVER_DELETE_MESSAGE = "wallet_custom_page_cover_delete_message"
    const val CUSTOM_PAGE_STOCK = "wallet_stock_card_name"

    const val CROP_TITLE = "wallet_crop_title"
    const val CROP_CONFIRM = "wallet_crop_confirm"
    const val CROP_SAVING = "wallet_crop_saving"
    const val CROP_CANCEL = "wallet_crop_cancel"
    const val CROP_FAILED = "wallet_crop_failed"
    const val CROP_SAVE_FAILED = "wallet_crop_save_failed"

    const val TOAST_SAVE_SUCCESS = "wallet_toast_save_success"
    const val TOAST_NAME_UPDATED = "wallet_toast_name_updated"
    const val TOAST_COVER_READY = "wallet_toast_cover_ready"
    const val TOAST_COVER_DELETED = "wallet_toast_cover_deleted"
    const val TOAST_UNAVAILABLE = "wallet_toast_unavailable"
    const val TOAST_PICKER_FAILED = "wallet_toast_picker_failed"
    const val TOAST_THEME_SAVED = "wallet_toast_theme_saved"

    const val CUSTOM_PAGE_COVER_LONG_PRESS_HINT = "wallet_custom_page_cover_long_press_hint"

    const val DIALOG_TITLE = "wallet_dialog_title"
    const val DIALOG_HIDE_NAME = "wallet_dialog_hide_name"
    const val DIALOG_NAME_HINT = "wallet_dialog_name_hint"
    const val DIALOG_SAVE_NAME = "wallet_dialog_save_name"
    const val DIALOG_CHANGE_COVER = "wallet_dialog_change_cover"
    const val DIALOG_CONFIRM_DELETE = "wallet_dialog_confirm_delete"
    const val DIALOG_CANCEL = "wallet_dialog_cancel"

    private val fallback = mapOf(
        CUSTOM_PAGE_TITLE to "Card Customization",
        CUSTOM_PAGE_SAVE to "Save",
        CUSTOM_PAGE_COVER_LABEL to "Card Cover",
        CUSTOM_PAGE_COVER_ADD to "Add",
        CUSTOM_PAGE_COVER_DELETE_TITLE to "Delete cover",
        CUSTOM_PAGE_COVER_DELETE_MESSAGE to "The image will be removed from the local pool.",
        CUSTOM_PAGE_COVER_LONG_PRESS_HINT to " (long press to delete a custom cover)",
        CUSTOM_PAGE_STOCK to "Official Stock Cover",
        CROP_TITLE to "Crop Cover",
        CROP_CONFIRM to "Done",
        CROP_SAVING to "Saving...",
        CROP_CANCEL to "Cancel",
        CROP_FAILED to "Crop failed",
        CROP_SAVE_FAILED to "Failed to save image",
        TOAST_SAVE_SUCCESS to "Saved",
        TOAST_NAME_UPDATED to "Card name updated",
        TOAST_COVER_READY to "Cover ready",
        TOAST_COVER_DELETED to "Cover deleted",
        TOAST_UNAVAILABLE to "Feature unavailable on this version",
        TOAST_PICKER_FAILED to "Cannot open image picker",
        TOAST_THEME_SAVED to "Cover and name updated",
        DIALOG_TITLE to "Card Settings",
        DIALOG_HIDE_NAME to "Hide card name and icon",
        DIALOG_NAME_HINT to "Custom card name",
        DIALOG_SAVE_NAME to "Save name",
        DIALOG_CHANGE_COVER to "Change cover",
        DIALOG_CONFIRM_DELETE to "Delete",
        DIALOG_CANCEL to "Cancel"
    )

    private val idCache = HashMap<String, Int>()

    private fun idOf(name: String): Int {
        idCache[name]?.let { return it }
        val id = try {
            R.string::class.java.getField(name).getInt(null)
        } catch (_: Throwable) {
            0
        }
        idCache[name] = id
        return id
    }

    /**
     * Resolves display text.
     *
     * @param resources caller Resources (host Resources in the host process; pairs with the
     *   module resource wrapper in [WalletCardCustomPage])
     */
    fun t(resources: Resources?, resourcesName: String): String {
        val id = idOf(resourcesName)
        if (id != 0 && resources != null) {
            runCatching { return resources.getString(id) }
        }
        return fallback[resourcesName] ?: resourcesName
    }

    fun t(context: Context, name: String): String = t(context.resources, name)

    /**
     * Rewritten menu item title on the transit card detail page.
     *
     * Host and module share package id 0x7f, so a module resource id must never be handed to
     * the host: the host would resolve it against its own table. The title is resolved to a
     * String through [ModuleResources] first, and only the String reaches the host menu data.
     */
    fun cardCustomMenuTitle(context: Context): String =
        ModuleResources.string(context, R.string.wallet_card_custom_menu_title)
}
