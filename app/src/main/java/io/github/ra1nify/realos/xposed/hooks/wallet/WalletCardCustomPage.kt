package io.github.ra1nify.realos.xposed.hooks.wallet

import io.github.ra1nify.realos.xposed.common.SafeModuleResources

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import io.github.ra1nify.realos.BuildConfig
import android.os.Handler
import android.os.Looper
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text as ComposeText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.ra1nify.realos.wallet.WalletConfig
import io.github.ra1nify.realos.ui.theme.RealOSTheme
import io.github.ra1nify.realos.ui.components.SquircleShape
import io.github.ra1nify.realos.ui.components.OverlayDialog
import io.github.ra1nify.realos.ui.components.Button
import io.github.ra1nify.realos.ui.components.Card
import io.github.ra1nify.realos.ui.components.CircularProgressIndicator
import io.github.ra1nify.realos.ui.components.TopAppBar
import io.github.ra1nify.realos.ui.components.Scaffold
import io.github.ra1nify.realos.ui.extend.image.ImageCropper
import io.github.ra1nify.realos.ui.extend.image.rememberImageCropperState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults
import io.github.ra1nify.realos.ui.components.BackButton

/**
 * Local full-page "Card Customization" controller for transit cards (replaces BusCardThemeChooseActivity):
 * 1. Top bar: back navigation and save button;
 * 2. Live card preview at 85.6 : 54 with the selected cover and card name layer;
 * 3. First row: custom card name input (empty hides the name on the card face);
 * 4. Second row: horizontal cover list:
 *    - First item: stock cover (always present, not deletable);
 *    - Middle items: local cover pool (long press opens a delete confirmation dialog);
 *    - Last item: "+" tile (SAF picker -> host PhotoCropActivity crop -> added to the pool and selected);
 * 5. Bottom: save button; saves the selection and finishes the page.
 */
object WalletCardCustomPage {

    private const val TAG = "RealOS-WalletCardCustomPage"

    /**
     * Host page text resolution: merges module resources and falls back to the
     * English map in [WalletUiText]. Never throws.
     */
    private fun uiText(activity: Activity, name: String): String {
        val res = resolveModuleResources(activity)
        return if (res != null) WalletUiText.t(res, name) else WalletUiText.t(activity, name)
    }

    const val REQUEST_CODE_PICK_IMAGE: Int = 9001

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Compose state shared across page layers.
     *
     * Picker and crop callbacks (Activity.onActivityResult, host crop page) hold this object;
     * [selectedCover] / [pool] drive recomposition.
     */
    class PageState(
        val activity: Activity,
        val aid: String,
        val stockCover: String,
        val symbols: WalletSymbols
    ) {
        var selectedCover by mutableStateOf("")
        var pool by mutableStateOf<List<String>>(emptyList())

        fun refreshPool() {
            pool = WalletConfig.getCoverPool(activity)
        }

        /** Initial page entry: directory listing runs off the composition thread. */
        suspend fun refreshPoolAsync() {
            val loaded = withContext(Dispatchers.IO) { WalletConfig.getCoverPool(activity) }
            pool = loaded
        }
    }

    @Volatile
    private var activePageStateRef: WeakReference<PageState>? = null

    /** Replaces the BusCardThemeChooseActivity content with the full-screen custom page. */
    fun setupPage(activity: Activity, aid: String, symbols: WalletSymbols) {
        if (activity.isFinishing || activity.isDestroyed) return

        mainHandler.post {
            try {
                val moduleRes = resolveModuleResources(activity)
                val safeRes = SafeModuleResources(activity.resources, moduleRes)
                val wrappedContext = ModuleContextWrapper(activity, safeRes)
                val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

                val stockCover = WalletConfig.getStockCover(activity, aid) ?: ""
                val savedCustomCover = WalletConfig.getCustomCover(activity, aid) ?: ""
                val initialSelectedCover = if (savedCustomCover.isNotEmpty()) savedCustomCover else stockCover

                // A ComposeView used as ContentView attaches and composes on setContentView.
                // Injects owners before handing the view to the Activity; the host Activity/DecorView may lack them.
                // Host ancestor layouts consume the status bar inset and WindowInsets.safeDrawing reads 0,
                // The safe area uses measured window insets with a system resource fallback.
                val safeInsets = resolveSystemBarsInsets(activity)

                val owners = resolveHostOwners(activity, activity.window.decorView)
                val root = ComposeView(wrappedContext).apply {
                    setViewTreeLifecycleOwner(owners.lifecycle)
                    setViewTreeSavedStateRegistryOwner(owners.savedState)
                    setViewTreeViewModelStoreOwner(owners.viewModelStore)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setContent {
                        CompositionLocalProvider(LocalContext provides wrappedContext) {
                            RealOSTheme(darkTheme = isDark) {
                                WalletCardCustomScreen(
                                    activity = activity,
                                    aid = aid,
                                    stockCover = stockCover,
                                    initialCover = initialSelectedCover,
                                    symbols = symbols,
                                    safeTopPx = safeInsets.first,
                                    safeBottomPx = safeInsets.second
                                )
                            }
                        }
                    }
                }

                activity.setContentView(root)
                Log.i(TAG, "WalletCardCustomPage setupPage completed for aid=$aid, stockCover=$stockCover")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to setup WalletCardCustomPage", t)
            }
        }
    }

