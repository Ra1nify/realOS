package io.github.ra1nify.realos.xposed.hooks.wallet

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.wallet.WalletConfig

/**
 * Card face hook for FinUINfcCardFaceView:
 * 1. Manages title and logo visibility on the card face;
 * 2. Adds a long-press entry on every card (name visibility switch, rename, cover page).
 *
 * Constraints:
 * - Name hiding applies only to instances with a bound card AID. The AID is looked up
 *   by card name in `showCardImg(url, name, ...)` and completed in `setCardIconAndName`.
 * - `setTitleVisibility` / `setLogoVisibility` also carry **other semantics** in the host
 *   (for example title-less layouts). Rewriting happens only when the instance holds a
 *   card with a hidden name; all other calls pass through.
 */
object WalletCardFaceHook {

    private const val TAG = "RealOS-WalletCardFaceHook"

    @Volatile
    private var isInstalled = false

    fun install(module: XposedModule, symbols: WalletSymbols) {
        if (isInstalled) return
        val faceViewClass = symbols.finUINfcCardFaceViewClass ?: return
        val setTitleMethod = symbols.setTitleVisibilityMethod ?: return
        val setLogoMethod = symbols.setLogoVisibilityMethod ?: return

        module.log(Log.INFO, TAG, "Installing WalletCardFaceHook...")

        try {
            // 1. Hooks showCardImg(url, name, ...): binds the current card AID and attaches the long-press entry
            val showCardImgMethod = symbols.showCardImgMethod
            if (showCardImgMethod != null) {
                module.hook(showCardImgMethod).intercept { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View
                    val cardName = chain.args.getOrNull(1) as? String ?: ""
                    if (view != null) {
                        view.setTag(VIEW_TAG_CURRENT_CARD_NAME, cardName)
                        bindAidTag(view, cardName)
                        attachCardFaceLongClick(view, symbols)
                    }
                    result
                }
            } else {
                module.log(Log.WARN, TAG, "showCardImg unresolved, long-press entry limited to detail page")
            }

            // 2. Hooks setCardIconAndName: main binding entry for access card and widget card faces
            val setIconAndNameMethod = symbols.setCardIconAndNameMethod
            if (setIconAndNameMethod != null) {
                module.hook(setIconAndNameMethod).intercept { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View
                    val cardName = chain.args.getOrNull(1) as? String ?: ""
                    if (view != null) {
                        view.setTag(VIEW_TAG_CURRENT_CARD_NAME, cardName)
                        bindAidTag(view, cardName)
                        attachCardFaceLongClick(view, symbols)
                    }
                    result
                }
            }

            // 3. Hooks setTitleVisibility: hides the card name
            module.hook(setTitleMethod).intercept { chain ->
                val view = chain.thisObject as? View
                if (view != null) {
                    val aid = view.getTag(VIEW_TAG_CURRENT_AID) as? String
                    if (!aid.isNullOrEmpty() && shouldHideName(view.context, aid)) {
                        return@intercept chain.proceed(arrayOf<Any?>(false))
                    }
                }
                chain.proceed()
            }

            // 4. Hooks setLogoVisibility: hides the card logo (same switch as the name)
            module.hook(setLogoMethod).intercept { chain ->
                val view = chain.thisObject as? View
                if (view != null) {
                    val aid = view.getTag(VIEW_TAG_CURRENT_AID) as? String
                    if (!aid.isNullOrEmpty() && shouldHideName(view.context, aid)) {
                        return@intercept chain.proceed(arrayOf<Any?>(false))
                    }
                }
                chain.proceed()
            }

            isInstalled = true
            module.log(Log.INFO, TAG, "WalletCardFaceHook installed successfully.")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install WalletCardFaceHook", t)
        }
    }

    private fun shouldHideName(context: Context, aid: String): Boolean =
        WalletConfig.overrides(context).hideName(aid)

    /**
     * Resolves the AID by card name and writes it into the View tag.
     *
     * The card face View carries no AID; the host associates it through
     * `EntranceCard.aid` / `CardDetailRsp.aid`. This uses the registered
     * "card name -> AID" map filled by the detail page hooks and binds only on a hit.
     */
    private fun bindAidTag(view: View, cardName: String) {
        if (view.getTag(VIEW_TAG_CURRENT_AID) != null) return
        if (cardName.isEmpty()) return
        val aid = WalletDetailInjectHook.findAidByCardName(cardName) ?: return
        view.setTag(VIEW_TAG_CURRENT_AID, aid)
    }

    private fun attachCardFaceLongClick(view: View, symbols: WalletSymbols) {
        if (view.getTag(VIEW_TAG_LONG_CLICK_BOUND) == true) return
        view.setTag(VIEW_TAG_LONG_CLICK_BOUND, true)
        view.setOnLongClickListener { v ->
            showCardManagementDialog(v.context, v, symbols)
            true
        }
    }

    fun isHideNameEnabled(context: Context, aid: String): Boolean = WalletConfig.isHideNameEnabled(context, aid)

    fun setHideNameEnabled(context: Context, aid: String, hide: Boolean) {
        WalletConfig.setHideNameEnabled(context, aid, hide)
    }

