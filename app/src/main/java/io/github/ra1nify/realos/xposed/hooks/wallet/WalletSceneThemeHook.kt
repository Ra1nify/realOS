package io.github.ra1nify.realos.xposed.hooks.wallet

import android.app.Activity
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.wallet.WalletConfig
import java.lang.ref.WeakReference

/**
 * Card face and scene theme hooks (EntranceSceneViewModel and EntranceSceneThemeActivity):
 * 1. Injects the stock cover as the first candidate in the "other scenes" list;
 * 2. Removes the per-scene 4-cover truncation and shows every cover;
 * 3. Pre-fills the "card name" input with the existing card name;
 * 4. Intercepts transit card save requests, stores cover and name locally, then closes the page.
 *
 * Save writes card data only through [WalletCardDataHook]; this page adds no second
 * database write for the same row.
 */
object WalletSceneThemeHook {

    private const val TAG = "RealOS-WalletSceneThemeHook"
    private const val STOCK_THEME_ID = -99999L

    @Volatile
    private var isInstalled = false

    private var activeThemeActivity: WeakReference<Activity>? = null
    private var activeCardThemeActivity: WeakReference<Activity>? = null

    private fun getCurrentThemeActivity(): Activity? {
        activeThemeActivity?.get()?.let { return it }
        return findActivityByName("EntranceSceneThemeActivity")
    }

    private fun getCurrentCardThemeActivity(): Activity? {
        activeCardThemeActivity?.get()?.let { return it }
        return findActivityByName("CardThemeActivity")
    }

    @Suppress("PrivateAPI")
    private fun findActivityByName(namePart: String): Activity? = runCatching {
        val atClass = Class.forName("android.app.ActivityThread")
        val at = atClass.getMethod("currentActivityThread").invoke(null)
        val mActivities = atClass.getDeclaredField("mActivities").apply { isAccessible = true }
            .get(at) as? Map<*, *>
        mActivities?.values?.mapNotNull { record ->
            record?.javaClass?.getDeclaredField("activity")?.apply { isAccessible = true }
                ?.get(record) as? Activity
        }?.firstOrNull { it.javaClass.name.contains(namePart) }
    }.getOrNull()

    private fun resolveContext(): android.content.Context? =
        getCurrentThemeActivity() ?: getCurrentCardThemeActivity() ?: WalletRuntimeContext.current

