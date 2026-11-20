package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.streams.SourceAvailability
import com.nuvio.app.features.streams.SourceAvailabilityState
import com.nuvio.app.readmode.ReadModeBridge

/** What "Play" on a detail page would start, so "Found in" checks the same episode. */
internal data class FoundInTarget(val type: String, val videoId: String, val season: Int?, val episode: Int?)

/** Fenrir Read: the play target of each open detail page, by meta id. */
internal object FenrirFoundIn {
    val targets = mutableStateMapOf<String, FoundInTarget>()
}

private val FoundColor = Color(0xFF1D9E75)

/** "Found in your sources" badges under the Play button (Fenrir Read only). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FenrirFoundInRow(metaId: String, modifier: Modifier = Modifier) {
    if (!ReadModeBridge.isAvailable) return
    val target = FenrirFoundIn.targets[metaId] ?: return
    val state by produceState(SourceAvailabilityState(), target) {
        value = SourceAvailabilityState()
        runCatching { SourceAvailability.check(target.type, target.videoId, target.season, target.episode).collect { value = it } }
    }
    if (state.sources.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = buildString {
                append("Found in your sources")
                if (target.season != null && target.episode != null) append(" · S${target.season} E${target.episode}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Sources with links first, then the ones still checking, then the empty ones.
            state.sources
                .sortedBy { (_, count) -> when { count == null -> 1; count > 0 -> 0; else -> 2 } }
                .forEach { (name, count) -> SourceBadge(name, count) }
        }
    }
}

@Composable
private fun SourceBadge(name: String, count: Int?) {
    val colors = MaterialTheme.colorScheme
    val found = count != null && count > 0
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (found) FoundColor.copy(alpha = 0.18f) else colors.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            count == null -> CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
            found -> Box(Modifier.size(7.dp).clip(CircleShape).background(FoundColor))
            else -> Unit
        }
        if (count == null || found) Spacer(Modifier.width(5.dp))
        Text(
            text = when {
                count == null -> "$name · checking"
                count > 0 -> "$name · $count"
                else -> "$name · none"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                found -> FoundColor
                count == null -> colors.onSurfaceVariant
                else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
            },
            maxLines = 1,
        )
    }
}
