package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val HubMuted = Color(0xFF8E8E99)
private val HubAccentText = Color(0xFFFF6B73)

/**
 * Settings → Sources: one place for every kind of source (Stremio add-ons, Aniyomi extensions,
 * CloudStream plugins, manga extensions, novel plugins) and one "+ Add" box for links.
 * Opened from Settings and from the FENRIR READ ▾ menu ("Manage sources", "+").
 */
object FenrirSourcesHub {
    sealed interface Screen {
        data object Hub : Screen
        data object Add : Screen
        data object Stremio : Screen
        data class Plugins(val hostId: String) : Screen
    }

    private val _screen = MutableStateFlow<Screen?>(null)
    val screen: StateFlow<Screen?> = _screen.asStateFlow()

    /** Where Back goes: the hub when it was opened from the hub, otherwise closes. */
    private var fromHub = false

    fun open() { fromHub = false; _screen.value = Screen.Hub }
    fun openAdd() { fromHub = false; _screen.value = Screen.Add }
    internal fun go(screen: Screen) { fromHub = true; _screen.value = screen }
    internal fun back() {
        _screen.value = if (_screen.value != Screen.Hub && fromHub) Screen.Hub else null
        if (_screen.value == Screen.Hub) fromHub = false
    }
    fun close() { _screen.value = null }
}

/** Put once near the top of the app; shows whichever Sources screen is open. */
@Composable
fun FenrirSourcesHubHost() {
    val screen by FenrirSourcesHub.screen.collectAsStateWithLifecycle()
    val current = screen ?: return
    if (current is FenrirSourcesHub.Screen.Plugins) {
        // The plugin manager is its own full-screen dialog.
        val host = FenrirPlugins.all.firstOrNull { it.id == current.hostId }
        if (host == null) LaunchedEffect(current) { FenrirSourcesHub.back() }
        else FenrirPluginManagerDialog(host = host, onDismiss = { FenrirSourcesHub.back() })
        return
    }
    Dialog(
        onDismissRequest = { FenrirSourcesHub.back() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        when (current) {
            FenrirSourcesHub.Screen.Add -> AddSourceScreen()
            FenrirSourcesHub.Screen.Stremio -> StremioManager()
            else -> SourcesHub()
        }
    }
}

// ---------------------------------------------------------------------------- hub

@Composable
private fun SourcesHub() {
    val addons by AddonRepository.uiState.collectAsStateWithLifecycle()
    val sources = FenrirSources
    val mangaCount = remember { runCatching { sources.mangaExtensionCount?.invoke() }.getOrNull() }
    val novelCount = remember { runCatching { sources.novelPluginCount?.invoke() }.getOrNull() }
    fun installed(count: Int?) = count?.let { if (it == 1) "1 installed" else "$it installed" }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(Bg)) {
        FenrirSheetHeader("Sources", onBack = { FenrirSourcesHub.back() }) {
            PillButton("+ Add", filled = true) { FenrirSourcesHub.go(FenrirSourcesHub.Screen.Add) }
            Spacer(Modifier.width(6.dp))
        }
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottom + 24.dp)) {
            item {
                Text("Watch", color = HubMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 6.dp))
            }
            item {
                HubRow(
                    icon = Icons.Rounded.Extension,
                    title = "Stremio add-ons",
                    subtitle = listOfNotNull("Catalogs and streams", installed(addons.addons.size)).joinToString(" · "),
                ) { FenrirSourcesHub.go(FenrirSourcesHub.Screen.Stremio) }
            }
            items(FenrirPlugins.all, key = { it.id }) { host ->
                val state by host.state.collectAsStateWithLifecycle()
                val flags by FenrirSourceFlags.flags.collectAsStateWithLifecycle()
                val count = state.installed.count { !it.needsTrust }
                val onHome = remember(state, flags) {
                    state.installed.sumOf { p -> host.sourceNamesOf(p.id).count { FenrirSourceFlags.get(host.id, it).home } }
                }
                HubRow(
                    icon = if (host.id == "cloudstream") Icons.Rounded.Cloud else Icons.Rounded.Movie,
                    title = host.title,
                    subtitle = listOfNotNull(
                        installed(count),
                        onHome.takeIf { it > 0 }?.let { "$it on Home" },
                        state.installed.count { it.hasUpdate }.takeIf { it > 0 }?.let { "$it update(s)" },
                    ).joinToString(" · ").ifBlank { host.subtitle },
                ) { FenrirSourcesHub.go(FenrirSourcesHub.Screen.Plugins(host.id)) }
            }
            if (sources.openReadExtensions != null) {
                item {
                    Text("Read", color = HubMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp))
                }
                item {
                    HubRow(Icons.AutoMirrored.Rounded.MenuBook, "Manga extensions", listOfNotNull("Read side", installed(mangaCount)).joinToString(" · ")) {
                        FenrirSourcesHub.close(); sources.openReadExtensions?.invoke()
                    }
                }
                item {
                    HubRow(Icons.Rounded.AutoStories, "Novel plugins", listOfNotNull("Read side", installed(novelCount)).joinToString(" · ")) {
                        FenrirSourcesHub.close(); sources.openReadExtensions?.invoke()
                    }
                }
            }
            item {
                Text(
                    "Tap a CloudStream plugin or Aniyomi extension to choose where it's used: on Home, pinned, in Search, for streams.",
                    color = HubMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = HubAccentText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = HubMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = HubMuted)
    }
}

