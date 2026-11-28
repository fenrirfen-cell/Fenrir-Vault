package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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

/** Fenrir Vault: the play target of each open detail page, by meta id. */
internal object FenrirFoundIn {
    val targets = mutableStateMapOf<String, FoundInTarget>()
}

private val FoundColor = Color(0xFF1D9E75)

/**
 * "Links from" (All / Nuvio add-ons / Plugins, remembered per title) and the "Found in your sources"
 * badges under the Play button (Fenrir Vault only). Plugin sites show in purple; tap one to play
 * from that site only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FenrirFoundInRow(metaId: String, modifier: Modifier = Modifier, onPlay: (() -> Unit)? = null) {
    if (!ReadModeBridge.isAvailable) return
    val target = FenrirFoundIn.targets[metaId] ?: return
    val modes by com.nuvio.app.readmode.FenrirLinkChoice.modes.collectAsState()
    val mode = modes[metaId] ?: com.nuvio.app.readmode.FenrirLinkChoice.Mode.All
    // mayHaveSources: plugins still loading right after the app opened count (waited for below)
    val hosts = remember { com.nuvio.app.readmode.FenrirPlugins.all.filter { it.mayHaveSources } }
    val usePlugins = hosts.isNotEmpty() && mode != com.nuvio.app.readmode.FenrirLinkChoice.Mode.Nuvio
    val useNuvio = mode != com.nuvio.app.readmode.FenrirLinkChoice.Mode.Plugins || hosts.isEmpty()

    val state by produceState(SourceAvailabilityState(), target, useNuvio) {
        value = SourceAvailabilityState()
        if (!useNuvio) return@produceState
        runCatching { SourceAvailability.check(target.type, target.videoId, target.season, target.episode).collect { value = it } }
    }
    // plugin sites: (host id, site) to number of links; null while still looking
    val pluginSites by produceState<Pair<Boolean, Map<Pair<String, String>, Int>>>(false to emptyMap(), target, usePlugins) {
        value = false to emptyMap()
        if (!usePlugins) return@produceState
        val known = com.nuvio.app.readmode.FenrirPlugins.titleFor(metaId) ?: return@produceState
        val query = com.nuvio.app.readmode.FenrirStreamQuery(
            title = known.first,
            year = known.second,
            isMovie = target.type == "movie",
            season = target.season,
            episode = target.episode,
        )
        value = true to emptyMap()
        coroutineScope {
            hosts.map { host ->
                launch {
                    if (!host.awaitSources()) return@launch
                    runCatching {
                        com.nuvio.app.readmode.FenrirFoundLinks.find(host, query) { site, found ->
                            if (found.isNotEmpty()) {
                                val key = host.id to site
                                value = true to (value.second + (key to ((value.second[key] ?: 0) + found.size)))
                            }
                        }
                    }
                }
            }.joinAll()
        }
        value = false to value.second
    }

    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (hosts.isNotEmpty()) {
            Text("Links from", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            LinksFromSwitch(mode) { com.nuvio.app.readmode.FenrirLinkChoice.set(metaId, it) }
            Spacer(Modifier.size(12.dp))
        }
        val (pluginsLooking, sites) = pluginSites
        val nuvioSources = if (useNuvio) state.sources else emptyList()
        if (nuvioSources.isEmpty() && sites.isEmpty() && !pluginsLooking) return@Column
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
            nuvioSources
                .sortedBy { (_, count) -> when { count == null -> 1; count > 0 -> 0; else -> 2 } }
                .forEach { (name, count) -> SourceBadge(name, count) }
            sites.entries.sortedByDescending { it.value }.forEach { (key, count) ->
                val hostName = if (key.first == "aniyomi") "Aniyomi" else "CloudStream"
                SourceBadge(
                    "$hostName · ${key.second}", count, plugin = true,
                    onClick = onPlay?.let { play ->
                        {
                            com.nuvio.app.readmode.FenrirLinkChoice.playOnlyFrom(metaId, key.first, key.second)
                            play()
                        }
                    },
                )
            }
            if (pluginsLooking) SourceBadge("Plugins", null, plugin = true)
        }
    }
}

private val PluginColor = Color(0xFFB08CFF)

@Composable
private fun LinksFromSwitch(mode: com.nuvio.app.readmode.FenrirLinkChoice.Mode, onPick: (com.nuvio.app.readmode.FenrirLinkChoice.Mode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(Color(0xFF1E1E26))
            .border(1.dp, Color(0xFF3C3C48), CircleShape).padding(3.dp),
    ) {
        com.nuvio.app.readmode.FenrirLinkChoice.Mode.entries.forEach { m ->
            val sel = m == mode
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .background(if (sel) Color(0xFFFF4D57) else Color.Transparent)
                    .clickable { onPick(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label, color = if (sel) Color.White else Color(0xFFC8C8D2),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(name: String, count: Int?, plugin: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val found = count != null && count > 0
    val tint = if (plugin) PluginColor else FoundColor
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (found) tint.copy(alpha = 0.18f) else colors.surfaceVariant.copy(alpha = 0.6f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            count == null -> CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
            found -> Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
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
                found -> tint
                count == null -> colors.onSurfaceVariant
                else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
            },
            maxLines = 1,
        )
    }
}
