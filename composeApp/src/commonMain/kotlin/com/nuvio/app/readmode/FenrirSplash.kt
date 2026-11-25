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

/** 61 frames: the 16 drawn frames plus 3 blended in-betweens each. */
private const val FRAME_COUNT = 61
private const val HOWL_FRAME = 12 // the wolf throws its head back
private const val AHEAD = 8 // frames decoded ahead (keeps memory small)

/*
 * Fenrir: the pace changes like a real shot instead of one flat speed: a slow build while the
 * chains strain, a beat on the howl, a fast run, slow motion on the bite. Values are frames per ms
 * at that frame, blended smoothly in between.
 */
private val SPEEDS = floatArrayOf(0f, 0.016f, 10f, 0.028f, 14f, 0.011f, 20f, 0.017f, 24f, 0.046f, 46f, 0.048f, 50f, 0.026f, 54f, 0.013f, 60f, 0.010f)

private fun speedAt(p: Float): Float {
    var i = 0
    while (i + 2 < SPEEDS.size) {
        val a = SPEEDS[i]; val sa = SPEEDS[i + 1]; val b = SPEEDS[i + 2]; val sb = SPEEDS[i + 3]
        if (p <= b) {
            val u = (1f - kotlin.math.cos(kotlin.math.PI.toFloat() * ((p - a) / (b - a)))) / 2f
            return sa + (sb - sa) * u
        }
        i += 2
    }
    return SPEEDS.last()
}

/** Frame position for each millisecond of playback. */
private val TIMELINE: FloatArray by lazy {
    val out = ArrayList<Float>(3000)
    var p = 0f
    out += p
    while (p < FRAME_COUNT - 1) { p = minOf((FRAME_COUNT - 1).toFloat(), p + speedAt(p)); out += p }
    out.toFloatArray()
}
private fun posAt(t: Long): Float = TIMELINE[t.coerceIn(0L, (TIMELINE.size - 1).toLong()).toInt()]
private fun msWhen(frame: Float): Long = TIMELINE.indexOfFirst { it >= frame }.coerceAtLeast(0).toLong()

private const val FADE_IN_MS = 450L
private const val HOLD_AFTER_MS = 600L
private const val EXIT_MS = 520L

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun easeOut(x: Float): Float { val t = x.coerceIn(0f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t) }
private fun easeIn(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * t }
private fun bump(t: Long, at: Long, ms: Float): Float {
    val u = (t - at) / ms
    return if (u < 0f || u > 1f) 0f else kotlin.math.sin(kotlin.math.PI.toFloat() * u) * (1f - u)
}
private fun gauss(t: Long, at: Long, width: Float): Float { val u = (t - at) / width; return kotlin.math.exp(-u * u) }