    /** Card customization page (full Compose). Handles the cover only; name and visibility come from the detail page. */
    @Composable
    private fun WalletCardCustomScreen(
        activity: Activity,
        aid: String,
        stockCover: String,
        initialCover: String,
        symbols: WalletSymbols,
        safeTopPx: Int,
        safeBottomPx: Int
    ) {
        val scope = rememberCoroutineScope()
        // Cover pool listing runs on the IO thread; the page renders first and thumbnails follow.
        val state = remember(aid) {
            PageState(activity, aid, stockCover, symbols).apply {
                selectedCover = initialCover
            }
        }
        LaunchedEffect(state) {
            state.refreshPoolAsync()
        }
        DisposableEffect(state) {
            activePageStateRef = WeakReference(state)
            onDispose {
                if (activePageStateRef?.get() === state) activePageStateRef = null
            }
        }

        var deleteTarget by remember { mutableStateOf<String?>(null) }

        // coui Scaffold: body as the background layer sampled by the top bar,
        // topBar slot for the bar with the round glass back button, body padding inside the scroll container.
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppTheme.colorScheme.background,
            contentWindowInsets = AppScaffoldDefaults.zeroWindowInsets,
            topBar = {
                // Safe area from measured window insets (see setupPage); WindowInsets.safeDrawing reads 0 here,
                // WindowInsets.safeDrawing reads 0 here; the top bar carries safeTop padding and passes zero insets.
                val safeTop = with(LocalDensity.current) { safeTopPx.toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = safeTop)
                ) {
                    TopAppBar(
                        title = WalletUiText.t(LocalContext.current, WalletUiText.CUSTOM_PAGE_TITLE),
                        windowInsets = AppScaffoldDefaults.zeroWindowInsets,
                        navigationIcon = {
                            BackButton(onClick = { activity.finish() })
                        }
                    )
                }
            }
        ) { innerPadding ->
            val safeBottom = with(LocalDensity.current) { safeBottomPx.toDp() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colorScheme.background)
                    .padding(bottom = safeBottom)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        // Padding inside the scroll container; content scrolls under the top bar
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Live card face preview (85.6 : 54 ratio)
                    CoverImage(
                        pathOrUrl = state.selectedCover.ifEmpty { stockCover },
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(85.6f / 54f)
                            .clip(SquircleShape(cornerRadius = 14.dp))
                            .shadow(4.dp)
                            .background(ComposeColor(0xFF333333))
                    )

                    Spacer(Modifier.height(20.dp))

                    // rmuix CardDefaults.InsideMargin defaults to 0dp; the margin is passed explicitly
                    // for the title, thumbnails, and corner badge.
                    Card(insideMargin = PaddingValues(16.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ComposeText(
                                    text = WalletUiText.t(LocalContext.current, WalletUiText.CUSTOM_PAGE_COVER_LABEL),
                                    style = AppTheme.textStyles.body1,
                                    color = AppTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                ComposeText(
                                    text = WalletUiText.t(LocalContext.current, WalletUiText.CUSTOM_PAGE_COVER_LONG_PRESS_HINT),
                                    style = AppTheme.textStyles.footnote2,
                                    color = AppTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                            Spacer(Modifier.height(12.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item(key = "stock") {
                                    CoverTile(
                                        selected = state.selectedCover.isEmpty() || state.selectedCover == stockCover,
                                        pathOrUrl = stockCover,
                                        badge = WalletUiText.t(LocalContext.current, WalletUiText.CUSTOM_PAGE_STOCK),
                                        onClick = { state.selectedCover = stockCover },
                                        onLongClick = null
                                    )
                                }
                                items(state.pool, key = { it }) { path ->
                                    CoverTile(
                                        selected = state.selectedCover == path,
                                        pathOrUrl = path,
                                        badge = null,
                                        onClick = { state.selectedCover = path },
                                        onLongClick = { deleteTarget = path }
                                    )
                                }
                                item(key = "add") {
                                    AddCoverTile(onClick = { launchSafImagePicker(activity) })
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            performSave(activity, state)
                            activity.finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        ComposeText(WalletUiText.t(LocalContext.current, WalletUiText.CUSTOM_PAGE_SAVE))
                    }
                }
            }

            val target = deleteTarget
            if (target != null) {
                OverlayDialog(
                    show = true,
                    onDismissRequest = { deleteTarget = null },
                    title = WalletUiText.t(LocalContext.current, WalletUiText.CUSTOM_PAGE_COVER_DELETE_TITLE),
                    summary = WalletUiText.t(LocalContext.current, WalletUiText.CUSTOM_PAGE_COVER_DELETE_MESSAGE),
                    confirmButtonText = WalletUiText.t(LocalContext.current, WalletUiText.DIALOG_CONFIRM_DELETE),
                    confirmButtonDestructive = true,
                    onConfirm = {
                        deleteTarget = null
                        WalletConfig.removeCoverFromPool(activity, target)
                        state.refreshPool()
                        if (state.selectedCover == target) state.selectedCover = state.stockCover
                        Toast.makeText(activity, WalletUiText.t(activity, WalletUiText.TOAST_COVER_DELETED), Toast.LENGTH_SHORT).show()
                    },
                    dismissButtonText = WalletUiText.t(LocalContext.current, WalletUiText.DIALOG_CANCEL),
                    onDismiss = { deleteTarget = null }
                ) {
                }
            }
        }
    }

    /** Cover thumbnails: local files and remote URLs. */
    /** Cover thumbnail load state. */
    private sealed interface CoverLoad {
        data object Loading : CoverLoad
        data object Empty : CoverLoad
        data class Loaded(val bitmap: Bitmap) : CoverLoad
        data object Remote : CoverLoad
    }

    @Composable
    private fun CoverImage(pathOrUrl: String, contentDescription: String?, modifier: Modifier) {
        val load by produceState<CoverLoad>(CoverLoad.Loading, pathOrUrl) {
            // Decoding runs on the IO dispatcher; large bitmaps would block the main thread.
            value = if (pathOrUrl.isEmpty()) {
                CoverLoad.Empty
            } else {
                withContext(Dispatchers.IO) {
                    val bmp = decodeSampledBitmap(pathOrUrl, 800)
                    when {
                        bmp != null -> CoverLoad.Loaded(bmp)
                        pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") -> CoverLoad.Remote
                        else -> CoverLoad.Empty
                    }
                }
            }
        }
        when (val current = load) {
            is CoverLoad.Loaded -> {
                Image(
                    bitmap = current.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                )
            }

            CoverLoad.Remote -> {
                // Remote URLs only: host ImageView path through reflective Glide
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                    },
                    update = { view -> if (pathOrUrl.isNotEmpty()) loadCoverImage(view, pathOrUrl) },
                    modifier = modifier
                )
            }

            // Loading or empty: draws a placeholder color block.
            // Bitmap decoding runs in the produceState block above.
            else -> Box(modifier = modifier.background(AppTheme.colorScheme.surface))
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun CoverTile(
        selected: Boolean,
        pathOrUrl: String,
        badge: String?,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?
    ) {
        val borderColor = if (selected) AppTheme.colorScheme.primary else AppTheme.colorScheme.dividerLine
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 48.dp)
                .clip(SquircleShape(cornerRadius = 6.dp))
                .background(AppTheme.colorScheme.surface)
                .border(if (selected) 2.dp else 1.dp, borderColor, SquircleShape(cornerRadius = 6.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            CoverImage(pathOrUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            if (badge != null) {
                ComposeText(
                    text = badge,
                    style = AppTheme.textStyles.footnote2,
                    color = ComposeColor.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(ComposeColor(0x99000000))
                        .padding(vertical = 1.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun AddCoverTile(onClick: () -> Unit) {
        val shape = SquircleShape(cornerRadius = 6.dp)
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 48.dp)
                .clip(shape)
                .background(AppTheme.colorScheme.surface)
                .border(1.dp, AppTheme.colorScheme.dividerLine, shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            ComposeText(
                text = "+",
                style = AppTheme.textStyles.title3,
                color = AppTheme.colorScheme.onSurfaceSecondary
            )
        }
    }

    private fun performSave(activity: Activity, state: PageState) {
        val aid = state.aid
        if (aid.isEmpty()) {
            // Empty AID: no host data is written
            Log.w(TAG, "performSave skipped: empty aid")
            Toast.makeText(activity, WalletUiText.t(activity, WalletUiText.TOAST_UNAVAILABLE), Toast.LENGTH_SHORT).show()
            return
        }

        val selected = state.selectedCover
        val isStock = selected.isEmpty() || selected == state.stockCover
        if (isStock) {
            WalletConfig.setCustomCover(activity, aid, "")
        } else {
            WalletConfig.setCustomCover(activity, aid, selected)
        }
        WalletCardDataHook.invalidateCoverCache(aid)
        WalletCardDataHook.applyCoverToCardDetail(activity, aid, if (isStock) "" else selected, state.symbols)

        Toast.makeText(activity, WalletUiText.t(activity, WalletUiText.TOAST_SAVE_SUCCESS), Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Saved card cover: aid=$aid, isStockCover=$isStock")
    }

    /** Opens the system SAF image picker. */
    fun launchSafImagePicker(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            activity.startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
            Log.i(TAG, "Launched SAF image picker")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch SAF image picker", t)
            Toast.makeText(activity, uiText(activity, WalletUiText.TOAST_PICKER_FAILED), Toast.LENGTH_SHORT).show()
        }
    }

    private var activeCropComposeViewRef: WeakReference<ComposeView>? = null

    fun isCropOverlayShowing(): Boolean {
        return activeCropComposeViewRef?.get() != null
    }

    fun dismissCardCropOverlay(activity: Activity) {
        val composeView = activeCropComposeViewRef?.get()
        if (composeView != null) {
            val decorView = activity.window.decorView as? ViewGroup
            activeCropKeepOnTopListener?.let { listener ->
                runCatching { decorView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener) }
            }
            activeCropKeepOnTopListener = null
            decorView?.removeView(composeView)
            activeCropComposeViewRef = null
            Log.i(TAG, "Dismissed card crop overlay")
        }
    }

    /** See [showCardCropOverlay]: re-raises the overlay after the host rebuilds its content. */
    private var activeCropKeepOnTopListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    /**
     * Resolves status bar and navigation bar heights.
     *
     * The overlay attaches directly to decorView, outside android.R.id.content, and added
     * child Views receive no inset dispatch. This reads the real insets once and falls back
     * to the standard system resource heights.
     */
    private fun resolveSystemBarsInsets(activity: Activity): Pair<Int, Int> {
        var top = 0
        var bottom = 0
        runCatching {
            val insets = activity.window?.decorView?.rootWindowInsets
            if (insets != null) {
                top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
                bottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            }
        }
        if (top <= 0) top = systemDimension(activity, "status_bar_height")
        if (bottom <= 0) bottom = systemDimension(activity, "navigation_bar_height")
        return top to bottom
    }

    /** Reads a dimension from the android package; returns 0 when unresolved. */
    private fun systemDimension(context: Context, name: String): Int {
        val res = context.resources
        val id = res.getIdentifier(name, "dimen", "android")
        return if (id > 0) runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0) else 0
    }

    private class BridgeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class BridgeSavedStateOwner(private val lco: LifecycleOwner) : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.INITIALIZED
        }
        private val controller = SavedStateRegistryController.create(this).apply {
            performAttach()
            performRestore(null)
        }

        init {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            runCatching {
                lco.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                    lifecycleRegistry.handleLifecycleEvent(event)
                })
            }
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    }

    private class ModuleContextWrapper(
        base: Context,
        private val safeResources: Resources
    ) : ContextWrapper(base) {
        override fun getResources(): Resources = safeResources
    }

    /** Cached module Resources; `createPackageContext` is a PackageManager IPC call. */
    @Volatile
    private var cachedModuleResources: Resources? = null

    private fun resolveModuleResources(activity: Activity): Resources? {
        cachedModuleResources?.let { return it }
        runCatching {
            val resources = activity
                .createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
                .resources
            cachedModuleResources = resources
            return resources
        }
        return null
    }

    /**
     * Handles the SAF picker result: shows the full-screen rmuix crop overlay in this page.
     * Cropping is local, with a cover-clamped constraint and a G2 continuous squircle viewport.
     */
    fun handlePickImageResult(activity: Activity, imageUri: Uri) {
        showCardCropOverlay(activity, imageUri)
    }

    /**
     * Resolves the Compose owner trio for the host page.
     *
     * The host Activity and DecorView may lack Lifecycle/SavedState/ViewModelStore.
     * Missing owners fall back to the module bridge implementations, which avoid the
     * `ViewTreeLifecycleOwner not found` crash.
     */
    private class HostOwners(
        val lifecycle: LifecycleOwner,
        val savedState: SavedStateRegistryOwner,
        val viewModelStore: ViewModelStoreOwner
    )

    private fun resolveHostOwners(activity: Activity, anchor: View): HostOwners {
        val lco: LifecycleOwner = (activity as? LifecycleOwner)
            ?: anchor.findViewTreeLifecycleOwner()
            ?: BridgeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val sso: SavedStateRegistryOwner = (activity as? SavedStateRegistryOwner)
            ?: anchor.findViewTreeSavedStateRegistryOwner()
            ?: BridgeSavedStateOwner(lco)
        val vmso: ViewModelStoreOwner = (activity as? ViewModelStoreOwner)
            ?: anchor.findViewTreeViewModelStoreOwner()
            ?: object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        return HostOwners(lco, sso, vmso)
    }

    fun showCardCropOverlay(activity: Activity, imageUri: Uri) {
        val decorView = activity.window.decorView as? ViewGroup ?: return

        // Removes a leftover overlay
        activeCropComposeViewRef?.get()?.let { oldView ->
            decorView.removeView(oldView)
        }

        val moduleRes = resolveModuleResources(activity)
        val safeRes = SafeModuleResources(activity.resources, moduleRes)
        val wrappedContext = ModuleContextWrapper(activity, safeRes)

        // Injects owners into decorView and composeView; WindowRecomposer resolves lifecycles from decorView
        val owners = resolveHostOwners(activity, decorView)
        val resolvedLco = owners.lifecycle
        val resolvedSso = owners.savedState
        val resolvedVmso = owners.viewModelStore
        decorView.setViewTreeLifecycleOwner(resolvedLco)
        decorView.setViewTreeSavedStateRegistryOwner(resolvedSso)
        decorView.setViewTreeViewModelStoreOwner(resolvedVmso)

        val safeInsets = resolveSystemBarsInsets(activity)
        // Overlay background and text follow the system light/dark state
        val isNightMode = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val composeView = ComposeView(wrappedContext).apply {
            setViewTreeLifecycleOwner(resolvedLco)
            setViewTreeSavedStateRegistryOwner(resolvedSso)
            setViewTreeViewModelStoreOwner(resolvedVmso)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)

            setContent {
                CompositionLocalProvider(LocalContext provides wrappedContext) {
                    RealOSTheme(darkTheme = isNightMode) {
                        // Same structure as other injected pages: coui Scaffold with zero insets,
                        // while the crop page applies its own measured safe area.
                        Scaffold(
                            windowInsets = AppScaffoldDefaults.zeroWindowInsets,
                            containerColor = ComposeColor.Transparent
                        ) { _ ->
                            CardCropOverlay(
                            imageUri = imageUri,
                            safeTopPx = safeInsets.first,
                            safeBottomPx = safeInsets.second,
                            onCancel = {
                                dismissCardCropOverlay(activity)
                            },
                            onCropped = { destFile ->
                                dismissCardCropOverlay(activity)
                                onNewCoverCropped(destFile.absolutePath)
                            }
                            )
                        }
                    }
                }
            }
        }

        activeCropComposeViewRef = WeakReference(composeView)
        decorView.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 1) Brings the overlay to front; a host setContentView on onResume would push it down
        //    and the ScrollView below would consume its touch events.
        // 2) requestApplyInsets: added child Views receive no inset dispatch by default.
        composeView.bringToFront()
        composeView.requestApplyInsets()

        // 3) Keeps the overlay on top after a host content rebuild; no action when already last.
        val keepOnTop = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            if (composeView.parent !== decorView) return@OnGlobalLayoutListener
            if (decorView.childCount == 0) return@OnGlobalLayoutListener
            if (decorView.getChildAt(decorView.childCount - 1) !== composeView) {
                composeView.bringToFront()
            }
        }
        activeCropKeepOnTopListener = keepOnTop
        decorView.viewTreeObserver.addOnGlobalLayoutListener(keepOnTop)

        Log.i(TAG, "Displayed card crop overlay for uri: $imageUri")
    }

    @Composable
    private fun CardCropOverlay(
        imageUri: Uri,
        safeTopPx: Int,
        safeBottomPx: Int,
        onCancel: () -> Unit,
        onCropped: (File) -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isSaving by remember { mutableStateOf(false) }
        var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
        var decodeFailed by remember(imageUri) { mutableStateOf(false) }

        // SAF Uri decodes to a Bitmap on the IO thread before entering the crop canvas
        LaunchedEffect(imageUri) {
            val decoded = withContext(Dispatchers.IO) { decodeUriBitmap(context, imageUri) }
            sourceBitmap = decoded
            decodeFailed = decoded == null
        }

        val bitmap = sourceBitmap
        if (bitmap == null) {
            CropLoadingLayer(failed = decodeFailed, safeTopPx = safeTopPx, onCancel = onCancel)
            return
        }

        // asImageBitmap() returns a new wrapper per call; using it as a remember key rebuilds
        // the crop state (zoom and offset reset). The key is the Bitmap; ImageBitmap identity is stable.
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val state = rememberImageCropperState(imageBitmap)
        val viewportShape = remember { SquircleShape(cornerRadius = 16.dp) }

        // Safe area: the overlay receives no inset dispatch on decorView; measured values are used
        val density = LocalDensity.current
        val safeTop = with(density) { safeTopPx.toDp() }
        val safeBottom = with(density) { safeBottomPx.toDp() }
        val topBarHeight = 56.dp
        // Viewport side padding: at full width the squircle corners land on the screen edge and clip.
        // The padding keeps the corners whole.
        val viewportSidePadding = 20.dp
        val bottomBarHeight = 72.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppTheme.colorScheme.background,
            contentWindowInsets = AppScaffoldDefaults.zeroWindowInsets,
            topBar = {
                // The bar belongs in the Scaffold slot: a bar inside the content Box samples no
                // backdrop and renders solid.
                TopAppBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = safeTop),
                    title = WalletUiText.t(context, WalletUiText.CROP_TITLE),
                    // Safe area handled by the outer safeTop; the bar adds no system bar insets
                    windowInsets = AppScaffoldDefaults.zeroWindowInsets,
                    navigationIcon = { BackButton(onClick = onCancel) }
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Crop area between the top bar and bottom button, vertically centered.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = viewportSidePadding,
                            end = viewportSidePadding,
                                top = innerPadding.calculateTopPadding(),
                            bottom = safeBottom + bottomBarHeight
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Crop viewport: strict 85.6 : 54 ratio clipped to the ColorOS squircle.
                    //
                    // The outer Box clips: the ImageCropper Canvas does not clip its drawing and a
                    // cover-scaled image exceeds the viewport on one axis. Without the outer clip the
                    // image draws across the full screen.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(85.6f / 54f)
                            .clip(viewportShape)
                    ) {
                        ImageCropper(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            cropRatio = 85.6f / 54f,
                            shape = viewportShape,
                            maskColor = ComposeColor.Transparent
                        )
                    }
                }


                // Bottom done button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = safeBottom)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Button(
                        onClick = {
                            if (isSaving || !state.canCrop) return@Button
                            isSaving = true
                            scope.launch {
                                val cropped = withContext(Dispatchers.Default) { state.crop() }
                                if (cropped == null) {
                                    isSaving = false
                                    Log.w(TAG, "Crop returned no bitmap for uri: $imageUri")
                                    Toast.makeText(
                                        context,
                                        WalletUiText.t(context, WalletUiText.CROP_FAILED),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                val saved = withContext(Dispatchers.IO) { saveCroppedPng(context, cropped) }
                                if (saved != null) {
                                    onCropped(saved)
                                } else {
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        WalletUiText.t(context, WalletUiText.CROP_SAVE_FAILED),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = state.canCrop && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        ComposeText(
                            if (isSaving) {
                                WalletUiText.t(context, WalletUiText.CROP_SAVING)
                            } else {
                                WalletUiText.t(context, WalletUiText.CROP_CONFIRM)
                            }
                        )
                    }
                }
            }
        }
    }

    /** Full-screen decode placeholder: progress while loading, failure text on error, cancel always available. */
    @Composable
    private fun CropLoadingLayer(failed: Boolean, safeTopPx: Int, onCancel: () -> Unit) {
        val context = LocalContext.current
        val safeTop = with(LocalDensity.current) { safeTopPx.toDp() }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppTheme.colorScheme.background,
            contentWindowInsets = AppScaffoldDefaults.zeroWindowInsets,
            topBar = {
                // Same top bar as the crop page. It has to live in the Scaffold slot: a bar
                // inside the content Box samples no backdrop and renders solid.
                TopAppBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = safeTop),
                    title = WalletUiText.t(context, WalletUiText.CROP_TITLE),
                    // Safe area handled by the outer safeTop; the bar adds no system bar insets
                    windowInsets = AppScaffoldDefaults.zeroWindowInsets,
                    navigationIcon = { BackButton(onClick = onCancel) }
                )
            },
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (failed) {
                    ComposeText(
                        text = WalletUiText.t(context, WalletUiText.CROP_FAILED),
                        style = AppTheme.textStyles.body1,
                        color = AppTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AppTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }

    /**
     * Reads a SAF Uri and decodes it to a Bitmap; returns null on failure.
     *
     * Sampling is bounded by [maxDim]: gallery images and screenshots often exceed 4000px,
     * and a full-size decode exhausts memory in the wallet process. Screen resolution suffices.
     */
    private fun decodeUriBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    /** Writes the crop result as a PNG in the cover pool; returns null on failure. */
    private fun saveCroppedPng(context: Context, image: ImageBitmap): File? = runCatching {
        val dir = WalletConfig.getCoverPoolDir(context)
        val dest = File(dir, "cover_${System.currentTimeMillis()}.png")
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { out ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        dest.takeIf { it.length() > 0L }
    }.getOrNull()

    /** Callback after the host PhotoCropActivity saves a crop (hooked through c70.a). */
    fun onNewCoverCropped(filePath: String) {
        mainHandler.post {
            val state = activePageStateRef?.get() ?: return@post
            Log.i(TAG, "onNewCoverCropped: adding $filePath to pool and updating page")
            state.refreshPool()
            state.selectedCover = filePath
            Toast.makeText(state.activity, uiText(state.activity, WalletUiText.TOAST_COVER_READY), Toast.LENGTH_SHORT).show()
        }
    }

    /** Loads an image from a local file path or a remote HTTP URL. */
    fun loadCoverImage(imageView: ImageView, pathOrUrl: String) {
        if (pathOrUrl.isEmpty()) {
            imageView.setImageDrawable(null)
            return
        }

        // 1. Local image file
        if (pathOrUrl.startsWith("/")) {
            val bmp = decodeSampledBitmap(pathOrUrl, 800)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
                return
            }
        }

        // 2. Host Glide through reflection
        val glideSuccess = runCatching {
            val glideClass = Class.forName("com.bumptech.glide.Glide", true, imageView.context.classLoader)
            val withMethod = glideClass.getMethod("with", View::class.java)
            val requestManager = withMethod.invoke(null, imageView)
            val loadMethod = requestManager.javaClass.getMethod("load", String::class.java)
            val requestBuilder = loadMethod.invoke(requestManager, pathOrUrl)
            val intoMethod = requestBuilder.javaClass.getMethod("into", ImageView::class.java)
            intoMethod.invoke(requestBuilder, imageView)
            true
        }.getOrDefault(false)

        if (glideSuccess) return

        // 3. Network fallback: single-thread executor, backfill only while the View is attached
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            networkExecutor.execute {
                runCatching {
                    val conn = java.net.URL(pathOrUrl).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.inputStream.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null) {
                            imageView.post {
                                // Detached View: drops the bitmap instead of backfilling
                                if (imageView.isAttachedToWindow) {
                                    imageView.setImageBitmap(bmp)
                                } else {
                                    bmp.recycle()
                                }
                            }
                        }
                    }
                    runCatching { conn.disconnect() }
                }
            }
        }
    }

    /** Single-thread executor for network fallback images. */
    private val networkExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "realos-wallet-cover").apply { isDaemon = true }
    }

    fun decodeSampledBitmap(path: String, maxDim: Int): Bitmap? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, decodeOptions)
        }.getOrNull()
    }
}
