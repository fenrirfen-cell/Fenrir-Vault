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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource

/** Shown once per app start (not again when switching between Watch and Read). */
private var splashShown = false

private val SplashFrames = listOf(
    Res.drawable.fenrir_splash_00, Res.drawable.fenrir_splash_01, Res.drawable.fenrir_splash_02, Res.drawable.fenrir_splash_03,
    Res.drawable.fenrir_splash_04, Res.drawable.fenrir_splash_05, Res.drawable.fenrir_splash_06, Res.drawable.fenrir_splash_07,
    Res.drawable.fenrir_splash_08, Res.drawable.fenrir_splash_09, Res.drawable.fenrir_splash_10, Res.drawable.fenrir_splash_11,
    Res.drawable.fenrir_splash_12, Res.drawable.fenrir_splash_13, Res.drawable.fenrir_splash_14, Res.drawable.fenrir_splash_15,
)
private const val FRAME_MS = 160L
private const val HOWL_FRAME = 3 // the wolf throws its head back

/**
 * Opening animation: the chained wolf howls, breaks free, runs and bites open the vault, then
 * FENRIR VAULT fades in. The howl (if on) starts with the wolf's howl. Tap to skip.
 */
@Composable
fun FenrirSplash() {
    if (!FenrirStyle.isAvailable || splashShown) return
    var visible by remember { mutableStateOf(true) }
    var frame by remember { mutableStateOf(0) }
    val wordAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(150) // let the first frames load
        for (i in SplashFrames.indices) {
            frame = i
            if (i == HOWL_FRAME) runCatching { FenrirHowl.openHowl?.invoke() }
            delay(FRAME_MS)
        }
        delay(450)
        visible = false
        splashShown = true
    }
    val showWord = frame >= SplashFrames.size - 4
    LaunchedEffect(showWord) { if (showWord) wordAlpha.animateTo(1f, tween(500)) }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(350))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Tap to skip.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    visible = false
                    splashShown = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(357f / 267f)) {
                    // All frames stay loaded; only the current one shows, so it never flickers.
                    SplashFrames.forEachIndexed { i, res ->
                        Image(
                            painter = painterResource(res),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().alpha(if (i == frame) 1f else 0f),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.alpha(wordAlpha.value), verticalAlignment = Alignment.Bottom) {
                    Text("FENRIR", color = Color.White, fontSize = 30.sp, letterSpacing = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("VAULT", color = Color(0xFFB45CFF), fontSize = 16.sp, letterSpacing = 6.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}
