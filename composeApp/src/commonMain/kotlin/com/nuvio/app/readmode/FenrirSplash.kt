package com.nuvio.app.readmode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shown once per app start (not again when switching between Watch and Read). */
private var splashShown = false

/** Black screen with "FENRIR", a red line and "VAULT", fading out after about a second. */
@Composable
fun FenrirSplash() {
    if (!FenrirStyle.isAvailable || splashShown) return
    var visible by remember { mutableStateOf(true) }
    val wordAlpha = remember { Animatable(0f) }
    val lineWidth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        wordAlpha.animateTo(1f, tween(350))
        lineWidth.animateTo(44f, tween(350))
        delay(450)
        visible = false
        splashShown = true
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(300))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0F))
                // Swallow taps while it's showing.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.alpha(wordAlpha.value),
            ) {
                Text("FENRIR", color = Color.White, fontSize = 36.sp, letterSpacing = 12.sp, modifier = Modifier.padding(start = 12.dp))
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .width(lineWidth.value.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.height(14.dp))
                Text("VAULT", color = Color(0xFFA7A7B2), fontSize = 14.sp, letterSpacing = 8.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
