package com.nuvio.app.readmode

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A Continue watching entry played from a CloudStream source. */
data class FenrirResumeTarget(
    val parentMetaId: String,
    val videoId: String,
    val title: String,
    val poster: String?,
    val episodeTitle: String?,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
) {
    val sourceName: String get() = FenrirSourceHome.sourceOf(parentMetaId).orEmpty()
}

/** What the home screen shows for the picked source. */
data class FenrirSourceHomeState(
    val sourceId: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val rows: List<FenrirHomeRow> = emptyList(),
)

/**
 * MugiVerse: like Zangetsu / CloudStream, the home screen can show one CloudStream source's own
 * home page (its categories) instead of Nuvio's. Picked from the FENRIR WATCH drop-down; null means
 * the normal Nuvio home. The pick is remembered by the Android app.
 */
object FenrirSourceHome {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()
    private val _state = MutableStateFlow(FenrirSourceHomeState())
    val state: StateFlow<FenrirSourceHomeState> = _state.asStateFlow()
    private var persist: ((String?) -> Unit)? = null
    private var loadJob: Job? = null
    private val cache = HashMap<String, List<FenrirHomeRow>>()

    fun install(selected: String?, persist: (String?) -> Unit) {
        this.persist = persist
        _selected.value = selected?.takeIf { it.isNotBlank() }
    }

    /** Aniyomi home ids start with "ay:"; everything else is a CloudStream site name. */
    fun isAniyomiSource(sourceId: String?): Boolean = sourceId?.startsWith("ay:") == true

    fun hostFor(sourceId: String?): FenrirPluginHost? =
        if (isAniyomiSource(sourceId)) FenrirPlugins.aniyomi else FenrirPlugins.cloudStream

    /** The host of the picked source. */
    val host: FenrirPluginHost? get() = hostFor(_selected.value)

    /** The picked source's name and icon, from whichever host has it. */
    fun sourceInfo(sourceId: String?): FenrirHomeSource? =
        sourceId?.let { id -> hostFor(id)?.homeSources()?.firstOrNull { it.id == id } }

    /** Continue watching keys are "<site>|<url>"; Aniyomi sites are numbers. */
    private fun hostForKey(key: String): FenrirPluginHost? {
        val site = key.substringBefore("|")
        return if (site.isNotEmpty() && site.all { it.isDigit() || it == '-' }) FenrirPlugins.aniyomi ?: FenrirPlugins.cloudStream
        else FenrirPlugins.cloudStream
    }

    private fun homeIdForKey(key: String): String {
        val site = key.substringBefore("|")
        return if (hostForKey(key) === FenrirPlugins.aniyomi) "ay:$site" else site
    }

    fun select(sourceId: String?) {
        _selected.value = sourceId
        persist?.invoke(sourceId)
        if (sourceId != null) load(sourceId)
    }

    /** Loads [sourceId]'s rows; shows the last ones straight away while refreshing. */
    fun load(sourceId: String, force: Boolean = false) {
        val current = _state.value
        if (!force && current.sourceId == sourceId && (current.loading || current.rows.isNotEmpty())) return
        val cached = cache[sourceId]
        if (!force && cached != null) {
            _state.value = FenrirSourceHomeState(sourceId, loading = false, rows = cached)
            return
        }
        loadJob?.cancel()
        _state.value = FenrirSourceHomeState(sourceId, loading = true, rows = cached.orEmpty())
        loadJob = scope.launch {
            val host = hostFor(sourceId)
            // Plugins load in the background when the app starts; wait for this source briefly.
            var waited = 0
            while (host != null && host.homeSources().none { it.id == sourceId } && waited < 15_000) {
                kotlinx.coroutines.delay(500)
                waited += 500
            }
            val rows = runCatching { host?.homeRows(sourceId).orEmpty() }.getOrDefault(emptyList())
            if (_selected.value != sourceId) return@launch
            if (rows.isNotEmpty()) cache[sourceId] = rows
            _state.value = FenrirSourceHomeState(
                sourceId = sourceId,
                loading = false,
                failed = rows.isEmpty() && host?.homeSources()?.any { it.id == sourceId } != true,
                rows = rows,
            )
        }
    }

    // ------------------------------------------------------------------ Continue watching

    const val ID_PREFIX = "fenrircs:"

    fun isCloudStreamId(id: String?): Boolean = id?.startsWith(ID_PREFIX) == true

