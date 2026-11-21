package io.github.ra1nify.realos.xposed.hooks.launcher.ui

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * One selectable recent task layout.
 *
 * [animBrightId] / [animDarkId] are view id names in the host layout (such as `img_stacked_bright`),
 * not raw resource names; the preview reuses the host-inflated views, which play from their attributes.
 *
 * A plugin style has no official asset and draws through [customPreview].
 */
data class RecentsStyleOption(
    /** Value written to `Settings.Secure key_recent_style` */
    val id: String,
    /** Display name resolved per host language; evaluated at render time */
    val label: (Context) -> String,
    val animBrightId: String? = null,
    val animDarkId: String? = null,
    val customPreview: (@Composable () -> Unit)? = null
)

/**
 * Recent task style picker inside the host settings page.
 *
 * Interaction: swiping only browses and does not change the selection; tapping a page selects it.
 * The selection indicator is a ring with an inner dot, matching the official RadioButton semantics.
 */
@Composable
fun RecentsStylePicker(
    options: List<RecentsStyleOption>,
    state: PickerState,
    onSelect: (String) -> Unit
) {
    val startPage = options.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage) { options.size }
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 28.dp),
        pageSpacing = 10.dp,
        pageSize = PageSize.Fixed(200.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) { page ->
        val option = options[page]
        val selected = option.id == state.selectedId

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (!selected) {
                        state.selectedId = option.id
                        onSelect(option.id)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val custom = option.customPreview
            if (custom != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { custom() }
            } else {
                LottiePreview(
                    idName = if (dark) option.animDarkId else option.animBrightId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            Text(
                text = option.label(context),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) AppTheme.colorScheme.primary else AppTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )

            SelectionRing(selected = selected, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Selection indicator: ring plus inner dot */
@Composable
private fun SelectionRing(selected: Boolean, modifier: Modifier = Modifier) {
    val accent = AppTheme.colorScheme.primary
    val idle = AppTheme.colorScheme.onBackground.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .size(20.dp)
            .border(
                width = if (selected) 2.dp else 1.5.dp,
                color = if (selected) accent else idle,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, CircleShape)
            )
        }
    }
}

/** Observable picker state, synced by the host side on every bind */
class PickerState(initial: String) {
    var selectedId by mutableStateOf(initial)
}

@Composable
private fun LottiePreview(idName: String?, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            idName?.let { HostLottie.create(ctx, it) } ?: View(ctx)
        }
    )
}

/**
 * Reads the Lottie view from the host `recent_style_setting_layout`.
 *
 * No reflection new + setAnimation: R8 renames the host `playAnimation()` to a single letter, and
 * the original name throws `NoSuchMethodException`, leaving the animation on its first frame.
 * The inflated host layout already carries `lottie_autoPlay` / `lottie_loop` attributes, so the
 * behavior matches the official view.
 */
private object HostLottie {

    private const val TAG = "RealOS-HostLottie"
    private const val HOST_PACKAGE = "com.android.launcher"
    private const val HOST_LAYOUT_NAME = "recent_style_setting_layout"

    fun create(context: Context, idName: String): View? {
        val layoutId = runCatching {
            context.resources.getIdentifier(HOST_LAYOUT_NAME, "layout", HOST_PACKAGE)
        }.getOrDefault(0)
        if (layoutId == 0) {
            Log.w(TAG, "Host layout $HOST_LAYOUT_NAME not found")
            return null
        }

        val root = runCatching {
            LayoutInflater.from(context).inflate(layoutId, null, false)
        }.getOrNull() ?: run {
            Log.w(TAG, "Failed to inflate the host layout")
            return null
        }

        val viewId = runCatching {
            context.resources.getIdentifier(idName, "id", HOST_PACKAGE)
        }.getOrDefault(0)
        val view = if (viewId != 0) root.findViewById<View>(viewId) else null
        if (view == null) {
            Log.w(TAG, "View $idName not found")
            return null
        }

        // Removes it from the host layout (the XML marks it gone; the host toggles visibility by theme)
        (view.parent as? ViewGroup)?.removeView(view)
        view.visibility = View.VISIBLE
        // The host layout uses LinearLayout.LayoutParams; replaced here for Compose measurement
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        (view as? ImageView)?.scaleType = ImageView.ScaleType.CENTER_INSIDE
        return view
    }
}
