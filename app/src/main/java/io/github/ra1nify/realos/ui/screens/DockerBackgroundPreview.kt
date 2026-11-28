package io.github.ra1nify.realos.ui.screens

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCornerWeight
import io.github.ra1nify.realos.launcher.LauncherConfig
import io.github.ra1nify.realos.ui.components.SquircleShape

/**
 * Base unit: icon centre -> its edge (circle radius / square half-width).
 * All geometry is derived from this so device tuning only needs one value.
 */
private val A = 18.dp
private val IconSize = A * 2f            // icon is wrapped in a 2a square
private val Cell = A * 4f                // each icon centred in a 4a collision box
private val DockHeight = A * 4f
private val Padding = A * 1f             // top/bottom margin and the gap between docks
private val CanvasHeight = A * 18f      // 3 docks + 4 paddings of 1a
private val DockWidth = A * 16f          // 4 collision boxes, edge to edge
private val ScreenCorner = A * 0.6f

/**
 * Dock corner radius, as the stock hotseat computes it.
 *
 * `OplusHotseat` passes `measuredHeight * BACKGROUND_SMOOTH_CORNER_RADIUS_FACTOR` to
 * `updateBlurParams`, and that factor is `SmoothRoundedManager.useSmoothCornerV2() ? 0.44f : 0.26f`.
 * `useSmoothCornerV2()` is `getSupportSmoothCornerVersion() == 2`, which requires
 * `SDK_VERSION >= 38`; this build targets API 37, so the stock value is 0.26 and the older
 * `OplusCanvas` smooth-round-rect is what actually draws the corner.
 *
 * The preview used to take 0.1 x height here, which made its corners 2.6x tighter than the host.
 */
private val CornerFactor = 0.26f
private val BaseRadius = DockHeight * CornerFactor

/**
 * Continuous-curvature corner weight.
 *
 * The host asks for a smooth corner and, on the non-V2 path, `SmoothRoundedManager.mapWeight`
 * rewrites the weight to 1.1 - the OPLUS "corner extension" convention, not coui's exponent
 * convention. realOS `SquircleShape` already documents that mapping and uses
 * [CouiCornerWeight] for it, so the preview follows the same choice instead of the raw 3.0f the
 * host passes in.
 */
private const val DockCornerWeight = CouiCornerWeight

/**
 * Blur radius, in px, that the host passes to the system blur service.
 *
 * It is a hardcoded constant in the launcher, not a system setting: `OplusBlurProperties.a(...)`
 * writes `ht0.blurRadius` from `pantanal.annotaions.EntranceTypeKt.ENTRANCE_HOST_ID_SPEECH_ASSISTANT`,
 * whose value is 120 (jadx resolves the constant to that unrelated name). The same float array
 * carries the dock rect in px, so this is px too.
 */
private const val BlurRadiusPx = 120f

private val ScreenGradient = listOf(
    Color(0xFF79BBF3),
    Color(0xFF8FC8F0),
    Color(0xFF7CCFDD),
    Color(0xFF74D2D9)
)

private val DockGradient = listOf(
    Color(0xFF74B8EE),
    Color(0xFF6FC8DA)
)

/** Dark-mode multiBlend colour from BlurTransitionDrawable (mDarkModeMultiBlendColor). */
private val DarkMultiBlend = Color(0x14000000)

/**
 * Blend shader covering all three supported modes plus the night-mode extra layer.
 *
 * Every formula is the host's AGSL verbatim (`com.oplus.posteffect.agsl.a`). The host's own
 * `doBlend` loop dispatches on a layer type; the shader takes that as `layerA` / `layerB` so the
 * preview can run the same stack the hook configures:
 * 1 = MIX, 2 = OVERLAY, 3 = DODGE_BG, 5 = LUMINOSITY.
 *
 * The host's `luminosity` is a per-channel luminosity shift, not a set-luminosity: it shifts every
 * channel of the backdrop by the same amount, which moves hue and saturation along with lightness.
 * That is the behaviour the dock really shows, so the preview reproduces it rather than using
 * Skia's hue-preserving `BlendMode.Luminosity`.
 *
 * Colours arrive as plain sRGB components through `setFloatUniform`, the same path the host takes
 * with `u_multiBlendParams` (`Color.red(argb) / 255f`). `setColorUniform` is not used because it
 * converts into the shader output colour space and would diverge from the host.
 */
