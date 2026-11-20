// Ported from rmuix-extend image/ImageCropper.kt (compose-rmuix-ui, Apache-2.0), realOS-maintained copy.
// Uses common Compose foundation / ui APIs only; implementation matches apart from the package name.

package io.github.ra1nify.realos.ui.extend.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Image cropper: a fixed viewport over a pannable, zoomable image, with a mask
 * outside the viewport.
 *
 * `com.coui.appcompat` has no Compose cropper. The component takes an
 * [androidx.compose.ui.graphics.ImageBitmap], not a platform Uri; the caller decodes it.
 *
 * Gestures: one-finger pan, two-finger zoom, no rotation.
 *
 * Drawing is not clipped. At cover scale the image exceeds the viewport on one
 * axis; the caller applies `Modifier.clip(...)`.
 *
 * @param state Crop state, usually from [rememberImageCropperState].
 * @param modifier Modifier applied to the cropper.
 * @param cropRatio Viewport aspect ratio; `1f` avatar, `1.6f` card cover.
 * @param shape Viewport outline shape.
 * @param maskColor Mask color outside the viewport.
 * @param enabled Whether gestures are accepted.
 */
@Composable
fun ImageCropper(
    state: ImageCropperState,
    modifier: Modifier = Modifier,
    cropRatio: Float = 1f,
    shape: Shape = RoundedCornerShape(16.dp),
    maskColor: Color = Color(ImageCropperDefaults.MASK_COLOR_ARGB),
    enabled: Boolean = true,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(cropRatio.coerceAtLeast(0.1f)),
    ) {
        val viewport =
            Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()).takeIf {
                it.width > 0f && it.height > 0f
            } ?: Size.Zero

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        state.viewport = viewport
                        // Re-clamps the offset on size change.
                        state.applyTransform()
                    }.pointerInput(enabled, viewport) {
                        if (!enabled) return@pointerInput
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            state.applyTransform(zoom = zoom, pan = pan, focal = centroid)
                        }
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cover =
                    coverScale(
                        sourceWidth = state.image.width.toFloat(),
                        sourceHeight = state.image.height.toFloat(),
                        viewportWidth = size.width,
                        viewportHeight = size.height,
                    )
                val drawnWidth = state.image.width * cover * state.scale
                val drawnHeight = state.image.height * cover * state.scale
                drawImage(
                    image = state.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(state.image.width, state.image.height),
                    dstOffset = IntOffset(state.offset.x.toInt(), state.offset.y.toInt()),
                    dstSize =
                        IntSize(
                            drawnWidth.toInt().coerceAtLeast(1),
                            drawnHeight.toInt().coerceAtLeast(1),
                        ),
                    filterQuality = FilterQuality.High,
                )
                // Mask: viewport minus viewport outline.
                val outline = shape.createOutline(size, layoutDirection, this)
                val scrim =
                    Path.combine(
                        PathOperation.Difference,
                        Path().apply { addRect(Rect(Offset.Zero, size)) },
                        Path().apply { addOutline(outline) },
                    )
                drawPath(path = scrim, color = maskColor)
                drawPath(
                    path = Path().apply { addOutline(outline) },
                    color = Color.White.copy(alpha = 0.2f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}
