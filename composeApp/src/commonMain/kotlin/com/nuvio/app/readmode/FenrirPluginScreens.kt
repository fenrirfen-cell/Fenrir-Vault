package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A link picked while browsing a plugin; the main screen opens it in the player. */
data class FenrirPluginPlayRequest(
    val hostId: String,
    val showTitle: String,
    val episodeName: String,
    val stream: FenrirPluginStream,
)

object FenrirPluginPlayback {
    private val _pending = MutableStateFlow<FenrirPluginPlayRequest?>(null)
    val pending: StateFlow<FenrirPluginPlayRequest?> = _pending.asStateFlow()

    fun play(request: FenrirPluginPlayRequest) {
        _pending.value = request
    }

    fun consume(): FenrirPluginPlayRequest? = _pending.value.also { _pending.value = null }
}

private val Bg = Color(0xFF0B0B0F)
private val Card = Color(0xFF16161C)
private val Field = Color(0xFF1C1D26)
private val Muted = Color(0xFF8E8E99)

/** Full-screen plugin manager: Installed / Available / Repositories, plus "Browse". */
@Composable
fun FenrirPluginManagerDialog(host: FenrirPluginHost, onDismiss: () -> Unit) {
    var browsing by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (browsing) browsing = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (browsing) {
            FenrirPluginBrowse(host = host, onBack = { browsing = false }, onPlayed = onDismiss)
        } else {
            FenrirPluginManager(host = host, onBack = onDismiss, onBrowse = { browsing = true })
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(8.dp).size(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
private fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(CircleShape)
            .background(Field)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (index == selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (index == selected) Color.White else Color(0xFFA7A7B2), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        color = if (filled) Color.White else Color(0xFFFF6B73),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) accent else accent.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun FenrirPluginManager(host: FenrirPluginHost, onBack: () -> Unit, onBrowse: () -> Unit) {
    val state by host.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(if (state.installed.isEmpty()) 2 else 0) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().background(Bg)) {
        Header(host.title, onBack) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
            }
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = Color.White,
                modifier = Modifier.clip(CircleShape).clickable { host.refresh() }.padding(8.dp).size(22.dp),
            )
        }
        Tabs(listOf("Installed", "Available", "Repositories"), tab) { tab = it }
        state.message?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { host.clearMessage() }.padding(6.dp))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottom + 24.dp),
        ) {
            when (tab) {
                0 -> {
                    if (host.hasSources) {
                        item(key = "browse") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Card).clickable(onClick = onBrowse).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Browse", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Search them and play straight from them", color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (state.installed.isEmpty()) {
                        item(key = "empty-installed") { EmptyNote("Nothing installed yet. Add your repository under Repositories, then install from Available.") }
                    }
                    items(state.installed, key = { "i-${it.id}" }) { plugin ->
                        PluginRow(plugin) {
                            when {
                                plugin.needsTrust -> PillButton("Trust", filled = true) { host.trust(plugin.id) }
                                plugin.hasUpdate -> PillButton("Update", filled = false) { host.install(plugin.id) }
                                else -> Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Remove",
                                    tint = Muted,
                                    modifier = Modifier.clip(CircleShape).clickable { host.uninstall(plugin.id) }.padding(6.dp).size(20.dp),
                                )
                            }
                        }
                    }
                }
                1 -> {
                    val installedIds = state.installed.map { it.id }.toSet()
                    val notInstalled = state.available.filter { it.id !in installedIds }
                    if (notInstalled.isEmpty()) {
                        item(key = "empty-available") {
                            EmptyNote(if (state.repos.isEmpty()) "Add a repository first." else "Everything from your repositories is installed.")
                        }
                    }
                    items(notInstalled, key = { "a-${it.id}" }) { plugin ->
                        PluginRow(plugin) { PillButton("Install", filled = true) { host.install(plugin.id) } }
                    }
                }
                else -> {
                    item(key = "add-repo") { AddRepoField(onAdd = host::addRepo) }
                    items(state.repos, key = { "r-$it" }) { repo ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(repo, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Remove repository",
                                tint = Muted,
                                modifier = Modifier.clip(CircleShape).clickable { host.removeRepo(repo) }.padding(8.dp).size(20.dp),
                            )
                        }
                    }
                    item(key = "nsfw") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Card).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Show 18+ plugins", color = Color.White)
                                Text("Off hides plugins marked adult.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = state.showNsfw, onCheckedChange = host::setShowNsfw)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
}

@Composable
private fun PluginRow(plugin: FenrirPluginItem, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF22232C))) {
            if (plugin.iconUrl != null) {
                AsyncImage(model = plugin.iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(plugin.name, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(
                plugin.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                plugin.lang.takeIf { it.isNotBlank() }?.uppercase(),
                if (plugin.nsfw) "18+" else null,
                if (plugin.needsTrust) "Not trusted yet" else null,
                plugin.unsupportedReason,
            ).joinToString(" · ")
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun AddRepoField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Paste your repository link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Add", filled = true) {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            }
        }
    }
}

// ---------------------------------------------------------------- browse

@Composable
private fun FenrirPluginBrowse(host: FenrirPluginHost, onBack: () -> Unit, onPlayed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<FenrirPluginHit>>(emptyList()) }
    var openHit by remember { mutableStateOf<FenrirPluginHit?>(null) }
    var episodes by remember { mutableStateOf<List<FenrirPluginEpisode>>(emptyList()) }
    var openEpisode by remember { mutableStateOf<FenrirPluginEpisode?>(null) }
    var streams by remember { mutableStateOf<List<FenrirPluginStream>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun back() {
        when {
            openEpisode != null -> { openEpisode = null; streams = emptyList() }
            openHit != null -> { openHit = null; episodes = emptyList() }
            else -> onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Header(openEpisode?.name ?: openHit?.title ?: "Browse ${host.title.lowercase()}", { back() })
        if (openHit == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                PillButton("Go", filled = true) {
                    if (query.isNotBlank()) {
                        loading = true
                        searched = true
                        scope.launch {
                            hits = runCatching { host.search(query.trim()) }.getOrDefault(emptyList())
                            loading = false
                        }
                    }
                }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = bottom + 24.dp),
        ) {
            when {
                openEpisode != null -> {
                    if (!loading && streams.isEmpty()) item { EmptyNote("No playable links for this episode.") }
                    items(streams) { stream ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Card)
                                .clickable {
                                    FenrirPluginPlayback.play(
                                        FenrirPluginPlayRequest(host.id, openHit?.title.orEmpty(), openEpisode?.name.orEmpty(), stream),
                                    )
                                    onPlayed()
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stream.label, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(stream.sourceName, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                openHit != null -> {
                    if (!loading && episodes.isEmpty()) item { EmptyNote("No episodes found.") }
                    items(episodes, key = { it.key }) { episode ->
                        Text(
                            episode.name,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().clickable {
                                openEpisode = episode
                                loading = true
                                scope.launch {
                                    streams = runCatching { host.streams(episode) }.getOrDefault(emptyList())
                                    loading = false
                                }
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
                else -> {
                    if (searched && !loading && hits.isEmpty()) item { EmptyNote("Nothing found.") }
                    items(hits, key = { it.key }) { hit ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                openHit = hit
                                loading = true
                                scope.launch {
                                    episodes = runCatching { host.episodes(hit) }.getOrDefault(emptyList())
                                    loading = false
                                }
                            }.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(52.dp).height(76.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF22232C))) {
                                if (hit.poster != null) AsyncImage(model = hit.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(hit.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(hit.sourceName, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
