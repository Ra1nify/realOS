package io.github.ra1nify.realos.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.ra1nify.realos.ui.theme.AppIcons

/**
 * realOS icon set.
 *
 * Two icon sources exist:
 *
 * - `CouiIcons`: ColorOS style, each icon a `@Composable get()` that resolves a drawable with
 *   `remember`; readable only during composition.
 * - M3 `Icons`: plain `ImageVector`, readable in any context.
 *
 * This object exposes M3 `ImageVector` values on purpose. COUI icons resolve a drawable through
 * `LocalContext.current.createConfigurationContext(...)`, which only sees the resources of the
 * process that owns the composition. In a host process (settings, SystemUI, keyboard) the module
 * APK's resources are absent from that table, so a COUI icon throws `Resources$NotFoundException`
 * and kills the host. M3 vectors are pure Kotlin data and are safe in both the realOS process and
 * any injected host process.
 */
object AppIcons {

    val ArrowBack: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack

    val ChevronRight: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight

    val Close: ImageVector = Icons.Outlined.Close

    val Add: ImageVector = Icons.Outlined.Add

    val Info: ImageVector = Icons.Outlined.Info

    val Camera: ImageVector = Icons.Outlined.PhotoCamera

    /** Outlined keyboard icon. */
    val Keyboard: ImageVector = Icons.Outlined.Keyboard

    /** Filled keyboard icon. */
    val KeyboardFilled: ImageVector = Icons.Filled.Keyboard

    /** Restart arrow used by the spec page top-bar action. */
    val Refresh: ImageVector = Icons.Outlined.Refresh
}
