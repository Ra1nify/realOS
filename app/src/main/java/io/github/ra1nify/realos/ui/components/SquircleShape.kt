package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.itos.coui.shape.CouiShape
import com.itos.coui.shape.CouiCornerWeight

/**
 * realOS continuous-curvature rounded shape.
 *
 * Maps the rmuix `SquircleShape(cornerRadius = ..., extension = ..., squircleEnabled = ...)` to
 * coui [CouiShape]; the outline uses the same G2 equal-radius curve as coui materials and
 * outline light. `.clip(SquircleShape(...))` aligns with coui component corners.
 *
 * Call shape matches rmuix: the first parameter is `cornerRadius`; `SquircleShape(10.dp)` stays valid.
 *
 * rmuix `extension` (corner tile multiplier of the radius, default 1.1) and coui `cornerWeight`
 * (Le norm exponent, >= 2) use different units; the facade passes the coui default weight
 * [CouiCornerWeight] (2.5, matching the material outline).
 *
 * @param cornerRadius Corner radius.
 * @param cornerWeight coui corner weight, >= 2.
 * @param squircleEnabled false falls back to [RoundedCornerShape].
 */
@Immutable
data class SquircleShape(
    val cornerRadius: Dp,
    val cornerWeight: Float = CouiCornerWeight,
    val squircleEnabled: Boolean = true,
) : Shape {

    init {
        require(cornerRadius.value.isFinite() && cornerRadius.value >= 0f) {
            "cornerRadius must be a finite non-negative value"
        }
        require(cornerWeight.isFinite() && cornerWeight >= 2f) {
            "cornerWeight must be finite and at least 2 (matching the coui outline shader)"
        }
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline =
        if (squircleEnabled) {
            CouiShape(radius = cornerRadius, cornerWeight = cornerWeight)
                .createOutline(size, layoutDirection, density)
        } else {
            RoundedCornerShape(cornerRadius)
                .createOutline(size, layoutDirection, density)
        }
}
