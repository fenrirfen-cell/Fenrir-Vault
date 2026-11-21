package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
internal actual fun FenrirLiquidGlassBackground(hazeState: HazeState?, modifier: Modifier) {
    Box(
        modifier
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(hazeState) {
                        blurRadius = 20.dp
                        backgroundColor = Color(0xFF0B0B0F)
                        tints = listOf(HazeTint(Color(0xFF1C1C22).copy(alpha = 0.45f)))
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
