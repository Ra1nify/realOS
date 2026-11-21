package io.github.ra1nify.realos.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.font.SystemFontConfig
import io.github.ra1nify.realos.font.SystemFontContract
import io.github.ra1nify.realos.font.SystemFontError
import io.github.ra1nify.realos.font.SystemFontImportResult
import io.github.ra1nify.realos.font.SystemFontRecord
import io.github.ra1nify.realos.font.SystemFontStore
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.ui.components.TextButton
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.ra1nify.realos.ui.components.Text

/**
 * Management UI for the realOS font library.
 *
 * The Settings font page displays imported fonts as cards; applying happens in
 * the Settings process through the ROM's own font manager. This screen only
 * imports, previews and deletes font files.
 */
@Composable
fun SystemFontManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<SystemFontRecord>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var entryEnabled by remember { mutableStateOf(SystemFontConfig.isEnabled(context)) }
    var currentTypeface by remember {
        mutableStateOf(readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE))
    }
    var currentName by remember {
        mutableStateOf(readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME))
    }

    fun refreshSystemFont() {
        currentTypeface = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE)
        currentName = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME)
    }

    fun reload() {
        scope.launch {
            records = withContext(Dispatchers.IO) { SystemFontStore.list(context) }
            refreshSystemFont()
        }
    }

    fun isApplied(record: SystemFontRecord): Boolean =
        currentTypeface == SystemFontContract.CUSTOM_TYPEFACE_VALUE &&
            !currentName.isNullOrBlank() &&
            currentName == record.displayName

    fun deleteFont(record: SystemFontRecord) {
        if (isApplied(record)) {
            Toast.makeText(context, R.string.system_font_in_use_delete, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) { SystemFontStore.delete(context, record.id) }
            reload()
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) {
                SystemFontStore.importFromUri(context, uri)
            }
            importing = false
            when (result) {
                is SystemFontImportResult.Success -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.system_font_import_success, result.record.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                    reload()
                }
                is SystemFontImportResult.Duplicate -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.system_font_duplicate, result.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is SystemFontImportResult.Failure -> {
                    Toast.makeText(
                        context,
                        context.getString(result.error.messageRes()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val currentLabel = when {
        currentTypeface == SystemFontContract.CUSTOM_TYPEFACE_VALUE &&
            !currentName.isNullOrBlank() -> currentName.orEmpty()
        !currentName.isNullOrBlank() &&
            currentTypeface != null &&
            currentTypeface != SystemFontContract.DEFAULT_TYPEFACE_VALUE -> currentName.orEmpty()
        else -> stringResource(R.string.system_font_current_default)
    }

    ModuleDetailScaffold(
        title = stringResource(R.string.system_font_title),
        onNavigateBack = onBack,
        modifier = modifier,
        tipsTitle = stringResource(R.string.system_font_tips_title),
        tipsMessage = stringResource(R.string.system_font_tips_message)
    ) {
        BasicComponentGroup(
            title = stringResource(R.string.system_font_section_current),
            footer = stringResource(R.string.system_font_apply_hint)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentLabel,
                    modifier = Modifier.weight(1f),
                    kind = CouiTextKind.ItemTitle,
                    color = AppTheme.colorScheme.onSurface
                )
            }
            SwitchPreference(
                checked = entryEnabled,
                onCheckedChange = { enabled ->
                    entryEnabled = enabled
                    SystemFontConfig.setEnabled(context, enabled)
                },
                title = stringResource(R.string.system_font_entry_title),
                summary = stringResource(R.string.system_font_entry_summary)
            )
        }

        BasicComponentGroup(
            title = stringResource(R.string.system_font_section_imported),
            footer = stringResource(R.string.system_font_footer),
            headerContent = {
                TextButton(
                    text = stringResource(R.string.system_font_import),
                    enabled = !importing,
                    onClick = { picker.launch(arrayOf("*/*")) }
                )
            }
        ) {
            if (records.isEmpty() && !importing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = stringResource(R.string.system_font_empty),
                        kind = CouiTextKind.ItemSummary,
                        color = AppTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            records.forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider()
                FontRow(
                    record = record,
                    isApplied = isApplied(record),
                    onDelete = { deleteFont(record) }
                )
            }
        }
    }
}

@Composable
private fun FontRow(
    record: SystemFontRecord,
    isApplied: Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sample = stringResource(R.string.system_font_preview_sample)
    val textColor = AppTheme.colorScheme.onSurface
    val typeface = remember(record.id) {
        runCatching {
            Typeface.createFromFile(SystemFontStore.fontFile(context, record))
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.displayName,
                modifier = Modifier.weight(1f),
                kind = CouiTextKind.ItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isApplied) {
                Text(
                    text = stringResource(R.string.system_font_in_use),
                    kind = CouiTextKind.GroupFooter,
                    color = AppTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
        Text(
            text = "${record.format.wireValue.uppercase()} · ${record.sizeBytes / 1024} KB",
            kind = CouiTextKind.ItemSummary,
            color = AppTheme.colorScheme.onSurfaceSecondary
        )
        key(record.id) {
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        textSize = 22f
                        setTextColor(textColor.toArgb())
                        text = sample
                    }
                },
                update = { view ->
                    view.typeface = typeface
                    view.text = sample
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                text = stringResource(R.string.system_font_delete),
                onClick = onDelete
            )
        }
    }
}

private fun readSetting(context: Context, key: String): String? = runCatching {
    Settings.System.getString(context.contentResolver, key)
}.getOrNull()

private fun SystemFontError.messageRes(): Int = when (this) {
    SystemFontError.TOO_SMALL, SystemFontError.TOO_LARGE -> R.string.system_font_error_size
    SystemFontError.UNSUPPORTED_FORMAT -> R.string.system_font_error_format
    SystemFontError.NOT_FONT,
    SystemFontError.MALFORMED,
    SystemFontError.MISSING_TABLES -> R.string.system_font_error_invalid
    SystemFontError.IO_ERROR -> R.string.system_font_error_io
}
