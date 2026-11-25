package com.nuvio.app.readmode

import android.app.Application
import eu.kanade.tachiyomi.animeext.AniyomiExtensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Connects the Watch side's plugin screens and source list to Aniyomi extensions. */
internal class AniyomiPluginHost(private val app: Application) : FenrirPluginHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        AniyomiExtensions.init(app)
    }

    override val id = "aniyomi"
    override val title = "Aniyomi extensions"
    override val subtitle = "Anime"

    override val state: StateFlow<FenrirPluginState> = AniyomiExtensions.state
        .map { it.toPluginState() }
        .stateIn(scope, SharingStarted.Eagerly, AniyomiExtensions.state.value.toPluginState())

    override val hasSources: Boolean get() = AniyomiExtensions.hasSources

    private fun AniyomiExtensions.State.toPluginState(): FenrirPluginState {
        val availableByPkg = available.associateBy { it.pkg }
        return FenrirPluginState(
            repos = repos,
            installed = installed.map { ext ->
                val update = availableByPkg[ext.pkg]?.let { it.versionCode > ext.versionCode } == true
                FenrirPluginItem(
                    id = ext.pkg,
                    name = ext.name,
                    version = ext.versionName,
                    lang = ext.lang,
                    iconUrl = availableByPkg[ext.pkg]?.iconUrl,
                    nsfw = ext.nsfw,
                    hasUpdate = update,
                )
            } + untrusted.map { ext ->
                FenrirPluginItem(
                    id = ext.pkg,
                    name = ext.name,
                    version = ext.versionName,
                    lang = "",
                    iconUrl = availableByPkg[ext.pkg]?.iconUrl,
                    needsTrust = true,
                )
            },
            available = available.map { ext ->
                FenrirPluginItem(
                    id = ext.pkg,
                    name = ext.name,
                    version = ext.versionName,
                    lang = ext.lang,
                    iconUrl = ext.iconUrl,
                    nsfw = ext.nsfw,
                )
            },
            showNsfw = showNsfw,
            busy = busy,
            message = message,
        )
    }

    // Fenrir: extension check (installed ones as they are, others from a throwaway copy)
    private val checkStore by lazy { FenrirCheckStore(app, "aniyomi", scope) }
    override val checks: StateFlow<Map<String, com.nuvio.app.readmode.FenrirPluginCheck>> get() = checkStore.checks
    override val canTest: Boolean get() = true
    override fun test(ids: List<String>) = checkStore.test(
        ids,
        versionOf = { id ->
            val s = AniyomiExtensions.state.value
            s.installed.firstOrNull { it.pkg == id }?.versionName ?: s.available.firstOrNull { it.pkg == id }?.versionName
        },
    ) { id ->
        val c = AniyomiExtensions.test(id)
        com.nuvio.app.readmode.FenrirPluginCheck(
            when (c.result) {
                "Works" -> com.nuvio.app.readmode.FenrirPluginCheck.Result.Works
                "Slow" -> com.nuvio.app.readmode.FenrirPluginCheck.Result.Slow
                else -> com.nuvio.app.readmode.FenrirPluginCheck.Result.Down
            },
            c.seconds,
            c.reason,
        )
    }

    override fun refresh() = AniyomiExtensions.refresh()
    override fun addRepo(url: String) = AniyomiExtensions.addRepo(url)
    override fun removeRepo(url: String) = AniyomiExtensions.removeRepo(url)
    override fun install(id: String) = AniyomiExtensions.install(id)
    override fun uninstall(id: String) = AniyomiExtensions.uninstall(id)
    override fun trust(id: String) = AniyomiExtensions.trust(id)
    override fun setShowNsfw(show: Boolean) = AniyomiExtensions.setShowNsfw(show)
    override fun clearMessage() = AniyomiExtensions.clearMessage()

    override suspend fun findStreams(
        query: FenrirStreamQuery,
        onFound: suspend (sourceName: String, streams: List<FenrirPluginStream>) -> Unit,
    ) {
        AniyomiExtensions.findStreams(
            title = query.title,
            altTitles = query.altTitles,
            isMovie = query.isMovie,
            season = query.season,
            episode = query.episode,
            include = { FenrirSourceFlags.usedForStreams("aniyomi", it) },
        ) { sourceName, videos ->
            onFound(sourceName, videos.map { it.toStream() })
        }
    }

    override suspend fun search(query: String): List<FenrirPluginHit> =
        AniyomiExtensions.search(query) { FenrirSourceFlags.usedInSearch("aniyomi", it) }.map { FenrirPluginHit(it.key, it.sourceName, it.title, it.poster) }

    override suspend fun episodes(hit: FenrirPluginHit): List<FenrirPluginEpisode> =
        AniyomiExtensions.episodes(hit.key).map { FenrirPluginEpisode(it.key, it.name) }

    override suspend fun streams(episode: FenrirPluginEpisode): List<FenrirPluginStream> =
        AniyomiExtensions.videos(episode.key).map { it.toStream() }

    override fun sourceNamesOf(pluginId: String): List<String> =
        AniyomiExtensions.state.value.installed.firstOrNull { it.pkg == pluginId }?.sources?.map { it.name }.orEmpty()

    // Home pages: each Aniyomi source can be shown on Home (Popular and Latest rows).
    override fun homeSources(): List<com.nuvio.app.readmode.FenrirHomeSource> =
        AniyomiExtensions.homeSources().map {
            com.nuvio.app.readmode.FenrirHomeSource(
                id = it.id,
                name = it.name,
                subtitle = listOf("Aniyomi", it.lang.uppercase()).filter { p -> p.isNotBlank() }.joinToString(" · "),
            )
        }

    override suspend fun homeRows(sourceId: String): List<com.nuvio.app.readmode.FenrirHomeRow> {
        val supportsLatest = AniyomiExtensions.homeSources().firstOrNull { it.id == sourceId }?.supportsLatest == true
        val popular = AniyomiExtensions.homePage(sourceId, latest = false, page = 1)
        val latest = if (supportsLatest) AniyomiExtensions.homePage(sourceId, latest = true, page = 1) else emptyList()
        return listOfNotNull(
            latest.takeIf { it.isNotEmpty() }?.let { row("Latest", "latest", it) },
            popular.takeIf { it.isNotEmpty() }?.let { row("Popular", "popular", it) },
        )
    }

    override suspend fun homeRowPage(
        sourceId: String,
        categoryName: String,
        categoryData: String,
        page: Int,
    ): List<com.nuvio.app.readmode.FenrirHomeItem> =
        AniyomiExtensions.homePage(sourceId, latest = categoryData == "latest", page = page).map { it.toItem() }

    override suspend fun searchSource(sourceId: String, query: String): List<FenrirPluginHit> =
        AniyomiExtensions.searchIn(sourceId, query).map { FenrirPluginHit(it.key, it.sourceName, it.title, it.poster) }

    private fun AniyomiExtensions.Hit.toItem() =
        com.nuvio.app.readmode.FenrirHomeItem(FenrirPluginHit(key, sourceName, title, poster))

    private fun row(title: String, data: String, hits: List<AniyomiExtensions.Hit>) =
        com.nuvio.app.readmode.FenrirHomeRow(
            title = title,
            categoryName = title,
            categoryData = data,
            items = hits.map { it.toItem() },
        )

    private fun AniyomiExtensions.FoundVideo.toStream() = FenrirPluginStream(
        sourceName = sourceName,
        label = quality,
        detail = listOfNotNull(
            matchedTitle.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
            episodeName?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { "Aniyomi" },
        url = url,
        headers = headers,
        subtitles = subtitles,
    )
}
