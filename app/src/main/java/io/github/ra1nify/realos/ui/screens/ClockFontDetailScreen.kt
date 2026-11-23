package io.github.ra1nify.realos.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.clockfont.ClockFontError
import io.github.ra1nify.realos.clockfont.ClockFontImportResult
import io.github.ra1nify.realos.clockfont.ClockFontRecord
import io.github.ra1nify.realos.clockfont.ClockFontStore
import io.github.ra1nify.realos.clockfont.ClockFontType
import io.github.ra1nify.realos.restart.RestartExecutor
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.restart.RestartResult
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.ui.components.OverlayDialog
import io.github.ra1nify.realos.ui.components.TextButton
import io.github.ra1nify.realos.ui.components.preference.JumpPreference
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.ra1nify.realos.ui.components.Text

@Composable
fun ClockFontDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<ClockFontRecord>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ClockFontRecord?>(null) }

    fun reload() {
        scope.launch {
            records = withContext(Dispatchers.IO) {
                ClockFontStore.list(context, includeHidden = true)
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) {
                ClockFontStore.importFromUri(context, uri)
            }
            importing = false
            when (result) {
                is ClockFontImportResult.Success -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.clock_font_import_success, result.record.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                    reload()
                }
                is ClockFontImportResult.Duplicate -> {
                    errorMessage = context.getString(R.string.clock_font_duplicate, result.displayName)
                }
                is ClockFontImportResult.Failure -> {
                    errorMessage = context.getString(result.error.messageRes())
                }
            }
        }
    }

    ModuleDetailScaffold(
        title = stringResource(R.string.clock_font_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        tipsTitle = stringResource(R.string.clock_font_tips_title),
        tipsMessage = stringResource(R.string.clock_font_tips_message),
        onRestart = {
            scope.launch {
                val appResult = RestartExecutor.restart(context, RestartPolicies.clockFont)
                val systemUiResult = RestartExecutor.restart(context, RestartPolicies.systemUi)
                val success = appResult is RestartResult.Success &&
                    systemUiResult is RestartResult.Success
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) {
                            R.string.restart_success_clock_font
                        } else {
                            R.string.restart_failed_need_root
                        }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        restartContentDescription = stringResource(R.string.clock_font_restart_action)
    ) {
        BasicComponentGroup(
            title = stringResource(R.string.clock_font_section_imported),
            footer = stringResource(R.string.clock_font_footer),
            headerContent = {
                TextButton(
                    text = stringResource(R.string.clock_font_import_action),
                    enabled = !importing,
                    onClick = { picker.launch(arrayOf("*/*")) }
                )
            }
        ) {
            if (importing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = stringResource(R.string.clock_font_importing),
                        kind = CouiTextKind.GroupFooter,
                        color = AppTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            if (records.isEmpty() && !importing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = stringResource(R.string.clock_font_empty),
                        kind = CouiTextKind.ItemSummary,
                        color = AppTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            records.forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider()
                val typeLabel = stringResource(
                    if (record.type == ClockFontType.FLEX) {
                        R.string.clock_font_type_flex
                    } else {
                        R.string.clock_font_type_fixed
                    }
                )
                val weightLabel = stringResource(
                    if (record.dynamicWeight) {
                        R.string.clock_font_weight_dynamic
                    } else {
                        R.string.clock_font_weight_still
                    }
                )
                val sizeLabel = stringResource(R.string.clock_font_size_kb, record.sizeBytes / 1024f)
                JumpPreference(
                    title = record.displayName,
                    summary = stringResource(R.string.clock_font_summary, typeLabel, sizeLabel),
                    statusText = if (record.hidden) stringResource(R.string.clock_font_hidden) else null,
                    endActions = {
                        if (record.hidden) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    text = stringResource(R.string.clock_font_restore),
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                ClockFontStore.setHidden(context, record.id, false)
                                            }
                                            reload()
                                        }
                                    }
                                )
                                TextButton(
                                    text = stringResource(R.string.clock_font_delete),
                                    onClick = { pendingDelete = record }
                                )
                            }
                        } else {
                            TextButton(
                                text = stringResource(R.string.clock_font_delete),
                                onClick = { pendingDelete = record }
                            )
                        }
                    }
                )
                HorizontalDivider()
                SwitchPreference(
                    checked = record.dynamicWeight,
                    onCheckedChange = { checked ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ClockFontStore.setDynamicWeight(context, record.id, checked)
                            }
                            reload()
                        }
                    },
                    title = stringResource(R.string.clock_font_weight_toggle_title),
                    summary = weightLabel
                )
            }
        }
    }

    pendingDelete?.let { record ->
        OverlayDialog(
            show = true,
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.clock_font_delete_title),
            summary = stringResource(R.string.clock_font_delete_message),
            confirmButtonText = stringResource(R.string.clock_font_delete_confirm),
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ClockFontStore.delete(context, record.id)
                    }
                    reload()
                }
            },
            dismissButtonText = stringResource(R.string.clock_font_cancel),
            onDismiss = { pendingDelete = null }
        ) { }
    }

    errorMessage?.let { message ->
        OverlayDialog(
            show = true,
            onDismissRequest = { errorMessage = null },
            title = stringResource(R.string.clock_font_import_failed_title),
            summary = message,
            confirmButtonText = stringResource(R.string.dialog_got_it),
            onConfirm = { errorMessage = null }
        ) { }
    }
}

private fun ClockFontError.messageRes(): Int = when (this) {
    ClockFontError.NOT_TTF -> R.string.clock_font_error_not_ttf
    ClockFontError.TOO_SMALL -> R.string.clock_font_error_too_small
    ClockFontError.TOO_LARGE -> R.string.clock_font_error_too_large
    ClockFontError.MALFORMED -> R.string.clock_font_error_malformed
    ClockFontError.UNSUPPORTED_FORMAT -> R.string.clock_font_error_unsupported_format
    ClockFontError.MISSING_GLYPHS -> R.string.clock_font_error_missing_glyphs
    ClockFontError.NO_VARIATION -> R.string.clock_font_error_no_variation
    ClockFontError.WEIGHT_RANGE -> R.string.clock_font_error_weight_range
    ClockFontError.HEIGHT_RANGE -> R.string.clock_font_error_height_range
    ClockFontError.AVAR_UNSUPPORTED -> R.string.clock_font_error_avar_unsupported
    ClockFontError.IO_ERROR -> R.string.clock_font_error_io
    ClockFontError.DUPLICATE -> R.string.clock_font_error_duplicate
}
