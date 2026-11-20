package io.github.ra1nify.realos.xposed.hooks.wallet

import android.app.Application
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.wallet.WalletConfig
/**
 * Core spec loader for the wallet host (com.finshell.wallet).
 * Covers card face customization, stock cover fallback, and name control.
 *
 * Hook points and coverage (each installed and failed independently):
 * - CardFaceHook: access card face view (name/logo hiding, long-press entry)
 * - BigCardFaceHook: transit card detail page big card face (custom cover)
 * - CardDataHook: `NfcCardDetail#getCardImg/getCardName` - the only data source
 *   for **external scenes** (card list, scene thumbnails, auto switch)
 * - SceneThemeHook: card face selection page (limit removal, stock cover injection, local save)
 * - DetailInjectHook: detail page menu unlock, route takeover, tap-to-pay page prompt
 * - LocalCoverHook: Application context registration (config reads without an Activity)
 */
object WalletSpec : SpecLoader {

    private const val TAG = "RealOS-WalletSpec"

    override val packageNames: Set<String> = SpecPackages.WALLET_VARIANTS

    @Volatile
    private var symbols: WalletSymbols? = null

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        module.log(Log.INFO, TAG, "onPackageReady enter: packageName=$packageName")
        if (packageName !in SpecPackages.WALLET_VARIANTS) return

        if (!WalletConfig.isNonNfcCardDiyEnabled(module)) {
            module.log(Log.INFO, TAG, "Non-NFC Card DIY is disabled by user config. Skipping hooks.")
            return
        }

        try {
            // 1. Structured symbol resolution
            val resolvedSymbols = WalletSymbolResolver.resolve(module, classLoader)
            symbols = resolvedSymbols

            // 2. Status output (fail closed: FOUND / MISSING and reason per hook point)
            logHookPointStatus(module, resolvedSymbols)

            // 3. Installs the independent per-feature hooks
            // Registers the route entry first; business paths such as openSceneTheme consume resolved symbols only.
            WalletSymbolResolverEntry.register(resolvedSymbols.routerBuilderClass?.enclosingClass)

            if (resolvedSymbols.isFaceViewResolved) {
                WalletCardFaceHook.install(module, resolvedSymbols)
            } else {
                module.log(Log.WARN, TAG, "HookPoint[CardFace] state=MISSING reason=FinUINfcCardFaceView symbols")
            }

            if (resolvedSymbols.isThemeViewModelResolved) {
                WalletSceneThemeHook.install(module, resolvedSymbols)
            } else {
                module.log(Log.WARN, TAG, "HookPoint[SceneTheme] state=MISSING reason=EntranceSceneViewModel/CardTheme symbols")
            }

            // External scene coverage: depends on the NfcCardDetail data layer
            if (resolvedSymbols.isCardDataResolved) {
                WalletCardDataHook.install(module, resolvedSymbols)
            } else {
                module.log(
                    Log.WARN,
                    TAG,
                    "HookPoint[CardData] state=MISSING reason=NfcCardDetail#getCardImg/setCardImg; " +
                            "external scenes (card list / thumbnails) will not reflect custom cover"
                )
            }

            if (!resolvedSymbols.isNfcCardResolved) {
                module.log(
                    Log.WARN,
                    TAG,
                    "HookPoint[NfcCardData] state=MISSING reason=NfcCard#getCardImg/getAid; " +
                            "overview card list will not reflect custom cover"
                )
            }

            WalletDetailInjectHook.install(module, resolvedSymbols)

            module.log(Log.INFO, TAG, "onPackageReady finished for $packageName")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "WalletSpec onPackageReady failed", t)
        }
    }

    /**
     * Registers the runtime Context and installs the Application-dependent hooks in the main process.
     *
     * The wallet host runs multiple processes (`:temp`, `:plugin`, and others). The NfcCard
     * read-side redirect applies only to the main process that hosts the overview card list.
     */
    override fun onAppStart(module: XposedModule, app: Application) {
        WalletRuntimeContext.attach(app)
        val isMainProcess = WalletRuntimeContext.isMainProcess(app)
        module.log(
            Log.INFO,
            TAG,
            "onAppStart process=${WalletRuntimeContext.currentProcessName()} isMain=$isMainProcess"
        )
        if (!isMainProcess) return

        val current = symbols ?: return
        if (!current.isNfcCardResolved) return
        WalletCardDataHook.installNfcCardLayer(module, current)
    }

    private fun logHookPointStatus(module: XposedModule, s: WalletSymbols) {
        module.log(
            Log.INFO,
            TAG,
            "Wallet Symbols Status: " +
                    "CardFaceHook=${state(s.isFaceViewResolved)}, " +
                    "BigCardFaceHook=${state(s.isBigCardViewResolved)}, " +
                    "CardDataHook=${state(s.isCardDataResolved)}, " +
                    "ExternalSceneCoverage=${state(s.isExternalSceneCovered)}, " +
                    "NfcCardDataHook=${state(s.isNfcCardResolved)}, " +
                    "CardDetailRsp=${state(s.isCardDetailRspResolved)}, " +
                    "RouterBuilder=${state(s.isRouterBuilderResolved)}, " +
                    "SceneThemeHook=${state(s.isThemeViewModelResolved)}, " +
                    "CardThemeActivity=${state(s.isCardThemeActivityResolved)}, " +
                    "EditCardNameActivity=${state(s.isEditCardNameActivityResolved)}, " +
                    "DoorFieldProbe=${state(s.isDoorFieldProbeAvailable)}"
        )
        if (s.scanErrors.isNotEmpty()) {
            module.log(Log.WARN, TAG, "Scan errors(count=${s.scanErrors.size}): ${s.scanErrors.joinToString("; ")}")
        }
    }

    private fun state(ok: Boolean): String = if (ok) "FOUND" else "MISSING"

    override fun onHotReloadCleanup() {
        symbols = null
        WalletCardFaceHook.reset()
        WalletSceneThemeHook.reset()
        WalletDetailInjectHook.reset()
        WalletCardDataHook.reset()
        WalletRuntimeContext.reset()
        WalletSymbolResolverEntry.register(null)
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        onHotReloadCleanup()
        onPackageReady(module, app.packageName, app.classLoader)
        onAppStart(module, app)
    }

    override fun onPackageReady(module: XposedModule, classLoader: ClassLoader) {
        onPackageReady(module, SpecPackages.WALLET, classLoader)
    }
}
