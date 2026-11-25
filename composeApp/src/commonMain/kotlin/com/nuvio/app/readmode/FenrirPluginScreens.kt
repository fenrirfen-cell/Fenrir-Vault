package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Home
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
    /** Source + title on that source ("Kisskh|url"): lets Continue watching find it again. */
    val hitKey: String? = null,
    val poster: String? = null,
    val episodeIndex: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isMovie: Boolean = false,
    val resumePositionMs: Long = 0L,
)

object FenrirPluginPlayback {
    private val _pending = MutableStateFlow<FenrirPluginPlayRequest?>(null)
    val pending: StateFlow<FenrirPluginPlayRequest?> = _pending.asStateFlow()

    fun play(request: FenrirPluginPlayRequest) {
        _pending.value = request
    }

    fun consume(): FenrirPluginPlayRequest? = _pending.value.also { _pending.value = null }
}

internal val Bg = Color(0xFF0B0B0F)
internal val Card = Color(0xFF16161C)
internal val Field = Color(0xFF1C1D26)
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
internal fun FenrirSheetHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
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
internal fun FenrirTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
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
internal fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
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
    val flags by FenrirSourceFlags.flags.collectAsStateWithLifecycle()
    var whereFor by remember { mutableStateOf<FenrirPluginItem?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val installGuard = rememberInstallGuard(host)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().background(Bg)) {
        FenrirSheetHeader(host.title, onBack) {
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
        val checks by host.checks.collectAsStateWithLifecycle()
        var checkFilter by remember { mutableStateOf<FenrirPluginCheck.Result?>(null) }
        FenrirTabs(listOf("Installed", "Available", "Repositories"), tab) { tab = it }
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
                        PluginRow(plugin, onClick = { whereFor = plugin }) {
                            val names = host.sourceNamesOf(plugin.id)
                            if (names.isNotEmpty() && !plugin.needsTrust) {
                                // flags is read so the chip updates as soon as it changes
                                val onHome = flags.let { names.any { FenrirSourceFlags.get(host.id, it).home } }
                                HomeChip(onHome) {
                                    names.forEach { n ->
                                        val key = FenrirSourceFlags.key(host.id, n)
                                        FenrirSourceFlags.set(key, FenrirSourceFlags.get(key).copy(home = !onHome))
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                            }
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
                    if (host.canTest && notInstalled.isNotEmpty()) {
                        item(key = "plugin-check") { PluginCheckCard(host, notInstalled, checkFilter) { checkFilter = it } }
                    }
                    val shownAvail = if (checkFilter == null) notInstalled else notInstalled.filter { p ->
                        val c = checks[p.id]?.result
                        when (checkFilter) {
                            FenrirPluginCheck.Result.Down -> c == FenrirPluginCheck.Result.Down || p.unsupportedReason != null
                            else -> c == checkFilter
                        }
                    }
                    items(shownAvail, key = { "a-${it.id}" }) { plugin ->
                        val check = checks[plugin.id]
                        val down = check?.result == FenrirPluginCheck.Result.Down || plugin.unsupportedReason != null
                        PluginRow(
                            plugin,
                            check = if (host.canTest) (check ?: FenrirPluginCheck(FenrirPluginCheck.Result.Testing, reason = "untested")) else null,
                            onTest = if (host.canTest) ({ host.test(listOf(plugin.id)) }) else null,
                        ) { PillButton("Install", filled = !down) { installGuard.request(plugin) } }
                    }
                }
                else -> {
                    item(key = "add-repo") { AddRepoField(placeholder = "Add by link: paste a repository link or code", onAdd = host::addRepo) }
                    if (state.repos.size > 1) {
                        item(key = "copy-all") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
                                PillButton("Copy all links", filled = false) {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.repos.joinToString("\n")))
                                }
                            }
                        }
                    }
                    items(state.repos, key = { "r-$it" }) { repo ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(repo, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            CopyButton { clipboard.setText(androidx.compose.ui.text.AnnotatedString(repo)) }
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
    whereFor?.let { plugin -> FenrirWhereToUseDialog(host, plugin) { whereFor = null } }
}

@Composable
internal fun CopyButton(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(1500); copied = false }
    }
    Text(
        if (copied) "Copied" else "Copy",
        color = if (copied) Color(0xFF7BD88F) else Color(0xFFFF6B73),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).clickable { onCopy(); copied = true }.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun HomeChip(onHome: Boolean, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (onHome) accent.copy(alpha = 0.22f) else Field)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Home,
            contentDescription = null,
            tint = if (onHome) Color(0xFFFF6B73) else Muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(if (onHome) "Home" else "Off", color = if (onHome) Color.White else Muted, style = MaterialTheme.typography.labelMedium)
    }
}