    /** "fenrircs:Kisskh|https://…" → "Kisskh". */
    fun sourceOf(parentMetaId: String?): String? =
        parentMetaId?.takeIf { isCloudStreamId(it) }?.removePrefix(ID_PREFIX)?.let { key ->
            val site = key.substringBefore("|").takeIf { it.isNotBlank() } ?: return@let null
            if (hostForKey(key) === FenrirPlugins.aniyomi) sourceInfo("ay:$site")?.name ?: site else site
        }

    /**
     * Gets a fresh link for the saved episode from the same source and plays it from where it was
     * left. Returns the title to show in the episode sheet instead when that isn't possible.
     */
    suspend fun resume(target: FenrirResumeTarget): FenrirHomeItem? {
        val key = target.parentMetaId.removePrefix(ID_PREFIX)
        val host = hostForKey(key) ?: return null
        val homeId = homeIdForKey(key)
        val hit = FenrirPluginHit(key, sourceOf(target.parentMetaId).orEmpty(), target.title, target.poster)
        val fallback = FenrirHomeItem(hit)
        // Plugins load in the background when the app starts; give them a moment.
        var waited = 0
        while (host.homeSources().none { it.id == homeId } && waited < 10_000) {
            kotlinx.coroutines.delay(500)
            waited += 500
        }
        val episodes = runCatching { host.episodes(hit) }.getOrDefault(emptyList())
        if (episodes.isEmpty()) return fallback
        val index = target.videoId.substringAfterLast("#", "").toIntOrNull()
        val episode = episodes.getOrNull(index ?: -1)?.takeIf { target.episodeTitle == null || it.name == target.episodeTitle }
            ?: episodes.firstOrNull { target.episodeTitle != null && it.name == target.episodeTitle }
            ?: episodes.firstOrNull { target.episode != null && it.episode == target.episode && (target.season == null || it.season == target.season) }
            ?: episodes.getOrNull(index ?: -1)
            ?: return fallback.copy(isMovie = episodes.size == 1)
        val streams = runCatching { host.streams(episode) }.getOrDefault(emptyList())
        val stream = streams.firstOrNull() ?: return fallback.copy(isMovie = episodes.size == 1)
        FenrirPluginPlayback.play(
            FenrirPluginPlayRequest(
                hostId = host.id,
                showTitle = target.title,
                episodeName = episode.name,
                stream = stream,
                hitKey = key,
                poster = target.poster,
                episodeIndex = episodes.indexOf(episode),
                season = episode.season,
                episode = episode.episode,
                isMovie = episodes.size == 1,
                resumePositionMs = target.positionMs,
            ),
        )
        return null
    }

    // ------------------------------------------------------------------ matching to a Nuvio show page

    private fun normalize(text: String) = text.lowercase()
        .replace(Regex("\\(\\d{4}\\)"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    /**
     * Looks the title up in the user's own addons (like Zangetsu matching to its catalogue), so the
     * normal show page can open. Null when there's no confident match within a few seconds.
     */
    suspend fun matchToNuvio(item: FenrirHomeItem): MetaPreview? = withTimeoutOrNull(8_000) {
        val wanted = normalize(item.hit.title)
        if (wanted.isEmpty()) return@withTimeoutOrNull null
        val types = if (item.isMovie) listOf("movie") else listOf("series", "anime", "movie")
        val requests = AddonRepository.uiState.value.addons.enabledAddons()
            .mapNotNull { addon -> addon.manifest }
            .flatMap { manifest ->
                manifest.catalogs
                    .filter { catalog ->
                        catalog.type in types &&
                            catalog.extra.any { it.name == "search" } &&
                            catalog.extra.none { it.isRequired && it.name != "search" }
                    }
                    .map { catalog -> Triple(manifest.transportUrl, catalog.type, catalog.id) }
            }
            .distinct()
            .take(8)
        if (requests.isEmpty()) return@withTimeoutOrNull null
        val results = coroutineScope {
            requests.map { (url, type, id) ->
                async(Dispatchers.Default) {
                    withTimeoutOrNull(6_000) {
                        runCatching { fetchCatalogPage(manifestUrl = url, type = type, catalogId = id, search = item.hit.title).items }
                            .getOrNull()
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }
        results
            .mapNotNull { meta ->
                val name = normalize(meta.name)
                var score = when {
                    name == wanted -> 100
                    name.startsWith(wanted) && name.length - wanted.length <= 6 -> 70
                    else -> 0
                }
                if (score == 0) return@mapNotNull null
                val year = meta.releaseInfo?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }
                if (item.year != null && year != null) score += if (year == item.year) 20 else -40
                if (item.isMovie && meta.type != "movie") score -= 30
                if (!item.isMovie && meta.type == "movie") score -= 30
                meta to score
            }
            .filter { it.second >= 70 }
            .maxByOrNull { it.second }
            ?.first
    }
}
