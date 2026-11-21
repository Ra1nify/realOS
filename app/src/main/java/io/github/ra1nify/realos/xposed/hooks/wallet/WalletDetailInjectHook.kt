package io.github.ra1nify.realos.xposed.hooks.wallet

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.wallet.WalletConfig
import java.lang.ref.WeakReference
import java.util.Collections

/**
 * Detail page control block injection, unlock, and route takeover hooks:
 * 1. Transit card (BusDetailActivity and CardDetailRsp):
 *    - Unlocks the card face theme count (>=2) for the native "card cover" menu item;
 *    - Returns custom cover and custom name, and records the stock cover;
 *    - Redirects /nfc/cardshow/choose to the full /entrance/sceneTheme route on "card cover" tap;
 * 2. Access card (DoorDetailFragment): removes third-party physical/encrypted card limits and fills the stock cover;
 * 3. Tap-to-pay page (DoorCardPage): removes the third-party card scene prompt interception.
 */
object WalletDetailInjectHook {

    private const val TAG = "RealOS-WalletDetailInjectHook"

    @Volatile
    private var isInstalled = false

    private val transitCardAids = Collections.synchronizedSet(HashSet<String>())
    private val transitCardNames = Collections.synchronizedMap(HashMap<String, String>())

    /**
     * Active transit card detail page contexts.
     *
     * Binding is per Activity. Host API rewrites apply only while a transit card detail
     * page is on the stack; other cards keep their own data.
     */
    private val activeTransitActivities = Collections.synchronizedList(ArrayList<WeakReference<Activity>>())

    fun isTransitCard(aid: String): Boolean {
        if (aid.isEmpty()) return false
        return transitCardAids.contains(aid)
    }

    fun getTransitCardName(aid: String): String? {
        if (aid.isEmpty()) return null
        return transitCardNames[aid]
    }

    /**
     * Resolves a registered transit card AID by card name.
     *
     * The card face View carries no AID; `showCardImg(url, name, ...)` / `setCardIconAndName(...)`
     * pass only the name. The lookup uses the unique "name -> AID" map; duplicate names return null.
     */
    fun findAidByCardName(cardName: String): String? {
        if (cardName.isEmpty()) return null
        synchronized(transitCardNames) {
            val matched = transitCardNames.entries.filter { it.value == cardName }.map { it.key }
            return if (matched.size == 1) matched.first() else null
        }
    }

    fun registerTransitAid(aid: String) {
        if (aid.isNotEmpty()) transitCardAids.add(aid)
    }

    fun registerTransitName(aid: String, name: String) {
        if (aid.isNotEmpty() && name.isNotEmpty()) transitCardNames[aid] = name
    }


    private fun trackTransitActivity(activity: Activity) {
        val ref = WeakReference(activity)
        synchronized(activeTransitActivities) {
            activeTransitActivities.add(ref)
        }
        WalletRuntimeContext.recordActivity(activity)
    }