/** "Where to use it": for each site the plugin adds, Home / Pinned / Search / Streams. */
@Composable
internal fun FenrirWhereToUseDialog(host: FenrirPluginHost, plugin: FenrirPluginItem, onDismiss: () -> Unit) {
    val flags by FenrirSourceFlags.flags.collectAsStateWithLifecycle()
    val names = remember(plugin.id) { host.sourceNamesOf(plugin.id) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Color(0xFF15151B))
                    .clickable(enabled = false) {}
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = bottom + 16.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color(0xFF3A3A44)))
                Spacer(Modifier.height(12.dp))
                Text(plugin.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Where to use it", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (names.isEmpty()) {
                    Text(
                        if (plugin.needsTrust) "Trust this plugin first." else "This plugin is still loading or has no sites.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(names, key = { it }) { name ->
                        val key = FenrirSourceFlags.key(host.id, name)
                        val f = flags[key] ?: FenrirSourceFlags.get(key)
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 14.dp, vertical = 6.dp)) {
                            if (names.size > 1) Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                            FlagRow("On Home", "Listed in the FENRIR READ ▾ menu", f.home) { FenrirSourceFlags.set(key, f.copy(home = it, pinned = if (!it) false else f.pinned)) }
                            FlagRow("Pinned", "At the top of that menu", f.pinned) { FenrirSourceFlags.set(key, f.copy(pinned = it, home = if (it) true else f.home)) }
                            FlagRow("In Search", "Its results show when you search", f.search) { FenrirSourceFlags.set(key, f.copy(search = it)) }
                            FlagRow("For streams", "Used to find links on show pages", f.streams) { FenrirSourceFlags.set(key, f.copy(streams = it)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        WideButton("Turn off", filled = false) {
                            names.forEach { FenrirSourceFlags.set(FenrirSourceFlags.key(host.id, it), FenrirSourceFlags.Flags(home = false, pinned = false, search = false, streams = false)) }
                            onDismiss()
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        WideButton("Remove", filled = true) {
                            host.uninstall(plugin.id)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlagRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun WideButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (filled) accent else accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) Color.White else Color(0xFFFF6B73), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EmptyNote(text: String) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
}

@Composable
internal fun PluginRow(
    plugin: FenrirPluginItem,
    onClick: (() -> Unit)? = null,
    check: FenrirPluginCheck? = null,
    onTest: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
            if (check != null) PluginCheckLine(check, onTest)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
internal fun AddRepoField(placeholder: String = "Paste your repository link", onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder) },
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
        FenrirSheetHeader(openEpisode?.name ?: openHit?.title ?: "Browse ${host.title.lowercase()}", { back() })
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


/** Asks first when a plugin looks broken ("may not work · Install anyway"); otherwise installs. */
internal class InstallGuard(private val host: FenrirPluginHost) {
    var pending by androidx.compose.runtime.mutableStateOf<List<FenrirPluginItem>>(emptyList())

    fun request(plugin: FenrirPluginItem) = request(listOf(plugin))

    fun request(plugins: List<FenrirPluginItem>) {
        val risky = plugins.filter { it.unsupportedReason != null }
        plugins.filter { it.unsupportedReason == null }.forEach { host.install(it.id) }
        if (risky.isNotEmpty()) pending = risky
    }

    fun confirm() {
        pending.forEach { host.install(it.id) }
        pending = emptyList()
    }

    fun cancel() {
        pending = emptyList()
    }
}

@Composable
internal fun rememberInstallGuard(host: FenrirPluginHost): InstallGuard {
    val guard = remember(host) { InstallGuard(host) }
    val list = guard.pending
    if (list.isNotEmpty()) {
        FenrirInstallWarningDialog(
            names = list.map { it.name },
            reason = list.first().unsupportedReason ?: "",
            onInstall = guard::confirm,
            onCancel = guard::cancel,
        )
    }
    return guard
}

@Composable
internal fun FenrirInstallWarningDialog(names: List<String>, reason: String, onInstall: () -> Unit, onCancel: () -> Unit) {
    val title = if (names.size == 1) "${names.first()} may not work" else "${names.size} plugins may not work"
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF1A1A21),
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                (if (names.size > 1) names.joinToString(", ") + ": " else "") +
                    "$reason. You can still install it, but it probably won't load anything.",
                color = Color(0xFFB8B8C2),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onInstall) { Text("Install anyway", color = Color(0xFFFF6B73)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
        },
    )
}


// ---------------------------------------------------------------- Fenrir: plugin check

private val CheckGreen = Color(0xFF4CD787)
private val CheckAmber = Color(0xFFFFB547)
private val CheckRed = Color(0xFFFF6E78)

@Composable
private fun PluginCheckLine(check: FenrirPluginCheck, onTest: (() -> Unit)?) {
    val untested = check.result == FenrirPluginCheck.Result.Testing && check.reason == "untested"
    val (color, label) = when {
        untested -> Muted to "Not tested · Test"
        check.result == FenrirPluginCheck.Result.Testing -> Muted to "Testing…"
        check.result == FenrirPluginCheck.Result.Works -> CheckGreen to "Works · ${fmtSeconds(check.seconds)}"
        check.result == FenrirPluginCheck.Result.Slow -> CheckAmber to "Slow · ${fmtSeconds(check.seconds)}"
        else -> CheckRed to "Down · ${check.reason ?: "not loading"}"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 2.dp)
            .then(
                if (onTest != null && (untested || check.result == FenrirPluginCheck.Result.Down)) {
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTest)
                } else Modifier,
            ),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun fmtSeconds(s: Double): String {
    val tenths = (s * 10).toInt().coerceAtLeast(1)
    return "${tenths / 10}.${tenths % 10}s"
}

@Composable
private fun PluginCheckCard(
    host: FenrirPluginHost,
    plugins: List<FenrirPluginItem>,
    filter: FenrirPluginCheck.Result?,
    onFilter: (FenrirPluginCheck.Result?) -> Unit,
) {
    val checks by host.checks.collectAsStateWithLifecycle()
    val ids = plugins.map { it.id }
    val results = ids.mapNotNull { checks[it] }
    val testing = results.count { it.result == FenrirPluginCheck.Result.Testing }
    val tested = results.count { it.result != FenrirPluginCheck.Result.Testing }
    val works = results.count { it.result == FenrirPluginCheck.Result.Works }
    val slow = results.count { it.result == FenrirPluginCheck.Result.Slow }
    val down = results.count { it.result == FenrirPluginCheck.Result.Down }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .border(1.dp, Color(0xFF34343F), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Plugin check", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    if (tested == 0 && testing == 0) "Try plugins out before installing them"
                    else "Tested $tested of ${ids.size} · $works work · $slow slow · $down down",
                    color = Color(0xFFBEBEC8),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (testing > 0 || tested > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFF32323C))) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (ids.isEmpty()) 0f else tested.toFloat() / ids.size)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF4D5A)),
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            if (testing > 0) {
                Text("Testing…", color = Muted, style = MaterialTheme.typography.labelLarge)
            } else {
                PillButton(if (tested > 0) "Test again" else "Test all", filled = true) { host.test(ids) }
            }
        }
        if (tested > 0) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to "All",
                    FenrirPluginCheck.Result.Works to "Works",
                    FenrirPluginCheck.Result.Slow to "Slow",
                    FenrirPluginCheck.Result.Down to "Down",
                ).forEach { (key, label) ->
                    val on = filter == key
                    Text(
                        label,
                        color = if (on) Color.White else Muted,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) Color(0xFF3C161C) else Color.Transparent)
                            .border(1.dp, if (on) Color(0xFFFF4D5A) else Color(0xFF3C3C48), CircleShape)
                            .clickable { onFilter(key) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
