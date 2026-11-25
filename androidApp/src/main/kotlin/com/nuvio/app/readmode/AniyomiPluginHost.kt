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

    /** Fenrir: extensions that may still be loading (right after the app starts). */
    override val mayHaveSources: Boolean
        get() = hasSources || (
            AniyomiExtensions.state.value.let { it.installed.isEmpty() && it.untrusted.isEmpty() } &&
                (aniyomiPrefs().getStringSet("trusted", emptySet()).orEmpty().isNotEmpty() ||
                    java.io.File(app.filesDir, "anime_exts").listFiles().orEmpty().any { it.extension == "ext" })
            )

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

    // ------------------------------------------------------------------ Fenrir: backup
    // Same settings file AniyomiExtensions keeps ("fenrir_aniyomi"): repositories, trusted
    // extensions (package + signing key, so only that exact extension is trusted again) and NSFW.

    private fun aniyomiPrefs() = app.getSharedPreferences("fenrir_aniyomi", android.content.Context.MODE_PRIVATE)

    override fun backup(): String? {
        val prefs = aniyomiPrefs()
        val repos = prefs.getStringSet("repos", emptySet()).orEmpty()
        val trusted = prefs.getStringSet("trusted", emptySet()).orEmpty()
        // Extensions installed inside Fenrir are lost with the app; ones installed as their own
        // app stay on the phone and only need trusting again.
        val inApp = AniyomiExtensions.state.value.installed.filter { it.isPrivate }.map { it.pkg }
        if (repos.isEmpty() && trusted.isEmpty() && inApp.isEmpty()) return null
        return org.json.JSONObject()
            .put("repos", org.json.JSONArray(repos.sorted()))
            .put("trusted", org.json.JSONArray(trusted.sorted()))
            .put("inApp", org.json.JSONArray(inApp.sorted()))
            .put("showNsfw", prefs.getBoolean("show_nsfw", false))
            .toString()
    }

    override suspend fun restore(data: String): Int = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val o = org.json.JSONObject(data)
        fun list(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        val before = AniyomiExtensions.state.value
        val waitingBefore = before.untrusted.map { it.pkg }.toSet()
        // trusted entries are "package:signature"; keep only well-formed ones
        val trusted = list("trusted").filter { it.contains(':') }
        val prefs = aniyomiPrefs()
        prefs.edit().putStringSet("trusted", prefs.getStringSet("trusted", emptySet()).orEmpty() + trusted).apply()
        if (o.has("showNsfw")) AniyomiExtensions.setShowNsfw(o.optBoolean("showNsfw"))
        list("repos").filter { it !in before.repos }.forEach { AniyomiExtensions.addRepo(it) }
        AniyomiExtensions.refresh()
        kotlinx.coroutines.delay(1_500) // let the reload with the restored trust list start

        // Extensions that were installed inside Fenrir: install them again from the repositories.
        val wanted = list("inApp").filter { pkg -> AniyomiExtensions.state.value.installed.none { it.pkg == pkg } }
        kotlinx.coroutines.withTimeoutOrNull(60_000) {
            while (true) {
                val s = AniyomiExtensions.state.value
                if (!s.busy && wanted.all { pkg -> s.available.any { it.pkg == pkg } }) break
                if (!s.busy && s.available.isNotEmpty() && s.repos.isNotEmpty()) break
                kotlinx.coroutines.delay(500)
            }
        }
        var installed = 0
        for (pkg in wanted) {
            if (AniyomiExtensions.state.value.available.none { it.pkg == pkg }) continue
            AniyomiExtensions.install(pkg)
            // one at a time: each install shows its own progress and reloads the list
            kotlinx.coroutines.delay(300)
            kotlinx.coroutines.withTimeoutOrNull(120_000) {
                while (AniyomiExtensions.state.value.busy) kotlinx.coroutines.delay(300)
            }
            if (AniyomiExtensions.state.value.installed.any { it.pkg == pkg }) installed++
        }
        // ones on the phone that were trusted before and are trusted again now
        val trustedAgain = AniyomiExtensions.state.value.installed.count { it.pkg in waitingBefore }
        installed + trustedAgain
    }
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
