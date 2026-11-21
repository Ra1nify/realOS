package io.github.ra1nify.realos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * realOS top-tips [TopTips] facade.
 *
 * coui has no matching component. The facade draws a rounded overlay with title/content, optional
 * icon, action and close button, animates with [AnimatedVisibility] and hides after [autoDismissMillis].
 * Call shape: `TopTips(content = ..., title = ..., icon = ...)`.
 *
 * Defaults to a persistent card; a non-zero [autoDismissMillis] hides the tips automatically. rmuix
 * `colors` and `marquee` are absent; colors use `AppTheme.colorScheme`.
 *
 * @param content Body text, required.
 * @param title Optional title.
 * @param icon Optional leading icon slot (such as [Icon]).
 * @param actionText Optional inline action text.
 * @param onActionClick Inline action callback; null makes the action text non-clickable.
 * @param onClose Non-null shows a close button that collapses the tips immediately.
 * @param closeContentDescription Accessibility description of the close button.
 * @param autoDismissMillis Auto-dismiss delay; 0 keeps the tips visible.
 */
@Composable
fun TopTips(
    content: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: (@Composable () -> Unit)? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    closeContentDescription: String? = null,
    autoDismissMillis: Long = 0L,
) {
    var visible by remember(content, title) { mutableStateOf(true) }
    LaunchedEffect(content, title, autoDismissMillis) {
        visible = true
        if (autoDismissMillis > 0L) {
            delay(autoDismissMillis)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { fullHeight -> -fullHeight / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { fullHeight -> -fullHeight / 3 },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        kind = CouiTextKind.ItemValue,
                        color = AppTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = content,
                    kind = CouiTextKind.ItemSummary,
                    color = AppTheme.colorScheme.onSurfaceSecondary,
                )
            }
            if (actionText != null) {
                Text(
                    text = actionText,
                    modifier =
                        Modifier
                            .padding(start = 12.dp)
                            .clickable(enabled = onActionClick != null) { onActionClick?.invoke() },
                    kind = CouiTextKind.ItemSummary,
                    color = AppTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            if (onClose != null) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = closeContentDescription,
                    tint = AppTheme.colorScheme.onSurfaceSecondary,
                    modifier =
                        Modifier
                            .padding(start = 12.dp)
                            .size(20.dp)
                            .clickable(onClick = onClose),
                )
            }
        }
    }
}
