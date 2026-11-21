package io.github.ra1nify.realos.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.icon.AppIconManager
import io.github.ra1nify.realos.root.RootFlavor
import io.github.ra1nify.realos.root.RootManager
import io.github.ra1nify.realos.root.RootState
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.ui.components.LocalScaffoldContentPadding
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.components.preference.AppInfoPreference
import io.github.ra1nify.realos.ui.components.SquircleShape
import io.github.ra1nify.realos.ui.components.Card

// Microsoft four-color palette (red, green, blue, yellow).
private val MsRed = Color(0xFFF25022)
private val MsGreen = Color(0xFF7FBA00)
private val MsBlue = Color(0xFF229ED9) // Telegram brand blue.
private val MsYellow = Color(0xFFFFB900)

/** Telegram group URL. */
private const val TELEGRAM_URL = "https://t.me/Ra1nify"

/** Opens the Telegram group; failures are ignored. */
private fun openTelegramGroup(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Project page URL. */
private const val PROJECT_URL = "https://github.com/Ra1nify/realOS"

/** Opens the project page; failures are ignored. */
private fun openProjectPage(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun InfoScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = LocalScaffoldContentPadding.current,
) {
    val context = LocalContext.current

    val serviceConnected by XposedServiceManager.serviceConnected.collectAsState()
    val frameworkName by XposedServiceManager.frameworkName.collectAsState()
    val frameworkVersion by XposedServiceManager.frameworkVersion.collectAsState()
    val isCheckingXposed by XposedServiceManager.isCheckingXposed.collectAsState()

    val rootInfo by RootManager.rootInfo.collectAsState()
    val isCheckingRoot by RootManager.isCheckingRoot.collectAsState()

    val scopeList by XposedServiceManager.scope.collectAsState()
    val isRequestingScope by XposedServiceManager.isRequestingScope.collectAsState()
    // Pushed by the service after IO-thread warmup; composition never opens the APK.
    val targetScopePackages by XposedServiceManager.targetScopePackages.collectAsState()
    val activeScopeCount = remember(targetScopePackages, scopeList) {
        targetScopePackages.count { XposedServiceManager.isPackageInScope(it, scopeList) }
    }
    val totalScopeCount = targetScopePackages.size

    // State refreshes on app start and manual tap only.
    // Tab switches run no probe; this screen reads the global singleton state.

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Row 1: hero card (selected app icon, tap opens the repository) ──
        item {
            val currentIcon = remember { AppIconManager.current(context) }
            // AppInfoPreference has no onClick or subtitle; the outer Card handles the tap.
            // The slogan renders below the component.
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { openProjectPage(context) }
            ) {
                AppInfoPreference(
                    appName = stringResource(R.string.app_name),
                    appVersion = "v" + BuildConfig.VERSION_NAME,
                    // Top margin reduced from the component default 42dp to 20dp.
                    insideMargin = PaddingValues(top = 20.dp, bottom = 4.dp),
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(SquircleShape(cornerRadius = 16.dp))
                                .background(colorResource(currentIcon.backgroundRes)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(currentIcon.previewRes),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                )
                CouiText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    text = stringResource(R.string.app_slogan),
                    kind = CouiTextKind.GroupFooter,
                    color = AppTheme.colorScheme.onSurfaceSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Row 2 (Microsoft red/green): Root (red) left, Xposed (green) right ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Left: Root card (quadrant 1: red) ──
                val isRootGranted = rootInfo.state == RootState.GRANTED
                val isRootNormal = !isCheckingRoot && isRootGranted
                val hasRootVersion = !rootInfo.version.isNullOrBlank()

                val rootTitle = if (isRootNormal && (hasRootVersion || (rootInfo.flavor != RootFlavor.UNKNOWN && rootInfo.flavor != RootFlavor.GENERIC))) {
                    rootInfo.flavor.title
                } else {
                    stringResource(R.string.indicator_root_title)
                }

                val rootBadgeText = when {
                    isCheckingRoot -> stringResource(R.string.indicator_root_checking)
                    isRootGranted -> rootInfo.version?.takeIf { it.isNotBlank() } ?: stringResource(R.string.indicator_active)
                    else -> stringResource(R.string.indicator_inactive)
                }

                // rmuix Card has no spotlightGlow; the press highlight is fixed white.
                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    onClick = { RootManager.refreshExplicit(context) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isCheckingRoot -> Icons.Outlined.Sync
                                    isRootGranted -> Icons.Outlined.Security
                                    else -> Icons.Outlined.Dangerous
                                },
                                contentDescription = null,
                                tint = if (isRootNormal) MsRed else AppTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CouiText(
                                text = rootTitle,
                                kind = CouiTextKind.ItemTitle,
                                color = AppTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isRootNormal) MsRed.copy(alpha = 0.14f) else AppTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            CouiText(
                                text = rootBadgeText,
                                kind = CouiTextKind.GroupTitle,
                                color = if (isRootNormal) MsRed else AppTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }

                // ── Right: Xposed card (quadrant 2: green) ──
                val isXposedNormal = !isCheckingXposed && serviceConnected
                val hasSpecificVersion = !frameworkVersion.isNullOrBlank()
                val xposedTitle = if (isXposedNormal && (hasSpecificVersion || !frameworkName.isNullOrBlank())) {
                    XposedServiceManager.resolveFrameworkTitle(frameworkName)
                } else {
                    stringResource(R.string.indicator_xposed_title)
                }

                val xposedBadgeText = when {
                    isCheckingXposed -> stringResource(R.string.indicator_root_checking)
                    serviceConnected -> frameworkVersion?.takeIf { it.isNotBlank() } ?: stringResource(R.string.indicator_active)
                    else -> stringResource(R.string.indicator_inactive)
                }

                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    onClick = { XposedServiceManager.refreshExplicit(context) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isCheckingXposed -> Icons.Outlined.Sync
                                    serviceConnected -> Icons.Outlined.CheckCircle
                                    else -> Icons.Outlined.Extension
                                },
                                contentDescription = null,
                                tint = if (isXposedNormal) MsGreen else AppTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CouiText(
                                text = xposedTitle,
                                kind = CouiTextKind.ItemTitle,
                                color = AppTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isXposedNormal) MsGreen.copy(alpha = 0.14f) else AppTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            CouiText(
                                text = xposedBadgeText,
                                kind = CouiTextKind.GroupTitle,
                                color = if (isXposedNormal) MsGreen else AppTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
            }
        }

        // ── Row 3 (Microsoft blue/yellow): Telegram (blue) left, scope (yellow) right ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Left: Telegram group card (quadrant 3: blue) ──
                val isTelegramNormal = true

                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    onClick = { openTelegramGroup(context) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                tint = if (isTelegramNormal) MsBlue else AppTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CouiText(
                                text = stringResource(R.string.indicator_telegram_title),
                                kind = CouiTextKind.ItemTitle,
                                color = AppTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isTelegramNormal) MsBlue.copy(alpha = 0.14f) else AppTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            CouiText(
                                text = stringResource(R.string.indicator_telegram_subtitle),
                                kind = CouiTextKind.GroupTitle,
                                color = if (isTelegramNormal) MsBlue else AppTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }

                // ── Right: scope card (quadrant 4: yellow) ──
                val isScopeNormal = serviceConnected && totalScopeCount > 0 && activeScopeCount >= totalScopeCount
                val scopeBadgeText = when {
                    isRequestingScope -> stringResource(R.string.scope_requesting)
                    !serviceConnected -> stringResource(R.string.indicator_inactive)
                    else -> "$activeScopeCount/$totalScopeCount"
                }

                Card(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    onClick = { XposedServiceManager.requestScopeForMissing(context) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isRequestingScope -> Icons.Outlined.Sync
                                    else -> Icons.Outlined.Layers
                                },
                                contentDescription = null,
                                tint = if (isScopeNormal) MsYellow else AppTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CouiText(
                                text = stringResource(R.string.indicator_scope_title),
                                kind = CouiTextKind.ItemTitle,
                                color = AppTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isScopeNormal) MsYellow.copy(alpha = 0.14f) else AppTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            CouiText(
                                text = scopeBadgeText,
                                kind = CouiTextKind.GroupTitle,
                                color = if (isScopeNormal) MsYellow else AppTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
