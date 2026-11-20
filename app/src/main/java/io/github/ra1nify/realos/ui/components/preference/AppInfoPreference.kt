package io.github.ra1nify.realos.ui.components.preference

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * App info header: app icon, app name, version.
 *
 * rmuix `AppInfoPreference` facade over coui: coui has no equivalent AppInfo component;
 * the layout and the long-press-to-copy-version behavior are rewritten after rmuix;
 * colors and typography come from [AppTheme].
 *
 * Differences from the rmuix implementation:
 * - colors: `AppInfoPreferenceColors` keeps the five rmuix fields; defaults are realOS theme colors;
 * - copy bubble: coui has no tooltip component; a Compose [Popup] draws the same bubble above the version;
 * - other parameters (`iconSize`, `iconCornerRadius`, `insideMargin`, `onVersionCopied`, etc.) keep their semantics.
 *
 * @param appName App name.
 * @param modifier Modifier applied to the header container.
 * @param appVersion Version text; `null` hides the version line.
 * @param icon App icon content, clipped by the component to [iconSize] / [iconCornerRadius].
 * @param copyText Button text inside the copy bubble; `null` disables long-press copy.
 * @param onVersionCopied Callback after the version is copied to the clipboard.
 * @param colors Header and copy bubble colors.
 * @param iconSize Icon size.
 * @param iconCornerRadius Icon corner radius.
 * @param insideMargin Header padding.
 */
@Composable
fun AppInfoPreference(
    appName: String,
    modifier: Modifier = Modifier,
    appVersion: String? = null,
    icon: (@Composable () -> Unit)? = null,
    copyText: String? = null,
    onVersionCopied: ((String) -> Unit)? = null,
    colors: AppInfoPreferenceColors = AppInfoPreferenceDefaults.appInfoColors(),
    iconSize: Dp = AppInfoPreferenceDefaults.IconSize,
    iconCornerRadius: Dp = AppInfoPreferenceDefaults.IconCornerRadius,
    insideMargin: PaddingValues = AppInfoPreferenceDefaults.InsideMargin,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(insideMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier =
                    Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape(iconCornerRadius)),
            ) {
                icon()
            }
        }
        AppInfoPreferenceTitle(text = appName, color = colors.nameColor)
        if (appVersion != null) {
            AppInfoPreferenceVersion(
                appVersion = appVersion,
                copyText = copyText,
                onVersionCopied = onVersionCopied,
                colors = colors,
            )
        }
    }
}

/** App name: 20sp, Medium, centered. */
@Composable
private fun AppInfoPreferenceTitle(
    text: String,
    color: Color,
) {
    Text(
        modifier = Modifier.padding(top = AppInfoPreferenceDefaults.NameTopMargin),
        text = text,
        style = AppTheme.textStyles.title3.copy(fontSize = AppInfoPreferenceDefaults.NameFontSize),
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** Version: 12sp, secondary color, centered; with [copyText], a long press opens the copy bubble above the version. */
@Composable
private fun AppInfoPreferenceVersion(
    appVersion: String,
    copyText: String?,
    onVersionCopied: ((String) -> Unit)?,
    colors: AppInfoPreferenceColors,
) {
    var bubbleVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val bubbleSpacingPx = with(density) { AppInfoPreferenceDefaults.CopyBubbleSpacing.roundToPx() }
    // Copy bubble position: horizontally centered above the version, clamped to the window.
    val bubblePositionProvider =
        remember(bubbleSpacingPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val centerX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                    val aboveY = anchorBounds.top - popupContentSize.height - bubbleSpacingPx
                    return IntOffset(centerX.coerceIn(0, maxX), aboveY.coerceAtLeast(0))
                }
            }
        }

    fun copyVersion() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText(appVersion, appVersion))
        bubbleVisible = false
        onVersionCopied?.invoke(appVersion)
    }

    Box(contentAlignment = Alignment.Center) {
        Text(
            modifier =
                Modifier
                    .padding(top = AppInfoPreferenceDefaults.VersionTopMargin)
                    .then(
                        if (copyText == null) {
                            Modifier
                        } else {
                            Modifier.pointerInput(appVersion, copyText) {
                                detectTapGestures(onLongPress = { bubbleVisible = true })
                            }
                        },
                    ),
            text = appVersion,
            style = AppTheme.textStyles.footnote1.copy(fontSize = AppInfoPreferenceDefaults.VersionFontSize),
            color = colors.versionColor,
            textAlign = TextAlign.Center,
        )
        if (copyText != null && bubbleVisible) {
            Popup(
                popupPositionProvider = bubblePositionProvider,
                onDismissRequest = { bubbleVisible = false },
                properties = PopupProperties(focusable = true),
            ) {
                CopyLabel(text = copyText, colors = colors, onClick = { copyVersion() })
            }
        }
    }
}

