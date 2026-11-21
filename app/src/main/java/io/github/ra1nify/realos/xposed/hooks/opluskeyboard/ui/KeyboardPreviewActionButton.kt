package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.ui.theme.AppTheme
import io.github.ra1nify.realos.ui.theme.AppIcons
import io.github.ra1nify.realos.ui.components.Icon
import io.github.ra1nify.realos.ui.components.IconButton

/**
 * Shared top-bar keyboard preview show/hide action button.
 *
 * 1. Tap toggle: shows the IME when inactive (filled icon), hides it when active (outline icon).
 * 2. Back interception: with the IME up, the back key or back gesture hides it first,
 *    the page stays open.
 * 3. In-host invocation: hidden anchor text field plus LocalSoftwareKeyboardController / IMM.
 */
@Composable
fun KeyboardPreviewActionButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val imm = remember(context) { context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager }
    var dummyText by remember { mutableStateOf("") }

    // Reactive observation of whether the IME is currently shown
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    // Back key interception: with the keyboard up, back only hides it (intercepted only where a back dispatcher exists)
    val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    if (backDispatcherOwner != null) {
        BackHandler(enabled = isImeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
            (context as? Activity)?.currentFocus?.let { view ->
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    // Hidden text field (0 size, 0 alpha) used as the IME focus anchor
    BasicTextField(
        value = dummyText,
        onValueChange = { dummyText = it },
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboardController?.hide()
            focusManager.clearFocus()
        })
    )

    // ColorOS 17 unified top-bar round action button (rmuix has no TopAppBarDefaults.ActionButton; uses IconButton + Icon)
    IconButton(
        onClick = {
            if (!isImeVisible) {
                focusRequester.requestFocus()
                keyboardController?.show()
                (context as? Activity)?.currentFocus?.let { view ->
                    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                keyboardController?.hide()
                focusManager.clearFocus()
                (context as? Activity)?.currentFocus?.let { view ->
                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isImeVisible) AppIcons.KeyboardFilled else AppIcons.Keyboard,
            contentDescription = OplusWorkshopUiText.getToolbarPreviewKeyboardDesc(context),
            tint = if (isImeVisible) AppTheme.colorScheme.primary else AppTheme.colorScheme.onBackground
        )
    }
}
