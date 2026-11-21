package com.nuvio.app.readmode

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Clear liquid glass, tuned like Zangetsu's dock: barely any blur, colours a touch richer, a 12%
 * dark tint, content bending toward the rim like a lens, a darker band just inside the rim and a
 * thin light line around the edge (brightest on top).
 */
private const val FenrirLiquidGlassShader = """
uniform shader backdrop;
uniform float2 resolution;
uniform float density;
uniform float outset;

half4 main(float2 position) {
    float2 halfSize = resolution * 0.5 - outset;
    float radius = halfSize.y;
    float2 local = position - resolution * 0.5;
    float2 capsule = float2(max(abs(local.x) - halfSize.x + radius, 0.0), local.y);
    float distanceToCenter = length(capsule);
    float distanceToEdge = distanceToCenter - radius;
    float coverage = 1.0 - smoothstep(-0.5, 0.5, distanceToEdge);
    if (coverage <= 0.0) return half4(0.0);

    float2 normal = float2(capsule.x * sign(local.x), capsule.y) / max(distanceToCenter, 0.001);
    float depth = max(-distanceToEdge, 0.0) / density;

    // Lens: near the rim, look further inward so what's behind bends toward the edge.
    float bend = exp(-depth / 9.0);
    half3 color = backdrop.eval(position - normal * density * 18.0 * bend).rgb;

    half luminance = dot(color, half3(0.2126, 0.7152, 0.0722));
    color = clamp(mix(half3(luminance), color, 1.15), 0.0, 1.0);
    color = mix(color, half3(0.18), 0.12) * 0.95;

    // Darker band just inside the rim, then a thin light line on the rim itself.
    color *= 1.0 - 0.18 * exp(-depth / 3.0);
    float rimLine = exp(-pow((depth - 0.6) / 0.6, 2.0));
    float light = 0.35 + 0.65 * max(-normal.y, 0.0) + 0.25 * max(normal.y, 0.0);
    color += half3(0.42) * rimLine * light;
    color += half3(0.06) * exp(-depth / 6.0);

    return half4(clamp(color, 0.0, 1.0) * coverage, coverage);
}
"""

@Composable
internal actual fun FenrirLiquidGlassBackground(hazeState: HazeState?, modifier: Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hazeState?.blurEnabled == true) {
        RefractedLiquidGlass(hazeState, modifier)
    } else {
        // No shader support: a soft frosted fill with a light rim.
        Box(
            modifier
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(hazeState) {
                            blurRadius = 20.dp
                            backgroundColor = Color(0xFF0B0B0F)
                            tints = listOf(HazeTint(Color(0xFF1C1C22).copy(alpha = 0.45f)))
                            fallbackTint = HazeTint(Color(0xFF1C1C22).copy(alpha = 0.9f))
                            noiseFactor = 0f
                        }
                    } else {
                        Modifier.background(Color(0xFF1C1C22).copy(alpha = 0.9f))
                    },
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f))),
                    CircleShape,
                ),
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun RefractedLiquidGlass(hazeState: HazeState, modifier: Modifier) {
    val shader = remember { RuntimeShader(FenrirLiquidGlassShader) }
    Box(
        modifier
            // Draw a little past the edges so the lens has something to bend in from.
            .layout { measurable, constraints ->
                val outset = 24.dp.roundToPx()
                val placeable = measurable.measure(constraints.offset(outset * 2, outset * 2))
                layout(placeable.width - outset * 2, placeable.height - outset * 2) {
                    placeable.place(-outset, -outset)
                }
            }
            .graphicsLayer {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("density", density)
                shader.setFloatUniform("outset", 24.dp.roundToPx().toFloat())
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
            }
            .hazeEffect(hazeState) {
                blurRadius = 2.dp
                backgroundColor = Color(0xFF0B0B0F)
                tints = listOf(HazeTint(Color.Transparent))
                noiseFactor = 0f
            },
    )
}
