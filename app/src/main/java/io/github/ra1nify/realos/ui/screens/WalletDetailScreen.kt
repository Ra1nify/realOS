package io.github.ra1nify.realos.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
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
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.restart.RestartExecutor
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.restart.toastMessageRes
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.wallet.WalletConfig
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.Icon
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference

/**
 * Wallet module detail page.
 *
 * The master switch covers card face customization outside NFC cards; the left
 * side opens the wallet and the top bar restarts it.
 */
@Composable
fun WalletDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nonNfcDiyEnabled by remember {
        mutableStateOf(WalletConfig.isNonNfcCardDiyEnabled(context))
    }

    LaunchedEffect(Unit) {
        WalletConfig.syncToRemote(context)
    }

    fun openWallet() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SpecPackages.WALLET)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = if (launchIntent != null) {
            runCatching {
                context.startActivity(launchIntent)
                true
            }.getOrDefault(false)
        } else false

        if (!ok) {
            Toast.makeText(
                context,
                context.getString(R.string.wallet_open_app_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ModuleDetailScaffold(
        title = rememberHostAppName(SpecPackages.WALLET, stringResource(R.string.wallet_app_name)),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        tipsTitle = stringResource(R.string.wallet_tips_title),
        tipsMessage = stringResource(R.string.wallet_tips_message),
        onRestart = {
            scope.launch {
                val policy = RestartPolicies.wallet
                val result = RestartExecutor.restart(context, policy)
                Toast.makeText(
                    context,
                    context.getString(result.toastMessageRes(policy)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    ) {
        BasicComponentGroup(
            title = stringResource(R.string.wallet_section_features_title)
        ) {
            SwitchPreference(
                title = stringResource(R.string.wallet_non_nfc_diy_title),
                summary = stringResource(R.string.wallet_non_nfc_diy_summary),
                shortcutHint = stringResource(R.string.wallet_shortcut_hint),
                // The startAction slot holds the leading icon; the row click uses onClick.
                startAction = { Icon(Icons.Outlined.CreditCard, null) },
                checked = nonNfcDiyEnabled,
                onCheckedChange = { enabled ->
                    nonNfcDiyEnabled = enabled
                    WalletConfig.setNonNfcCardDiyEnabled(context, enabled)
                },
                shortcutOnlyWhenChecked = true,
                onClick = { openWallet() }
            )
        }
    }
}
