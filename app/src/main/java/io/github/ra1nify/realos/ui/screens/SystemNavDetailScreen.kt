package io.github.ra1nify.realos.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import io.github.ra1nify.realos.util.RealOSLog as Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.restart.RestartExecutor
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.restart.toastMessageRes
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.navigation.SettingsNavContract
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/** Host settings package name. */
private const val SETTINGS_PACKAGE = "com.android.settings"

/** Log tag. */
private const val TAG = "RealOS-SystemNav"

/**
 * Shell Activity class name suffix.
 *
 * Discovery matches this suffix. The host renders externally launched settings
 * subpages in an empty Activity with no Fragment transaction.
 */
private const val SHELL_ACTIVITY_SUFFIX = "BackupRestoreEmptyActivity"


/**
 * Fallback settings entry Activities appended to every resolution result.
 */
private val SETTINGS_ENTRY_FALLBACKS = listOf(
    "com.android.settings.Settings",
    "com.oplus.settings.OplusSettingsActivity"
)

/**
 * Builds the realOS subpage intent.
 *
 * @param host Exported settings Activity that hosts the subpage.
 */
private fun subPageIntent(action: String, pageType: String, host: ComponentName): Intent =
    Intent(action).apply {
        component = host
        putExtra(SettingsNavContract.EXTRA_PAGE_TYPE, pageType)
        putExtra(SettingsNavContract.EXTRA_SHOW_FRAGMENT_AS_SUBSETTING, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

@Composable
fun SystemNavDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pushes local values to the host once on entry.
    LaunchedEffect(Unit) { SettingsConfig.syncToRemote(context) }

    // Current navigation mode (2 = gesture, 0 = buttons).
    val isGestureMode = remember(context) {
        val mode = runCatching {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode")
        }.getOrDefault(2)
        mode != 0
    }
    val currentNavMode = stringResource(
        if (isGestureMode) R.string.system_nav_mode_gesture else R.string.system_nav_mode_buttons
    )

    var visualEnabled by remember {
        mutableStateOf(SettingsConfig.visualEntryEnabled())
    }
    var hapticEnabled by remember {
        mutableStateOf(SettingsConfig.hapticEntryEnabled())
    }
    var logicEnabled by remember {
        mutableStateOf(SettingsConfig.logicEntryEnabled())
    }
    var advancedEnabled by remember {
        mutableStateOf(SettingsConfig.advancedEntryEnabled())
    }

    /**
     * Opens the host system navigation settings without a custom action.
     *
     * Logs each failed candidate.
     */
    fun openSystemNavSettings(ctx: Context) {
        val candidates = listOf(
            Intent("android.settings.SYSTEM_NAVIGATION_SETTINGS"),
            Intent("android.settings.SETTINGS")
        )
        for (intent in candidates) {
            val ok = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                true
            }.getOrElse { t ->
                Log.w(TAG, "openSystemNavSettings failed: $intent", t)
                false
            }
            if (ok) return
        }
        Toast.makeText(ctx, ctx.getString(R.string.system_nav_open_settings_failed), Toast.LENGTH_SHORT).show()
    }

    /**
     * Resolves settings entry components that can host a subpage.
     *
     * Uses the MAIN/LAUNCHER entry from `getLaunchIntentForPackage`, then the
     * fallback names.
     */
    fun resolveSettingsEntries(ctx: Context): List<ComponentName> {
        val pm = ctx.packageManager
        val activities = runCatching {
            pm.getPackageInfo(SETTINGS_PACKAGE, PackageManager.GET_ACTIVITIES)?.activities
        }.getOrNull().orEmpty()

        val exported = activities.filter { it.exported }

        // Priority:
        // 1. shell page (verified; usually exported=false)
        // 2. launch entry (main settings page)
        // 3. hardcoded fallback names
        val shell = exported.firstOrNull { it.name.endsWith(SHELL_ACTIVITY_SUFFIX) }?.name
        val fromLaunch = runCatching { pm.getLaunchIntentForPackage(SETTINGS_PACKAGE) }
            .getOrNull()?.component?.className

        // SubSettings is not a candidate.
        // The launch entry follows the shell page.
        val resolved = LinkedHashSet<ComponentName>()
        shell?.let { resolved += ComponentName(SETTINGS_PACKAGE, it) }
        fromLaunch?.let { resolved += ComponentName(SETTINGS_PACKAGE, it) }
        SETTINGS_ENTRY_FALLBACKS.forEach { resolved += ComponentName(SETTINGS_PACKAGE, it) }

        Log.i(
            TAG,
            "resolveSettingsEntries: shell=$shell launch=$fromLaunch " +
                "candidates=${resolved.map { it.className }}"
        )
        return resolved.toList()
    }

    /**
     * Opens a realOS subpage.
     *
     * Targets the settings launch entry from [resolveSettingsEntries] and adds the
     * module action and pageType. The hook side matches those fields and renders
     * the Compose page, with no dependence on host Activity names.
     */
    fun openSubPage(ctx: Context, action: String, pageType: String) {
        for (host in resolveSettingsEntries(ctx)) {
            val intent = subPageIntent(action, pageType, host)
            val ok = runCatching {
                ctx.startActivity(intent)
                true
            }.getOrElse { t ->
                Log.w(TAG, "openSubPage failed: page=$pageType host=$host", t)
                false
            }
            if (ok) return
        }
        openSystemNavSettings(ctx)
    }


    ModuleDetailScaffold(
        title = rememberHostAppName(SpecPackages.SYSTEMUI, stringResource(R.string.systemui_app_name)),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        onRestart = {
            scope.launch {
                val policy = RestartPolicies.systemUi
                val result = RestartExecutor.restart(context, policy)
                Toast.makeText(
                    context,
                    context.getString(result.toastMessageRes(policy)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    ) {
        // Rows 1 to 5 share one card group.
        BasicComponentGroup(
            title = stringResource(R.string.system_nav_section_title)
        ) {
            // Row 1: current system navigation state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.system_nav_current_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colorScheme.primary
                )
                Text(
                    text = currentNavMode,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colorScheme.onBackground
                )
            }

            HorizontalDivider()

            // Row 2: visual tuning (compound switch)
            SwitchPreference(
                checked = visualEnabled,
                onCheckedChange = { checked ->
                    visualEnabled = checked
                    SettingsConfig.setVisualEntryEnabled(context, checked)
                },
                shortcutOnlyWhenChecked = true,
                onClick = {
                    if (isGestureMode) {
                        openSubPage(context, SettingsNavContract.ACTION_APPEARANCE, SettingsNavContract.PAGE_APPEARANCE)
                    } else {
                        openSubPage(context, SettingsNavContract.ACTION_BUTTON, SettingsNavContract.PAGE_BUTTON)
                    }
                },
                title = OplusWorkshopUiText.getNavSectionVisualTuning(context),
                summary = stringResource(
                    if (isGestureMode) R.string.system_nav_visual_summary_gesture
                    else R.string.system_nav_visual_summary_buttons
                ),
                shortcutHint = stringResource(R.string.system_nav_visual_hint)
            )

            HorizontalDivider()

            // Row 3: haptic tuning (compound switch)
            SwitchPreference(
                checked = hapticEnabled,
                onCheckedChange = { checked ->
                    hapticEnabled = checked
                    SettingsConfig.setHapticEntryEnabled(context, checked)
                },
                shortcutOnlyWhenChecked = true,
                onClick = {
                    if (isGestureMode) {
                        openSubPage(context, SettingsNavContract.ACTION_VIBRATION, SettingsNavContract.PAGE_VIBRATION)
                    } else {
                        openSubPage(context, SettingsNavContract.ACTION_BUTTON_VIBRATION, SettingsNavContract.PAGE_BUTTON_VIBRATION)
                    }
                },
                title = OplusWorkshopUiText.getNavSectionHapticTuning(context),
                summary = stringResource(
                    if (isGestureMode) R.string.system_nav_haptic_summary_gesture
                    else R.string.system_nav_haptic_summary_buttons
                ),
                shortcutHint = stringResource(R.string.system_nav_haptic_hint)
            )

            HorizontalDivider()

            // Row 4: logic tuning (compound switch)
            SwitchPreference(
                checked = logicEnabled,
                onCheckedChange = { checked ->
                    logicEnabled = checked
                    SettingsConfig.setLogicEntryEnabled(context, checked)
                },
                shortcutOnlyWhenChecked = true,
                onClick = {
                    if (isGestureMode) {
                        openSubPage(context, SettingsNavContract.ACTION_ADVANCED, SettingsNavContract.PAGE_ADVANCED)
                    } else {
                        openSubPage(context, SettingsNavContract.ACTION_BUTTON_GLASS, SettingsNavContract.PAGE_BUTTON_GLASS)
                    }
                },
                title = OplusWorkshopUiText.getNavSectionLogicTuning(context),
                summary = stringResource(
                    if (isGestureMode) R.string.system_nav_logic_summary_gesture
                    else R.string.system_nav_logic_summary_buttons
                ),
                shortcutHint = stringResource(R.string.system_nav_logic_hint)
            )

            HorizontalDivider()

            // Row 5: miscellaneous tuning (compound switch)
            SwitchPreference(
                checked = advancedEnabled,
                onCheckedChange = { checked ->
                    advancedEnabled = checked
                    SettingsConfig.setAdvancedEntryEnabled(context, checked)
                },
                shortcutOnlyWhenChecked = true,
                onClick = {
                    openSubPage(context, SettingsNavContract.ACTION_ADVANCED_MISC, SettingsNavContract.PAGE_ADVANCED_MISC)
                },
                title = OplusWorkshopUiText.getNavSectionAdvancedTuning(context),
                summary = stringResource(R.string.system_nav_advanced_summary),
                shortcutHint = stringResource(R.string.system_nav_advanced_hint)
            )
        }
    }
}
