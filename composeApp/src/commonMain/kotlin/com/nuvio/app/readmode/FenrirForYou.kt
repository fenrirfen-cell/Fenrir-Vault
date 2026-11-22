package com.nuvio.app.readmode

import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Watch "For you": rows of titles like the ones you watched most recently. */
object FenrirForYou {
    data class ForYouRow(val title: String, val because: String, val items: List<MetaPreview>)

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()
    private val _rows = MutableStateFlow<List<ForYouRow>>(emptyList())
    val rows: StateFlow<List<ForYouRow>> = _rows.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private var loadedAt = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun show() {
        _open.value = true
        load()
    }

    fun hide() {
        _open.value = false
    }

    private data class Seed(val id: String, val type: String, val title: String, val at: Long)

    private fun load() {
        if (_loading.value) return
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (_rows.value.isNotEmpty() && now - loadedAt < 30 * 60 * 1000L) return
        _loading.value = true
        scope.launch {
            try {
                // Everything you've watched or saved: Continue watching, watched history and My list,
                // including CloudStream and Aniyomi titles (matched by name below).
                val progress = WatchProgressRepository.uiState.value.entries.map {
                    Seed(it.parentMetaId, it.parentMetaType, it.title, it.lastUpdatedEpochMs)
                }
                val watched = runCatching { com.nuvio.app.features.watched.WatchedRepository.uiState.value.items }
                    .getOrDefault(emptyList())
                    .map { Seed(it.id, it.type, it.name, it.markedAtEpochMs) }
                val saved = runCatching { com.nuvio.app.features.library.LibraryRepository.uiState.value.items }
                    .getOrDefault(emptyList())
                    .map { Seed(it.id, it.type, it.name, it.savedAtEpochMs / 2) } // saved counts less than watched
                val all = (progress + watched + saved).filter { it.title.isNotBlank() }
                val seen = all.map { it.id }.toSet()
                val seenTitles = all.map { titleKey(it.title) }.toSet()
                val seeds = all
                    .sortedByDescending { it.at }
                    .distinctBy { titleKey(it.title) }
                    .take(8)
                _seedCount.value = all.distinctBy { titleKey(it.title) }.size

                val rows = seeds.map { seed ->
                    async {
                        withTimeoutOrNull(25_000) {
                            val (type, id) = resolve(seed) ?: return@withTimeoutOrNull null
                            val (meta, related) = MetaDetailsRepository.relatedFor(type, id)
                            val items = related
                                .filterNot { it.id in seen || titleKey(it.name) in seenTitles }
                                .take(15)
                            if (items.isEmpty()) null else ForYouRow(
                                title = "Because you watched",
                                because = meta?.name ?: seed.title,
                                items = items,
                            )
                        }
                    }
                }.awaitAll().filterNotNull().distinctBy { it.because }

                // Top picks: titles that come up for several things you watched.
                val counts = rows.flatMap { row -> row.items.distinctBy { it.id } }.groupingBy { it.id }.eachCount()
                val byId = rows.flatMap { it.items }.associateBy { it.id }
                val top = counts.entries
                    .filter { it.value >= 2 }
                    .sortedByDescending { it.value }
                    .mapNotNull { byId[it.key] }
                    .take(15)
                _rows.value = buildList {
                    if (top.size >= 3) add(ForYouRow(title = "Top picks", because = "for you", items = top))
                    addAll(rows)
                }
                loadedAt = now
            } finally {
                _loading.value = false
            }
        }
    }

    private val _seedCount = MutableStateFlow(0)
    /** How many different titles the suggestions are based on. */
    val seedCount: StateFlow<Int> = _seedCount.asStateFlow()

    private fun titleKey(title: String) = title.lowercase().filter { it.isLetterOrDigit() }

