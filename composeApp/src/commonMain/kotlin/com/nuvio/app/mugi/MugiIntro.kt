package com.nuvio.app.mugi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mugi_logo
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

private val IntroBackground = Color(0xFF090F17)
private val WatchTrail = Color(0xFFF03644)
private val AnimeTrail = Color(0xFFAE53FF)
private val ReadTrail = Color(0xFFFFC04D)
private val CompassFlash = Color(0xFFFFE199)

/**
 * MugiVerse "Three Tides" opening (approved timing): three curved trails meet in the middle
 * (0–780 ms), a gold four-point compass flash (680–1160 ms), the logo opens out from the centre
 * with a slight scale (930–1350 ms), holds sharp, and hands over at 2450 ms. With animations
 * turned off on the phone it just shows the logo briefly.
 */
@Composable
fun MugiVerseIntro(onFinished: () -> Unit, reduceMotion: Boolean = false) {
    val timeline = remember { Animatable(if (reduceMotion) 1350f else 0f) }
    val done by rememberUpdatedState(onFinished)
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            kotlinx.coroutines.delay(700)
        } else {
            timeline.animateTo(2450f, tween(durationMillis = 2450, easing = LinearEasing))
        }
        done()
    }
    val time = timeline.value
    val logoReveal = ((time - 930f) / 420f).coerceIn(0f, 1f)
    val logoOpacity = ((time - 930f) / 200f).coerceIn(0f, 1f)
    val logoScale = 0.94f + 0.06f * logoReveal

    Box(Modifier.fillMaxSize().background(IntroBackground), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = this.center
            val colors = listOf(WatchTrail, AnimeTrail, ReadTrail)
            val w = size.width
            val h = size.height
            val starts = listOf(Offset(-w * 0.1f, h * 0.70f), Offset(w * 1.1f, h * 0.25f), Offset(w * 0.44f, h * 0.08f))
            val control1 = listOf(Offset(w * 0.18f, h * 0.63f), Offset(w * 0.83f, h * 0.30f), Offset(w * 0.64f, h * 0.22f))
            val control2 = listOf(Offset(w * 0.32f, h * 0.32f), Offset(w * 0.67f, h * 0.70f), Offset(w * 0.19f, h * 0.51f))
            // trails scale with the shorter side so tablets and folds don't get fat strokes
            val unit = minOf(w, h)
            colors.forEachIndexed { index, color ->
                val progress = ((time - index * 45f) / 780f).coerceIn(0f, 1f)
                val opacity = 1f - ((time - 730f) / 190f).coerceIn(0f, 1f)
                if (progress > 0f && opacity > 0f) {
                    val route = Path().apply {
                        moveTo(starts[index].x, starts[index].y)
                        cubicTo(control1[index].x, control1[index].y, control2[index].x, control2[index].y, center.x, center.y)
                    }
                    val measure = PathMeasure()
                    measure.setPath(route, false)
                    val length = measure.length
                    val head = length * progress
                    val tail = (head - length * 0.48f).coerceAtLeast(0f)
                    for (segment in 0 until 48) {
                        val fraction = segment / 48f
                        val next = (segment + 1) / 48f
                        val a = measure.getPosition(tail + (head - tail) * fraction)
                        val b = measure.getPosition(tail + (head - tail) * next)
                        val thickness = sin(PI * fraction).toFloat().coerceAtLeast(0f)
                        drawLine(color.copy(alpha = opacity), a, b, strokeWidth = unit * 0.024f * thickness + 0.1f, cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = opacity * 0.22f), a, b, strokeWidth = unit * 0.007f * thickness + 0.1f, cap = StrokeCap.Round)
                    }
                }
            }
            // gold compass flash
            val flashProgress = ((time - 680f) / 230f).coerceIn(0f, 1f)
            val flashAlpha = 1f - ((time - 910f) / 250f).coerceIn(0f, 1f)
            if (flashProgress > 0f && flashAlpha > 0f) {
                val radius = unit * 0.17f * flashProgress
                val width = unit * 0.012f * flashProgress
                val star = Path().apply {
                    moveTo(center.x, center.y - radius)
                    lineTo(center.x + width, center.y - width)
                    lineTo(center.x + radius, center.y)
                    lineTo(center.x + width, center.y + width)
                    lineTo(center.x, center.y + radius)
                    lineTo(center.x - width, center.y + width)
                    lineTo(center.x - radius, center.y)
                    lineTo(center.x - width, center.y - width)
                    close()
                }
                drawPath(star, CompassFlash.copy(alpha = flashAlpha))
            }
        }
        if (logoReveal > 0f) {
            Image(
                painter = painterResource(Res.drawable.mugi_logo),
                contentDescription = "MugiVerse",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 520.dp)
                    .graphicsLayer {
                        alpha = logoOpacity
                        scaleX = logoScale
                        scaleY = logoScale
                    }
                    .drawWithContent {
                        val halfWidth = size.width * logoReveal / 2f
                        clipRect(left = center.x - halfWidth, right = center.x + halfWidth) {
                            this@drawWithContent.drawContent()
                        }
                    },
            )
        }
    }
}
