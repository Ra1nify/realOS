package io.github.ra1nify.realos.xposed.hooks.wallet

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * A field symbol in the obfuscated host (logging and cross-version comparison only).
 *
 * R8 renames wallet host fields to single letters and shifts them across versions.
 * Field matching uses neither `name` alone nor a `declaredFields` order index fallback.
 */
data class FieldDescriptor(
    val name: String,
    val type: Class<*>,
    val orderIndex: Int
) {
    fun toLogString(): String = "$name:${type.simpleName}#$orderIndex"
}

/**
 * Wallet module symbol data model and status output.
 * No hardcoded obfuscated short names; all symbols are resolved and validated by the resolver.
 */
data class WalletSymbols(
    // 1. FinUINfcCardFaceView atomic access card face component
    val finUINfcCardFaceViewClass: Class<*>? = null,
    val setTitleVisibilityMethod: Method? = null,
    val setLogoVisibilityMethod: Method? = null,
    val setCardIconAndNameMethod: Method? = null,
    val showCardImgMethod: Method? = null,

    // 2. FinBigCardFaceView atomic transit card face component
    val finBigCardFaceViewClass: Class<*>? = null,
    val finBigCardSetUrlMethod: Method? = null,

    // 3. CardDetailRsp transit card detail response entity
    val cardDetailRspClass: Class<*>? = null,
    val cardDetailGetCountThemesMethod: Method? = null,
    val cardDetailGetCardImgMethod: Method? = null,
    val cardDetailGetCardNameMethod: Method? = null,
    val cardDetailGetAidMethod: Method? = null,

    // 4. RouterBuilder (com.nearme.router.a$l) route navigation builder
    val routerBuilderClass: Class<*>? = null,
    val routerBuilderVMethod: Method? = null,
    val routerBuilderLMethod: Method? = null,

    // 5. EntranceSceneViewModel scene and card face data model
    val entranceSceneViewModelClass: Class<*>? = null,
    val buildThemeItemListMethod: Method? = null,
    val buildThemeNameItemMethod: Method? = null,
    val uploadEntranceInfoMethod: Method? = null,
    val entranceThemeInfoField: Field? = null,
    val themeLiveDataField: Field? = null,

    // 6. CardTheme entity
    val cardThemeClass: Class<*>? = null,
    val cardThemeConstructor: Constructor<*>? = null,
    val cardThemeSetCardImgMethod: Method? = null,
    val cardThemeSetCardThumbMethod: Method? = null,
    val cardThemeSetNameMethod: Method? = null,
    val cardThemeSetCardThemeIdMethod: Method? = null,
    val cardThemeSetSelectedMethod: Method? = null,

    // 7. ListUsageScenarioRsp scenario entity
    val listUsageScenarioRspClass: Class<*>? = null,
    val getScenarioMethod: Method? = null,
    val getDefaultCardThemeListMethod: Method? = null,

    // 8. NfcDbHelper and NfcCardDetail data access
    val nfcDbHelperClass: Class<*>? = null,
    val queryNfcCardMethod: Method? = null,
    val insertOrUpdateNfcCardMethod: Method? = null,
    val nfcCardDetailClass: Class<*>? = null,
    val getOfficalUrlMethod: Method? = null,
    val setCardImgUrlMethod: Method? = null,
    val setCardImgMethod: Method? = null,
    val setCardNameMethod: Method? = null,
    val getCardNameMethod: Method? = null,

    // 9. Card detail page components and the full cover/name page
    val busDetailActivityClass: Class<*>? = null,
    val entranceSceneThemeActivityClass: Class<*>? = null,

    // Access card native cover page (CardThemeActivity and /entrance/theme)
    val cardThemeActivityClass: Class<*>? = null,
    val cardThemeD4Method: Method? = null,
    val cardThemeF4Method: Method? = null,
    val cardThemeSelectedField: Field? = null,
    val cardThemeGetCardImgMethod: Method? = null,
    val cardThemePresentHClass: Class<*>? = null,
    val cardThemeHMethodA: Method? = null,

    // Access card native name edit page (EditCardNameActivity and /entrance/entranceEditName)
    val editCardNameActivityClass: Class<*>? = null,
    val editCardNameO3Method: Method? = null,
    val editCardNameR3Method: Method? = null,

    val doorDetailFragmentClass: Class<*>? = null,
    val doorDetailS0Method: Method? = null,
    val doorDetailO1Method: Method? = null,
    val doorDetailFieldA: Field? = null,
    /**
     * DoorDetailFragment String field candidate pool (field name -> declaration order).
     *
     * Short names such as `g`/`r`/`u` move across versions. This pool carries no semantics:
     * `WalletDetailFieldsHook` binds name -> semantic (aid / cover / name) in `p1()` from the
     * getter return values on the first real call, and stays fail closed without writing
     * any field on failure.
     */
    val doorDetailStringFieldCandidates: Map<String, Int> = emptyMap(),

    // 10. Transit card native cover selection page (BusCardThemeChooseActivity and /nfc/cardshow/choose)
    val busCardThemeChooseActivityClass: Class<*>? = null,
    val busCardThemeCMethod: Method? = null,
    val busCardThemeInitViewMethod: Method? = null,

    // 11. Image crop utility (b70.c)
    val cropViewUtilsClass: Class<*>? = null,
    val cropCompressMethod: Method? = null,

    // 12. Tap-to-pay page DoorCardPage
    val doorCardPageClass: Class<*>? = null,
    val doorCardPageIsInterceptMethod: Method? = null,

    // 13. Transit card list adapter (BusSeriesCardAdapter): direct data source for external scene thumbnails
    val busSeriesCardAdapterClass: Class<*>? = null,

    // 14. NfcCard: actual render data source for the overview card list and tap-to-pay page.
    //     The official CARD_IMG is a network snapshot that PayCardInfo#copyFrom overwrites on
    //     every card-list refresh; the custom cover is redirected at this getter's read side.
    val nfcCardClass: Class<*>? = null,
    val nfcCardGetCardImgMethod: Method? = null,
    val nfcCardGetAidMethod: Method? = null,

    // Scan error records
    val scanErrors: List<String> = emptyList()
) {
    val isFaceViewResolved: Boolean
        get() = finUINfcCardFaceViewClass != null && setTitleVisibilityMethod != null && setLogoVisibilityMethod != null

    val isBigCardViewResolved: Boolean
        get() = finBigCardFaceViewClass != null && finBigCardSetUrlMethod != null

    val isThemeViewModelResolved: Boolean
        get() = entranceSceneViewModelClass != null && cardThemeClass != null

    val isCardDetailRspResolved: Boolean
        get() = cardDetailRspClass != null && cardDetailGetCountThemesMethod != null

    val isRouterBuilderResolved: Boolean
        get() = routerBuilderClass != null && routerBuilderVMethod != null

    val isCardThemeActivityResolved: Boolean
        get() = cardThemeActivityClass != null && cardThemeD4Method != null && cardThemeF4Method != null

    val isEditCardNameActivityResolved: Boolean
        get() = editCardNameActivityClass != null && editCardNameO3Method != null && editCardNameR3Method != null

    /** Transit card face data layer: requires all NfcCardDetail cover read/write methods. */
    val isCardDataResolved: Boolean
        get() = nfcCardDetailClass != null && cardDetailGetCardImgMethod != null && setCardImgMethod != null

    /** External scene (card list / scene thumbnails) coverage: adapter and data layer both resolved. */
    val isExternalSceneCovered: Boolean
        get() = isCardDataResolved && busSeriesCardAdapterClass != null

    /** NfcCard read-side redirect: the only effective path for overview card list faces. */
    val isNfcCardResolved: Boolean
        get() = nfcCardClass != null && nfcCardGetCardImgMethod != null && nfcCardGetAidMethod != null

    /** Access card detail field probe: installs only with a non-empty candidate pool. */
    val isDoorFieldProbeAvailable: Boolean
        get() = doorDetailFragmentClass != null && doorDetailStringFieldCandidates.isNotEmpty()

    val isFullyResolved: Boolean
        get() = isThemeViewModelResolved && isRouterBuilderResolved
}