    /**
     * IMDb / TMDB titles are used as they are. Anything else (CloudStream, Aniyomi…) is matched by
     * name on Cinemeta to find the same show or movie.
     */
    private suspend fun resolve(seed: Seed): Pair<String, String>? {
        val type = if (seed.type == "movie") "movie" else "series"
        if (seed.id.startsWith("tt") || seed.id.startsWith("tmdb:")) return type to seed.id
        val name = seed.title.substringBefore(" (").trim()
        if (name.isBlank()) return null
        val types = if (seed.type == "movie") listOf("movie", "series") else listOf("series", "movie")
        for (t in types) {
            val url = "https://v3-cinemeta.strem.io/catalog/$t/top/search=" + encode(name) + ".json"
            val id = runCatching {
                val root = kotlinx.serialization.json.Json.parseToJsonElement(
                    com.nuvio.app.features.addons.httpGetText(url),
                ) as? kotlinx.serialization.json.JsonObject
                val metas = root?.get("metas") as? kotlinx.serialization.json.JsonArray
                metas?.firstNotNullOfOrNull { el ->
                    val o = el as? kotlinx.serialization.json.JsonObject ?: return@firstNotNullOfOrNull null
                    val metaName = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    val metaId = (o["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    metaId.takeIf { it.startsWith("tt") && titleKey(metaName) == titleKey(name) }
                } ?: (metas?.firstOrNull() as? kotlinx.serialization.json.JsonObject)
                    ?.get("id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?.takeIf { it.startsWith("tt") }
            }.getOrNull()
            if (id != null) return t to id
        }
        return null
    }

    private fun encode(text: String): String = buildString {
        text.encodeToByteArray().forEach { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && b >= 0) append(c) else append('%' + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
}

private val ForYouRed = Color(0xFFFF4D57)

/** The For you panel (Watch). [onOpen] opens a title's page. */
@Composable
fun FenrirForYouHost(onOpen: (MetaPreview) -> Unit) {
    val open by FenrirForYou.open.collectAsStateWithLifecycle()
    if (!open) return
    val rows by FenrirForYou.rows.collectAsStateWithLifecycle()
    val loading by FenrirForYou.loading.collectAsStateWithLifecycle()
    val seedCount by FenrirForYou.seedCount.collectAsStateWithLifecycle()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Dialog(onDismissRequest = { FenrirForYou.hide() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { FenrirForYou.hide() }, contentAlignment = Alignment.BottomCenter) {
            // Fenrir: swipe the top down to close, up for full screen.
            var dragY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            var full by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (full) 0.95f else 0.78f)
                    .offset { androidx.compose.ui.unit.IntOffset(0, dragY.coerceAtLeast(0f).toInt()) }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xFF15151B))
                    .clickable(enabled = false) {}
                    .padding(top = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                when {
                                    dragY > 260f -> FenrirForYou.hide()
                                    dragY > 90f && full -> full = false
                                    dragY < -90f -> full = true
                                }
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                            onVerticalDrag = { _, dy -> dragY += dy },
                        )
                    },
                ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color(0xFF3A3A44)))
                Row(Modifier.padding(start = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ForYouRed, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("For you", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    if (seedCount > 0) "From $seedCount shows and movies you've watched or saved, including CloudStream and Aniyomi" else "Based on what you watch",
                    color = Color(0xFF9A9AA6),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 10.dp),
                )
                }
                when {
                    loading && rows.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForYouRed)
                    }
                    rows.isEmpty() -> Text(
                        "Nothing to suggest yet. Watch a few shows or movies and check back.",
                        color = Color(0xFFB8B8C2),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
                        items(rows, key = { it.title + it.because }) { row ->
                            Column(Modifier.padding(top = 10.dp)) {
                                Row(Modifier.padding(horizontal = 18.dp)) {
                                    Text("${row.title} ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(row.because, color = ForYouRed, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(row.items, key = { it.type + it.id }) { item ->
                                        Column(Modifier.width(112.dp).clickable { FenrirForYou.hide(); onOpen(item) }) {
                                            Box(
                                                Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF22232C)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(item.name.take(2).uppercase(), color = Color(0xFF55555F), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                                if (!item.poster.isNullOrBlank()) {
                                                    AsyncImage(model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                }
                                            }
                                            Text(item.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                            Text(
                                                listOfNotNull(if (item.type == "movie") "Movie" else "Series", item.releaseInfo).joinToString(" · "),
                                                color = Color(0xFF9A9AA6),
                                                fontSize = 10.5.sp,
                                                maxLines = 1,
                                            )
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
