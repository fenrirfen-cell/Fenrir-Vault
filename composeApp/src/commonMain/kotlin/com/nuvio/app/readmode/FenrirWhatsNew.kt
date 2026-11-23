package com.nuvio.app.readmode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "What's new" after an update: the first time a new build opens, a card with that release's
 * bullets (from the GitHub release's "What's new" section). Shows once; swipe or Got it to close.
 */
object FenrirWhatsNew {
    data class Notes(val build: Int, val bullets: List<String>)

    /** Set by the Android side once the notes for this build are fetched (null = nothing to show). */
    val notes = MutableStateFlow<Notes?>(null)

    /** Set by the Android side: remembers that this build's notes were seen. */
    var markSeen: ((Int) -> Unit)? = null

    /** True once the opening animation has finished (the card waits for it). */
    val splashDone = MutableStateFlow(false)

    fun dismiss() {
        notes.value?.let { markSeen?.invoke(it.build) }
        notes.value = null
    }

    /** Pulls the bullets out of a release's notes: the "- " lines under "What's new". */
    fun parse(body: String): List<String> {
        val lines = body.lines()
        val start = lines.indexOfFirst { it.trim().trimStart('#').trim().equals("What's new", ignoreCase = true) }
        if (start < 0) return emptyList()
        return lines.drop(start + 1)
            .takeWhile { !it.trimStart().startsWith("#") }
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).trim() }
            .filter { it.isNotEmpty() }
    }
}

@Composable
fun FenrirWhatsNewCard() {
    val n by FenrirWhatsNew.notes.collectAsState()
    val splashDone by FenrirWhatsNew.splashDone.collectAsState()
    val notes = n
    var drag by remember { mutableFloatStateOf(0f) }
    AnimatedVisibility(visible = notes != null && splashDone, enter = fadeIn(), exit = fadeOut()) {
        val shown = notes ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { FenrirWhatsNew.dismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 22.dp)
                    .offset { IntOffset(0, drag.toInt()) }
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF15151B))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (kotlin.math.abs(drag) > 160f) FenrirWhatsNew.dismiss(); drag = 0f },
                            onDragCancel = { drag = 0f },
                            onVerticalDrag = { _, dy -> drag += dy },
                        )
                    }
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFF7B3FD1), Color(0xFF1A0B2E)))),
                        contentAlignment = Alignment.Center,
                    ) { Text("🐺", fontSize = 21.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("UPDATED", color = Color(0xFFB45CFF), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text("What's new in r${shown.build}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                shown.bullets.take(6).forEach { b ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text("•", color = Color(0xFFFF4D57), fontSize = 15.sp, modifier = Modifier.width(16.dp))
                        Text(b, color = Color(0xFFE4E4EA), fontSize = 14.5.sp, lineHeight = 20.sp)
                    }
                }
                Box(
                    Modifier.padding(top = 14.dp).fillMaxWidth().height(48.dp).clip(CircleShape).background(Color(0xFFFF4D57))
                        .clickable { FenrirWhatsNew.dismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text("Got it", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                Text(
                    "Swipe away · shows once",
                    color = Color(0xFF8E8E99),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
                )
            }
        }
    }
}
