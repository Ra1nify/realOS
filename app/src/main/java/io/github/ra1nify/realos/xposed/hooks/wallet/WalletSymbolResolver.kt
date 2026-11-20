package io.github.ra1nify.realos.xposed.hooks.wallet

import android.os.Bundle
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Wallet module symbol resolver.
 * Matches host classes, methods, and fields by structural features with strict fail closed.
 *
 * Fields are obfuscated symbols too: R8 renames them to single letters and shifts semantic
 * names across versions (`r`/`u` in older samples lost their meaning in 5.51.5). Allowed:
 * 1. Stable public API (getter/setter names that survive version changes);
 * 2. Fields cross-checked against a stable API (for example the unique String field
 *    written by the `getCardImg()` return value);
 * 3. Fail closed with a `scanErrors` entry when no unique match exists.
 */
object WalletSymbolResolver {

    private const val TAG = "RealOS-WalletSymbolResolver"

    private const val CLS_FACE_VIEW = "com.finshell.finui.widget.atomic.card.FinUINfcCardFaceView"
    private const val CLS_BIG_CARD_FACE_VIEW = "com.finshell.finui.widget.atomic.FinBigCardFaceView"
    private const val CLS_CARD_DETAIL_RSP = "com.nearme.nfc.domain.transit.rsp.CardDetailRsp"
    private const val CLS_ROUTER_BUILDER = "com.nearme.router.a\$l"
    private const val CLS_SCENE_VIEW_MODEL = "com.nearme.wallet.entrance.scene.viewmodel.EntranceSceneViewModel"
    private const val CLS_CARD_THEME = "com.nearme.nfc.domain.door.rsp.CardTheme"
    private const val CLS_USAGE_SCENARIO = "com.nearme.nfc.domain.door.rsp.ListUsageScenarioRsp"
    private const val CLS_NFC_DB_HELPER = "com.nearme.wallet.db.NfcDbHelper"
    private const val CLS_NFC_CARD_DETAIL = "com.nearme.wallet.db.NfcCardDetail"
    private const val CLS_BUS_DETAIL_ACTIVITY = "com.nearme.wallet.nfc.ui.BusDetailActivity"
    private const val CLS_ENTRANCE_SCENE_THEME_ACTIVITY = "com.nearme.wallet.entrance.scene.EntranceSceneThemeActivity"
    private const val CLS_CARD_THEME_ACTIVITY = "com.nearme.wallet.entrance.view.CardThemeActivity"
    private const val CLS_CARD_THEME_PRESENT_H = "com.nearme.wallet.entrance.present.h"
    private const val CLS_EDIT_CARD_NAME_ACTIVITY = "com.nearme.wallet.entrance.view.EditCardNameActivity"
    private const val CLS_DOOR_DETAIL_FRAGMENT = "com.nearme.wallet.entrance.fragment.DoorDetailFragment"
    private const val CLS_DOOR_CARD_PAGE = "com.nearme.pay.business.consume.card.door.DoorCardPage"
    private const val CLS_BUS_CARD_THEME_CHOOSE_ACTIVITY = "com.nearme.wallet.bus.ui.BusCardThemeChooseActivity"
    private const val CLS_CROP_VIEW_UTILS = "b70.c"
    private const val CLS_BUS_SERIES_CARD_ADAPTER = "com.nearme.wallet.nfc.ui.adapter.BusSeriesCardAdapter"
    private const val CLS_NFC_CARD = "com.nearme.wallet.db.NfcCard"

