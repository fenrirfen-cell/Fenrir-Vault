package com.nuvio.app.readmode

import androidx.compose.foundation.background
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val Red = Color(0xFFFF4D57)
private val RedSoft = Color(0xFF3A1F25)
private val Panel = Color(0xFF141419)
private val Tile = Color(0xFF1C1C23)
private val Muted = Color(0xFF8E8E99)

/** Open "See all" grid and title sheet for the source home. */
object FenrirSourceHomeUi {
    /** A CloudStream show tapped in Continue watching, being resumed. */
    val resuming = MutableStateFlow<FenrirResumeTarget?>(null)
    val seeAll = MutableStateFlow<FenrirHomeRow?>(null)
    val sheetItem = MutableStateFlow<FenrirHomeItem?>(null)
    val opening = MutableStateFlow<FenrirHomeItem?>(null)
    /** True while the picked source's own search is open (top Search button). */
    val searching = MutableStateFlow(false)
}

// ---------------------------------------------------------------------------- FENRIR READ drop-down

/** "FENRIR READ ▾": tapping it opens the list of sources to show on Home. */
@Composable
fun FenrirWordmarkSourceButton(onPicked: () -> Unit, modifier: Modifier = Modifier) {
    val host = FenrirPlugins.cloudStream ?: FenrirPlugins.aniyomi
    var open by remember { mutableStateOf(false) }
    val selected by FenrirSourceHome.selected.collectAsStateWithLifecycle()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (host != null) Modifier.clickable { open = true } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A little bigger in the top bar, centred on the Watch / Search buttons.
        FenrirWordmark(scale = 1.15f, word = "WATCH")
        if (host != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Choose what Home shows",
                tint = Red,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    if (open && host != null) {
        FenrirSourcePickerPanel(
            selected = selected,
            onPick = { id ->
                open = false
                FenrirSourceHome.select(id)
                onPicked()
            },
            onDismiss = { open = false },
        )
    }
}

private data class PickerEntry(val source: FenrirHomeSource, val key: String, val pinned: Boolean, val aniyomi: Boolean)

@Composable
private fun FenrirSourcePickerPanel(
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = FenrirPlugins.cloudStream
    val ay = FenrirPlugins.aniyomi
    val csState = cs?.state?.collectAsStateWithLifecycle()?.value
    val ayState = ay?.state?.collectAsStateWithLifecycle()?.value
    val flags by FenrirSourceFlags.flags.collectAsStateWithLifecycle()
    val pinOrder by FenrirSourceFlags.pinOrder.collectAsStateWithLifecycle()

    fun entries(host: FenrirPluginHost?, aniyomi: Boolean, hidden: Boolean = false): List<PickerEntry> =
        host?.homeSources().orEmpty().mapNotNull { src ->
            val key = FenrirSourceFlags.key(host!!.id, src.name)
            val f = flags[key] ?: FenrirSourceFlags.get(key)
            // The picked one always shows, so it can be seen and switched off.
            val shown = f.home || src.id == selected
            if (shown == hidden) null else PickerEntry(src, key, f.pinned, aniyomi)
        }.sortedWith(
            compareBy<PickerEntry>({ !it.pinned }, { pinOrder.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.source.name.lowercase() }),
        )

    val csEntries = remember(csState, flags, pinOrder, selected) { entries(cs, false) }
    val ayEntries = remember(ayState, flags, pinOrder, selected) { entries(ay, true) }
    val csHidden = remember(csState, flags, selected) { entries(cs, false, hidden = true) }
    val ayHidden = remember(ayState, flags, selected) { entries(ay, true, hidden = true) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            // A floating glass card under the wordmark, like the preview: compact rows.
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(
                Modifier
                    .padding(top = top + 62.dp, start = 12.dp, end = 40.dp, bottom = bottomInset + 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xF71C1C22))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(8.dp),
            ) {
                // Groups fold up so the list stays short and the buttons always show.
                var csOpen by remember { mutableStateOf(false) }
                var ayOpen by remember { mutableStateOf(false) }
                var csShowHidden by remember { mutableStateOf(false) }
                var ayShowHidden by remember { mutableStateOf(false) }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    // Fenrir: For you lives here (it left the bottom bar)
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Red.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                                .clickable {
                                    onDismiss()
                                    FenrirForYou.show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF2D2D38)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFFF6B73), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("For you", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("Picks based on what you watch", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(1.dp).background(Color(0xFF3A3A46)))
                    }
                    item {
                        val off by FenrirSourceHealth.turnedOff.collectAsStateWithLifecycle()
                        if (off.isNotEmpty()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Red.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${off.size} not working, turned off (see 👁 hidden)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "Undo",
                                    color = Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(CircleShape).clickable { FenrirSourceHealth.undo() }.padding(6.dp),
                                )
                                Text(
                                    "✕",
                                    color = Muted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clip(CircleShape).clickable { FenrirSourceHealth.dismiss() }.padding(6.dp),
                                )
                            }
                        }
                    }
                    item {
                        val checking by FenrirSourceHealth.running.collectAsStateWithLifecycle()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { GroupLabel("Show on home") }
                            Text(
                                if (checking) "Checking…" else "Check sources",
                                color = Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 6.dp, end = 6.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !checking) { FenrirSourceHealth.checkAll() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    item {
                        MenuRow(
                            name = "Nuvio home",
                            subtitle = "Stremio add-on catalogs",
                            iconUrl = null,
                            letter = null,
                            selected = selected == null,
                            onClick = { onPick(null) },
                        )
                    }
                    sourceGroup(
                        prefix = "cs",
                        title = "CloudStream",
                        shown = csEntries,
                        hidden = csHidden,
                        selected = selected,
                        open = csOpen,
                        onToggle = { csOpen = !csOpen },
                        showHidden = csShowHidden,
                        onToggleHidden = { csShowHidden = !csShowHidden },
                        onPick = onPick,
                    )
                    sourceGroup(
                        prefix = "ay",
                        title = "Aniyomi",
                        shown = ayEntries,
                        hidden = ayHidden,
                        selected = selected,
                        open = ayOpen,
                        onToggle = { ayOpen = !ayOpen },
                        showHidden = ayShowHidden,
                        onToggleHidden = { ayShowHidden = !ayShowHidden },
                        onPick = onPick,
                    )
                    if (csEntries.isEmpty() && ayEntries.isEmpty() && csHidden.isEmpty() && ayHidden.isEmpty()) {
                        item {
                            val anything = csState?.installed.orEmpty().isNotEmpty() || ayState?.installed.orEmpty().isNotEmpty()
                            Text(
                                if (anything) {
                                    "None of your sources are on Home yet, or they're still loading. Turn them on in Manage sources."
                                } else {
                                    "No CloudStream or Aniyomi sources yet. Tap + to add some."
                                },
                                color = Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(1.dp).background(Color(0xFF2A2A34)))
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF22222A))
                            .border(1.dp, Color(0xFF34343F), RoundedCornerShape(20.dp))
                            .clickable { onDismiss(); FenrirSourcesHub.open() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Manage sources", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Red)
                            .clickable { onDismiss(); FenrirSourcesHub.openAdd() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ Add extensions", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One compact row of the FENRIR READ ▾ menu (50dp, small icon), as in the preview. */
@Composable
private fun MenuRow(
    name: String,
    subtitle: String,
    iconUrl: String?,
    letter: String?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    health: FenrirSourceHealth.Status? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Red.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))) {
                SourceIcon(iconUrl, letter, 26.dp)
            }
            if (health != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C22))
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(
                            when (health) {
                                FenrirSourceHealth.Status.Working -> Color(0xFF4CD787)
                                FenrirSourceHealth.Status.Slow -> Color(0xFFFFB547)
                                FenrirSourceHealth.Status.Broken -> Color(0xFFFF4D57)
                                FenrirSourceHealth.Status.Checking -> Color(0xFF6E6E78)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (health) {
                    FenrirSourceHealth.Status.Broken -> "Not working right now"
                    FenrirSourceHealth.Status.Slow -> "$subtitle · slow"
                    FenrirSourceHealth.Status.Checking -> "$subtitle · checking…"
                    else -> subtitle
                },
                color = if (health == FenrirSourceHealth.Status.Broken) Color(0xFFFF6B73) else Color(0xFF9A9AA6),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Red, modifier = Modifier.size(18.dp))
        trailing?.invoke()
    }
}

