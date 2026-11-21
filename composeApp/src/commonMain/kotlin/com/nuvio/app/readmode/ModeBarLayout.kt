package com.nuvio.app.readmode

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height of the Watch / Read bar below the status bar. */
private val ModeBarContentHeight = 70.dp

/** Extra fade below the bar so posters blend away instead of being cut off. */
private val ModeBarFadeHeight = 28.dp

/** Hides the Watch / Read bar while Home scrolls down and shows it again on scroll up. */
object ModeBarScroll {
    var visible by mutableStateOf(true)
        private set
    private var pendingDown = 0f

    fun reset() {
        visible = true
        pendingDown = 0f
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < -0.5f) {
                pendingDown += dy
                if (pendingDown < -24f) {
                    visible = false
                    pendingDown = 0f
                }
            } else if (dy > 0.5f) {
                pendingDown = 0f
                visible = true
            }
            return Offset.Zero
        }
    }
}

/**
 * The floating bottom island: slides away while scrolling down, comes back when scrolling up
 * (and on every tab change).
 */
object FenrirBarScroll {
    var visible by mutableStateOf(true)
        private set
    private var pendingDown = 0f

    fun reset() {
        visible = true
        pendingDown = 0f
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < -0.5f) {
                pendingDown += dy
                if (pendingDown < -24f) {
                    visible = false
                    pendingDown = 0f
                }
            } else if (dy > 0.5f) {
                pendingDown = 0f
                visible = true
            }
            return Offset.Zero
        }
    }
}

@Composable
fun modeBarHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + ModeBarContentHeight

@Composable
fun modeBarHiddenFraction(): Float {
    val target = if (ModeBarScroll.visible) 0f else 1f
    val value by animateFloatAsState(targetValue = target, animationSpec = tween(260), label = "modeBarHidden")
    return value
}

/** Slides the element up by [distance] and fades it as the bar hides. */
fun Modifier.modeBarHideable(fraction: Float, distance: Dp): Modifier = graphicsLayer {
    translationY = -fraction * distance.toPx()
    alpha = 1f - fraction
}

/** Faint shade behind the status bar only, so the clock stays readable over bright posters. */
@Composable
fun ModeBarBackground(modifier: Modifier = Modifier) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBar + 14.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.6f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** The space Home leaves above its banner for the switch (part of the scrolling list). */
@Composable
fun ModeBarSpacer() {
    Spacer(Modifier.fillMaxWidth().height(modeBarHeight()))
}