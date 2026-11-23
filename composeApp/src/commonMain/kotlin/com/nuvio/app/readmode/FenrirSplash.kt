package com.nuvio.app.readmode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Shown once per app start (not again when switching between Watch and Read). */
private var splashShown = false

/** 61 frames: the 16 drawn frames plus 3 blended in-betweens each, played at 25 a second. */
private const val FRAME_COUNT = 61
private const val FRAME_MS = 40L
private const val HOWL_FRAME = 12 // the wolf throws its head back
private const val FADE_IN_MS = 300L
private const val PLAY_MS = FRAME_COUNT * FRAME_MS // ~2.4 s
private const val WORD_START_MS = 2200L
private const val FLASH_MS = 2280L
private const val HOLD_END_MS = PLAY_MS + 500L
private const val EXIT_MS = 420L
private const val AHEAD = 8 // frames decoded ahead (keeps memory small)

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

/**
 * Opening animation: the chained wolf howls, breaks free, runs and bites open the vault, then
 * FENRIR VAULT rises in. Fades in with a slow push-in, flashes when the vault breaks, and zooms
 * into the app at the end. The howl (if on) starts with the wolf's howl. Tap to skip.
 */
@Composable
fun FenrirSplash() {
    if (!FenrirStyle.isAvailable || splashShown) {
        FenrirWhatsNew.splashDone.value = true
        return
    }
    var done by remember { mutableStateOf(false) }
    if (done) return
    val frames = remember { arrayOfNulls<ImageBitmap>(FRAME_COUNT) }
    var decoded by remember { mutableStateOf(-1) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var clock by remember { mutableLongStateOf(0L) }
    var exitAt by remember { mutableLongStateOf(-1L) }

    fun finish() {
        splashShown = true
        FenrirWhatsNew.splashDone.value = true
        done = true
    }

    LaunchedEffect(Unit) {
        // Decode frames a few ahead of playback on a background thread; drop old ones.
        val loader = launch {
            for (i in 0 until FRAME_COUNT) {
                while (i - currentFrame(clock) > AHEAD) delay(10)
                val bmp = runCatching {
                    withContext(Dispatchers.Default) {
                        Res.readBytes("files/fenrir_splash/f${i.toString().padStart(2, '0')}.webp").decodeToImageBitmap()
                    }
                }.getOrNull()
                frames[i] = bmp
                decoded = i
            }
        }
        while (decoded < minOf(5, FRAME_COUNT - 1) && loader.isActive) delay(10)
        var howled = false
        var start = -1L
        while (true) {
            val now = withFrameMillis { it }
            if (start < 0) start = now
            val t = now - start
            clock = t
            val f = currentFrame(t)
            // never wait on a frame that isn't ready: keep showing the last one we have
            var k = f
            while (k > 0 && frames[k] == null) k--
            frames[k]?.let { shown = it }
            for (j in 0 until k) frames[j] = null // played already: let them go
            if (!howled && f >= HOWL_FRAME) {
                howled = true
                runCatching { FenrirHowl.openHowl?.invoke() }
            }
            if (exitAt < 0 && t >= HOLD_END_MS) exitAt = t
            if (exitAt >= 0 && t - exitAt >= EXIT_MS) break
        }
        finish()
    }

    val t = clock
    val exit = if (exitAt >= 0) ease((t - exitAt) / EXIT_MS.toFloat()) else 0f
    val fadeIn = ease(t / FADE_IN_MS.toFloat())
    // gentle push-in while it plays, then a quicker zoom into the app
    val zoom = (if (t < FADE_IN_MS) 1.04f - 0.04f * fadeIn else 1f + 0.05f * ease(t / PLAY_MS.toFloat())) + 0.12f * exit
    val flashT = (t - FLASH_MS) / 280f
    val flash = if (flashT in 0f..1f) 0.45f * (1f - flashT) * minOf(1f, flashT * 4f) else 0f
    val word = ease((t - WORD_START_MS) / 600f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit }
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (exitAt < 0) exitAt = clock
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom },
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(357f / 267f).graphicsLayer { alpha = fadeIn }) {
                shown?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.graphicsLayer {
                    alpha = word
                    translationY = (1f - word) * 18.dp.toPx()
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("FENRIR", color = Color.White, fontSize = 30.sp, letterSpacing = (10f + 5f * (1f - word)).sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("VAULT", color = Color(0xFFB45CFF), fontSize = 16.sp, letterSpacing = (6f + 3f * (1f - word)).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (flash > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash }.background(Color(0xFF9650E6)))
        }
    }
}

private fun currentFrame(t: Long): Int = (t / FRAME_MS).toInt().coerceIn(0, FRAME_COUNT - 1)
