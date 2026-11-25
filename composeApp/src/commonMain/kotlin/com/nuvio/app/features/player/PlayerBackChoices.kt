package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.PlatformBackHandler

/*
 * Fenrir Read: when the source was picked automatically (auto-play or the reused last link),
 * the first "back" doesn't leave the player straight away. It offers a different source,
 * a different episode, or leaving. A second "back" leaves.
 */

/** Back button / system back from the player. */
internal fun PlayerScreenRuntime.requestLeavePlayer() {
    // Fenrir: no "Not the right one?" step any more; Sources in the player does that job.
    leavePlayer()
}

internal fun PlayerScreenRuntime.leavePlayer() {
    showBackChoices = false
    flushWatchProgress()
    args.onBack()
}

@Composable
internal fun PlayerScreenRuntime.RenderBackChoices() {
    val autoPicked = args.sourceAutoSelected || args.sourceFromLinkCache
    // Catch the system back gesture the first time, unless a panel is open (it closes the panel).
    PlatformBackHandler(
        enabled = autoPicked && !backChoicesOffered && !isPanelOpen && activeVideoId != null,
        onBack = { requestLeavePlayer() },
    )
    // While the choices are showing, back leaves.
    PlatformBackHandler(enabled = showBackChoices, onBack = { leavePlayer() })

    AnimatedVisibility(
        visible = showBackChoices,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = interactionSource, indication = null) { showBackChoices = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF16161A))
                    // Taps on the card itself shouldn't dismiss it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Not the right one?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = buildString {
                        append("This source was picked automatically")
                        if (providerName.isNotBlank()) append(" (").append(providerName).append(")")
                        append(".")
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                BackChoiceButton("Choose a different source", primary = true) {
                    showBackChoices = false
                    openSourcesPanel()
                }
                if (isSeries) {
                    BackChoiceButton("Pick a different episode") {
                        showBackChoices = false
                        openEpisodesPanel()
                    }
                }
                BackChoiceButton("Leave player") { leavePlayer() }
                BackChoiceButton("Keep watching", subtle = true) { showBackChoices = false }
            }
        }
    }
}

@Composable
private fun BackChoiceButton(
    label: String,
    primary: Boolean = false,
    subtle: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    primary -> Color.White
                    subtle -> Color.Transparent
                    else -> Color.White.copy(alpha = 0.1f)
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                primary -> Color.Black
                subtle -> Color.White.copy(alpha = 0.7f)
                else -> Color.White
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
