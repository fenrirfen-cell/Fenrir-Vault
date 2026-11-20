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
internal class AniyomiPluginHost(app: Application) : FenrirPluginHost {
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
        ) { sourceName, videos ->
            onFound(sourceName, videos.map { it.toStream() })
        }
    }

    override suspend fun search(query: String): List<FenrirPluginHit> =
        AniyomiExtensions.search(query).map { FenrirPluginHit(it.key, it.sourceName, it.title, it.poster) }

    override suspend fun episodes(hit: FenrirPluginHit): List<FenrirPluginEpisode> =
        AniyomiExtensions.episodes(hit.key).map { FenrirPluginEpisode(it.key, it.name) }

    override suspend fun streams(episode: FenrirPluginEpisode): List<FenrirPluginStream> =
        AniyomiExtensions.videos(episode.key).map { it.toStream() }

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