/** Copy bubble body: rounded corners, bubble background, press feedback; a click copies the version. */
@Composable
private fun CopyLabel(
    text: String,
    colors: AppInfoPreferenceColors,
    onClick: () -> Unit,
) {
    val shape = remember { RoundedCornerShape(AppInfoPreferenceDefaults.CopyLabelCornerRadius) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(colors.copyBubbleColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .background(if (pressed) colors.copyLabelPressedColor else Color.Transparent)
                .defaultMinSize(
                    minWidth = AppInfoPreferenceDefaults.CopyLabelMinWidth,
                    minHeight = AppInfoPreferenceDefaults.CopyLabelMinHeight,
                )
                .padding(horizontal = AppInfoPreferenceDefaults.CopyLabelHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTheme.textStyles.body1.copy(fontSize = AppInfoPreferenceDefaults.CopyLabelFontSize),
            color = colors.copyLabelColor,
            maxLines = 1,
        )
    }
}

/** [AppInfoPreference] colors. */
@Immutable
data class AppInfoPreferenceColors(
    val nameColor: Color,
    val versionColor: Color,
    val copyLabelColor: Color,
    val copyBubbleColor: Color,
    val copyLabelPressedColor: Color,
)

/** [AppInfoPreference] default sizes and colors. */
object AppInfoPreferenceDefaults {
    /** Icon size. */
    val IconSize = 60.dp

    /** Icon corner radius. */
    val IconCornerRadius = 16.dp

    /** Header top padding (About page metric). */
    val IconTopMargin = 42.dp

    /** App name top padding. */
    val NameTopMargin = 8.dp

    /** Version top padding. */
    val VersionTopMargin = 4.dp

    /** Header bottom padding. */
    val BottomMargin = 8.dp

    /** App name font size. */
    val NameFontSize = 20.sp

    /** Version font size. */
    val VersionFontSize = 12.sp

    /** Copy bubble min width. */
    val CopyLabelMinWidth = 76.dp

    /** Copy bubble min height. */
    val CopyLabelMinHeight = 48.dp

    /** Copy bubble text horizontal padding. */
    val CopyLabelHorizontalPadding = 24.dp

    /** Copy bubble corner radius. */
    val CopyLabelCornerRadius = 12.dp

    /** Copy bubble text font size. */
    val CopyLabelFontSize = 14.sp

    /** Gap between the version and the copy bubble. */
    val CopyBubbleSpacing = 16.dp

    /** Default padding: top matches the full About page padding; bottom is 8dp. */
    val InsideMargin = PaddingValues(top = IconTopMargin, bottom = BottomMargin)

    /**
     * Default colors: app name primary text, version secondary text, copy bubble surface and pressed colors,
     * all from the realOS theme.
     */
    @Composable
    fun appInfoColors(
        nameColor: Color? = null,
        versionColor: Color? = null,
        copyLabelColor: Color? = null,
        copyBubbleColor: Color? = null,
        copyLabelPressedColor: Color? = null,
    ): AppInfoPreferenceColors {
        val theme = AppTheme.colorScheme
        return AppInfoPreferenceColors(
            nameColor = nameColor ?: theme.onSurface,
            versionColor = versionColor ?: theme.onSurfaceSecondary,
            copyLabelColor = copyLabelColor ?: theme.onSurface,
            copyBubbleColor = copyBubbleColor ?: theme.surfaceContainerHigh,
            copyLabelPressedColor = copyLabelPressedColor ?: theme.dividerLine,
        )
    }
}