    fun install(module: XposedModule, symbols: WalletSymbols) {
        if (isInstalled) return

        module.log(Log.INFO, TAG, "Installing WalletSceneThemeHook...")

        try {
            // A. Access card native cover page (CardThemeActivity and /entrance/theme)
            val cardThemeActivityClass = symbols.cardThemeActivityClass
            if (cardThemeActivityClass != null) {
                val onCreateMethod = cardThemeActivityClass.declaredMethods.firstOrNull {
                    it.name == "onCreate" && it.parameterTypes.size == 1 &&
                            android.os.Bundle::class.java.isAssignableFrom(it.parameterTypes[0])
                }
                if (onCreateMethod != null) {
                    onCreateMethod.isAccessible = true
                    module.hook(onCreateMethod).intercept { chain ->
                        val act = chain.thisObject as? Activity
                        if (act != null) {
                            activeCardThemeActivity = WeakReference(act)
                            WalletRuntimeContext.recordActivity(act)
                        }
                        chain.proceed()
                    }
                }
            }

            // Hook h.a: clears the aid for transit card queries and queries as a normal emulated access card
            val cardThemeHMethodA = symbols.cardThemeHMethodA
            if (cardThemeHMethodA != null) {
                module.hook(cardThemeHMethodA).intercept { chain ->
                    val aid = chain.args.getOrNull(3) as? String ?: ""
                    if (WalletDetailInjectHook.isTransitCard(aid)) {
                        module.log(Log.INFO, TAG, "Querying CardTheme as normal nfc emulate card for transit aid=$aid")
                        val newArgs = chain.args.toTypedArray()
                        newArgs[3] = ""
                        newArgs[2] = "3"
                        return@intercept chain.proceed(newArgs)
                    }
                    chain.proceed()
                }
            }

            // Hook CardThemeActivity.d4: short-circuits the cloud request on transit card cover save
            val cardThemeD4Method = symbols.cardThemeD4Method
            if (cardThemeD4Method != null) {
                module.hook(cardThemeD4Method).intercept { chain ->
                    val activity = chain.thisObject as? Activity ?: getCurrentCardThemeActivity()
                    val aid = extractAidFromCardThemeActivity(activity)
                    if (activity != null && WalletDetailInjectHook.isTransitCard(aid)) {
                        module.log(Log.INFO, TAG, "Intercepted CardThemeActivity.d4 for transit card aid=$aid")
                        val selectedTheme = symbols.cardThemeSelectedField?.get(activity)
                        val selectedImg = if (selectedTheme != null) {
                            runCatching { symbols.cardThemeGetCardImgMethod?.invoke(selectedTheme) as? String }
                                .getOrNull().orEmpty()
                        } else ""
                        if (selectedImg.isNotEmpty()) {
                            WalletConfig.setCustomCover(activity, aid, selectedImg)
                            WalletCardDataHook.invalidateCoverCache(aid)
                            WalletCardDataHook.applyCoverToCardDetail(activity, aid, selectedImg, symbols)
                        }
                        finishActivity(activity, symbols, 801)
                        return@intercept null
                    }
                    chain.proceed()
                }
            }

            // B. Access card native name edit page (EditCardNameActivity)
            val editCardNameO3Method = symbols.editCardNameO3Method
            if (editCardNameO3Method != null) {
                module.hook(editCardNameO3Method).intercept { chain ->
                    val activity = chain.thisObject as? Activity
                    val newName = chain.args.getOrNull(1) as? String ?: ""
                    val aid = extractAidFromEditCardNameActivity(activity)
                    if (activity != null && WalletDetailInjectHook.isTransitCard(aid)) {
                        val cleanName = newName.replace("\n", "").replace("\r", "").trim()
                        module.log(Log.INFO, TAG, "Intercepted EditCardNameActivity.O3 for transit aid=$aid")
                        if (cleanName.isNotEmpty()) {
                            WalletConfig.setCustomName(activity, aid, cleanName)
                            WalletCardDataHook.applyNameToCardDetail(activity, aid, cleanName, symbols)
                        }
                        finishActivity(activity, symbols, 701)
                        return@intercept null
                    }
                    chain.proceed()
                }
            }

            // C. Scene theme components (EntranceSceneThemeActivity and EntranceSceneViewModel)
            val themeActivityClass = symbols.entranceSceneThemeActivityClass
            if (themeActivityClass != null) {
                val onCreateMethod = themeActivityClass.declaredMethods.firstOrNull {
                    it.name == "onCreate" && it.parameterTypes.size == 1 &&
                            android.os.Bundle::class.java.isAssignableFrom(it.parameterTypes[0])
                }
                if (onCreateMethod != null) {
                    onCreateMethod.isAccessible = true
                    module.hook(onCreateMethod).intercept { chain ->
                        val act = chain.thisObject as? Activity
                        if (act != null) {
                            activeThemeActivity = WeakReference(act)
                            WalletRuntimeContext.recordActivity(act)
                        }
                        chain.proceed()
                    }
                }
            }

            // 1. Hooks buildThemeItemList (P): removes the 4-cover limit and injects the stock cover
            val buildListMethod = symbols.buildThemeItemListMethod
            if (buildListMethod != null) {
                module.hook(buildListMethod).intercept { chain ->
                    buildThemeItemList(chain, symbols)
                }
            }

            // 2. Hooks buildThemeNameItem (Q): pre-fills the existing card name
            val buildNameMethod = symbols.buildThemeNameItemMethod
            if (buildNameMethod != null) {
                module.hook(buildNameMethod).intercept { chain ->
                    val result = chain.proceed()
                    val viewModel = chain.thisObject
                    if (viewModel != null && result != null) {
                        val currentAid = extractAidFromViewModel(viewModel, symbols)
                        val ctx = resolveContext()
                        val currentName = (if (ctx != null && currentAid.isNotEmpty()) {
                            WalletConfig.getCustomName(ctx, currentAid)
                        } else null) ?: WalletDetailInjectHook.getTransitCardName(currentAid)
                        if (!currentName.isNullOrEmpty()) {
                            runCatching {
                                val nameField = result.javaClass.declaredFields.firstOrNull { f ->
                                    f.type == String::class.java &&
                                        (f.apply { isAccessible = true }.get(result) as? String).isNullOrEmpty()
                                }
                                nameField?.isAccessible = true
                                nameField?.set(result, currentName)
                            }.onFailure { Log.w(TAG, "Pre-populate card name failed", it) }
                        }
                    }
                    result
                }
            }

            // 3. Hooks the save/submit method: local save and short-circuit for transit cards, host request otherwise
            val uploadMethod = symbols.uploadEntranceInfoMethod
            if (uploadMethod != null) {
                module.hook(uploadMethod).intercept { chain ->
                    val viewModel = chain.thisObject
                    if (viewModel != null) {
                        val currentAid = extractAidFromViewModel(viewModel, symbols)
                        if (WalletDetailInjectHook.isTransitCard(currentAid)) {
                            val currentImgUrl = extractSelectedImgFromViewModel(viewModel, symbols)
                            val currentName = extractSelectedNameFromViewModel(viewModel, symbols)
                            val act = getCurrentThemeActivity()
                            val ctx = act ?: resolveContext()

                            if (ctx != null) {
                                if (currentImgUrl.isNotEmpty()) {
                                    WalletConfig.setCustomCover(ctx, currentAid, currentImgUrl)
                                }
                                if (currentName.isNotEmpty()) {
                                    WalletConfig.setCustomName(ctx, currentAid, currentName)
                                }
                                WalletCardDataHook.invalidateCoverCache(currentAid)
                                if (currentImgUrl.isNotEmpty()) {
                                    WalletCardDataHook.applyCoverToCardDetail(ctx, currentAid, currentImgUrl, symbols)
                                }
                                if (currentName.isNotEmpty()) {
                                    WalletCardDataHook.applyNameToCardDetail(ctx, currentAid, currentName, symbols)
                                }
                            }

                            // Short-circuits the cloud request. Host D0() returns void and null equals
                            // a normal return; the module local write replaces the upload flow.
                            if (act != null && !act.isFinishing && !act.isDestroyed && WalletDetailInjectHook.isTransitCard(currentAid)) {
                                runCatching {
                                    act.setResult(Activity.RESULT_OK)
                                    Toast.makeText(
                                        act,
                                        WalletUiText.t(act, WalletUiText.TOAST_THEME_SAVED),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    act.finish()
                                }
                            }
                            return@intercept null
                        }
                    }
                    chain.proceed()
                }
            }

            isInstalled = true
            module.log(Log.INFO, TAG, "WalletSceneThemeHook installed successfully.")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install WalletSceneThemeHook", t)
        }
    }

    /**
     * Finishes the host page.
     *
     * Calls the host finish method first to keep its return-value semantics; an unresolved
     * method falls back to a result code plus finish.
     */
    private fun finishActivity(activity: Activity, symbols: WalletSymbols, resultCode: Int) {
        val invoked = if (resultCode == 801) {
            val f4 = symbols.cardThemeF4Method
            f4 != null && runCatching { f4.invoke(activity, true); true }.getOrDefault(false)
        } else {
            val r3 = symbols.editCardNameR3Method
            val aid = extractAidFromEditCardNameActivity(activity)
            val name = if (aid.isEmpty()) "" else WalletConfig.getCustomName(activity, aid).orEmpty()
            r3 != null && runCatching { r3.invoke(activity, name); true }.getOrDefault(false)
        }
        if (!invoked) {
            activity.setResult(resultCode)
            activity.finish()
        }
    }

    private fun buildThemeItemList(chain: XposedInterface.Chain, symbols: WalletSymbols): Any? {
        val originResult = chain.proceed()
        val inputList = chain.args.getOrNull(0) as? List<*> ?: return originResult
        val viewModel = chain.thisObject ?: return originResult
        if (inputList.isEmpty()) return originResult

        val resultList = originResult as? List<*> ?: return originResult
        // Derives the item constructor from a real returned item instead of a fixed bean class name
        val itemCtor = resultList.firstOrNull()?.javaClass?.declaredConstructors?.firstOrNull { c ->
            c.parameterTypes.size == 3 &&
                (c.parameterTypes[0] == Long::class.javaPrimitiveType || c.parameterTypes[0] == java.lang.Long::class.java) &&
                c.parameterTypes[1] == String::class.java &&
                (c.parameterTypes[2] == Boolean::class.javaPrimitiveType || c.parameterTypes[2] == java.lang.Boolean::class.java)
        }?.also { it.isAccessible = true } ?: return originResult

        val currentAid = extractAidFromViewModel(viewModel, symbols)
        val ctx = resolveContext()
        val stockUrl = (if (ctx != null && currentAid.isNotEmpty()) {
            WalletConfig.getStockCover(ctx, currentAid)
        } else null) ?: queryStockCoverUrl(currentAid, symbols)
        val currentCover = if (ctx != null && currentAid.isNotEmpty()) {
            WalletConfig.getCustomCover(ctx, currentAid)
        } else null

        val expanded = ArrayList<Any>(inputList.size + 1)
        // 1. Injects the stock cover at the top for the "other scenes" case
        if (isOtherScenario(viewModel) && !stockUrl.isNullOrEmpty()) {
            runCatching {
                val isSelected = currentCover.isNullOrEmpty() || currentCover == stockUrl
                expanded.add(itemCtor.newInstance(STOCK_THEME_ID, stockUrl, isSelected))
            }.onFailure { Log.w(TAG, "Inject stock cover failed", it) }
        }

        // 2. Removes the i <= 4 truncation and expands every item
        inputList.forEachIndexed { index, theme ->
            if (theme == null) return@forEachIndexed
            val cardImg = runCatching {
                theme.javaClass.getMethod("getCardImg").invoke(theme) as? String ?: ""
            }.getOrDefault("")
            if (cardImg.isEmpty()) return@forEachIndexed
            val themeId = runCatching {
                (theme.javaClass.getMethod("getCardThemeId").invoke(theme) as? Number)?.toLong() ?: 0L
            }.getOrDefault(0L)
            val isSelected = if (!currentCover.isNullOrEmpty()) {
                cardImg == currentCover
            } else {
                expanded.isEmpty() && index == 0
            }
            runCatching { expanded.add(itemCtor.newInstance(themeId, cardImg, isSelected)) }
                .onFailure { Log.w(TAG, "Rebuild theme item failed", it) }
        }

        return if (expanded.isNotEmpty()) expanded else originResult
    }

    private fun isOtherScenario(viewModel: Any): Boolean {
        val scenario = runCatching {
            val uMethod = viewModel.javaClass.methods.firstOrNull { it.name == "U" && it.parameterTypes.isEmpty() }
            uMethod?.invoke(viewModel) as? String
        }.getOrNull().orEmpty()
        return scenario.isEmpty() ||
            scenario.contains("other", ignoreCase = true) ||
            scenario == "999" ||
            scenario.contains("其他")
    }

    /**
     * The three no-arg String getters of host `ThemeInfo`.
     *
     * Known limit: all three signatures are identical and the resolver cannot separate
     * them structurally; the R8 names are used (`a`=AID, `d`=image URL, `e`=name).
     * Two measures apply:
     *  1. All three go through [extractThemeInfoString];
     *  2. A missing getter logs the candidate list **once** and returns an empty string.
     */
    private val themeGetterMissingLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun extractThemeInfoString(viewModel: Any, symbols: WalletSymbols, getterName: String): String {
        return runCatching {
            val themeInfoField = symbols.entranceThemeInfoField ?: return ""
            val themeInfo = themeInfoField.get(viewModel) ?: return ""
            val getter = themeInfo.javaClass.methods.firstOrNull {
                it.name == getterName && it.parameterTypes.isEmpty() &&
                    it.returnType == String::class.java
            }
            if (getter == null) {
                if (themeGetterMissingLogged.compareAndSet(false, true)) {
                    Log.w(
                        TAG,
                        "fail closed: ThemeInfo#$getterName not found; " +
                            "this host version may have renamed it; candidates=" +
                            themeInfo.javaClass.methods
                                .filter { it.parameterTypes.isEmpty() && it.returnType == String::class.java }
                                .map { it.name }
                    )
                }
                return ""
            }
            getter.invoke(themeInfo) as? String ?: ""
        }.getOrDefault("")
    }

    private fun extractAidFromViewModel(viewModel: Any, symbols: WalletSymbols): String =
        extractThemeInfoString(viewModel, symbols, "a")

    private fun extractSelectedImgFromViewModel(viewModel: Any, symbols: WalletSymbols): String =
        extractThemeInfoString(viewModel, symbols, "d")

    private fun extractSelectedNameFromViewModel(viewModel: Any, symbols: WalletSymbols): String =
        extractThemeInfoString(viewModel, symbols, "e")

    private fun queryStockCoverUrl(aid: String, symbols: WalletSymbols): String? {
        if (aid.isEmpty()) return null
        return runCatching {
            val queryMethod = symbols.queryNfcCardMethod ?: return null
            val detail = queryMethod.invoke(null, aid) ?: return null
            symbols.getOfficalUrlMethod?.invoke(detail) as? String
        }.getOrNull()
    }

    private fun extractAidFromCardThemeActivity(activity: Activity?): String {
        if (activity == null) return ""
        val fromIntent = activity.intent?.getStringExtra("aid")
            ?: activity.intent?.getStringExtra("KEY_CARD_AID")
        if (!fromIntent.isNullOrEmpty()) return fromIntent
        return uniqueAidAmongStringFields(activity)
    }

    private fun extractAidFromEditCardNameActivity(activity: Activity?): String {
        if (activity == null) return ""
        val fromIntent = activity.intent?.getStringExtra("KEY_CARD_AID")
            ?: activity.intent?.getStringExtra("aid")
        if (!fromIntent.isNullOrEmpty()) return fromIntent
        return uniqueAidAmongStringFields(activity)
    }

    /** Accepts only a single String field that matches the AID pattern. */
    private fun uniqueAidAmongStringFields(activity: Activity): String {
        val values = activity.javaClass.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { field ->
                runCatching { field.isAccessible = true; field.get(activity) as? String }.getOrNull()
            }
            .filter { AID_PATTERN.matches(it) }
        return if (values.size == 1) values.first() else ""
    }

    fun reset() {
        isInstalled = false
        activeThemeActivity = null
        activeCardThemeActivity = null
    }

    private val AID_PATTERN = Regex("^[0-9A-Fa-f]{16,40}$")
}
