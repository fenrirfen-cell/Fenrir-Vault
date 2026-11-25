package com.nuvio.app.readmode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Fenrir: result of trying a plugin out before installing it. */
data class FenrirPluginCheck(val result: Result, val seconds: Double = 0.0, val reason: String? = null) {
    enum class Result { Testing, Works, Slow, Down }
}

internal val FenrirNoChecks: StateFlow<Map<String, FenrirPluginCheck>> = MutableStateFlow(emptyMap())

/** One plugin (extension) row in the plugin manager. */
data class FenrirPluginItem(
    val id: String,
    val name: String,
    val version: String,
    val lang: String,
    val iconUrl: String? = null,
    val nsfw: Boolean = false,
    val hasUpdate: Boolean = false,
    /** Found on the phone but not trusted yet: it won't run until the user trusts it. */
    val needsTrust: Boolean = false,
    /** Couldn't be loaded (for example it needs something Fenrir doesn't have). */
    val unsupportedReason: String? = null,
)

data class FenrirPluginState(
    val repos: List<String> = emptyList(),
    val installed: List<FenrirPluginItem> = emptyList(),
    val available: List<FenrirPluginItem> = emptyList(),
    val showNsfw: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

/** What the show page is playing, so plugins can look it up by name. */
data class FenrirStreamQuery(
    val title: String,
    val altTitles: List<String> = emptyList(),
    val year: Int? = null,
    val isMovie: Boolean,
    val season: Int?,
    val episode: Int?,
)

/** One playable link from a plugin. */
data class FenrirPluginStream(
    val sourceName: String,
    val label: String,
    val detail: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Pair<String, String>> = emptyList(),
)

data class FenrirPluginHit(val key: String, val sourceName: String, val title: String, val poster: String?)

data class FenrirPluginEpisode(
    val key: String,
    val name: String,
    val season: Int? = null,
    val episode: Int? = null,
)

/** A site whose own home page (its categories) can be shown on Fenrir Read's home screen. */
data class FenrirHomeSource(
    val id: String,
    val name: String,
    val subtitle: String,
    val iconUrl: String? = null,
)

/** One title in a source's home row. */
data class FenrirHomeItem(
    val hit: FenrirPluginHit,
    /** "SUB", "DUB", "SUB · DUB" or null. */
    val badge: String? = null,
    val year: Int? = null,
    val isMovie: Boolean = false,
)

/** One category of a source's home page ("Latest", "Top K-Drama"…), in the source's own order. */
data class FenrirHomeRow(
    val title: String,
    val categoryName: String,
    val categoryData: String,
    val items: List<FenrirHomeItem>,
    val horizontalImages: Boolean = false,
)

/**
 * A kind of plugin that runs inside Fenrir Read (Aniyomi extensions, CloudStream plugins).
 * Implemented by the Android app; plugins come only from repositories the user adds.
 */
interface FenrirPluginHost {
    /** Short id used for stream groups ("aniyomi", "cloudstream"). */
    val id: String
    /** "Aniyomi extensions", "CloudStream plugins". */
    val title: String
    /** "Anime", "Movies, shows, anime, dramas". */
    val subtitle: String
    val state: StateFlow<FenrirPluginState>
    val hasSources: Boolean

    fun refresh()

    /** Fenrir: can plugins be tried out before installing ([test])? */
    val canTest: Boolean get() = false
    /** Results of [test], by plugin id. */
    val checks: StateFlow<Map<String, FenrirPluginCheck>> get() = FenrirNoChecks
    /** Tries each plugin on its own for a moment (opens its home page) without installing it. */
    fun test(ids: List<String>) = Unit

    fun addRepo(url: String)
    fun removeRepo(url: String)
    fun install(id: String)
    fun uninstall(id: String)
    fun trust(id: String)
    fun setShowNsfw(show: Boolean)
    fun clearMessage()

    /** Finds links for [query]; [onFound] is called once per source as results come in. */
    suspend fun findStreams(query: FenrirStreamQuery, onFound: suspend (sourceName: String, streams: List<FenrirPluginStream>) -> Unit)

    suspend fun search(query: String): List<FenrirPluginHit>

    /** Searches only [sourceId] (one site, as listed by [homeSources]), not every plugin. */
    suspend fun searchSource(sourceId: String, query: String): List<FenrirPluginHit> =
        search(query).filter { it.sourceName == sourceId }
    suspend fun episodes(hit: FenrirPluginHit): List<FenrirPluginEpisode>
    suspend fun streams(episode: FenrirPluginEpisode): List<FenrirPluginStream>

    /** The site names a plugin provides (one plugin can add several sites). */
    fun sourceNamesOf(pluginId: String): List<String> = emptyList()

    /** Sites that have their own home page (categories). */
    fun homeSources(): List<FenrirHomeSource> = emptyList()

    /** All of [sourceId]'s home categories, first page each. */
    suspend fun homeRows(sourceId: String): List<FenrirHomeRow> = emptyList()

    /** A further page of one category, for "See all". Empty when there's no more. */
    suspend fun homeRowPage(sourceId: String, categoryName: String, categoryData: String, page: Int): List<FenrirHomeItem> = emptyList()
}

object FenrirPlugins {
    var aniyomi: FenrirPluginHost? = null
    var cloudStream: FenrirPluginHost? = null

    val all: List<FenrirPluginHost> get() = listOfNotNull(aniyomi, cloudStream)

    /** Name and year of shows opened recently, by meta id, so plugin searches know what to look for. */
    private val titles = HashMap<String, Pair<String, Int?>>()

    fun rememberTitle(metaId: String, name: String, year: Int?) {
        if (name.isNotBlank()) titles[metaId] = name to year
    }

    fun titleFor(metaId: String?): Pair<String, Int?>? = metaId?.let { titles[it] }

    /** Name and year of the title whose links were looked up last (what's playing). */
    var lastTitle: Pair<String, Int?>? = null
}