// ---------------------------------------------------------------------------- one Add box

private enum class AddKind(val label: String) { CloudStream("CloudStream"), Aniyomi("Aniyomi"), Stremio("Stremio") }

private fun guessKind(link: String): AddKind {
    val l = link.trim().lowercase()
    return when {
        l.startsWith("stremio://") || l.contains("manifest.json") -> AddKind.Stremio
        l.contains("aniyomi") || l.contains("anime") && l.contains("index") -> AddKind.Aniyomi
        else -> AddKind.CloudStream
    }
}

private fun looksLikeReadRepo(link: String): Boolean {
    val l = link.lowercase()
    return l.contains("plugins.min.json") || l.contains("lnreader") ||
        (l.contains("index.min.json") && !l.contains("anime") && !l.contains("aniyomi"))
}

private fun hostFor(kind: AddKind): FenrirPluginHost? = when (kind) {
    AddKind.CloudStream -> FenrirPlugins.cloudStream
    AddKind.Aniyomi -> FenrirPlugins.aniyomi
    AddKind.Stremio -> null
}

@Composable
private fun AddSourceScreen() {
    var link by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AddKind.CloudStream) }
    var picked by remember { mutableStateOf(false) }
    var addedKind by remember { mutableStateOf<AddKind?>(null) }
    var before by remember { mutableStateOf<Set<String>>(emptySet()) }
    var stremioBusy by remember { mutableStateOf(false) }
    var stremioMessage by remember { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf<AddonManifest?>(null) }
    val scope = rememberCoroutineScope()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(link) { if (!picked && link.isNotBlank()) kind = guessKind(link) }

    fun add() {
        val text = link.trim()
        if (text.isEmpty()) return
        when (kind) {
            AddKind.Stremio -> {
                stremioBusy = true
                stremioMessage = null
                scope.launch {
                    stremioMessage = when (val result = AddonRepository.addAddon(text)) {
                        is AddAddonResult.Success -> "Added ${result.manifest.name}."
                        is AddAddonResult.TrackingConsentRequired -> { consent = result.manifest; null }
                        is AddAddonResult.Error -> result.message
                    }
                    stremioBusy = false
                    addedKind = AddKind.Stremio
                }
            }
            else -> {
                val host = hostFor(kind) ?: return
                before = host.state.value.available.map { it.id }.toSet()
                host.addRepo(text)
                addedKind = kind
            }
        }
        link = ""
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        FenrirSheetHeader("Add sources", onBack = { FenrirSourcesHub.back() })
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = bottom + 24.dp)) {
            item {
                Text(
                    "Paste a CloudStream repo or short code, an Aniyomi repo, or a Stremio add-on link.",
                    color = HubMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("Paste a link or code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AddKind.entries.forEach { k ->
                        val on = k == kind
                        Text(
                            k.label,
                            color = if (on) Color.White else Color(0xFFA7A7B2),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else Field)
                                .clickable { kind = k; picked = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PillButton("Add", filled = true) { add() }
                }
                if (link.isNotBlank() && looksLikeReadRepo(link)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Card)
                            .clickable { FenrirSourcesHub.close(); FenrirSources.openReadExtensions?.invoke() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Looks like a manga or novel repo. Add it on the Read side →", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }

            when (val k = addedKind) {
                null -> Unit
                AddKind.Stremio -> item {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (stremioBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Adding…", color = HubMuted)
                        } else {
                            stremioMessage?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                else -> {
                    val host = hostFor(k)
                    if (host != null) item(key = "found") { FoundPlugins(host, before) }
                }
            }
        }
    }

    consent?.let { manifest ->
        ConfirmDialog(
            title = "Add ${manifest.name}?",
            text = "This add-on says it keeps track of what you watch.",
            confirm = "Add anyway",
            onConfirm = {
                consent = null
                stremioBusy = true
                scope.launch {
                    stremioMessage = when (val r = AddonRepository.installWithTrackingConsent(manifest)) {
                        is AddAddonResult.Success -> "Added ${r.manifest.name}."
                        is AddAddonResult.Error -> r.message
                        else -> null
                    }
                    stremioBusy = false
                }
            },
            onDismiss = { consent = null; stremioMessage = "Not added." },
        )
    }
}

/** What the new repo added: Install each, or Install all. */
@Composable
private fun FoundPlugins(host: FenrirPluginHost, before: Set<String>) {
    val state by host.state.collectAsStateWithLifecycle()
    val installedIds = state.installed.map { it.id }.toSet()
    val guard = rememberInstallGuard(host)
    val fresh = state.available.filter { it.id !in before }
    val list = (fresh.ifEmpty { state.available }).filter { it.id !in installedIds }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (fresh.isNotEmpty()) "From this repo" else "${host.title}: not installed yet",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            if (list.size > 1) PillButton("Install all", filled = false) { guard.request(list) }
        }
        state.message?.let {
            Text(it, color = HubMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        }
        if (list.isEmpty() && !state.busy) {
            Text("Nothing new to install from it.", color = HubMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
        }
        list.forEach { plugin ->
            PluginRow(plugin) { PillButton("Install", filled = true) { guard.request(plugin) } }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------- Stremio, same layout

@Composable
private fun StremioManager() {
    val ui by AddonRepository.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<AddonManifest?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val on = ui.addons.filter { it.enabled }
    val off = ui.addons.filter { !it.enabled }

    fun addLink(text: String) {
        busy = true
        scope.launch {
            message = when (val r = AddonRepository.addAddon(text)) {
                is AddAddonResult.Success -> "Added ${r.manifest.name}."
                is AddAddonResult.TrackingConsentRequired -> { consent = r.manifest; null }
                is AddAddonResult.Error -> r.message
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        FenrirSheetHeader("Stremio add-ons", onBack = { FenrirSourcesHub.back() }) {
            if (busy || ui.addons.any { it.isRefreshing }) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            PillButton("Refresh", filled = false) { AddonRepository.refreshAll() }
            Spacer(Modifier.width(6.dp))
        }
        FenrirTabs(listOf("Installed", "Available", "Repositories"), tab) { tab = it }
        message?.let { text ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { message = null }.padding(6.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
            when (tab) {
                0 -> {
                    if (on.isEmpty()) item { EmptyNote("No add-ons turned on. Add one under Repositories.") }
                    items(on, key = { "on-${it.manifestUrl}" }) { addon ->
                        AddonRow(addon.displayTitle, addon.manifest?.description ?: addon.errorMessage ?: addon.manifestUrl, addon.manifest?.logoUrl) {
                            Switch(checked = true, onCheckedChange = { AddonRepository.setAddonEnabled(addon.manifestUrl, false) })
                        }
                    }
                }
                1 -> {
                    item { EmptyNote(if (off.isEmpty()) "Add-ons you turn off show here, ready to turn back on." else "Turned off. Turn one on to use it again.") }
                    items(off, key = { "off-${it.manifestUrl}" }) { addon ->
                        AddonRow(addon.displayTitle, addon.manifest?.description ?: addon.manifestUrl, addon.manifest?.logoUrl) {
                            PillButton("Turn on", filled = true) { AddonRepository.setAddonEnabled(addon.manifestUrl, true) }
                        }
                    }
                }
                else -> {
                    item { AddRepoField(placeholder = "Add by link: paste an add-on link", onAdd = ::addLink) }
                    if (ui.addons.size > 1) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
                                PillButton("Copy all links", filled = false) {
                                    clipboard.setText(AnnotatedString(ui.addons.joinToString("\n") { it.manifestUrl }))
                                    message = "Copied ${ui.addons.size} links."
                                }
                            }
                        }
                    }
                    items(ui.addons, key = { "r-${it.manifestUrl}" }) { addon ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Card)
                                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(addon.displayTitle, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(addon.manifestUrl, color = HubMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            CopyButton { clipboard.setText(AnnotatedString(addon.manifestUrl)) }
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Remove add-on",
                                tint = HubMuted,
                                modifier = Modifier.clip(CircleShape).clickable { AddonRepository.removeAddon(addon.manifestUrl) }.padding(8.dp).size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    consent?.let { manifest ->
        ConfirmDialog(
            title = "Add ${manifest.name}?",
            text = "This add-on says it keeps track of what you watch.",
            confirm = "Add anyway",
            onConfirm = {
                consent = null
                busy = true
                scope.launch {
                    message = when (val r = AddonRepository.installWithTrackingConsent(manifest)) {
                        is AddAddonResult.Success -> "Added ${r.manifest.name}."
                        is AddAddonResult.Error -> r.message
                        else -> null
                    }
                    busy = false
                }
            },
            onDismiss = { consent = null },
        )
    }
}

@Composable
private fun AddonRow(title: String, subtitle: String, logo: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF22232C)), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), color = Color(0xFFB4B2A9), fontWeight = FontWeight.SemiBold)
            if (!logo.isNullOrBlank()) {
                AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = HubMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