private const val DOCK_BLEND_SHADER = """
uniform float4 blendColorA;
uniform float4 blendColorB;
uniform float4 blendColorC;
uniform int layerA;
uniform int layerB;
uniform float enableC;
uniform shader content;

vec3 luminosity(vec3 s, vec3 d) {
    float dLum = dot(d, vec3(0.3, 0.59, 0.11));
    float sLum = dot(s, vec3(0.3, 0.59, 0.11));
    float lum = sLum - dLum;
    vec3 c = d + lum;
    float minC = min(min(c.x, c.y), c.z);
    float maxC = max(max(c.x, c.y), c.z);
    if (minC < 0.0) return sLum + ((c - sLum) * sLum) / (sLum - minC);
    else if (maxC > 1.0) return sLum + ((c - sLum) * (1.0 - sLum)) / (maxC - sLum);
    else return c;
}

float overlay(float s, float d) {
    return (d < 0.5) ? 2.0 * s * d : 1.0 - 2.0 * (1.0 - s) * (1.0 - d);
}

vec3 overlay(vec3 s, vec3 d) {
    vec3 c;
    c.x = overlay(s.x, d.x);
    c.y = overlay(s.y, d.y);
    c.z = overlay(s.z, d.z);
    return c;
}

float colorDodge(float s, float d) { return d / (1.0 - s + 0.00000001); }

vec3 colorDodge(vec3 s, vec3 d) {
    vec3 c;
    c.x = colorDodge(s.x, d.x);
    c.y = colorDodge(s.y, d.y);
    c.z = colorDodge(s.z, d.z);
    return c;
}

vec3 blendLayer(int layer, vec3 s, vec3 d) {
    if (layer == 5) return luminosity(s, d);
    if (layer == 3) return colorDodge(s, d);
    if (layer == 2) return overlay(s, d);
    return s;
}

half4 main(float2 position) {
    // The host runs its blend on a vec4; keep every operand float so mix() sees matching types.
    float4 col = content.eval(position);
    col.rgb = mix(col.rgb, blendLayer(layerA, blendColorA.rgb, col.rgb), blendColorA.a);
    if (layerB != 0) {
        col.rgb = mix(col.rgb, blendLayer(layerB, blendColorB.rgb, col.rgb), blendColorB.a);
    }
    col.rgb = mix(col.rgb, blendColorC.rgb, blendColorC.a * enableC);
    return half4(col);
}
"""

/** `#AARRGGBB` as plain sRGB components in the host's order (r, g, b, a), each 0..1. */
private fun Color.toSrgbComponents(): FloatArray = floatArrayOf(
    red.coerceIn(0f, 1f),
    green.coerceIn(0f, 1f),
    blue.coerceIn(0f, 1f),
    alpha.coerceIn(0f, 1f),
)

/**
 * Stock blend as a render effect: the layer content is bound to the shader's `content` input, and
 * the optional blur wraps that effect as its own input, so the stock formulas run on the blurred
 * result the same way the host runs them on the blurred wallpaper.
 *
 * [darkMultiBlend] adds the third layer the host appends in night mode: shader type 1, a plain
 * `mix` by the colour's alpha, with the stock `#14000000`.
 */
private fun dockBlendEffect(
    blendColorA: Color,
    blendColorB: Color,
    layerA: Int,
    layerB: Int,
    darkMultiBlend: Boolean,
    blurRadiusPx: Float,
): RenderEffect {
    val shader = RuntimeShader(DOCK_BLEND_SHADER)
    shader.setFloatUniform("blendColorA", blendColorA.toSrgbComponents())
    shader.setFloatUniform("blendColorB", blendColorB.toSrgbComponents())
    shader.setFloatUniform("blendColorC", DarkMultiBlend.toSrgbComponents())
    shader.setIntUniform("layerA", layerA)
    shader.setIntUniform("layerB", layerB)
    shader.setFloatUniform("enableC", if (darkMultiBlend) 1f else 0f)
    val blend = RenderEffect.createRuntimeShaderEffect(shader, "content")
    if (blurRadiusPx <= 0f) return blend
    // Host order: the wallpaper is blurred first and the blend shader mixes over the blurred
    // result, so the blur is the inner effect and the shader wraps it.
    val blur = RenderEffect.createBlurEffect(
        blurRadiusPx,
        blurRadiusPx,
        android.graphics.Shader.TileMode.CLAMP,
    )
    return RenderEffect.createChainEffect(blend, blur)
}

private val IconColors = listOf(
    Color(0xFF4A90E2),
    Color(0xFF2DC100),
    Color(0xFFFF9500),
    Color(0xFFFF3B30)
)

/** Circle, squircle, rounded rectangle, small-corner rectangle; radii scale with a. */
private fun iconShape(index: Int): Shape = when (index) {
    0 -> CircleShape
    1 -> RoundedCornerShape(A * 0.8f)
    2 -> RoundedCornerShape(A * 0.45f)
    else -> RoundedCornerShape(A * 0.18f)
}