    fun resolve(module: XposedModule, classLoader: ClassLoader): WalletSymbols {
        module.log(Log.INFO, TAG, "Starting wallet symbols resolution...")
        val errors = mutableListOf<String>()

        // 1. FinUINfcCardFaceView (access card atomic face)
        var faceViewClass: Class<*>? = null
        var setTitleVisibilityMethod: Method? = null
        var setLogoVisibilityMethod: Method? = null
        var setCardIconAndNameMethod: Method? = null
        var showCardImgMethod: Method? = null
        try {
            faceViewClass = classLoader.loadClass(CLS_FACE_VIEW)
            setTitleVisibilityMethod = faceViewClass.methods.firstOrNull {
                it.name == "setTitleVisibility" && it.parameterTypes.size == 1 &&
                        (it.parameterTypes[0] == Boolean::class.javaPrimitiveType || it.parameterTypes[0] == java.lang.Boolean::class.java)
            }
            setLogoVisibilityMethod = faceViewClass.methods.firstOrNull {
                it.name == "setLogoVisibility" && it.parameterTypes.size == 1 &&
                        (it.parameterTypes[0] == Boolean::class.javaPrimitiveType || it.parameterTypes[0] == java.lang.Boolean::class.java)
            }
            setCardIconAndNameMethod = faceViewClass.methods.firstOrNull {
                it.name == "setCardIconAndName" && it.parameterTypes.size == 4 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == String::class.java &&
                        (it.parameterTypes[2] == Boolean::class.javaPrimitiveType || it.parameterTypes[2] == java.lang.Boolean::class.java)
            }
            // showCardImg(url, name, ...): the only entry that passes a cover into the atomic face view
            showCardImgMethod = faceViewClass.methods.firstOrNull {
                it.name == "showCardImg" && it.parameterTypes.size >= 2 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == String::class.java
            }
            if (setTitleVisibilityMethod == null) errors.add("Missing FinUINfcCardFaceView#setTitleVisibility")
            if (setLogoVisibilityMethod == null) errors.add("Missing FinUINfcCardFaceView#setLogoVisibility")
            if (setCardIconAndNameMethod == null) errors.add("Missing FinUINfcCardFaceView#setCardIconAndName")
            if (showCardImgMethod == null) errors.add("Missing FinUINfcCardFaceView#showCardImg")
        } catch (t: Throwable) {
            errors.add("Failed to resolve FinUINfcCardFaceView: ${t.message}")
        }

        // 2. FinBigCardFaceView (transit card atomic big face)
        var bigCardFaceViewClass: Class<*>? = null
        var finBigCardSetUrlMethod: Method? = null
        try {
            bigCardFaceViewClass = classLoader.loadClass(CLS_BIG_CARD_FACE_VIEW)
            finBigCardSetUrlMethod = bigCardFaceViewClass.methods.firstOrNull {
                it.name == "setCardFaceUrl" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            }
            if (finBigCardSetUrlMethod == null) errors.add("Missing FinBigCardFaceView#setCardFaceUrl")
        } catch (t: Throwable) {
            errors.add("Failed to resolve FinBigCardFaceView: ${t.message}")
        }

        // 3. CardDetailRsp (transit card detail response data)
        var cardDetailRspClass: Class<*>? = null
        var cardDetailGetCountThemesMethod: Method? = null
        var cardDetailGetCardImgMethod: Method? = null
        var cardDetailGetCardNameMethod: Method? = null
        var cardDetailGetAidMethod: Method? = null
        try {
            cardDetailRspClass = classLoader.loadClass(CLS_CARD_DETAIL_RSP)
            cardDetailGetCountThemesMethod = cardDetailRspClass.methods.firstOrNull {
                it.name == "getCountThemes" && it.parameterTypes.isEmpty()
            }
            cardDetailGetCardImgMethod = cardDetailRspClass.methods.firstOrNull {
                it.name == "getCardImg" && it.parameterTypes.isEmpty()
            }
            cardDetailGetCardNameMethod = cardDetailRspClass.methods.firstOrNull {
                it.name == "getCardName" && it.parameterTypes.isEmpty()
            }
            cardDetailGetAidMethod = cardDetailRspClass.methods.firstOrNull {
                it.name == "getAid" && it.parameterTypes.isEmpty()
            }
            if (cardDetailGetCountThemesMethod == null) errors.add("Missing CardDetailRsp#getCountThemes")
        } catch (t: Throwable) {
            errors.add("Failed to resolve CardDetailRsp: ${t.message}")
        }

        // 4. RouterBuilder (route redirect)
        var routerBuilderClass: Class<*>? = null
        var routerBuilderVMethod: Method? = null
        var routerBuilderLMethod: Method? = null
        try {
            routerBuilderClass = classLoader.loadClass(CLS_ROUTER_BUILDER)
            routerBuilderVMethod = routerBuilderClass.methods.firstOrNull {
                it.name == "v" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            }
            routerBuilderLMethod = routerBuilderClass.methods.firstOrNull {
                it.name == "l" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Bundle::class.java
            }
            if (routerBuilderVMethod == null) errors.add("Missing RouterBuilder#v")
        } catch (t: Throwable) {
            errors.add("Failed to resolve RouterBuilder: ${t.message}")
        }

        // 5. EntranceSceneViewModel
        var viewModelClass: Class<*>? = null
        var buildThemeItemListMethod: Method? = null
        var buildThemeNameItemMethod: Method? = null
        var uploadEntranceInfoMethod: Method? = null
        var entranceThemeInfoField: Field? = null
        var themeLiveDataField: Field? = null
        try {
            viewModelClass = classLoader.loadClass(CLS_SCENE_VIEW_MODEL)

            // buildThemeItemListMethod: single List parameter, List return, internal conversion method (P)
            buildThemeItemListMethod = viewModelClass.declaredMethods.firstOrNull { m ->
                m.parameterTypes.size == 1 &&
                        List::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                        List::class.java.isAssignableFrom(m.returnType)
            }?.also { it.isAccessible = true }

            // buildThemeNameItemMethod: single List parameter, non-List non-void name builder (Q)
            buildThemeNameItemMethod = viewModelClass.declaredMethods.firstOrNull { m ->
                m.parameterTypes.size == 1 &&
                        List::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                        !List::class.java.isAssignableFrom(m.returnType) &&
                        m.returnType != Void.TYPE && !m.returnType.isPrimitive
            }?.also { it.isAccessible = true }

            // uploadEntranceInfo: no parameters, void return, starts the upload coroutine (D0)
            uploadEntranceInfoMethod = viewModelClass.methods.firstOrNull { m ->
                m.name == "D0" && m.parameterTypes.isEmpty() && m.returnType == Void.TYPE
            } ?: viewModelClass.declaredMethods.firstOrNull { m ->
                m.parameterTypes.isEmpty() && m.returnType == Void.TYPE && m.name.length <= 3 && !Modifier.isStatic(m.modifiers)
            }?.also { it.isAccessible = true }

            // entranceThemeInfoField: holds the selected theme parameters
            entranceThemeInfoField = viewModelClass.declaredFields.firstOrNull { f ->
                f.type.name.startsWith("com.nearme.wallet.entrance.scene.")
            }?.also { it.isAccessible = true }

            // themeLiveDataField: MutableLiveData or LiveData type
            themeLiveDataField = viewModelClass.declaredFields.firstOrNull { f ->
                f.type.name.contains("LiveData") && (f.name.contains("Theme") || f.name.contains("theme"))
            }?.also { it.isAccessible = true }

            if (buildThemeItemListMethod == null) errors.add("Missing EntranceSceneViewModel#buildThemeItemList")
            if (uploadEntranceInfoMethod == null) errors.add("Missing EntranceSceneViewModel#uploadEntranceInfo")
        } catch (t: Throwable) {
            errors.add("Failed to resolve EntranceSceneViewModel: ${t.message}")
        }

        // 6. CardTheme entity
        var cardThemeClass: Class<*>? = null
        var cardThemeConstructor: Constructor<*>? = null
        var setCardImgMethod: Method? = null
        var setCardThumbMethod: Method? = null
        var setNameMethod: Method? = null
        var setCardThemeIdMethod: Method? = null
        var setSelectedMethod: Method? = null
        try {
            cardThemeClass = classLoader.loadClass(CLS_CARD_THEME)
            cardThemeConstructor = cardThemeClass.getDeclaredConstructor().also { it.isAccessible = true }
            setCardImgMethod = cardThemeClass.methods.firstOrNull { it.name == "setCardImg" && it.parameterTypes.size == 1 }
            setCardThumbMethod = cardThemeClass.methods.firstOrNull { it.name == "setCardThumb" && it.parameterTypes.size == 1 }
            setNameMethod = cardThemeClass.methods.firstOrNull { it.name == "setName" && it.parameterTypes.size == 1 }
            setCardThemeIdMethod = cardThemeClass.methods.firstOrNull {
                it.name == "setCardThemeId" && it.parameterTypes.size == 1 &&
                        (it.parameterTypes[0] == Long::class.javaPrimitiveType || it.parameterTypes[0] == java.lang.Long::class.java)
            }
            setSelectedMethod = cardThemeClass.methods.firstOrNull {
                it.name == "setSelected" && it.parameterTypes.size == 1 &&
                        (it.parameterTypes[0] == Int::class.javaPrimitiveType || it.parameterTypes[0] == java.lang.Integer::class.java)
            }
        } catch (t: Throwable) {
            errors.add("Failed to resolve CardTheme: ${t.message}")
        }

        // 7. ListUsageScenarioRsp
        var usageScenarioClass: Class<*>? = null
        var getScenarioMethod: Method? = null
        var getDefaultCardThemeListMethod: Method? = null
        try {
            usageScenarioClass = classLoader.loadClass(CLS_USAGE_SCENARIO)
            getScenarioMethod = usageScenarioClass.methods.firstOrNull { it.name == "getScenario" && it.parameterTypes.isEmpty() }
            getDefaultCardThemeListMethod = usageScenarioClass.methods.firstOrNull { it.name == "getDefaultCardThemeList" && it.parameterTypes.isEmpty() }
        } catch (t: Throwable) {
            errors.add("Failed to resolve ListUsageScenarioRsp: ${t.message}")
        }

        // 8. NfcDbHelper & NfcCardDetail
        var nfcDbHelperClass: Class<*>? = null
        var queryNfcCardMethod: Method? = null
        var insertOrUpdateNfcCardMethod: Method? = null
        var nfcCardDetailClass: Class<*>? = null
        var getOfficalUrlMethod: Method? = null
        var setCardImgUrlMethod: Method? = null
        var setCardImgFieldMethod: Method? = null
        var setCardNameMethod: Method? = null
        var getCardNameMethod: Method? = null
        var nfcCardDetailGetCardImgMethod: Method? = null
        try {
            nfcDbHelperClass = classLoader.loadClass(CLS_NFC_DB_HELPER)
            nfcCardDetailClass = classLoader.loadClass(CLS_NFC_CARD_DETAIL)

            queryNfcCardMethod = nfcDbHelperClass.methods.firstOrNull {
                it.name == "queryNfcCard" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            }
            insertOrUpdateNfcCardMethod = nfcDbHelperClass.methods.firstOrNull {
                it.name == "insertOrUpdateNfcCard" && it.parameterTypes.isNotEmpty() &&
                        it.parameterTypes[0] == nfcCardDetailClass
            }

            getOfficalUrlMethod = nfcCardDetailClass.methods.firstOrNull {
                (it.name == "getOfficalUrl" || it.name == "getOfficialUrl") && it.parameterTypes.isEmpty()
            }
            setCardImgUrlMethod = nfcCardDetailClass.methods.firstOrNull { it.name == "setCardImgUrl" && it.parameterTypes.size == 1 }
            setCardImgFieldMethod = nfcCardDetailClass.methods.firstOrNull { it.name == "setCardImg" && it.parameterTypes.size == 1 }
            setCardNameMethod = nfcCardDetailClass.methods.firstOrNull { it.name == "setCardName" && it.parameterTypes.size == 1 }
            getCardNameMethod = nfcCardDetailClass.methods.firstOrNull { it.name == "getCardName" && it.parameterTypes.isEmpty() }

            // Only data source for external scene faces (card list / scene thumbnails): NfcCardDetail#getCardImg()
            nfcCardDetailGetCardImgMethod = nfcCardDetailClass.methods.firstOrNull {
                it.name == "getCardImg" && it.parameterTypes.isEmpty() && it.returnType == String::class.java
            }
            if (nfcCardDetailGetCardImgMethod == null) errors.add("Missing NfcCardDetail#getCardImg")
            if (setCardImgFieldMethod == null) errors.add("Missing NfcCardDetail#setCardImg")
            if (queryNfcCardMethod == null) errors.add("Missing NfcDbHelper#queryNfcCard")
        } catch (t: Throwable) {
            errors.add("Failed to resolve NfcDbHelper / NfcCardDetail: ${t.message}")
        }

        // 9. Detail page components (BusDetailActivity, CardThemeActivity, EditCardNameActivity, DoorDetailFragment)
        val busDetailActivityClass = runCatching { classLoader.loadClass(CLS_BUS_DETAIL_ACTIVITY) }.getOrNull()
        val entranceSceneThemeActivityClass = runCatching { classLoader.loadClass(CLS_ENTRANCE_SCENE_THEME_ACTIVITY) }.getOrNull()

        // 9.1 CardThemeActivity (access card native cover page /entrance/theme)
        val cardThemeActivityClass = runCatching { classLoader.loadClass(CLS_CARD_THEME_ACTIVITY) }.getOrNull()
        var cardThemeD4Method: Method? = null
        var cardThemeF4Method: Method? = null
        var cardThemeSelectedField: Field? = null
        var cardThemeGetCardImgMethod: Method? = null

        if (cardThemeActivityClass != null) {
            cardThemeD4Method = cardThemeActivityClass.declaredMethods.firstOrNull {
                it.name == "d4" && it.parameterTypes.isEmpty() && it.returnType == Void.TYPE
            } ?: cardThemeActivityClass.declaredMethods.firstOrNull {
                it.parameterTypes.isEmpty() && it.returnType == Void.TYPE &&
                        it.name !in setOf("finish", "findViewById", "initData", "onFold", "onUnFold", "setListener", "V3", "W3", "Y3", "Z3", "e4", "onClick")
            }?.also { it.isAccessible = true }

            cardThemeF4Method = cardThemeActivityClass.declaredMethods.firstOrNull {
                it.name == "f4" && it.parameterTypes.size == 1 &&
                        (it.parameterTypes[0] == Boolean::class.javaPrimitiveType || it.parameterTypes[0] == java.lang.Boolean::class.java) &&
                        it.returnType == Void.TYPE
            } ?: cardThemeActivityClass.declaredMethods.firstOrNull {
                it.parameterTypes.size == 1 &&
                        (it.parameterTypes[0] == Boolean::class.javaPrimitiveType || it.parameterTypes[0] == java.lang.Boolean::class.java) &&
                        it.returnType == Void.TYPE
            }?.also { it.isAccessible = true }

            cardThemeSelectedField = cardThemeActivityClass.declaredFields.firstOrNull {
                cardThemeClass != null && cardThemeClass.isAssignableFrom(it.type)
            }?.also { it.isAccessible = true }
        }

        if (cardThemeClass != null) {
            cardThemeGetCardImgMethod = cardThemeClass.methods.firstOrNull {
                it.name == "getCardImg" && it.parameterTypes.isEmpty() && it.returnType == String::class.java
            }
        }

        // 9.2 com.nearme.wallet.entrance.present.h (access card theme request interface)
        val cardThemePresentHClass = runCatching { classLoader.loadClass(CLS_CARD_THEME_PRESENT_H) }.getOrNull()
        var cardThemeHMethodA: Method? = null
        if (cardThemePresentHClass != null) {
            cardThemeHMethodA = cardThemePresentHClass.declaredMethods.firstOrNull {
                it.name == "a" && it.parameterTypes.size == 6 &&
                        it.parameterTypes[0] == Integer.TYPE &&
                        it.parameterTypes[1] == String::class.java &&
                        it.parameterTypes[2] == String::class.java &&
                        it.parameterTypes[3] == String::class.java &&
                        it.parameterTypes[4] == String::class.java
            }?.also { it.isAccessible = true }
        }

        // 9.3 EditCardNameActivity (access card native name edit page /entrance/entranceEditName)
        val editCardNameActivityClass = runCatching { classLoader.loadClass(CLS_EDIT_CARD_NAME_ACTIVITY) }.getOrNull()
        var editCardNameO3Method: Method? = null
        var editCardNameR3Method: Method? = null

        if (editCardNameActivityClass != null) {
            editCardNameO3Method = editCardNameActivityClass.declaredMethods.firstOrNull {
                it.name == "O3" && it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == String::class.java
            } ?: editCardNameActivityClass.declaredMethods.firstOrNull {
                it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == String::class.java &&
                        it.returnType == Void.TYPE
            }?.also { it.isAccessible = true }

            editCardNameR3Method = editCardNameActivityClass.declaredMethods.firstOrNull {
                it.name == "R3" && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.returnType == Void.TYPE
            } ?: editCardNameActivityClass.declaredMethods.firstOrNull {
                it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.returnType == Void.TYPE
            }?.also { it.isAccessible = true }
        }

        val doorDetailFragmentClass = runCatching { classLoader.loadClass(CLS_DOOR_DETAIL_FRAGMENT) }.getOrNull()
        var doorDetailS0Method: Method? = null
        var doorDetailO1Method: Method? = null
        var doorDetailFieldA: Field? = null
        var doorDetailStringFieldCandidates: Map<String, Int> = emptyMap()

        if (doorDetailFragmentClass != null) {
            doorDetailS0Method = doorDetailFragmentClass.declaredMethods.firstOrNull {
                it.name == "S0" && it.parameterTypes.isEmpty()
            }?.also { it.isAccessible = true }

            doorDetailO1Method = doorDetailFragmentClass.declaredMethods.firstOrNull {
                it.name == "o1" && it.parameterTypes.isEmpty()
            }?.also { it.isAccessible = true }

            doorDetailFieldA = doorDetailFragmentClass.declaredFields.firstOrNull {
                it.type.name.endsWith("FINUICardListSelectedItemLayout")
            }?.also { it.isAccessible = true }

            // Field semantics cannot be determined statically: R8 renames String fields to
            // single letters and declaration order moves across versions (an older
            // `getOrNull(5)` fallback hit recommendedSource and rewrote an unrelated field).
            // This collects same-type fields as a candidate pool only; name -> semantic binding
            // happens in WalletDetailFieldsHook on the first `p1()` call through a value probe.
            doorDetailStringFieldCandidates = doorDetailFragmentClass.declaredFields
                .filter { it.type == String::class.java }
                .withIndex()
                .associate { it.value.name to it.index }
        }

        // 10. Tap-to-pay page DoorCardPage
        var doorCardPageClass: Class<*>? = null
        var doorCardPageIsInterceptMethod: Method? = null
        try {
            doorCardPageClass = classLoader.loadClass(CLS_DOOR_CARD_PAGE)
            doorCardPageIsInterceptMethod = doorCardPageClass.declaredMethods.firstOrNull {
                it.name == "isInterceptShowSceneTips" &&
                        (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == java.lang.Boolean::class.java)
            }?.also { it.isAccessible = true }
        } catch (t: Throwable) {
            // DoorCardPage is an optional hook point and does not block on failure
        }

        // 11. Transit card native cover selection page BusCardThemeChooseActivity (/nfc/cardshow/choose)
        val busCardThemeChooseActivityClass = runCatching { classLoader.loadClass(CLS_BUS_CARD_THEME_CHOOSE_ACTIVITY) }.getOrNull()
        val busCardThemeCMethod = busCardThemeChooseActivityClass?.declaredMethods?.firstOrNull {
            it.name == "c" && it.parameterTypes.isEmpty()
        }?.also { it.isAccessible = true }
        val busCardThemeInitViewMethod = busCardThemeChooseActivityClass?.declaredMethods?.firstOrNull {
            it.name == "initView" && it.parameterTypes.isEmpty()
        }?.also { it.isAccessible = true }

        // 12. Image crop utility b70.c
        val cropViewUtilsClass = runCatching { classLoader.loadClass(CLS_CROP_VIEW_UTILS) }.getOrNull()
        val cropCompressMethod = cropViewUtilsClass?.methods?.firstOrNull {
            it.name == "b" && it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
        }

        // 13. External scene card list adapter (coverage assertion: thumbnails go through NfcCardDetail#getCardImg)
        val busSeriesCardAdapterClass = runCatching { classLoader.loadClass(CLS_BUS_SERIES_CARD_ADAPTER) }.getOrNull()
        if (busSeriesCardAdapterClass == null) {
            errors.add("Missing BusSeriesCardAdapter (external scene card list)")
        }

        // 14. NfcCard: actual render source for overview card list faces
        var nfcCardClass: Class<*>? = null
        var nfcCardGetCardImgMethod: Method? = null
        var nfcCardGetAidMethod: Method? = null
        try {
            nfcCardClass = classLoader.loadClass(CLS_NFC_CARD)
            nfcCardGetCardImgMethod = nfcCardClass.methods.firstOrNull {
                it.name == "getCardImg" && it.parameterTypes.isEmpty() && it.returnType == String::class.java
            }
            nfcCardGetAidMethod = nfcCardClass.methods.firstOrNull {
                it.name == "getAid" && it.parameterTypes.isEmpty() && it.returnType == String::class.java
            }
            if (nfcCardGetCardImgMethod == null) errors.add("Missing NfcCard#getCardImg")
            if (nfcCardGetAidMethod == null) errors.add("Missing NfcCard#getAid")
        } catch (t: Throwable) {
            errors.add("Failed to resolve NfcCard: ${t.message}")
        }

        val symbols = WalletSymbols(
            finUINfcCardFaceViewClass = faceViewClass,
            setTitleVisibilityMethod = setTitleVisibilityMethod,
            setLogoVisibilityMethod = setLogoVisibilityMethod,
            setCardIconAndNameMethod = setCardIconAndNameMethod,
            showCardImgMethod = showCardImgMethod,
            finBigCardFaceViewClass = bigCardFaceViewClass,
            finBigCardSetUrlMethod = finBigCardSetUrlMethod,
            cardDetailRspClass = cardDetailRspClass,
            cardDetailGetCountThemesMethod = cardDetailGetCountThemesMethod,
            cardDetailGetCardImgMethod = cardDetailGetCardImgMethod,
            cardDetailGetCardNameMethod = cardDetailGetCardNameMethod,
            cardDetailGetAidMethod = cardDetailGetAidMethod,
            routerBuilderClass = routerBuilderClass,
            routerBuilderVMethod = routerBuilderVMethod,
            routerBuilderLMethod = routerBuilderLMethod,
            entranceSceneViewModelClass = viewModelClass,
            buildThemeItemListMethod = buildThemeItemListMethod,
            buildThemeNameItemMethod = buildThemeNameItemMethod,
            uploadEntranceInfoMethod = uploadEntranceInfoMethod,
            entranceThemeInfoField = entranceThemeInfoField,
            themeLiveDataField = themeLiveDataField,
            cardThemeClass = cardThemeClass,
            cardThemeConstructor = cardThemeConstructor,
            cardThemeSetCardImgMethod = setCardImgMethod,
            cardThemeSetCardThumbMethod = setCardThumbMethod,
            cardThemeSetNameMethod = setNameMethod,
            cardThemeSetCardThemeIdMethod = setCardThemeIdMethod,
            cardThemeSetSelectedMethod = setSelectedMethod,
            listUsageScenarioRspClass = usageScenarioClass,
            getScenarioMethod = getScenarioMethod,
            getDefaultCardThemeListMethod = getDefaultCardThemeListMethod,
            nfcDbHelperClass = nfcDbHelperClass,
            queryNfcCardMethod = queryNfcCardMethod,
            insertOrUpdateNfcCardMethod = insertOrUpdateNfcCardMethod,
            nfcCardDetailClass = nfcCardDetailClass,
            getOfficalUrlMethod = getOfficalUrlMethod,
            setCardImgUrlMethod = setCardImgUrlMethod,
            setCardImgMethod = setCardImgFieldMethod,
            setCardNameMethod = setCardNameMethod,
            getCardNameMethod = getCardNameMethod,
            busDetailActivityClass = busDetailActivityClass,
            entranceSceneThemeActivityClass = entranceSceneThemeActivityClass,
            cardThemeActivityClass = cardThemeActivityClass,
            cardThemeD4Method = cardThemeD4Method,
            cardThemeF4Method = cardThemeF4Method,
            cardThemeSelectedField = cardThemeSelectedField,
            cardThemeGetCardImgMethod = cardThemeGetCardImgMethod,
            cardThemePresentHClass = cardThemePresentHClass,
            cardThemeHMethodA = cardThemeHMethodA,
            editCardNameActivityClass = editCardNameActivityClass,
            editCardNameO3Method = editCardNameO3Method,
            editCardNameR3Method = editCardNameR3Method,
            doorDetailFragmentClass = doorDetailFragmentClass,
            doorDetailS0Method = doorDetailS0Method,
            doorDetailO1Method = doorDetailO1Method,
            doorDetailFieldA = doorDetailFieldA,
            doorDetailStringFieldCandidates = doorDetailStringFieldCandidates,
            doorCardPageClass = doorCardPageClass,
            doorCardPageIsInterceptMethod = doorCardPageIsInterceptMethod,
            busCardThemeChooseActivityClass = busCardThemeChooseActivityClass,
            busCardThemeCMethod = busCardThemeCMethod,
            busCardThemeInitViewMethod = busCardThemeInitViewMethod,
            cropViewUtilsClass = cropViewUtilsClass,
            cropCompressMethod = cropCompressMethod,
            busSeriesCardAdapterClass = busSeriesCardAdapterClass,
            nfcCardClass = nfcCardClass,
            nfcCardGetCardImgMethod = nfcCardGetCardImgMethod,
            nfcCardGetAidMethod = nfcCardGetAidMethod,
            scanErrors = errors
        )

        module.log(
            Log.INFO,
            TAG,
            "Resolution completed. fullyResolved=${symbols.isFullyResolved}, " +
                    "faceView=${symbols.isFaceViewResolved}, bigCardView=${symbols.isBigCardViewResolved}, " +
                    "cardDetailRsp=${symbols.isCardDetailRspResolved}, router=${symbols.isRouterBuilderResolved}, " +
                    "cardData=${symbols.isCardDataResolved}, externalScenes=${symbols.isExternalSceneCovered}, " +
                    "nfcCard=${symbols.isNfcCardResolved}, " +
                    "doorFieldProbe=${symbols.isDoorFieldProbeAvailable}, " +
                    "cardThemeAct=${symbols.isCardThemeActivityResolved}, editNameAct=${symbols.isEditCardNameActivityResolved}, " +
                    "errors=${errors.size}"
        )
        if (errors.isNotEmpty()) {
            module.log(Log.WARN, TAG, "Scan errors: ${errors.joinToString("; ")}")
        }

        return symbols
    }

}