    fun showCardManagementDialog(context: Context, cardView: View, symbols: WalletSymbols) {
        val currentAid = cardView.getTag(VIEW_TAG_CURRENT_AID) as? String ?: ""
        val currentName = cardView.getTag(VIEW_TAG_CURRENT_CARD_NAME) as? String ?: ""

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        // 1. Name visibility switch
        val hideSwitch = Switch(context).apply {
            text = WalletUiText.t(context, WalletUiText.DIALOG_HIDE_NAME)
            isChecked = currentAid.isNotEmpty() && WalletConfig.overrides(context).hideName(currentAid)
            setOnCheckedChangeListener { _, isChecked ->
                if (currentAid.isEmpty()) return@setOnCheckedChangeListener
                WalletConfig.setHideNameEnabled(context, currentAid, isChecked)
                // Updates the card face immediately
                symbols.setTitleVisibilityMethod?.invoke(cardView, !isChecked)
                symbols.setLogoVisibilityMethod?.invoke(cardView, !isChecked)
            }
        }
        layout.addView(hideSwitch)

        // 2. Custom card name
        val nameInput = EditText(context).apply {
            hint = WalletUiText.t(context, WalletUiText.DIALOG_NAME_HINT)
            if (currentName.isNotEmpty()) setText(currentName)
        }
        layout.addView(nameInput)

        AlertDialog.Builder(context)
            .setTitle(WalletUiText.t(context, WalletUiText.DIALOG_TITLE))
            .setView(layout)
            .setPositiveButton(WalletUiText.t(context, WalletUiText.DIALOG_SAVE_NAME)) { _, _ ->
                val newName = nameInput.text.toString().trim()
                if (newName.isNotEmpty() && currentAid.isNotEmpty()) {
                    WalletConfig.setCustomName(context, currentAid, newName)
                    WalletCardDataHook.applyNameToCardDetail(context, currentAid, newName, symbols)
                    cardView.setTag(VIEW_TAG_CURRENT_CARD_NAME, newName)
                    Toast.makeText(context, WalletUiText.t(context, WalletUiText.TOAST_NAME_UPDATED), Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(WalletUiText.t(context, WalletUiText.DIALOG_CHANGE_COVER)) { _, _ ->
                val act = context as? Activity
                if (act != null && currentAid.isNotEmpty()) {
                    WalletDetailInjectHook.openCardCustomPage(act, currentAid)
                } else {
                    openSceneTheme(context, currentAid)
                }
            }
            .setNegativeButton(WalletUiText.t(context, WalletUiText.DIALOG_CANCEL), null)
            .show()
    }

    /**
     * Opens the scene theme page through the host router.
     *
     * Uses the router entry class validated by the symbol resolver; an unresolved entry
     * fails closed and shows the unavailable toast.
     */
    fun openSceneTheme(context: Context, aid: String) {
        if (aid.isEmpty()) return
        val routerClass = WalletSymbolResolverEntry.routerEntryClass() ?: run {
            Log.w(TAG, "openSceneTheme skipped: router entry unresolved")
            Toast.makeText(context, WalletUiText.t(context, WalletUiText.TOAST_UNAVAILABLE), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            // Structural discovery only: the host route entry is `static (Context, String, Bundle, [int])`.
            // The method name is not fixed (R8 renames it); the candidate must be **unique**.
            val qMethod = routerClass.methods.filter { m ->
                java.lang.reflect.Modifier.isStatic(m.modifiers) &&
                    m.parameterTypes.size >= 3 &&
                    m.parameterTypes[0] == Context::class.java &&
                    m.parameterTypes[1] == String::class.java &&
                    m.parameterTypes[2] == android.os.Bundle::class.java
            }.singleOrNull() ?: run {
                Log.w(TAG, "openSceneTheme skipped: router method unresolved (candidates not unique)")
                return
            }
            val currentName = WalletConfig.getCustomName(context, aid) ?: ""
            val currentCover = WalletConfig.getCustomCover(context, aid) ?: ""
            val args = arrayOfNulls<Any?>(qMethod.parameterTypes.size)
            args[0] = context
            args[1] = "/entrance/theme"
            args[2] = android.os.Bundle().apply {
                putString("KEY_CARD_AID", aid)
                putString("aid", aid)
                putString("KEY_APP_CODE", "com.nearme.wallet")
                putString("appCode", "com.nearme.wallet")
                putString("CARD_TYPE", "3")
                putString("cardType", "3")
                putString("CARD_NAME", currentName)
                putString("KEY_CARD_NAME", currentName)
                putInt("SOURCE", 1)
                putString("imgUrl", currentCover)
                putString("cardImg", currentCover)
                putString("from", "CardManagementDialog")
            }
            if (qMethod.parameterTypes.size > 3 && qMethod.parameterTypes[3] == Int::class.javaPrimitiveType) {
                args[3] = 800
            }
            qMethod.invoke(null, *args)
        }.onFailure {
            Log.w(TAG, "openSceneTheme failed for aid=$aid", it)
            Toast.makeText(context, WalletUiText.t(context, WalletUiText.TOAST_UNAVAILABLE), Toast.LENGTH_SHORT).show()
        }
    }

    fun reset() {
        isInstalled = false
    }

    const val VIEW_TAG_CURRENT_AID: Int = 0x7e000001
    const val VIEW_TAG_CURRENT_CARD_NAME: Int = 0x7e000002
    private const val VIEW_TAG_LONG_CLICK_BOUND: Int = 0x7e000003
}

/**
 * Bridge that exposes the host route entry class to [WalletCardFaceHook].
 *
 * The route entry method (`com.nearme.router.a`) has no stable name across versions.
 * WalletSpec registers the class after symbol resolution; without a registration it fails closed.
 */
object WalletSymbolResolverEntry {

    private const val TAG = "RealOS-WalletRouterEntry"

    @Volatile
    private var routerClass: Class<*>? = null

    fun register(routerClass: Class<*>?) {
        this.routerClass = routerClass
        Log.i(TAG, "Router entry registered: ${routerClass?.name ?: "none"}")
    }

    fun routerEntryClass(): Class<*>? = routerClass
}
