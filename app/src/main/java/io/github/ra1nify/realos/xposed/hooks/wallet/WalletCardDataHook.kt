package io.github.ra1nify.realos.xposed.hooks.wallet

import android.app.Activity
import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.wallet.WalletConfig
import java.lang.ref.WeakReference
import java.util.Collections

/**
 * Card face data layer hook (single landing point for all scenes).
 *
 * ## Two independent data paths (5.51.5 decompile + runtime)
 *
 * 1. **Detail page**: `FinBigCardFaceView.setCardFaceUrl(...)` in `BusDetailActivity`,
 *    fed by network `CardDetailRsp#getCardImg()` or local `NfcCardDetail#getCardImg()`;
 * 2. **Overview card list / tap-to-pay page**: `BusCardFrameVH.updateCardImg(NfcCard)` ->
 *    `CircleNetworkImageView.setImageUrl(NfcCard#getCardImg())`,
 *    **without** any `FinXxxCardFaceView`.
 *
 * Both layers redirect on the read side.
 *
 * ## Read side, not database writes
 *
 * The official `CARD_IMG` is a network snapshot: every `POST /qp/pay/v4/card-list`
 * response rewrites the stock URL through `PayCardInfo#copyFrom`, which overwrites
 * database redirects.
 *
 * The database keeps the stock/network URL. The custom cover lives only in the module
 * side [WalletConfig] and replaces the getter return value with a cover pool `file://` URI.
 *
 * ## `file://` instead of a bare path
 *
 * `CircleNetworkImageView.setImageUrl` -> Glide String model ->
 * `ContentResolver.openInputStream(uri)` parses a bare path as `content://` and throws
 * `FileNotFoundException`. A `file://` URI goes through openFileDescriptor and decodes.
 */
object WalletCardDataHook {

    private const val TAG = "RealOS-WalletCardData"

    @Volatile
    private var isInstalled = false

    /** AIDs whose custom cover passed the file existence check. */
    private val verifiedCovers = Collections.synchronizedSet(HashSet<String>())

    fun install(module: XposedModule, symbols: WalletSymbols) {
        if (isInstalled) return
        val getCardImg = symbols.cardDetailGetCardImgMethod ?: return

        module.log(Log.INFO, TAG, "Installing WalletCardDataHook...")
        try {
            // CardDetailRsp#getCardImg: detail page card face data source.
            //
            // `thisObject` at this layer is CardDetailRsp; the AID comes from CardDetailRsp#getAid().
            // resolveNfcCardDetailAid() requires an NfcCardDetail instance and returns null for an RSP,
            // which sends every call back to the stock image.
            module.hook(getCardImg).intercept { chain ->
                val origin = chain.proceed() as? String
                val rsp = chain.thisObject
                val aid = resolveCardDetailRspAid(rsp, symbols) ?: return@intercept origin
                // Registers the transit card AID at this single entry point.
                // A Method carries one hook only; a second hook makes the result chain-order dependent.
                WalletDetailInjectHook.registerTransitAid(aid)
                val ctx = resolveContext() ?: return@intercept origin

                // Stock cover registration (only with a stock URL in the database)
                if (!origin.isNullOrEmpty() && !WalletConfig.isPoolCoverPath(origin)) {
                    WalletConfig.setStockCover(ctx, aid, origin)
                }

                validCustomCover(ctx, aid) ?: origin
            }

            // NfcCardDetail#getCardName: custom card name (card list / detail page / scene page)
            val getCardName = symbols.getCardNameMethod
            if (getCardName != null) {
                module.hook(getCardName).intercept { chain ->
                    val origin = chain.proceed() as? String
                    val detail = chain.thisObject
                    val aid = resolveNfcCardDetailAid(detail, symbols) ?: return@intercept origin
                    val ctx = resolveContext() ?: return@intercept origin
                    WalletConfig.overrides(ctx).customName(aid) ?: origin
                }
            }

            isInstalled = true
            module.log(Log.INFO, TAG, "WalletCardDataHook installed successfully.")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install WalletCardDataHook", t)
        }
    }

    /**
     * NfcCard read-side redirect (render source for overview card list / tap-to-pay faces).
     *
     * Host refreshes overwrite the database fields; this layer replaces the return value on
     * read. Application context; main process only, via [WalletSpec.onAppStart].
     */
    @Volatile
    private var isNfcCardLayerInstalled = false

