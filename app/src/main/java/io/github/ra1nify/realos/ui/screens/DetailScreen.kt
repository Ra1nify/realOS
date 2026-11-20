package io.github.ra1nify.realos.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import io.github.ra1nify.realos.ui.components.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.libxposed.service.HookedTarget
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.opluskeyboard.OplusKeyboardManager
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.ui.components.LocalScaffoldContentPadding
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.components.Card
import io.github.ra1nify.realos.ui.components.Chip
import io.github.ra1nify.realos.ui.components.ChipDefaults

private enum class DetailSubPage {
    LIST,
    KEYBOARD,
    SYSTEM_NAV,
    SYSTEM_FRAMEWORK,
    CAMERA,
    LAUNCHER,
    WALLET,
    MEDIA_PROVIDER,
    SETTINGS,
    CLOCK_FONT
}

/**
 * Software list row entry. Stores packageName only; display data comes from PackageManager.
 */
private data class AppEntry(
    val packageName: String,
    val onClick: (() -> Unit)? = null,
    val fallbackName: String? = null,
    val fallbackIcon: ImageVector? = null,
    val switchChecked: Boolean? = null,
    val onSwitchChange: ((Boolean) -> Unit)? = null
)

@Composable
fun DetailScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = LocalScaffoldContentPadding.current,
    onSubPageChange: (Boolean) -> Unit = {}
) {
    var currentPage by remember { mutableStateOf(DetailSubPage.LIST) }

    LaunchedEffect(currentPage) {
        onSubPageChange(currentPage != DetailSubPage.LIST)
    }

    // Back press on a module subpage returns to the module list.
    BackHandler(enabled = currentPage != DetailSubPage.LIST) {
        currentPage = DetailSubPage.LIST
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "DetailSubPageTransition"
    ) { page ->
        when (page) {
            DetailSubPage.LIST -> ModuleListContent(
                contentPadding = contentPadding,
                onNavigateToKeyboard = { currentPage = DetailSubPage.KEYBOARD },
                onNavigateToSystemNav = { currentPage = DetailSubPage.SYSTEM_NAV },
                onNavigateToSystemFramework = { currentPage = DetailSubPage.SYSTEM_FRAMEWORK },
                onNavigateToCamera = { currentPage = DetailSubPage.CAMERA },
                onNavigateToLauncher = { currentPage = DetailSubPage.LAUNCHER },
                onNavigateToWallet = { currentPage = DetailSubPage.WALLET },
                onNavigateToMediaProvider = { currentPage = DetailSubPage.MEDIA_PROVIDER },
                onNavigateToSettings = { currentPage = DetailSubPage.SETTINGS },
                onNavigateToClockFont = { currentPage = DetailSubPage.CLOCK_FONT },
                modifier = modifier
            )
            DetailSubPage.KEYBOARD -> if (BuildConfig.SCOPE_OPLUS_KEYBOARD) {
                KeyboardDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.SYSTEM_NAV -> if (BuildConfig.SCOPE_NAVIGATION) {
                SystemNavDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.SYSTEM_FRAMEWORK -> if (BuildConfig.SCOPE_FIDO || BuildConfig.SCOPE_INTENTROUTE) {
                SystemFrameworkDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.CAMERA -> if (BuildConfig.SCOPE_CAMERA) {
                CameraDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.LAUNCHER -> if (BuildConfig.SCOPE_LAUNCHER) {
                LauncherDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.WALLET -> if (BuildConfig.SCOPE_WALLET) {
                WalletDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.MEDIA_PROVIDER -> if (BuildConfig.SCOPE_MEDIA_PROVIDER) {
                MediaProviderDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.SETTINGS -> if (BuildConfig.SCOPE_FEATURESWITCH) {
                SettingsDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
            DetailSubPage.CLOCK_FONT -> if (BuildConfig.SCOPE_CLOCKFONT && BuildConfig.CLOCKFONT_STAGE >= 2) {
                ClockFontDetailScreen(
                    onNavigateBack = { currentPage = DetailSubPage.LIST },
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun ModuleListContent(
    contentPadding: PaddingValues,
    onNavigateToKeyboard: () -> Unit,
    onNavigateToSystemNav: () -> Unit,
    onNavigateToSystemFramework: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToLauncher: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToMediaProvider: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToClockFont: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardPkg = remember(context) {
        // In-process cache; resolution makes 2-3 getPackageInfo calls.
        InstalledKeyboardPackageCache.get(context)
    }

    val runningTargets by XposedServiceManager.runningTargets.collectAsState()

    // Ready app list; each row stores the package name.
    // Resolved here: COUI icons are composable values and AppEntry is built in remember.
    val cameraFallbackIcon = AppIcons.Camera
    val readyApps = remember(context, keyboardPkg) {
        listOf(
            AppEntry(
                packageName = keyboardPkg,
                onClick = onNavigateToKeyboard,
                fallbackName = context.getString(R.string.oplus_input_app_name),
                fallbackIcon = Icons.Outlined.Keyboard
            ),
            AppEntry(
                packageName = SpecPackages.SYSTEMUI,
                onClick = onNavigateToSystemNav,
                fallbackName = context.getString(R.string.systemui_app_name),
                fallbackIcon = Icons.Outlined.Navigation
            ),
            AppEntry(
                packageName = SpecPackages.SYSTEM,
                onClick = onNavigateToSystemFramework,
                fallbackName = context.getString(R.string.system_framework_app_name),
                fallbackIcon = Icons.Outlined.Android
            ),
            AppEntry(
                packageName = SpecPackages.OPLUS_CAMERA,
                onClick = onNavigateToCamera,
                fallbackName = context.getString(R.string.camera_app_name),
                fallbackIcon = cameraFallbackIcon
            ),
            AppEntry(
                packageName = SpecPackages.LAUNCHER,
                onClick = onNavigateToLauncher,
                fallbackName = context.getString(R.string.launcher_app_name),
                fallbackIcon = Icons.Outlined.Home
            ),
            AppEntry(
                packageName = SpecPackages.WALLET,
                onClick = onNavigateToWallet,
                fallbackName = context.getString(R.string.wallet_app_name),
                fallbackIcon = Icons.Outlined.CreditCard
            ),
            AppEntry(
                packageName = SpecPackages.MEDIA_PROVIDER,
                onClick = onNavigateToMediaProvider,
                fallbackName = context.getString(R.string.media_provider_app_name),
                fallbackIcon = Icons.Outlined.PhotoLibrary
            ),
            AppEntry(
                packageName = SpecPackages.SETTINGS,
                onClick = onNavigateToSettings,
                fallbackName = context.getString(R.string.settings_feature_switch_app_name),
                fallbackIcon = Icons.Outlined.Settings
            )
        )
    }.filter { entry ->
        // An entry is visible when at least one enabled business injects its host.
        when (entry.packageName) {
            keyboardPkg -> BuildConfig.SCOPE_OPLUS_KEYBOARD
            SpecPackages.SYSTEMUI -> BuildConfig.SCOPE_NAVIGATION
            SpecPackages.SYSTEM -> BuildConfig.SCOPE_FIDO || BuildConfig.SCOPE_INTENTROUTE
            SpecPackages.OPLUS_CAMERA -> BuildConfig.SCOPE_CAMERA
            SpecPackages.LAUNCHER -> BuildConfig.SCOPE_LAUNCHER
            SpecPackages.WALLET -> BuildConfig.SCOPE_WALLET
            SpecPackages.MEDIA_PROVIDER -> BuildConfig.SCOPE_MEDIA_PROVIDER
            SpecPackages.SETTINGS -> BuildConfig.SCOPE_FEATURESWITCH
            else -> true
        }
    }
        .sortedBy { it.packageName }

    // Unsupported app list; currently empty.
    val unsupportedApps = remember {
        emptyList<AppEntry>()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Section 1: ready ──
        CouiText(
            text = stringResource(R.string.detail_section_ready),
            kind = CouiTextKind.GroupTitle,
            color = AppTheme.colorScheme.primary,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
        )

        if (BuildConfig.SCOPE_CLOCKFONT && BuildConfig.CLOCKFONT_STAGE >= 2) {
            ClockFontEntryCard(onClick = onNavigateToClockFont)
        }

        readyApps.forEach { entry ->
            AppItemCard(
                entry = entry,
                runningTargets = runningTargets
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Section 2: unsupported ──
        CouiText(
            text = stringResource(R.string.detail_section_unsupported),
            kind = CouiTextKind.GroupTitle,
            color = AppTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp)
        )

        if (unsupportedApps.isEmpty()) {
            // Empty section renders a blank row.
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            unsupportedApps.forEach { entry ->
                AppItemCard(
                    entry = entry,
                    runningTargets = runningTargets
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Software list row card: app icon on the left, app name and package name on
 * the right, with a hot-reload state tag and a navigation chevron.
 */
@Composable
private fun ClockFontEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = AppTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                CouiText(
                    text = stringResource(R.string.clock_font_app_name),
                    kind = CouiTextKind.ItemTitle,
                    color = AppTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CouiText(
                    text = stringResource(R.string.clock_font_app_summary),
                    kind = CouiTextKind.ItemSummary,
                    color = AppTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = stringResource(R.string.detail_open_details),
                tint = AppTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AppItemCard(
    entry: AppEntry,
    runningTargets: List<HookedTarget>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Composition reads the in-process cache only. A cache miss renders the fallback
    // text and icon while an IO coroutine loads the real values.
    var displayInfo by remember(entry.packageName) {
        mutableStateOf(AppDisplayInfoCache.get(entry.packageName))
    }
    LaunchedEffect(entry.packageName, entry.fallbackName) {
        if (displayInfo != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            getAppDisplayInfo(context, entry.packageName, entry.fallbackName)
        }
        AppDisplayInfoCache.put(entry.packageName, loaded)
        displayInfo = loaded
    }

    val appName = displayInfo?.appName ?: entry.fallbackName ?: entry.packageName
    val iconBitmap = displayInfo?.icon

    val state = remember(runningTargets, entry.packageName) {
        runningTargets.firstOrNull {
            it.processName.substringBefore(":") == entry.packageName
        }?.state
    }

    Card(
        onClick = entry.onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: app icon from PackageManager, cache preferred.
            if (iconBitmap != null) {
                val imageBitmap = remember(iconBitmap) { iconBitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = AppTheme.colorScheme.surface
                ) {
                    Icon(
                        imageVector = entry.fallbackIcon ?: Icons.Outlined.Android,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = AppTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center: app name title and package name subtitle.
            Column(
                modifier = Modifier.weight(1f)
            ) {
                CouiText(
                    text = appName,
                    kind = CouiTextKind.ItemTitle,
                    color = AppTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CouiText(
                    text = entry.packageName,
                    kind = CouiTextKind.ItemSummary,
                    color = AppTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: hot-reload state tag.
            if (state != null) {
                Spacer(modifier = Modifier.width(8.dp))
                ModuleStateTag(state = state)
            }

            // Right: switch or detail chevron.
            if (entry.switchChecked != null && entry.onSwitchChange != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = entry.switchChecked,
                    onCheckedChange = entry.onSwitchChange
                )
            } else if (entry.onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = stringResource(R.string.detail_open_details),
                    tint = AppTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** In-process cache of the installed keyboard package name. */
private object InstalledKeyboardPackageCache {
    @Volatile
    private var cached: String? = null

    fun get(context: Context): String {
        cached?.let { return it }
        return OplusKeyboardManager.getInstalledKeyboardPackage(context).also { cached = it }
    }
}

/**
 * In-process cache of app names and icon bitmaps.
 *
 * Cache hit renders immediately; cache miss renders the fallback and loads
 * asynchronously on the IO dispatcher.
 */
private object AppDisplayInfoCache {
    private val entries = java.util.concurrent.ConcurrentHashMap<String, AppDisplayInfo>()

    fun get(packageName: String): AppDisplayInfo? = entries[packageName]

    fun put(packageName: String, info: AppDisplayInfo) {
        entries[packageName] = info
    }
}

private data class AppDisplayInfo(
    val packageName: String,
    val appName: String,
    val icon: Bitmap?
)

/**
 * Reads the app label and icon from PackageManager.
 */
private fun getAppDisplayInfo(
    context: Context,
    packageName: String,
    fallbackName: String? = null
): AppDisplayInfo {
    val pm = context.packageManager
    return runCatching {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrNull()
        val iconDrawable = runCatching { appInfo.loadIcon(pm) }.getOrNull()
        val bitmap = iconDrawable?.let { drawableToBitmap(it) }
        AppDisplayInfo(
            packageName = packageName,
            appName = if (!label.isNullOrBlank()) label else (fallbackName ?: packageName),
            icon = bitmap
        )
    }.getOrElse {
        AppDisplayInfo(
            packageName = packageName,
            appName = fallbackName ?: packageName,
            icon = null
        )
    }
}

/**
 * Resolves the host app label exactly as the module list shows it, so a spec page
 * title matches the launcher entry instead of a hand-written name.
 */
@Composable
internal fun rememberHostAppName(packageName: String, fallbackName: String): String {
    val context = LocalContext.current
    var displayInfo by remember(packageName) {
        mutableStateOf(AppDisplayInfoCache.get(packageName))
    }
    LaunchedEffect(packageName, fallbackName) {
        if (displayInfo != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            getAppDisplayInfo(context, packageName, fallbackName)
        }
        AppDisplayInfoCache.put(packageName, loaded)
        displayInfo = loaded
    }
    return displayInfo?.appName ?: fallbackName
}

/**
 * Renders a Drawable, including BitmapDrawable, AdaptiveIconDrawable and VectorDrawable, to a Bitmap.
 */
private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    return runCatching {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()
}

/**
 * Renders the hot-reload state with [Chip].
 */
@Composable
private fun ModuleStateTag(
    state: HookedTarget.State?,
    modifier: Modifier = Modifier
) {
    // ChipDefaults has no tagColors; chipColors with a borderColor draws the pill outline.
    // Chip requires onClick and label; the display-only tag passes an empty onClick.
    when (state) {
        HookedTarget.State.UP_TO_DATE -> {
            val colors = ChipDefaults.chipColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.12f),
                contentColor = Color(0xFF2E7D32),
                borderColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
            )
            Chip(
                onClick = {},
                label = stringResource(R.string.module_state_active),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.RadioButtonChecked,
                        contentDescription = null,
                        tint = colors.contentColor
                    )
                },
                colors = colors,
                modifier = modifier
            )
        }
        HookedTarget.State.STALE -> {
            val colors = ChipDefaults.chipColors(
                containerColor = Color(0xFFFF9800).copy(alpha = 0.12f),
                contentColor = Color(0xFFE65100),
                borderColor = Color(0xFFFF9800).copy(alpha = 0.3f)
            )
            Chip(
                onClick = {},
                label = stringResource(R.string.module_state_stale),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = colors.contentColor
                    )
                },
                colors = colors,
                modifier = modifier
            )
        }
        HookedTarget.State.RELOADING -> {
            val colors = ChipDefaults.chipColors(
                containerColor = Color(0xFF2196F3).copy(alpha = 0.12f),
                contentColor = Color(0xFF1565C0),
                borderColor = Color(0xFF2196F3).copy(alpha = 0.3f)
            )
            Chip(
                onClick = {},
                label = stringResource(R.string.module_state_reloading),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = colors.contentColor
                    )
                },
                colors = colors,
                modifier = modifier
            )
        }
        HookedTarget.State.FAILED -> {
            val colors = ChipDefaults.chipColors(
                containerColor = Color(0xFFF44336).copy(alpha = 0.12f),
                contentColor = Color(0xFFC62828),
                borderColor = Color(0xFFF44336).copy(alpha = 0.3f)
            )
            Chip(
                onClick = {},
                label = stringResource(R.string.module_state_failed),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = colors.contentColor
                    )
                },
                colors = colors,
                modifier = modifier
            )
        }
        null -> {
            val colors = ChipDefaults.chipColors(
                containerColor = AppTheme.colorScheme.surface,
                contentColor = AppTheme.colorScheme.onSurfaceVariantSummary,
                borderColor = AppTheme.colorScheme.dividerLine.copy(alpha = 0.3f)
            )
            Chip(
                onClick = {},
                label = stringResource(R.string.module_state_not_running),
                colors = colors,
                modifier = modifier
            )
        }
    }
}
