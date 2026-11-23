package io.github.ra1nify.realos.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import io.github.ra1nify.realos.restart.RestartExecutor
import io.github.ra1nify.realos.restart.RestartPolicy
import io.github.ra1nify.realos.restart.toastMessageRes
import io.github.ra1nify.realos.ui.theme.AppIcons
import kotlinx.coroutines.launch

/**
 * Spec page shell with one restart action.
 *
 * The restart button is shown only when [restartPolicy] resolves to at least
 * one non-NONE target. The command per host comes from RestartConfig.
 */
@Composable
fun SpecPageScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    restartPolicy: RestartPolicy,
    modifier: Modifier = Modifier,
    restartContentDescription: String? = null,
    tipsTitle: String? = null,
    tipsMessage: String? = null,
    tipsIcon: ImageVector = AppIcons.Info,
    actions: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canRestart = remember(restartPolicy) { RestartExecutor.supports(restartPolicy) }

    ModuleDetailScaffold(
        title = title,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        actions = actions,
        onRestart = if (canRestart) {
            {
                scope.launch {
                    val result = RestartExecutor.restart(context, restartPolicy)
                    Toast.makeText(
                        context,
                        context.getString(result.toastMessageRes(restartPolicy)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            null
        },
        restartContentDescription = restartContentDescription,
        tipsTitle = tipsTitle,
        tipsMessage = tipsMessage,
        tipsIcon = tipsIcon,
        content = content
    )
}
