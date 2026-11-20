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

/** What the home screen shows for the picked source. */
data class FenrirSourceHomeState(
    val sourceId: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val rows: List<FenrirHomeRow> = emptyList(),
)

/**
 * Fenrir Read: like Zangetsu / CloudStream, the home screen can show one CloudStream source's own
 * home page (its categories) instead of Nuvio's. Picked from the FENRIR READ drop-down; null means
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

    val host: FenrirPluginHost? get() = FenrirPlugins.cloudStream

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
            val host = host
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