/**
 * One large canvas with three rows: plain baseline, light mask, dark mask.
 *
 * Geometry: a = icon centre-to-edge. Icon 2a, collision box 4a, adjacent centres 4a apart,
 * docker wraps all 4 boxes tightly (16a x 4a). Docks are 1a apart, so icon centres of
 * neighbouring docks are 5a apart. Canvas height = 3 docks + 4 paddings of 1a = 18a.
 * The docker is centred horizontally, not edge to edge; its rounded corners may clip boxes.
 */
@Composable
fun DockerBackgroundPreview(
    multiplier: Float,
    blendMode: Int,
    blurEnabled: Boolean,
    lightBlend: Color,
    lightMix: Color,
    darkBlend: Color,
    darkMix: Color,
    modifier: Modifier = Modifier
) {
    val radius = BaseRadius * multiplier.coerceIn(0.5f, 2f)
    // The host draws the dock with a continuous-curvature corner (SmoothRoundedManager), not a
    // circular arc, so the preview uses the same squircle shape.
    val dockShape = SquircleShape(cornerRadius = radius, cornerWeight = DockCornerWeight)
    val screenBrush = remember { Brush.verticalGradient(ScreenGradient) }
    val dockBrush = remember { Brush.verticalGradient(DockGradient) }
    // Layer stack per mode, matching DrawableShader's dispatch; 0 means the slot is unused.
    val layers = remember(blendMode) {
        when (blendMode) {
            LauncherConfig.BLEND_MODE_MIX -> 1 to 0
            LauncherConfig.BLEND_MODE_MIX_OVERLAY -> 1 to 2
            else -> 5 to 3
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CanvasHeight)
            .clip(RoundedCornerShape(ScreenCorner))
            .background(screenBrush)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Padding))

            // Row 1: plain icons, baseline for comparison (no docker background).
            IconGrid(Modifier.height(DockHeight))

            Spacer(Modifier.height(Padding))

            // Row 2: light mask.
            MaskedDock(
                dockShape = dockShape,
                blurEnabled = blurEnabled,
                blurRadiusPx = BlurRadiusPx,
                dockBrush = dockBrush,
                blendColor = lightBlend,
                mixColor = lightMix,
                layerA = layers.first,
                layerB = layers.second,
                darkMultiBlend = false
            )

            Spacer(Modifier.height(Padding))

            // Row 3: dark mask.
            MaskedDock(
                dockShape = dockShape,
                blurEnabled = blurEnabled,
                blurRadiusPx = BlurRadiusPx,
                dockBrush = dockBrush,
                blendColor = darkBlend,
                mixColor = darkMix,
                layerA = layers.first,
                layerB = layers.second,
                darkMultiBlend = true
            )

            Spacer(Modifier.height(Padding))
        }
    }
}

@Composable
private fun IconGrid(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(DockWidth)
            .height(DockHeight),
        horizontalArrangement = Arrangement.Start
    ) {
        repeat(IconColors.size) { index ->
            Box(
                Modifier
                    .width(Cell)
                    .height(DockHeight),
                contentAlignment = Alignment.Center
            ) {
                MockIcon(IconColors[index], iconShape(index))
            }
        }
    }
}

@Composable
private fun MaskedDock(
    dockShape: Shape,
    blurEnabled: Boolean,
    blurRadiusPx: Float,
    dockBrush: Brush,
    blendColor: Color,
    mixColor: Color,
    layerA: Int,
    layerB: Int,
    darkMultiBlend: Boolean
) {
    // One effect does blur and every blend layer in the stock order. The mask colours only
    // apply with blur: without it the host draws the plain rounded drawable and runs no blend.
    // The platform RenderEffect is converted at this boundary: graphicsLayer takes the Compose
    // RenderEffect type, and asComposeRenderEffect is the conversion the COUI dialogs also use.
    val effect = if (blurEnabled) {
        remember(blendColor, mixColor, layerA, layerB, darkMultiBlend, blurRadiusPx) {
            dockBlendEffect(blendColor, mixColor, layerA, layerB, darkMultiBlend, blurRadiusPx)
                .asComposeRenderEffect()
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .width(DockWidth)
            .height(DockHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(dockShape)
                .then(
                    if (effect != null) {
                        Modifier.graphicsLayer { renderEffect = effect }
                    } else {
                        Modifier
                    }
                )
        ) {
            Box(Modifier.fillMaxSize().background(dockBrush))
            IconGrid(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MockIcon(color: Color, shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(IconSize)
            .clip(shape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(A / 2)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}