/**
 * Opening animation: the chained wolf howls, breaks free, runs and bites open the vault, then
 * FENRIR VAULT comes in letter by letter. The camera eases in, follows the run with a light gallop,
 * gives a small shake on the howl and the break, and zooms into the app at the end. The howl
 * (if on) starts with the wolf's howl. Tap to skip.
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
    var base by remember { mutableStateOf<ImageBitmap?>(null) }
    var next by remember { mutableStateOf<ImageBitmap?>(null) }
    var blend by remember { mutableStateOf(0f) }
    var clock by remember { mutableLongStateOf(0L) }
    var exitAt by remember { mutableLongStateOf(-1L) }
    val playMs = remember { (TIMELINE.size - 1).toLong() }
    val howlAt = remember { msWhen(14f) }
    val breakAt = remember { msWhen(55f) }
    val wordAt = playMs - 150L

    fun finish() {
        splashShown = true
        FenrirWhatsNew.splashDone.value = true
        done = true
    }

    LaunchedEffect(Unit) {
        // Decode frames a few ahead of playback on a background thread; drop old ones.
        val loader = launch {
            for (i in 0 until FRAME_COUNT) {
                while (i - posAt(clock).toInt() > AHEAD) delay(10)
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
            val p = posAt(t)
            // never wait on a frame that isn't ready: keep showing the last one we have
            var k = p.toInt()
            while (k > 0 && frames[k] == null) k--
            frames[k]?.let { base = it }
            val j = k + 1
            if (j < FRAME_COUNT && frames[j] != null && k == p.toInt()) {
                next = frames[j]; blend = p - k
            } else {
                next = null; blend = 0f
            }
            for (m in 0 until k) frames[m] = null // played already: let them go
            if (!howled && p >= HOWL_FRAME) {
                howled = true
                runCatching { FenrirHowl.openHowl?.invoke() }
            }
            if (exitAt < 0 && t >= playMs + HOLD_AFTER_MS) exitAt = t
            if (exitAt >= 0 && t - exitAt >= EXIT_MS) break
        }
        finish()
    }

    val t = clock
    val p = posAt(t)
    val exit = if (exitAt >= 0) ease((t - exitAt) / EXIT_MS.toFloat()) else 0f
    val fadeIn = easeOut(t / FADE_IN_MS.toFloat())
    // settle in, a small punch on the howl and the break, then a smooth zoom into the app
    val zoom = 1.07f - 0.07f * easeOut(t / 700f) + 0.018f * bump(t, howlAt, 500f) + 0.032f * bump(t, breakAt, 600f) + 0.18f * easeIn(exit)
    // follow the run, with one light gallop per stride
    val run = ease((p - 22f) / 4f) * (1f - ease((p - 48f) / 4f))
    val panX = run * (-16f * ((p - 22f) / 26f) + 8f) / 540f
    val bobY = run * -3.5f * kotlin.math.abs(kotlin.math.sin(kotlin.math.PI.toFloat() * p / 4f)) / 540f
    fun shake(at: Long, amp: Float, ms: Float): Pair<Float, Float> {
        val u = (t - at) / ms
        if (u < 0f || u > 1f) return 0f to 0f
        val k = amp * (1f - u) * (1f - u) / 540f
        return k * kotlin.math.sin(t * 0.09f) to k * kotlin.math.cos(t * 0.11f)
    }
    val (s1x, s1y) = shake(howlAt, 2f, 400f)
    val (s2x, s2y) = shake(breakAt, 5f, 550f)
    val glow = (0.15f + 0.2f * gauss(t, howlAt, 350f) + 0.4f * gauss(t, breakAt, 300f)) * fadeIn
    val flash = 0.32f * gauss(t, breakAt + 60, 110f)

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
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glow.coerceIn(0f, 1f) }
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Color(0xFF6E28BE), Color.Transparent),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = (panX + s1x + s2x) * size.width
                translationY = (bobY + s1y + s2y) * size.width
            },
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(357f / 267f).graphicsLayer { alpha = fadeIn }) {
                base?.let {
                    Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                // blend into the next drawing instead of jumping to it
                next?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = blend },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                "FENRIR".forEachIndexed { i, c ->
                    val u = (t - wordAt - i * 55L) / 420f
                    val v = u.coerceIn(0f, 1f)
                    Text(
                        c.toString(),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(end = if (i < 5) 10.dp else 0.dp)
                            .graphicsLayer {
                                alpha = easeOut(u)
                                translationY = if (u < 1f) 10.dp.toPx() * (1f - v) * kotlin.math.cos(v * kotlin.math.PI.toFloat() * 1.5f) else 0f
                            },
                    )
                }
                Spacer(Modifier.width(12.dp))
                val u = (t - wordAt - 6 * 55L) / 420f
                val v = u.coerceIn(0f, 1f)
                Text(
                    "VAULT",
                    color = Color(0xFFB45CFF),
                    fontSize = 16.sp,
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .graphicsLayer {
                            alpha = easeOut(u)
                            translationY = if (u < 1f) 10.dp.toPx() * (1f - v) * kotlin.math.cos(v * kotlin.math.PI.toFloat() * 1.5f) else 0f
                        },
                )
            }
        }
        if (flash > 0.01f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash }.background(Color(0xFF9650E6)))
        }
    }
}
