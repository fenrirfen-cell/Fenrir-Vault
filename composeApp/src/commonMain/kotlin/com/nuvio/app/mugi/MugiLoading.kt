package com.nuvio.app.mugi

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mugi_compass
import org.jetbrains.compose.resources.painterResource

/**
 * The compass artwork as the loading indicator: it swings gently back and forth like a needle
 * settling. Still when motion is reduced, and not animating while [active] is false (off screen).
 */
@Composable
fun MugiCompassSpinner(modifier: Modifier = Modifier, size: Dp = 40.dp, active: Boolean = true) {
    val still = remember { MugiMotion.reduceMotion() } || !active
    val swing = if (still) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "compassLoading")
        val a by t.animateFloat(
            initialValue = -24f,
            targetValue = 24f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "compassSwing",
        )
        a
    }
    Image(
        painterResource(Res.drawable.mugi_compass),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Loading" }
            .graphicsLayer { rotationZ = swing },
    )
}

/** Big loading state: the compass and "Loading your voyage…" in gold italic. */
@Composable
fun MugiCompassLoader(modifier: Modifier = Modifier, text: String = "Loading your voyage…") {
    Box(modifier.fillMaxWidth().heightIn(min = 300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            MugiCompassSpinner(size = 150.dp)
            Text(
                text,
                color = Color(0xFFF2E6CC),
                fontFamily = MugiPlayfair,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/**
 * MugiVerse's themed empty state for Watch and Ani-Drama: the compass, a gold (violet in
 * Ani-Drama) italic title, the message and an optional gold button.
 */
@Composable
fun MugiEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val ani = MugiAniMode.on.value
    val accent = if (ani) Color(0xFFC9A6F2) else Color(0xFFF2D592)
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painterResource(Res.drawable.mugi_compass),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(84.dp).graphicsLayer { alpha = 0.85f },
        )
        Text(title, color = accent, fontFamily = MugiPlayfair, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(message, color = Color(0xFFD9CDB6), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFD4AF65), Color(0xFFA87832))))
                    .border(1.dp, Color(0xFFD4AF65), shape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, color = Color(0xFF2B1A0C), fontFamily = MugiPlayfair, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