    fun installNfcCardLayer(module: XposedModule, symbols: WalletSymbols) {
        if (isNfcCardLayerInstalled) return
        val nfcGetCardImg = symbols.nfcCardGetCardImgMethod ?: return
        val nfcGetAid = symbols.nfcCardGetAidMethod ?: return

        try {
            module.hook(nfcGetCardImg).intercept { chain ->
                val origin = chain.proceed() as? String
                val aid = runCatching { nfcGetAid.invoke(chain.thisObject) as? String }.getOrNull()
                if (aid.isNullOrEmpty()) return@intercept origin
                val ctx = resolveContext() ?: return@intercept origin

                if (!origin.isNullOrEmpty() && !WalletConfig.isPoolCoverPath(origin)) {
                    WalletConfig.setStockCover(ctx, aid, origin)
                }
                validCustomCover(ctx, aid)?.let { localFileUri(it) } ?: origin
            }
            isNfcCardLayerInstalled = true
            module.log(Log.INFO, TAG, "WalletCardDataHook NfcCard layer installed.")
        } catch (t: Throwable) {
            module.log(Log.WARN, TAG, "HookPoint[NfcCardData] failed to install", t)
        }
    }

    /**
     * Converts a cover pool path into a `file://` URI for the host.
     *
     * The host CircleNetworkImageView uses the Glide String model and ContentResolver.openInputStream;
     * a bare path is parsed as content:// and fails; `file://` decodes through openFileDescriptor.
     */
    private fun localFileUri(pathOrUri: String): String =
        if (pathOrUri.startsWith("file://")) pathOrUri else "file://$pathOrUri"

    /**
     * Post-save data layer step.
     *
     * The custom cover lives only in the module side [WalletConfig] and is redirected
     * by the NfcCardDetail / NfcCard getters on read. Nothing is written to the database.
     *
     * Two cases still touch the data layer:
     * - Records the stock cover for the "restore stock" path;
     * - Writes the stock URL back to the database field on "restore stock" (clears leftover
     *   local paths).
     *
     * @param coverPathOrUrl empty restores the stock cover
     */
    fun applyCoverToCardDetail(
        context: Context?,
        aid: String,
        coverPathOrUrl: String,
        symbols: WalletSymbols
    ): Boolean {
        if (context == null || aid.isEmpty()) return false
        val query = symbols.queryNfcCardMethod ?: return false
        val current = runCatching {
            query.invoke(null, aid)?.let { symbols.cardDetailGetCardImgMethod?.invoke(it) as? String }
        }.getOrNull()

        // Records the stock cover
        if (!current.isNullOrEmpty() && !WalletConfig.isPoolCoverPath(current)) {
            WalletConfig.setStockCover(context, aid, current)
        }
        verifiedCovers.remove(aid)

        // Custom cover selected: the database keeps the stock value
        if (coverPathOrUrl.isNotEmpty()) return true

        // Restore stock: writes back only for a stale database value
        val stock = WalletConfig.getStockCover(context, aid).orEmpty()
        if (stock.isEmpty()) return false
        if (current == stock) return true
        return writeCardImg(context, aid, stock, symbols)
    }

    /** Restore-stock path only: writes the given URL back to the database card image. */
    private fun writeCardImg(context: Context, aid: String, url: String, symbols: WalletSymbols): Boolean {
        val query = symbols.queryNfcCardMethod ?: return false
        val update = symbols.insertOrUpdateNfcCardMethod ?: return false
        val setter = symbols.setCardImgMethod ?: return false
        return runCatching {
            val detail = query.invoke(null, aid) ?: return false
            setter.invoke(detail, url)
            if (update.parameterTypes.size == 1) {
                update.invoke(null, detail)
            } else {
                update.invoke(null, detail, true)
            }
            verifiedCovers.remove(aid)
            true
        }.onFailure {
            Log.e(TAG, "writeCardImg failed for aid=$aid", it)
        }.getOrDefault(false)
    }

