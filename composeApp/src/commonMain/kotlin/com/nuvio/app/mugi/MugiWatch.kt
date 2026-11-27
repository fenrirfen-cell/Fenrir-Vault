package com.nuvio.app.mugi

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Watch's Movies / TV Shows switch. It filters what Home shows (banner, Continue watching, Top 10
 * and every row) and what Search lists; nothing is deleted, the other half is one tap away.
 */
object MugiWatch {
    val kind = MutableStateFlow(WatchKind.MOVIES)

    /** True while the switch is drawn under the header (Home on Watch), so the page leaves room. */
    val selectorShown = mutableStateOf(false)

    fun isMovie(type: String?): Boolean = type.equals("movie", ignoreCase = true)

    fun matches(type: String?, kind: WatchKind): Boolean =
        if (kind == WatchKind.MOVIES) isMovie(type) else !isMovie(type)

    fun filterHero(items: List<MetaPreview>, kind: WatchKind): List<MetaPreview> =
        items.filter { matches(it.type, kind) }

    /** The row with only this kind's titles, or null when none are left. */
    fun filterSection(section: HomeCatalogSection, kind: WatchKind): HomeCatalogSection? {
        val kept = section.items.filter { matches(it.type, kind) }
        if (kept.isEmpty()) return null
        return if (kept.size == section.items.size) section else section.copy(items = kept, availableItemCount = kept.size)
    }

    fun filterResume(items: List<ContinueWatchingItem>, kind: WatchKind): List<ContinueWatchingItem> =
        items.filter { matches(it.parentMetaType, kind) }
}

/** Continue Watching as separate rope-framed cards with the sailing progress line. */
@Composable
fun MugiContinueWatching(
    items: List<ContinueWatchingItem>,
    mode: MugiMode,
    title: String = "Continue Watching",
    onItemClick: ((ContinueWatchingItem) -> Unit)?,
    onItemLongPress: ((ContinueWatchingItem) -> Unit)?,
) {
    if (items.isEmpty()) return
    val art = rememberMugiArt()
    val byId = items.associateBy { "${it.parentMetaId}:${it.videoId}" }
    val entries = items.map { item ->
        val fraction = (item.resumeProgressFraction ?: item.progressFraction).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val leftMs = if (item.durationMs > 0) (item.durationMs - item.resumePositionMs).coerceAtLeast(0) else 0L
        val left = when {
            item.durationMs <= 0 -> if (item.isNextUp) "Up next" else ""
            leftMs >= 3_600_000 -> "${leftMs / 3_600_000}h ${(leftMs % 3_600_000) / 60_000}m left"
            else -> "${(leftMs / 60_000).coerceAtLeast(1)}m left"
        }
        ResumeUi(
            id = "${item.parentMetaId}:${item.videoId}",
            title = item.title,
            subtitle = item.subtitle,
            remaining = left,
            progress = fraction,
        )
    }
    MugiResumeRail(
        art = art,
        mode = mode,
        entries = entries,
        title = title,
        thumbnail = { entry ->
            val item = byId[entry.id]
            AsyncImage(
                model = item?.episodeThumbnail ?: item?.background ?: item?.imageUrl ?: item?.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        onResume = { id -> byId[id]?.let { onItemClick?.invoke(it) } },
        onMenu = { id -> byId[id]?.let { onItemLongPress?.invoke(it) } },
    )
}

internal val MugiNavy = Color(0xFF071720)