/**
 * One group of the menu (CloudStream or Aniyomi): folded header; when open, its sources with a
 * hide button each, then "Show hidden (N)" to bring hidden ones back.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.sourceGroup(
    prefix: String,
    title: String,
    shown: List<PickerEntry>,
    hidden: List<PickerEntry>,
    selected: String?,
    open: Boolean,
    onToggle: () -> Unit,
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    onPick: (String?) -> Unit,
) {
    if (shown.isEmpty() && hidden.isEmpty()) return
    item(key = "$prefix-head") {
        val health by FenrirSourceHealth.status.collectAsStateWithLifecycle()
        GroupHeader(
            title,
            shown.size,
            shown.firstOrNull { it.source.id == selected }?.source?.name,
            open,
            onToggle,
            hiddenCount = hidden.size,
            brokenCount = shown.count { health[it.source.id] == FenrirSourceHealth.Status.Broken },
            showingHidden = showHidden,
            onToggleHidden = onToggleHidden,
        )
    }
    // Hidden ones, right under the header (the header's eye button shows them).
    if (showHidden && hidden.isNotEmpty()) {
        items(hidden, key = { "$prefix-h-" + it.source.id }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).graphicsLayer { alpha = 0.5f }) {
                    SourceIcon(e.source.iconUrl, e.source.name.take(1).uppercase(), 24.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    e.source.name,
                    color = Color(0xFF9A9AA6),
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Unhide",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Red)
                        .clickable { FenrirSourceFlags.setHome(e.key, true) }
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                )
            }
        }
    }
    if (!open) return
    items(shown, key = { "$prefix-" + it.source.id }) { e ->
        val health by FenrirSourceHealth.status.collectAsStateWithLifecycle()
        MenuRow(
            name = e.source.name,
            subtitle = if (e.pinned) "Pinned · $title" else title,
            iconUrl = e.source.iconUrl,
            letter = e.source.name.take(1).uppercase(),
            selected = selected == e.source.id,
            onClick = { onPick(e.source.id) },
            health = health[e.source.id],
            trailing = {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = "Hide from this menu",
                    tint = Color(0xFF8E8E99),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (selected == e.source.id) onPick(null)
                            FenrirSourceFlags.setHome(e.key, false)
                        }
                        .padding(7.dp)
                        .size(16.dp),
                )
            },
        )
    }
}

/** "CLOUDSTREAM · 12 ▾": tap to show or hide that group's sources. */
@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    pickedName: String?,
    open: Boolean,
    onToggle: () -> Unit,
    hiddenCount: Int = 0,
    brokenCount: Int = 0,
    showingHidden: Boolean = false,
    onToggleHidden: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) Color(0xFF24242C) else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                pickedName?.let { "On home now: $it" }
                    ?: ((if (count == 1) "1 source" else "$count sources") + if (brokenCount > 0) " · $brokenCount not working" else ""),
                color = if (pickedName != null) Red else Color(0xFF9A9AA6),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hiddenCount > 0) {
            // Eye button: show the hidden ones right here (no scrolling down).
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (showingHidden) Red else Color(0xFF2A2A33))
                    .clickable(onClick = onToggleHidden)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Visibility, contentDescription = "Show hidden", tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("$hiddenCount", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (open) "Hide" else "Show",
            tint = Color(0xFFD3D1C7),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SourceRow(
    name: String,
    subtitle: String,
    iconUrl: String?,
    letter: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) RedSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(iconUrl, letter, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (selected) Red else Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SourceIcon(iconUrl: String?, letter: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFF22222A)),
        contentAlignment = Alignment.Center,
    ) {
        if (letter == null) {
            Icon(Icons.Rounded.Home, contentDescription = null, tint = Color(0xFFD3D1C7), modifier = Modifier.size(size * 0.5f))
        } else {
            Text(letter, color = Color(0xFFB4B2A9), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(model = iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ---------------------------------------------------------------------------- home rows

/** The picked source's home: a header like the source page, then one row per category. */
/**
 * Room on Home for the source pill, which is drawn over the top bar (under the FENRIR READ
 * wordmark) by [FenrirSourcePillOverlay] so the Watch / Search buttons don't move.
 */
fun LazyListScope.fenrirSourceHomeHeader(
    source: FenrirHomeSource?,
    sourceId: String,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    item(key = "fenrir_source_header", contentType = "fenrir_source_header") {
        Spacer(Modifier.fillMaxWidth().height(4.dp))
    }
}

/** The picked source as a small "icon · name · CloudStream" pill, shown under FENRIR READ. */
@Composable
fun FenrirSourcePillOverlay(modifier: Modifier = Modifier) {
    val sourceId by FenrirSourceHome.selected.collectAsStateWithLifecycle()
    val id = sourceId ?: return
    val source = remember(id) { FenrirSourceHome.sourceInfo(id) }
    // A subtitle under the wordmark: tiny icon, name, "· CloudStream". No box around it.
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(CircleShape)) {
            SourceIcon(source?.iconUrl, (source?.name ?: id).take(1).uppercase(), 16.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            source?.name ?: id.removePrefix("ay:"),
            color = Color(0xFFE4E4EA),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            if (FenrirSourceHome.isAniyomiSource(id)) "  ·  Aniyomi" else "  ·  CloudStream",
            color = Muted, fontSize = 12.sp, maxLines = 1,
        )
    }
}

/** Fenrir: the featured banner on a plugin site's Home, from the site's first list. */
fun LazyListScope.fenrirSourceHomeHero(state: FenrirSourceHomeState, source: FenrirHomeSource?) {
    val row = state.rows.firstOrNull { !it.horizontalImages && it.items.size >= 3 } ?: return
    item(key = "fenrir_source_hero_${row.title}", contentType = "fenrir_source_hero") {
        val pages = remember(row) {
            row.items.take(5).mapIndexed { i, item ->
                FenrirHeroPage(
                    key = item.hit.key,
                    image = item.hit.poster,
                    logo = null,
                    title = item.hit.title,
                    tag = "#${i + 1} · ${row.title.uppercase()}",
                    meta = listOfNotNull(item.year?.toString(), if (item.isMovie) "Movie" else "TV", source?.name).joinToString("  ·  "),
                    rating = null,
                    audio = item.badge,
                    onPlay = { openItem(item) },
                    onInfo = { openItem(item) },
                )
            }
        }
        val h = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        FenrirBleedHero(pages = pages, height = (h * 0.50f).coerceIn(340.dp, 480.dp), topBarRoom = modeBarHeight())
    }
}

fun LazyListScope.fenrirSourceHomeItems(
    state: FenrirSourceHomeState,
    source: FenrirHomeSource?,
    sourceId: String,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    when {
        state.rows.isEmpty() && state.loading -> {
            items(3, key = { "fenrir_source_skeleton_$it" }) { SkeletonRow(horizontalPadding) }
        }
        state.rows.isEmpty() -> {
            item(key = "fenrir_source_empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (state.failed) "Couldn't load $sourceId" else "$sourceId didn't return any home lists",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Check your connection and try again.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Try again",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RedSoft)
                            .clickable { FenrirSourceHome.load(sourceId, force = true) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
        else -> {
            // Fenrir: Top 10 from the site's first list
            state.rows.firstOrNull { !it.horizontalImages && it.items.size >= 5 }?.let { top ->
                item(key = "fenrir_source_top10_${top.title}", contentType = "fenrir_top10") {
                    FenrirTop10Row(
                        title = "Top 10 today",
                        subtitle = listOfNotNull(top.title, source?.name).joinToString(" · "),
                        entries = top.items.take(10).map { FenrirTopEntry(it.hit.key, it.hit.title, it.hit.poster) { openItem(it) } },
                        horizontalPadding = horizontalPadding,
                    )
                }
            }
            state.rows.forEachIndexed { index, row ->
                item(key = "fenrir_source_row_${index}_${row.title}", contentType = "fenrir_source_row") {
                    SourceRowSection(row, horizontalPadding, source?.name)
                }
            }
            if (state.loading) {
                item(key = "fenrir_source_refreshing") { SkeletonRow(horizontalPadding) }
            }
        }
    }
}

@Composable
private fun SourceRowSection(row: FenrirHomeRow, horizontalPadding: androidx.compose.ui.unit.Dp, sourceName: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        // Fenrir: bold title, the site it comes from, a round › for See all
        FenrirRowHeader(
            title = row.title,
            subtitle = sourceName,
            horizontalPadding = horizontalPadding,
            onSeeAll = { FenrirSourceHomeUi.seeAll.value = row },
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(row.items, key = { it.hit.key }) { item ->
                PosterCard(item, Modifier.width(if (row.horizontalImages) 200.dp else 118.dp), wide = row.horizontalImages)
            }
        }
    }
}

@Composable
private fun PosterCard(item: FenrirHomeItem, modifier: Modifier, wide: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable { openItem(item) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (wide) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Tile),
        ) {
            if (!item.hit.poster.isNullOrBlank()) {
                AsyncImage(model = item.hit.poster, contentDescription = item.hit.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            item.badge?.let { badge ->
                FenrirAudioChip(badge, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Text(
            item.hit.title,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        val sub = listOfNotNull(item.year?.toString(), if (item.isMovie) "Movie" else null).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(sub, color = Muted, fontSize = 11.5.sp, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun SkeletonRow(horizontalPadding: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Box(Modifier.padding(horizontal = horizontalPadding).width(140.dp).height(20.dp).clip(RoundedCornerShape(6.dp)).background(Tile))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = horizontalPadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { Box(Modifier.width(118.dp).height(177.dp).clip(RoundedCornerShape(14.dp)).background(Tile)) }
        }
    }
}

private fun openItem(item: FenrirHomeItem) {
    // Fenrir: a title from a plugin site always opens that site's own page (its episodes and its
    // own links), never the matching Nuvio show page with the addon links.
    if (FenrirSourceHomeUi.sheetItem.value != null) return
    FenrirSourceHomeUi.sheetItem.value = item
}

// ---------------------------------------------------------------------------- overlays (see all, opening, title sheet)

/**
 * Put once on the home screen. Handles a poster tap (normal show page when the title matches,
 * otherwise the source's own episode sheet) and the "See all" grid.
 */
@Composable
fun FenrirSourceHomeOverlays(onOpenShowPage: (MetaPreview) -> Unit) {
    val opening by FenrirSourceHomeUi.opening.collectAsStateWithLifecycle()
    val seeAll by FenrirSourceHomeUi.seeAll.collectAsStateWithLifecycle()
    val sheetItem by FenrirSourceHomeUi.sheetItem.collectAsStateWithLifecycle()
    val selected by FenrirSourceHome.selected.collectAsStateWithLifecycle()

    val resuming by FenrirSourceHomeUi.resuming.collectAsStateWithLifecycle()
    LaunchedEffect(resuming) {
        val target = resuming ?: return@LaunchedEffect
        val outcome = runCatching { FenrirSourceHome.resume(target) }.getOrNull()
        FenrirSourceHomeUi.resuming.value = null
        if (outcome != null) FenrirSourceHomeUi.sheetItem.value = outcome
    }
    if (resuming != null) {
        Dialog(onDismissRequest = { FenrirSourceHomeUi.resuming.value = null }) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Panel).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Red, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Resuming ${resuming?.title.orEmpty()}…", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Getting a fresh link from ${resuming?.sourceName.orEmpty()}", color = Muted, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }

    LaunchedEffect(opening) {
        val item = opening ?: return@LaunchedEffect
        val match = runCatching { FenrirSourceHome.matchToNuvio(item) }.getOrNull()
        FenrirSourceHomeUi.opening.value = null
        if (match != null) {
            // The show page opens behind the search screen, so close search first.
            FenrirSourceHomeUi.searching.value = false
            onOpenShowPage(match)
        } else FenrirSourceHomeUi.sheetItem.value = item
    }

    if (opening != null) {
        Dialog(onDismissRequest = { FenrirSourceHomeUi.opening.value = null }) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Panel).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Red, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text("Opening ${opening?.hit?.title.orEmpty()}…", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val row = seeAll
    val sourceId = selected
    if (row != null && sourceId != null) {
        SeeAllDialog(row, sourceId, onDismiss = { FenrirSourceHomeUi.seeAll.value = null })
    }

    val searching by FenrirSourceHomeUi.searching.collectAsStateWithLifecycle()
    if (searching && sourceId != null) {
        SourceSearchDialog(sourceId, onDismiss = { FenrirSourceHomeUi.searching.value = false })
    }

    sheetItem?.let { item ->
        TitleSheet(item, onDismiss = { FenrirSourceHomeUi.sheetItem.value = null })
    }
}

/** Search inside the picked source only (not the other plugins or addons). */
@Composable
private fun SourceSearchDialog(sourceId: String, onDismiss: () -> Unit) {
    val host = FenrirSourceHome.host
    var query by remember(sourceId) { mutableStateOf("") }
    var results by remember(sourceId) { mutableStateOf<List<FenrirHomeItem>>(emptyList()) }
    var loading by remember(sourceId) { mutableStateOf(false) }
    var searched by remember(sourceId) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(query, sourceId) {
        val text = query.trim()
        if (text.isEmpty() || host == null) {
            results = emptyList(); loading = false; searched = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(400)
        loading = true
        // Plugins load in the background when the app starts; wait for this source briefly.
        var waited = 0
        while (host.homeSources().none { it.id == sourceId } && waited < 15_000) {
            kotlinx.coroutines.delay(500)
            waited += 500
        }
        val hits = runCatching { host.searchSource(sourceId, text) }.getOrDefault(emptyList())
        results = hits.distinctBy { it.key }.map { FenrirHomeItem(it) }
        loading = false
        searched = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
            Row(
                Modifier.fillMaxWidth().padding(top = top + 8.dp, start = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Row(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Tile).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search $sourceId", color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Red),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Text(
                "Only searching CS · $sourceId",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            )
            when {
                loading && results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Red, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                }
                searched && results.isEmpty() -> Text(
                    "Nothing found in $sourceId",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottom + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.hit.key }) { item ->
                        PosterCard(item, Modifier.fillMaxWidth(), wide = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeeAllDialog(row: FenrirHomeRow, sourceId: String, onDismiss: () -> Unit) {
    val host = FenrirSourceHome.host
    val items = remember(row) { mutableStateListOf<FenrirHomeItem>().apply { addAll(row.items) } }
    var page by remember(row) { mutableStateOf(1) }
    var loading by remember(row) { mutableStateOf(false) }
    var ended by remember(row) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    fun loadMore() {
        if (loading || ended || host == null) return
        loading = true
        scope.launch {
            val next = runCatching { host.homeRowPage(sourceId, row.categoryName, row.categoryData, page + 1) }.getOrDefault(emptyList())
            val known = items.map { it.hit.key }.toSet()
            val fresh = next.filter { it.hit.key !in known }
            if (fresh.isEmpty()) ended = true else { items.addAll(fresh); page += 1 }
            loading = false
        }
    }

    LaunchedEffect(gridState, row) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= items.size - 6) loadMore() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
            Row(
                Modifier.fillMaxWidth().padding(top = top + 8.dp, start = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(row.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("CS · $sourceId", color = Muted, fontSize = 13.sp)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottom + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.hit.key }) { item ->
                    PosterCard(item, Modifier.fillMaxWidth(), wide = false)
                }
                if (loading) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Red, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The source's own page for a title: episodes, its own links under each. */
@Composable
private fun TitleSheet(item: FenrirHomeItem, onDismiss: () -> Unit) {
    val host = FenrirSourceHome.host
    val scope = rememberCoroutineScope()
    var episodes by remember(item) { mutableStateOf<List<FenrirPluginEpisode>?>(null) }
    var openKey by remember(item) { mutableStateOf<String?>(null) }
    var links by remember(item) { mutableStateOf<Map<String, List<FenrirPluginStream>?>>(emptyMap()) }

    fun openEpisode(episode: FenrirPluginEpisode) {
        openKey = if (openKey == episode.key) null else episode.key
        if (host == null || links.containsKey(episode.key)) return
        links = links + (episode.key to null)
        scope.launch {
            val found = runCatching { host.streams(episode) }.getOrDefault(emptyList())
            links = links + (episode.key to found)
        }
    }

    LaunchedEffect(item) {
        val list = runCatching { host?.episodes(item.hit).orEmpty() }.getOrDefault(emptyList())
        episodes = list
        // A movie (one entry): show its links straight away.
        if (list.size == 1) openEpisode(list.first())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Panel)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 16.dp),
            ) {
                Box(Modifier.padding(top = 10.dp, bottom = 14.dp).width(44.dp).height(5.dp).clip(CircleShape).background(Color(0xFF3A3A44)).align(Alignment.CenterHorizontally))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(62.dp).height(88.dp).clip(RoundedCornerShape(10.dp)).background(Tile)) {
                        if (!item.hit.poster.isNullOrBlank()) {
                            AsyncImage(model = item.hit.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.hit.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull("on ${item.hit.sourceName}", item.year?.toString(), item.badge).joinToString(" · "),
                            color = Muted,
                            fontSize = 14.sp,
                        )
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val list = episodes
                when {
                    list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Red, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                    }
                    list.isEmpty() -> Text("No episodes found on ${item.hit.sourceName}.", color = Muted, modifier = Modifier.padding(16.dp))
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
                        items(list, key = { it.key }) { episode ->
                            val isOpen = openKey == episode.key
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isOpen) Tile else Color.Transparent)
                                    .then(if (isOpen) Modifier.border(1.5.dp, Color(0xFF5A2A30), RoundedCornerShape(18.dp)) else Modifier),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { openEpisode(episode) }.padding(horizontal = 14.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(episode.name, color = Color.White, fontSize = 16.sp, fontWeight = if (isOpen) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                    Icon(if (isOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Muted)
                                }
                                if (isOpen) {
                                    val found = links[episode.key]
                                    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                                        when {
                                            found == null -> Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(color = Red, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(10.dp))
                                                Text("Finding links…", color = Muted, fontSize = 14.sp)
                                            }
                                            found.isEmpty() -> Text("No playable links for this one.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(10.dp))
                                            else -> found.forEach { stream ->
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 6.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color(0xFF26262F))
                                                        .clickable {
                                                            FenrirPluginPlayback.play(
                                                                FenrirPluginPlayRequest(
                                                                    hostId = host?.id ?: "cloudstream",
                                                                    showTitle = item.hit.title,
                                                                    episodeName = episode.name,
                                                                    stream = stream,
                                                                    hitKey = item.hit.key,
                                                                    poster = item.hit.poster,
                                                                    episodeIndex = list.indexOf(episode),
                                                                    season = episode.season,
                                                                    episode = episode.episode,
                                                                    isMovie = item.isMovie || list.size == 1,
                                                                ),
                                                            )
                                                            onDismiss()
                                                        }
                                                        .padding(13.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(stream.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        if (stream.subtitles.isNotEmpty()) {
                                                            Text("${stream.subtitles.size} subtitle${if (stream.subtitles.size == 1) "" else "s"}", color = Muted, fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Small "CS · Kisskh" tag for Continue watching cards of shows played from a CloudStream source. */
@Composable
fun FenrirCloudStreamTag(parentMetaId: String, modifier: Modifier = Modifier) {
    val source = FenrirSourceHome.sourceOf(parentMetaId) ?: return
    Text(
        "CS · $source",
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xE63A1F25))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
