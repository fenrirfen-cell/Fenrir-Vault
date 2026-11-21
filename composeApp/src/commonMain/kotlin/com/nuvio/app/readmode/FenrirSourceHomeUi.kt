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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
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
    val host = FenrirPlugins.cloudStream
    var open by remember { mutableStateOf(false) }
    val selected by FenrirSourceHome.selected.collectAsStateWithLifecycle()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (host != null) Modifier.clickable { open = true } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FenrirWordmark()
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
            host = host,
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

@Composable
private fun FenrirSourcePickerPanel(
    host: FenrirPluginHost,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pluginState by host.state.collectAsStateWithLifecycle()
    val sources = remember(pluginState) { host.homeSources() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Panel)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(top = top + 14.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onDismiss)) {
                    FenrirWordmark()
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Close", tint = Red, modifier = Modifier.size(22.dp))
                }
                Text("Show on home", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    item {
                        SourceRow(
                            name = "Nuvio home",
                            subtitle = "Your normal home screen",
                            iconUrl = null,
                            letter = null,
                            selected = selected == null,
                            onClick = { onPick(null) },
                        )
                    }
                    items(sources, key = { it.id }) { source ->
                        SourceRow(
                            name = "CS · ${source.name}",
                            subtitle = source.subtitle.ifBlank { "CloudStream" },
                            iconUrl = source.iconUrl,
                            letter = source.name.take(1).uppercase(),
                            selected = selected == source.id,
                            onClick = { onPick(source.id) },
                        )
                    }
                    if (sources.isEmpty()) {
                        item {
                            Text(
                                if (pluginState.installed.isEmpty()) {
                                    "No CloudStream sources yet. Add them in Settings → CloudStream plugins."
                                } else {
                                    "Your CloudStream plugins are still loading, or none of them have a home page."
                                },
                                color = Muted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
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
fun LazyListScope.fenrirSourceHomeItems(
    state: FenrirSourceHomeState,
    source: FenrirHomeSource?,
    sourceId: String,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    item(key = "fenrir_source_header", contentType = "fenrir_source_header") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(source?.iconUrl, sourceId.take(1).uppercase(), 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(sourceId, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("CLOUDSTREAM")
                    source?.subtitle?.substringAfterLast(" · ", "")?.takeIf { it.isNotBlank() }?.let { Tag(it) }
                }
            }
        }
    }
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
            state.rows.forEachIndexed { index, row ->
                item(key = "fenrir_source_row_${index}_${row.title}", contentType = "fenrir_source_row") {
                    SourceRowSection(row, horizontalPadding)
                }
            }
            if (state.loading) {
                item(key = "fenrir_source_refreshing") { SkeletonRow(horizontalPadding) }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text.uppercase(),
        color = Color(0xFFD3D1C7),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Tile).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun SourceRowSection(row: FenrirHomeRow, horizontalPadding: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "See all",
                color = Red,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { FenrirSourceHomeUi.seeAll.value = row }.padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
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
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(7.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xCC0B0B0F)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            item.hit.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp, bottom = 2.dp),
        )
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
    if (FenrirSourceHomeUi.opening.value != null) return
    FenrirSourceHomeUi.opening.value = item
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

/** The source's own page for a title that didn't match a Nuvio show: episodes, links under each. */
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
