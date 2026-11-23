package io.github.ra1nify.realos.ui.screens

import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.restart.RestartExecutor
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.restart.toastMessageRes
import io.github.ra1nify.realos.featureswitch.FeatureSwitchConfig
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference

/**
 * System settings (`com.android.settings`) feature switch detail page (staging).
 *
 * Hosts software feature switches and restarts the settings and wallpaper processes.
 */
@Composable
fun SettingsDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── OnePlus and brand features ──
    LaunchedEffect(Unit) {
        FeatureSwitchConfig.syncToRemote(context)
    }

    var onePlusBrandEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_ONEPLUS_BRAND))
    }
    var onePlusRedOneEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_ONEPLUS_RED_ONE))
    }

    // ── System performance and scheduling ──
    var gtModeEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_GT_MODE))
    }
    var appFrozenEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_APP_FROZEN))
    }
    var pureModeEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_PURE_MODE))
    }
    var fluidCloudEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_FLUID_CLOUD))
    }
    var miniCapsuleEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_MINI_CAPSULE))
    }
    var customHideAppEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_CUSTOM_HIDE_APP))
    }
    var taskbarEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_TASKBAR))
    }

    // ── Display quality and eye comfort ──
    var videoColorEnhanceEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_VIDEO_COLOR_ENHANCE))
    }
    var videoOsieEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_VIDEO_OSIE))
    }
    var videoSrEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_VIDEO_SR))
    }
    var appHighRefreshEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_APP_HIGH_REFRESH))
    }
    var pwmRebootCloseEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_PWM_REBOOT_CLOSE))
    }
    var comfortColorTempEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_COMFORT_COLOR_TEMP))
    }
    var singlePulseRefreshEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_SINGLE_PULSE_REFRESH))
    }
    var dcMin2NitEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_DC_MIN_2NIT))
    }

    // ── Audio routing and media enhancements ──
    var dualHeadphoneEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_DUAL_HEADPHONE))
    }
    var multiSoundsPlayEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_MULTI_SOUNDS_PLAY))
    }
    var superVolumeEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_SUPER_VOLUME))
    }
    var karaokeEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_KARAOKE))
    }
    var dolbyEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_DOLBY))
    }
    var diracEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_DIRAC))
    }

    // ── Permissions and developer restriction unlocks ──
    var oemUnlockBypassEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_DISABLE_OEM_UNLOCK_BYPASS))
    }
    var disallowDisableBypassEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_APP_DISALLOW_DISABLE_BYPASS))
    }
    var clearCacheBypassEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_CLEAR_CACHE_DISABLED_BYPASS))
    }
    var volteBypassEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_VOLTE_UNAVAILABLE_BYPASS))
    }
    var vowifiBypassEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_VOWIFI_UNAVAILABLE_BYPASS))
    }
    var videoWallpaperEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_VIDEO_WALLPAPER))
    }
    var switchPrevAppBypassEnabled by remember {
        mutableStateOf(FeatureSwitchConfig.isEnabled(context, FeatureSwitchConfig.KEY_DISALLOW_SWITCH_PREV_APP_BYPASS))
    }

    ModuleDetailScaffold(
        title = stringResource(R.string.settings_detail_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        tipsTitle = stringResource(R.string.settings_tips_title),
        tipsMessage = stringResource(R.string.settings_tips_message),
        onRestart = {
            scope.launch {
                val policy = RestartPolicies.settings
                val result = RestartExecutor.restart(context, policy)
                Toast.makeText(
                    context,
                    context.getString(result.toastMessageRes(policy)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    ) {
        if (BuildConfig.SCOPE_FONT) {
            SystemFontConfigGroup()
        }

        // ── OnePlus and brand features ──
        BasicComponentGroup(
            title = stringResource(R.string.settings_sec_oneplus)
        ) {
            SwitchPreference(
                title = stringResource(R.string.feat_oneplus_brand_title),
                summary = stringResource(R.string.feat_oneplus_brand_summary),
                checked = onePlusBrandEnabled,
                onCheckedChange = { checked ->
                    onePlusBrandEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_ONEPLUS_BRAND, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_oneplus_red_one_title),
                summary = stringResource(R.string.feat_oneplus_red_one_summary),
                checked = onePlusRedOneEnabled,
                onCheckedChange = { checked ->
                    onePlusRedOneEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_ONEPLUS_RED_ONE, checked)
                }
            )
        }

        // ── System performance and scheduling ──
        BasicComponentGroup(
            title = stringResource(R.string.settings_sec_perf)
        ) {
            SwitchPreference(
                title = stringResource(R.string.feat_gt_mode_title),
                summary = stringResource(R.string.feat_gt_mode_summary),
                checked = gtModeEnabled,
                onCheckedChange = { checked ->
                    gtModeEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_GT_MODE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_app_frozen_title),
                summary = stringResource(R.string.feat_app_frozen_summary),
                checked = appFrozenEnabled,
                onCheckedChange = { checked ->
                    appFrozenEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_APP_FROZEN, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_pure_mode_title),
                summary = stringResource(R.string.feat_pure_mode_summary),
                checked = pureModeEnabled,
                onCheckedChange = { checked ->
                    pureModeEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_PURE_MODE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_fluid_cloud_title),
                summary = stringResource(R.string.feat_fluid_cloud_summary),
                checked = fluidCloudEnabled,
                onCheckedChange = { checked ->
                    fluidCloudEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_FLUID_CLOUD, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_mini_capsule_title),
                summary = stringResource(R.string.feat_mini_capsule_summary),
                checked = miniCapsuleEnabled,
                onCheckedChange = { checked ->
                    miniCapsuleEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_MINI_CAPSULE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_custom_hide_app_title),
                summary = stringResource(R.string.feat_custom_hide_app_summary),
                checked = customHideAppEnabled,
                onCheckedChange = { checked ->
                    customHideAppEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_CUSTOM_HIDE_APP, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_taskbar_title),
                summary = stringResource(R.string.feat_taskbar_summary),
                checked = taskbarEnabled,
                onCheckedChange = { checked ->
                    taskbarEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_TASKBAR, checked)
                }
            )
        }

        // ── Display quality and eye comfort ──
        BasicComponentGroup(
            title = stringResource(R.string.settings_sec_display)
        ) {
            SwitchPreference(
                title = stringResource(R.string.feat_video_color_enhance_title),
                summary = stringResource(R.string.feat_video_color_enhance_summary),
                checked = videoColorEnhanceEnabled,
                onCheckedChange = { checked ->
                    videoColorEnhanceEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_VIDEO_COLOR_ENHANCE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_video_osie_title),
                summary = stringResource(R.string.feat_video_osie_summary),
                checked = videoOsieEnabled,
                onCheckedChange = { checked ->
                    videoOsieEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_VIDEO_OSIE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_video_sr_title),
                summary = stringResource(R.string.feat_video_sr_summary),
                checked = videoSrEnabled,
                onCheckedChange = { checked ->
                    videoSrEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_VIDEO_SR, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_app_high_refresh_title),
                summary = stringResource(R.string.feat_app_high_refresh_summary),
                checked = appHighRefreshEnabled,
                onCheckedChange = { checked ->
                    appHighRefreshEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_APP_HIGH_REFRESH, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_pwm_reboot_close_title),
                summary = stringResource(R.string.feat_pwm_reboot_close_summary),
                checked = pwmRebootCloseEnabled,
                onCheckedChange = { checked ->
                    pwmRebootCloseEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_PWM_REBOOT_CLOSE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_comfort_color_temp_title),
                summary = stringResource(R.string.feat_comfort_color_temp_summary),
                checked = comfortColorTempEnabled,
                onCheckedChange = { checked ->
                    comfortColorTempEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_COMFORT_COLOR_TEMP, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_single_pulse_refresh_title),
                summary = stringResource(R.string.feat_single_pulse_refresh_summary),
                checked = singlePulseRefreshEnabled,
                onCheckedChange = { checked ->
                    singlePulseRefreshEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_SINGLE_PULSE_REFRESH, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_dc_min_2nit_title),
                summary = stringResource(R.string.feat_dc_min_2nit_summary),
                checked = dcMin2NitEnabled,
                onCheckedChange = { checked ->
                    dcMin2NitEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_DC_MIN_2NIT, checked)
                }
            )
        }

        // ── Audio routing and media enhancements ──
        BasicComponentGroup(
            title = stringResource(R.string.settings_sec_audio)
        ) {
            SwitchPreference(
                title = stringResource(R.string.feat_dual_headphone_title),
                summary = stringResource(R.string.feat_dual_headphone_summary),
                checked = dualHeadphoneEnabled,
                onCheckedChange = { checked ->
                    dualHeadphoneEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_DUAL_HEADPHONE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_multi_sounds_play_title),
                summary = stringResource(R.string.feat_multi_sounds_play_summary),
                checked = multiSoundsPlayEnabled,
                onCheckedChange = { checked ->
                    multiSoundsPlayEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_MULTI_SOUNDS_PLAY, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_super_volume_title),
                summary = stringResource(R.string.feat_super_volume_summary),
                checked = superVolumeEnabled,
                onCheckedChange = { checked ->
                    superVolumeEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_SUPER_VOLUME, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_karaoke_title),
                summary = stringResource(R.string.feat_karaoke_summary),
                checked = karaokeEnabled,
                onCheckedChange = { checked ->
                    karaokeEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_KARAOKE, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_dolby_title),
                summary = stringResource(R.string.feat_dolby_summary),
                checked = dolbyEnabled,
                onCheckedChange = { checked ->
                    dolbyEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_DOLBY, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_dirac_title),
                summary = stringResource(R.string.feat_dirac_summary),
                checked = diracEnabled,
                onCheckedChange = { checked ->
                    diracEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_DIRAC, checked)
                }
            )
        }

        // ── Permissions and developer restriction unlocks ──
        BasicComponentGroup(
            title = stringResource(R.string.settings_sec_permission)
        ) {
            SwitchPreference(
                title = stringResource(R.string.feat_oem_unlock_bypass_title),
                summary = stringResource(R.string.feat_oem_unlock_bypass_summary),
                checked = oemUnlockBypassEnabled,
                onCheckedChange = { checked ->
                    oemUnlockBypassEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_DISABLE_OEM_UNLOCK_BYPASS, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_disallow_disable_bypass_title),
                summary = stringResource(R.string.feat_disallow_disable_bypass_summary),
                checked = disallowDisableBypassEnabled,
                onCheckedChange = { checked ->
                    disallowDisableBypassEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_APP_DISALLOW_DISABLE_BYPASS, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_clear_cache_bypass_title),
                summary = stringResource(R.string.feat_clear_cache_bypass_summary),
                checked = clearCacheBypassEnabled,
                onCheckedChange = { checked ->
                    clearCacheBypassEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_CLEAR_CACHE_DISABLED_BYPASS, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_volte_bypass_title),
                summary = stringResource(R.string.feat_volte_bypass_summary),
                checked = volteBypassEnabled,
                onCheckedChange = { checked ->
                    volteBypassEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_VOLTE_UNAVAILABLE_BYPASS, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_vowifi_bypass_title),
                summary = stringResource(R.string.feat_vowifi_bypass_summary),
                checked = vowifiBypassEnabled,
                onCheckedChange = { checked ->
                    vowifiBypassEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_VOWIFI_UNAVAILABLE_BYPASS, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_video_wallpaper_title),
                summary = stringResource(R.string.feat_video_wallpaper_summary),
                checked = videoWallpaperEnabled,
                onCheckedChange = { checked ->
                    videoWallpaperEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_VIDEO_WALLPAPER, checked)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.feat_switch_prev_app_bypass_title),
                summary = stringResource(R.string.feat_switch_prev_app_bypass_summary),
                checked = switchPrevAppBypassEnabled,
                onCheckedChange = { checked ->
                    switchPrevAppBypassEnabled = checked
                    FeatureSwitchConfig.setEnabled(context, FeatureSwitchConfig.KEY_DISALLOW_SWITCH_PREV_APP_BYPASS, checked)
                }
            )
        }
    }
}