    /** Name write to the database, separate from the cover write. */
    fun applyNameToCardDetail(
        context: Context?,
        aid: String,
        name: String,
        symbols: WalletSymbols
    ): Boolean {
        if (context == null || aid.isEmpty()) return false
        val query = symbols.queryNfcCardMethod ?: return false
        val update = symbols.insertOrUpdateNfcCardMethod ?: return false
        val setter = symbols.setCardNameMethod ?: return false
        return runCatching {
            val detail = query.invoke(null, aid) ?: return false
            if (name.isEmpty()) return true
            setter.invoke(detail, name)
            if (update.parameterTypes.size == 1) {
                update.invoke(null, detail)
            } else {
                update.invoke(null, detail, true)
            }
            true
        }.onFailure {
            Log.e(TAG, "applyNameToCardDetail failed for aid=$aid", it)
        }.getOrDefault(false)
    }

    /**
     * Returns the current usable custom cover.
     *
     * The pool file may be deleted by the user or the system. The existence check runs once
     * per AID and is cached; a missing file clears the record and returns null.
     */
    fun validCustomCover(context: Context, aid: String): String? {
        val cover = WalletConfig.overrides(context).customCover(aid) ?: return null
        if (!WalletConfig.isPoolCoverPath(cover)) return cover
        if (verifiedCovers.contains(aid)) return cover
        if (WalletConfig.resolvePoolCoverPath(context, cover) != null) {
            verifiedCovers.add(aid)
            return cover
        }
        WalletConfig.setCustomCover(context, aid, "")
        verifiedCovers.remove(aid)
        Log.w(TAG, "Custom cover for aid=$aid is missing, falling back to stock cover")
        return null
    }

    fun invalidateCoverCache(aid: String) {
        verifiedCovers.remove(aid)
    }

    /**
     * Resolves the AID from `CardDetailRsp`.
     *
     * The detail page source is `CardDetailRsp` with its own `getAid()`.
     * Its target type differs from [resolveNfcCardDetailAid]; mixing them
     * returns null and disables the detail page custom cover.
     */
    private fun resolveCardDetailRspAid(rsp: Any?, symbols: WalletSymbols): String? {
        if (rsp == null) return null
        return runCatching {
            symbols.cardDetailGetAidMethod?.invoke(rsp) as? String
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Resolves the AID from `NfcCardDetail`; the instance must be an NfcCardDetail. */
    private fun resolveNfcCardDetailAid(detail: Any?, symbols: WalletSymbols): String? {
        if (detail == null) return null
        val cls = symbols.nfcCardDetailClass ?: return null
        if (!cls.isInstance(detail)) return null
        return runCatching {
            cls.methods.firstOrNull { it.name == "getAid" && it.parameterTypes.isEmpty() }
                ?.invoke(detail) as? String
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Context shared by the read-side hooks.
     *
     * Uses [WalletDetailInjectHook.getContext]: the active transit card Activity first,
     * then [WalletRuntimeContext.current]. The latter alone is null early in the detail page.
     */
    private fun resolveContext(): Context? = WalletDetailInjectHook.getContext()

    fun reset() {
        isInstalled = false
        isNfcCardLayerInstalled = false
        verifiedCovers.clear()
    }
}

/**
 * Current usable Context inside the host process.
 *
 * Paths such as the overview card list and scene thumbnails have no Activity to hook;
 * [WalletSpec.onAppStart] registers this context after Application.onCreate.
 */
object WalletRuntimeContext {

    private const val TAG = "RealOS-WalletRuntimeContext"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var lastActivityRef: WeakReference<Activity>? = null

    /** Most recent Activity, otherwise the Application context. */
    val current: Context?
        get() = lastActivityRef?.get() ?: appContext

    val isAttached: Boolean
        get() = appContext != null

    fun attach(app: android.app.Application) {
        appContext = app.applicationContext ?: app
        Log.i(TAG, "Runtime context attached: ${app.packageName} process=${currentProcessName()}")
    }

    /** Whether the host runs in the main process (`com.finshell.wallet`, not `:plugin`). */
    fun isMainProcess(app: android.app.Application): Boolean =
        currentProcessName() == app.packageName

    /**
     * Current process name.
     *
     * `SpecLoader.onAppStart` has no `ModuleLoadedParam`, and `Context#processName` is not
     * public on every SDK; the name comes from `/proc/self/cmdline` (constant after start).
     */
    fun currentProcessName(): String {
        return runCatching {
            java.io.File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n')
        }.getOrDefault("")
    }

    fun recordActivity(activity: Activity) {
        lastActivityRef = WeakReference(activity)
    }

    fun reset() {
        appContext = null
        lastActivityRef = null
    }
}