    /**
     * Current usable Context in the host process.
     *
     * Scans active transit card detail Activities (removing finishing/destroyed references),
     * then falls back to [WalletRuntimeContext.current].
     *
     * Read-side hooks such as [WalletCardDataHook] share this resolution; the runtime
     * context alone is null early in the detail page.
     */
    fun getContext(): Context? {
        val iterator = activeTransitActivities.listIterator()
        while (iterator.hasNext()) {
            val activity = iterator.next().get()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                iterator.remove()
            } else {
                return activity
            }
        }
        return WalletRuntimeContext.current
    }

    fun install(module: XposedModule, symbols: WalletSymbols) {
        if (isInstalled) return

        module.log(Log.INFO, TAG, "Installing WalletDetailInjectHook...")

        try {
            // 1. Hooks CardDetailRsp: unlocks the transit card face theme count
            //    Applies only to transit card AIDs; access card and bank card counts stay unchanged.
            val getCountThemesMethod = symbols.cardDetailGetCountThemesMethod
            if (getCountThemesMethod != null) {
                module.hook(getCountThemesMethod).intercept { chain ->
                    val origin = chain.proceed()
                    val originCount = (origin as? Number)?.toInt() ?: 0
                    val aid = readAid(chain.thisObject, symbols)
                    if (aid.isNotEmpty() && isTransitCard(aid)) maxOf(originCount, 2) else originCount
                }
            }

            // WalletCardDataHook owns the CardDetailRsp#getCardImg redirect and the AID registration.
            // A Method carries one hook; a second hook makes the result chain-order dependent.
            // This class keeps only getCardName, used by the detail page menu/name path.

            // Hook CardDetailRsp#getCardName: returns the custom card name
            val getCardNameMethod = symbols.cardDetailGetCardNameMethod
            if (getCardNameMethod != null) {
                module.hook(getCardNameMethod).intercept { chain ->
                    val origin = chain.proceed() as? String
                    val aid = readAid(chain.thisObject, symbols)
                    if (aid.isEmpty()) return@intercept origin
                    registerTransitAid(aid)
                    registerTransitName(aid, origin.orEmpty())

                    val ctx = getContext() ?: return@intercept origin
                    WalletConfig.overrides(ctx).customName(aid) ?: origin
                }
            }

            // 2. Route builder: logs only, no rewrite
            val routerVMethod = symbols.routerBuilderVMethod
            if (routerVMethod != null) {
                module.hook(routerVMethod).intercept { chain ->
                    val path = chain.args.getOrNull(0) as? String
                    if (path == "/nfc/cardshow/choose") {
                        module.log(Log.INFO, TAG, "Proceeding with native router path /nfc/cardshow/choose")
                    }
                    chain.proceed()
                }
            }

            // 3. Transit card native cover page (BusCardThemeChooseActivity and /nfc/cardshow/choose)
            val busThemeClass = symbols.busCardThemeChooseActivityClass
            if (busThemeClass != null) {
                // 3.1 Short-circuits the cloud network call c()
                val cMethod = symbols.busCardThemeCMethod ?: busThemeClass.declaredMethods.firstOrNull {
                    it.name == "c" && it.parameterTypes.isEmpty()
                }
                if (cMethod != null) {
                    module.hook(cMethod).intercept { _ ->
                        module.log(Log.INFO, TAG, "Short-circuited BusCardThemeChooseActivity.c() to prevent cloud network call")
                        null
                    }
                }

                // 3.2 Takes over rendering with the full-screen custom page
                val initViewMethod = symbols.busCardThemeInitViewMethod ?: busThemeClass.declaredMethods.firstOrNull {
                    it.name == "initView" && it.parameterTypes.isEmpty()
                }
                if (initViewMethod != null) {
                    module.hook(initViewMethod).intercept { chain ->
                        val result = chain.proceed()
                        val activity = chain.thisObject as? Activity
                        if (activity != null) {
                            WalletRuntimeContext.recordActivity(activity)
                            val intentAid = activity.intent?.getStringExtra("aid").orEmpty()
                            val aid = intentAid.ifEmpty { (activity.intent?.getStringExtra("KEY_CARD_AID")).orEmpty() }
                            WalletCardCustomPage.setupPage(activity, aid, symbols)
                        }
                        result
                    }
                }

                // 3.3 Hooks onActivityResult for the SAF picker result
                val onActivityResultMethod = busThemeClass.declaredMethods.firstOrNull {
                    it.name == "onActivityResult" && it.parameterTypes.size == 3
                } ?: busThemeClass.methods.firstOrNull {
                    it.name == "onActivityResult" && it.parameterTypes.size == 3
                }
                if (onActivityResultMethod != null) {
                    module.hook(onActivityResultMethod).intercept { chain ->
                        val requestCode = (chain.args.getOrNull(0) as? Number)?.toInt() ?: 0
                        val resultCode = (chain.args.getOrNull(1) as? Number)?.toInt() ?: 0
                        val intent = chain.args.getOrNull(2) as? Intent
                        val activity = chain.thisObject as? Activity

                        if (activity != null && requestCode == WalletCardCustomPage.REQUEST_CODE_PICK_IMAGE && resultCode == Activity.RESULT_OK && intent?.data != null) {
                            WalletCardCustomPage.handlePickImageResult(activity, intent.data!!)
                            return@intercept null
                        }
                        chain.proceed()
                    }
                }

                // 3.4 Hooks onBackPressed: closes the crop overlay when it is showing
                val onBackPressedMethod = busThemeClass.declaredMethods.firstOrNull {
                    it.name == "onBackPressed" && it.parameterTypes.isEmpty()
                } ?: busThemeClass.methods.firstOrNull {
                    it.name == "onBackPressed" && it.parameterTypes.isEmpty()
                }
                if (onBackPressedMethod != null) {
                    module.hook(onBackPressedMethod).intercept { chain ->
                        val activity = chain.thisObject as? Activity
                        if (activity != null && WalletCardCustomPage.isCropOverlayShowing()) {
                            WalletCardCustomPage.dismissCardCropOverlay(activity)
                            return@intercept null
                        }
                        chain.proceed()
                    }
                }
            }

            // 4. Transit card detail page (BusDetailActivity) onResume
            val busActivityClass = symbols.busDetailActivityClass
            if (busActivityClass != null) {
                val onResumeMethod = busActivityClass.methods.firstOrNull {
                    it.name == "onResume" && it.parameterTypes.isEmpty()
                }
                if (onResumeMethod != null) {
                    module.hook(onResumeMethod).intercept { chain ->
                        val result = chain.proceed()
                        val activity = chain.thisObject as? Activity
                        if (activity != null) {
                            trackTransitActivity(activity)
                            handleBusDetailResume(activity, symbols)
                        }
                        result
                    }
                }
            }

            // 5. Access card detail page (DoorDetailFragment)
            val doorFragmentClass = symbols.doorDetailFragmentClass
            if (doorFragmentClass != null) {
                // 5.1 Removes the "change cover" entry limit for third-party physical/encrypted cards
                val o1Method = symbols.doorDetailO1Method
                if (o1Method != null) {
                    module.hook(o1Method).intercept { chain ->
                        val result = chain.proceed()
                        val fragment = chain.thisObject
                        if (fragment != null) {
                            val viewA = symbols.doorDetailFieldA?.get(fragment) as? View
                            viewA?.visibility = View.VISIBLE
                        }
                        result
                    }
                }
            }

            // 6. Tap-to-pay page (DoorCardPage#isInterceptShowSceneTips)
            val isInterceptMethod = symbols.doorCardPageIsInterceptMethod
            if (isInterceptMethod != null) {
                module.hook(isInterceptMethod).intercept { _ ->
                    false
                }
            }

            isInstalled = true
            module.log(Log.INFO, TAG, "WalletDetailInjectHook installed successfully.")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install WalletDetailInjectHook", t)
        }
    }

    private fun readAid(rsp: Any?, symbols: WalletSymbols): String {
        if (rsp == null) return ""
        return runCatching {
            symbols.cardDetailGetAidMethod?.invoke(rsp) as? String
        }.getOrNull().orEmpty()
    }

    private fun handleBusDetailResume(activity: Activity, symbols: WalletSymbols) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val rsp = findCardDetailRsp(activity, symbols)
        val aid = extractAid(activity, rsp, symbols)
        if (aid.isEmpty()) return
        registerTransitAid(aid)

        // 1. Records the stock cover and card name when not yet recorded
        val stockUrl = if (rsp != null) {
            runCatching { symbols.cardDetailGetCardImgMethod?.invoke(rsp) as? String }.getOrNull().orEmpty()
        } else ""
        if (stockUrl.isNotEmpty() && !WalletConfig.isPoolCoverPath(stockUrl)) {
            WalletConfig.setStockCover(activity, aid, stockUrl)
        }
        val cardName = if (rsp != null) {
            runCatching { symbols.cardDetailGetCardNameMethod?.invoke(rsp) as? String }.getOrNull().orEmpty()
        } else ""
        registerTransitName(aid, cardName)

        // 2. Finds FinBigCardFaceView and applies the custom cover
        val bigCard = findViewByClass(root, symbols.finBigCardFaceViewClass)
        if (bigCard != null) {
            bigCard.setTag(WalletCardFaceHook.VIEW_TAG_CURRENT_AID, aid)
            val currentCardName = WalletConfig.getCustomName(activity, aid) ?: cardName
            bigCard.setTag(WalletCardFaceHook.VIEW_TAG_CURRENT_CARD_NAME, currentCardName)

            bigCard.setOnLongClickListener {
                openCardCustomPage(activity, aid)
                true
            }

            val customCover = WalletCardDataHook.validCustomCover(activity, aid)
            if (!customCover.isNullOrEmpty()) {
                symbols.finBigCardSetUrlMethod?.invoke(bigCard, customCover)
            }
        }

        // 3. Syncs the displayed card name and binds the edit dialog
        updateBusCardNameDisplay(activity, aid, cardName, symbols)

        // 4. Renames menu item 1 to "Card Customization"
        updateBusMenuItemTitle(activity, symbols)
    }

    /**
     * Renames the transit card detail page menu item.
     *
     * The menu container is a List field whose element type has `g()` (int return) and
     * `k(String)`. A non-unique match is dropped.
     */
    private fun updateBusMenuItemTitle(activity: Activity, symbols: WalletSymbols) {
        runCatching {
            val menuList = findMenuListField(activity) ?: return
            val title = WalletUiText.cardCustomMenuTitle(activity)
            for (item in menuList) {
                if (item == null) continue
                val type = runCatching {
                    item.javaClass.methods.firstOrNull { it.name == "g" && it.returnType == Int::class.javaPrimitiveType }
                        ?.invoke(item) as? Int
                }.getOrNull()
                if (type == 1) {
                    item.javaClass.methods.firstOrNull {
                        it.name == "k" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                    }?.invoke(item, title)
                }
            }
            val adapter = activity.javaClass.declaredFields.firstOrNull {
                it.type.name.contains("Adapter") && !it.type.isPrimitive
            }?.apply { isAccessible = true }?.get(activity)
            adapter?.javaClass?.methods?.firstOrNull { it.name == "notifyDataSetChanged" }?.invoke(adapter)
        }.onFailure {
            Log.w(TAG, "updateBusMenuItemTitle failed", it)
        }
    }

    private fun findMenuListField(activity: Activity): List<*>? {
        val candidates = activity.javaClass.declaredFields.filter { List::class.java.isAssignableFrom(it.type) }
        if (candidates.isEmpty()) return null
        val matched = ArrayList<Pair<java.lang.reflect.Field, List<*>>>()
        for (field in candidates) {
            runCatching { field.isAccessible = true }
            val value = runCatching { field.get(activity) as? List<*> }.getOrNull() ?: continue
            val sample = value.firstOrNull() ?: continue
            val hasTypeGetter = sample.javaClass.methods.any {
                it.name == "g" && it.parameterTypes.isEmpty() && it.returnType == Int::class.javaPrimitiveType
            }
            val hasTitleSetter = sample.javaClass.methods.any {
                it.name == "k" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            }
            if (hasTypeGetter && hasTitleSetter) matched.add(field to value)
        }
        // Uniqueness: only a single match is accepted.
        return if (matched.size == 1) matched.first().second else null
    }

    private fun updateBusCardNameDisplay(activity: Activity, aid: String, defaultName: String, symbols: WalletSymbols) {
        val res = activity.resources
        val pkg = activity.packageName

        // Card name TextView: host resource name lookup first, then the unique TextView-typed field
        val tvBusCardName = findUniqueTextViewField(activity)
        val cardNameId = runCatching { res.getIdentifier("card_name", "id", pkg) }.getOrDefault(0)
        val tvCardName = tvBusCardName ?: if (cardNameId != 0) activity.findViewById<TextView>(cardNameId) else null

        val llCardNameId = runCatching { res.getIdentifier("ll_card_name", "id", pkg) }.getOrDefault(0)
        val llCardName = if (llCardNameId != 0) activity.findViewById<View>(llCardNameId) else null

        val isHide = WalletConfig.isHideNameEnabled(activity, aid)
        val customName = WalletConfig.getCustomName(activity, aid)
        val targetName = customName ?: defaultName.ifEmpty { getTransitCardName(aid) ?: "" }

        if (isHide) {
            llCardName?.visibility = View.GONE
            tvCardName?.visibility = View.INVISIBLE
        } else {
            llCardName?.visibility = View.VISIBLE
            if (targetName.isNotEmpty()) {
                tvCardName?.text = targetName
                tvCardName?.visibility = View.VISIBLE
            }
        }

        val clickTarget = llCardName ?: tvCardName
        clickTarget?.setOnClickListener {
            openCardCustomPage(activity, aid)
        }
    }

    /**
     * Unique lookup of the "card name" TextView on the transit card detail page.
     *
     * Filters fields declared as TextView with a non-null runtime value; more than one
     * match is dropped and the resource name path is used.
     */
    private fun findUniqueTextViewField(activity: Activity): TextView? {
        val fields = activity.javaClass.declaredFields.filter { TextView::class.java.isAssignableFrom(it.type) }
        val resolved = fields.mapNotNull { field ->
            runCatching { field.isAccessible = true; field.get(activity) as? TextView }.getOrNull()
        }
        return if (resolved.size == 1) resolved.first() else null
    }

    fun openCardCustomPage(activity: Activity, aid: String) {
        try {
            val curCover = WalletConfig.getCustomCover(activity, aid)
                ?: WalletConfig.getStockCover(activity, aid) ?: ""
            val bundle = Bundle().apply {
                putString("aid", aid)
                putString("imgUrl", curCover)
                putInt("SOURCE", 1)
                putString("com.opos.acs.cmn.Constants.APP_CODE", "com.nearme.wallet")
            }
            val intent = Intent().apply {
                setClassName(activity.packageName, "com.nearme.wallet.bus.ui.BusCardThemeChooseActivity")
                putExtras(bundle)
            }
            activity.startActivity(intent)
            Log.i(TAG, "Opened BusCardThemeChooseActivity for aid=$aid")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open BusCardThemeChooseActivity", t)
        }
    }

    private fun findCardDetailRsp(activity: Activity, symbols: WalletSymbols): Any? {
        val rspClass = symbols.cardDetailRspClass ?: return null
        val field = activity.javaClass.declaredFields.firstOrNull { rspClass.isAssignableFrom(it.type) } ?: return null
        runCatching { field.isAccessible = true }
        return runCatching { field.get(activity) }.getOrNull()
    }

    private fun extractAid(activity: Activity, rsp: Any?, symbols: WalletSymbols): String {
        if (rsp != null) {
            val fromRsp = runCatching { symbols.cardDetailGetAidMethod?.invoke(rsp) as? String }.getOrNull()
            if (!fromRsp.isNullOrEmpty()) return fromRsp
        }
        // Fallback: only a single String field whose value matches the AID hex pattern
        // is accepted (at most one candidate).
        val stringFields = activity.javaClass.declaredFields.filter { it.type == String::class.java }
        val values = stringFields.mapNotNull { field ->
            runCatching { field.isAccessible = true; field.get(activity) as? String }.getOrNull()
        }.filter { AID_PATTERN.matches(it) }
        return if (values.size == 1) values.first() else ""
    }

    private fun findViewByClass(parent: ViewGroup, targetClass: Class<*>?): View? {
        if (targetClass == null) return null
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (targetClass.isInstance(child)) return child
            if (child is ViewGroup) {
                val found = findViewByClass(child, targetClass)
                if (found != null) return found
            }
        }
        return null
    }

    fun reset() {
        isInstalled = false
        activeTransitActivities.clear()
        transitCardAids.clear()
        transitCardNames.clear()
    }

    private val AID_PATTERN = Regex("^[0-9A-Fa-f]{16,40}$")
}
