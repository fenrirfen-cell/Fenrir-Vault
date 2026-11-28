package com.nuvio.app.readmode

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.nuvio.app.readmode.FenrirHomeItem
import com.nuvio.app.readmode.FenrirHomeRow
import com.nuvio.app.readmode.FenrirHomeSource
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * MugiVerse: runs CloudStream plugins (.cs3 files) from repositories the user adds. Plugins
 * are saved in the app's private folder and loaded with CloudStream's own plugin library.
 */
internal class CloudStreamPluginHost(private val app: Application) : FenrirPluginHost {
    private companion object {
        const val TAG = "FenrirCloudStream"
        const val HOME_ROW_TIMEOUT_MS = 20_000L
        const val PREFS = "fenrir_cloudstream"
        const val KEY_REPOS = "repos"
        const val KEY_INSTALLED = "installed"
        const val KEY_SHOW_NSFW = "show_nsfw"
    }

    override val id = "cloudstream"
    override val title = "CloudStream plugins"
    override val subtitle = "Movies, shows, anime, dramas"

    /** A plugin listed in one of the user's repositories. */
    private data class SitePlugin(
        val internalName: String,
        val name: String,
        val version: Int,
        val url: String,
        val language: String,
        val iconUrl: String?,
        val tvTypes: List<String>,
        val status: Int,
        val repo: String,
    )

    /** A plugin saved on the phone. */
    private data class Saved(
        val internalName: String,
        val name: String,
        val version: Int,
        val url: String,
        val language: String,
        val iconUrl: String?,
        val nsfw: Boolean,
    )

    private class Loaded(val saved: Saved, val file: File, val providers: List<MainAPI>, val error: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()
    private val _state = MutableStateFlow(FenrirPluginState())
    override val state: StateFlow<FenrirPluginState> = _state.asStateFlow()
    private var available: List<SitePlugin> = emptyList()
    private val loaded = mutableMapOf<String, Loaded>()
    private val hits = HashMap<String, Pair<MainAPI, SearchResponse>>()
    private val episodeData = HashMap<String, Pair<MainAPI, String>>()

    init {
        // CloudStream's library needs the app context (web view helper, preferences).
        runCatching { com.lagradost.api.setContext(app) }
        _state.update { it.copy(repos = repos(), showNsfw = prefs().getBoolean(KEY_SHOW_NSFW, false)) }
        scope.launch {
            reload()
            refreshAvailable()
        }
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun repos(): List<String> = prefs().getStringSet(KEY_REPOS, emptySet()).orEmpty().sorted()
    private fun pluginDir() = File(app.filesDir, "cs_plugins").apply { mkdirs() }

    private fun savedPlugins(): List<Saved> = runCatching {
        val array = JSONArray(prefs().getString(KEY_INSTALLED, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Saved(
                internalName = o.getString("internalName"),
                name = o.optString("name"),
                version = o.optInt("version"),
                url = o.optString("url"),
                language = o.optString("language"),
                iconUrl = o.optString("iconUrl").takeIf { it.isNotBlank() },
                nsfw = o.optBoolean("nsfw"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveSaved(list: List<Saved>) {
        val array = JSONArray()
        list.forEach { s ->
            array.put(
                JSONObject()
                    .put("internalName", s.internalName)
                    .put("name", s.name)
                    .put("version", s.version)
                    .put("url", s.url)
                    .put("language", s.language)
                    .put("iconUrl", s.iconUrl ?: "")
                    .put("nsfw", s.nsfw),
            )
        }
        prefs().edit().putString(KEY_INSTALLED, array.toString()).apply()
    }

    private fun fileFor(internalName: String) =
        File(pluginDir(), internalName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".cs3")

    // ------------------------------------------------------------------ state

    private fun publish(busy: Boolean? = null, message: String? = null, keepMessage: Boolean = true) {
        val showNsfw = prefs().getBoolean(KEY_SHOW_NSFW, false)
        val savedByName = savedPlugins().associateBy { it.internalName }
        val availableByName = available.associateBy { it.internalName }
        val installed = savedByName.values.map { saved ->
            val runtime = loaded[saved.internalName]
            FenrirPluginItem(
                id = saved.internalName,
                name = saved.name,
                version = saved.version.toString(),
                lang = saved.language,
                iconUrl = saved.iconUrl,
                nsfw = saved.nsfw,
                hasUpdate = (availableByName[saved.internalName]?.version ?: 0) > saved.version,
                unsupportedReason = runtime?.error,
            )
        }.sortedBy { it.name.lowercase() }
        val avail = available
            .filter { it.internalName !in savedByName }
            .filter { showNsfw || "NSFW" !in it.tvTypes }
            .map {
                FenrirPluginItem(
                    id = it.internalName,
                    name = it.name,
                    version = it.version.toString(),
                    lang = it.language,
                    iconUrl = it.iconUrl,
                    nsfw = "NSFW" in it.tvTypes,
                    unsupportedReason = if (it.status == 0) "Marked down by its author" else null,
                )
            }
            .sortedBy { it.name.lowercase() }
        _state.update {
            it.copy(
                repos = repos(),
                installed = installed,
                available = avail,
                showNsfw = showNsfw,
                busy = busy ?: it.busy,
                message = if (message != null) message else if (keepMessage) it.message else null,
            )
        }
    }

    override val hasSources: Boolean
        get() = synchronized(loaded) { loaded.values.any { it.providers.isNotEmpty() } }

    /** Fenrir: plugins saved on the phone that may still be loading (right after the app starts). */
    override val mayHaveSources: Boolean
        get() = hasSources || run {
            val showNsfw = prefs().getBoolean(KEY_SHOW_NSFW, false)
            savedPlugins().any { (showNsfw || !it.nsfw) && fileFor(it.internalName).exists() } &&
                // loaded and still none: they don't work, don't wait for them
                synchronized(loaded) { loaded.isEmpty() }
        }

    override fun clearMessage() = _state.update { it.copy(message = null) }

    override fun refresh() {
        scope.launch { reload(); refreshAvailable() }
    }

    override fun setShowNsfw(show: Boolean) {
        prefs().edit().putBoolean(KEY_SHOW_NSFW, show).apply()
        publish()
    }

    override fun trust(id: String) = Unit // Plugins only come from repositories the user added.

    // ------------------------------------------------------------------ Fenrir: backup

    override fun backup(): String? {
        val repos = repos()
        val installed = prefs().getString(KEY_INSTALLED, "[]") ?: "[]"
        if (repos.isEmpty() && savedPlugins().isEmpty()) return null
        return JSONObject()
            .put("repos", JSONArray(repos))
            .put("showNsfw", prefs().getBoolean(KEY_SHOW_NSFW, false))
            .put("installed", JSONArray(installed))
            .toString()
    }

    /** Adds the backup's repositories, then downloads each plugin again (newest from its repository). */
    override suspend fun restore(data: String): Int = withContext(Dispatchers.IO) {
        val o = JSONObject(data)
        val backupRepos = o.optJSONArray("repos")?.let { a -> (0 until a.length()).mapNotNull { normalizeRepo(a.optString(it)) } }.orEmpty()
        val editor = prefs().edit().putStringSet(KEY_REPOS, (repos() + backupRepos).toSet())
        if (o.has("showNsfw")) editor.putBoolean(KEY_SHOW_NSFW, o.optBoolean("showNsfw"))
        editor.apply()
        publish(busy = true, message = "Restoring CloudStream plugins…")
        refreshAvailable()
        val wanted = o.optJSONArray("installed") ?: JSONArray()
        var restored = 0
        val failed = mutableListOf<String>()
        for (i in 0 until wanted.length()) {
            val w = wanted.optJSONObject(i) ?: continue
            val internalName = w.optString("internalName")
            if (internalName.isBlank()) continue
            if (savedPlugins().any { it.internalName == internalName } && fileFor(internalName).exists()) continue
            val site = available.firstOrNull { it.internalName == internalName }
            val saved = Saved(
                internalName = internalName,
                name = site?.name ?: w.optString("name", internalName),
                version = site?.version ?: w.optInt("version"),
                url = site?.url ?: w.optString("url"),
                language = site?.language ?: w.optString("language"),
                iconUrl = site?.iconUrl ?: w.optString("iconUrl").takeIf { it.isNotBlank() },
                nsfw = site?.let { "NSFW" in it.tvTypes } ?: w.optBoolean("nsfw"),
            )
            val ok = runCatching {
                if (saved.url.isBlank()) error("no download link")
                val target = fileFor(internalName)
                val temp = File(app.cacheDir, target.name)
                download(saved.url, temp) // https only, like a normal install
                unload(internalName)
                target.setWritable(true)
                target.delete()
                temp.copyTo(target, overwrite = true)
                temp.delete()
                target.setReadOnly()
                saveSaved(savedPlugins().filter { it.internalName != internalName } + saved)
            }.onFailure { Log.w(TAG, "Restore failed: $internalName", it) }.isSuccess
            if (ok) restored++ else failed += saved.name
        }
        reload()
        publish(
            busy = false,
            message = if (failed.isEmpty()) null else "Couldn't bring back: ${failed.joinToString()}",
            keepMessage = false,
        )
        restored
    }

    // ------------------------------------------------------------------ repositories

    private fun normalizeRepo(raw: String): String? {
        val trimmed = raw.trim()
        val url = when {
            trimmed.startsWith("cloudstreamrepo://") -> "https://" + trimmed.removePrefix("cloudstreamrepo://")
            // Fenrir: plugins are code, so only over a secure (https) connection.
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
            else -> return null
        }
        return url
    }

    override fun addRepo(url: String) {
        val clean = normalizeRepo(url) ?: run {
            publish(message = "That doesn't look like a repository link.")
            return
        }
        prefs().edit().putStringSet(KEY_REPOS, (repos() + clean).toSet()).apply()
        publish(message = null, keepMessage = false)
        scope.launch { refreshAvailable() }
    }

    override fun removeRepo(url: String) {
        prefs().edit().putStringSet(KEY_REPOS, (repos() - url).toSet()).apply()
        available = available.filter { it.repo != url }
        publish()
    }

    /** Fenrir: plugin code and repository lists are only ever fetched over https. */
    private fun secureUrl(url: String): URL {
        val u = URL(if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url)
        require(u.protocol == "https") { "Not a secure (https) link" }
        return u
    }

    private fun httpGet(url: String): String {
        val connection = secureUrl(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        return connection.inputStream.use { it.readBytes().decodeToString() }
    }

    private fun download(url: String, target: File) {
        val connection = secureUrl(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
        connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    private suspend fun refreshAvailable() = withContext(Dispatchers.IO) {
        val repos = repos()
        if (repos.isEmpty()) {
            available = emptyList()
            publish()
            return@withContext
        }
        publish(busy = true)
        var failed = 0
        val found = repos.flatMap { repo ->
            runCatching { fetchRepo(repo) }
                .onFailure { failed++; Log.w(TAG, "Repo failed: $repo", it) }
                .getOrDefault(emptyList())
        }
        available = found.distinctBy { it.internalName }
        publish(
            busy = false,
            message = if (failed > 0) "Couldn't load $failed repository(s). Check the link." else null,
            keepMessage = failed == 0,
        )
    }

    /** A repo link is either a repository manifest (with "pluginLists") or a plugin list itself. */
    private fun fetchRepo(repo: String): List<SitePlugin> {
        val body = httpGet(repo).trim()
        val lists = if (body.startsWith("[")) {
            listOf(body)
        } else {
            val manifest = JSONObject(body)
            val urls = manifest.optJSONArray("pluginLists") ?: JSONArray()
            (0 until urls.length()).map { httpGet(urls.getString(it)) }
        }
        return lists.flatMap { listBody ->
            val array = JSONArray(listBody)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val internalName = o.optString("internalName").ifBlank { return@mapNotNull null }
                val types = o.optJSONArray("tvTypes")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty()
                SitePlugin(
                    internalName = internalName,
                    name = o.optString("name", internalName),
                    version = o.optInt("version"),
                    url = o.optString("url"),
                    language = o.optString("language"),
                    iconUrl = o.optString("iconUrl").takeIf { it.isNotBlank() && it != "null" }
                        ?.replace("%size%", "128"),
                    tvTypes = types,
                    status = o.optInt("status", 1),
                    repo = repo,
                )
            }
        }
    }

    // ------------------------------------------------------------------ install / remove

    override fun install(id: String) {
        val site = available.firstOrNull { it.internalName == id }
            ?: savedPlugins().firstOrNull { it.internalName == id }?.let { saved ->
                // Update: same plugin from the repository list.
                available.firstOrNull { it.internalName == saved.internalName }
            }
            ?: return
        scope.launch {
            publish(busy = true, message = "Installing ${site.name}…")
            val result = runCatching {
                val target = fileFor(site.internalName)
                val temp = File(app.cacheDir, target.name)
                download(site.url, temp)
                unload(site.internalName)
                target.setWritable(true)
                target.delete()
                temp.copyTo(target, overwrite = true)
                temp.delete()
                target.setReadOnly()
                val saved = Saved(site.internalName, site.name, site.version, site.url, site.language, site.iconUrl, "NSFW" in site.tvTypes)
                saveSaved(savedPlugins().filter { it.internalName != site.internalName } + saved)
            }
            reload()
            publish(
                busy = false,
                message = result.exceptionOrNull()?.let { "Couldn't install ${site.name}: ${it.message}" },
                keepMessage = false,
            )
        }
    }

    override fun uninstall(id: String) {
        scope.launch {
            unload(id)
            fileFor(id).apply { setWritable(true); delete() }
            saveSaved(savedPlugins().filter { it.internalName != id })
            publish()
        }
    }

    private fun unload(internalName: String) {
        val runtime = synchronized(loaded) { loaded.remove(internalName) } ?: return
        APIHolder.allProviders.removeAll(APIHolder.allProviders.filter { it.sourcePlugin == runtime.file.absolutePath }.toList())
    }

    // ------------------------------------------------------------------ Fenrir: plugin check

    private val checkStore by lazy { FenrirCheckStore(app, "cloudstream", scope) }
    override val checks: StateFlow<Map<String, com.nuvio.app.readmode.FenrirPluginCheck>> get() = checkStore.checks
    override val canTest: Boolean get() = true

    override fun test(ids: List<String>) = checkStore.test(
        ids,
        versionOf = { id ->
            (savedPlugins().firstOrNull { it.internalName == id }?.version ?: available.firstOrNull { it.internalName == id }?.version)?.toString()
        },
        run = ::testOne,
    )

    /**
     * Installed: tries the plugin as it is loaded. Not installed: loads a throwaway copy, tries it,
     * then removes it again. Opens the home page (or searches) and times it.
     */
    private suspend fun testOne(id: String): com.nuvio.app.readmode.FenrirPluginCheck = withContext(Dispatchers.IO) {
        val down = com.nuvio.app.readmode.FenrirPluginCheck.Result.Down
        synchronized(loaded) { loaded[id] }?.let { runtime ->
            if (runtime.error != null) return@withContext com.nuvio.app.readmode.FenrirPluginCheck(down, reason = runtime.error.lowercase())
            return@withContext probe(runtime.providers)
        }
        val site = available.firstOrNull { it.internalName == id }
            ?: return@withContext com.nuvio.app.readmode.FenrirPluginCheck(down, reason = "not in your repositories")
        val dir = File(app.cacheDir, "cs_test").apply { mkdirs() }
        val file = File(dir, "${id.replace(Regex("[^A-Za-z0-9_.-]"), "_")}-${System.nanoTime()}.cs3")
        try {
            runCatching { download(site.url, file) }.onFailure {
                return@withContext com.nuvio.app.readmode.FenrirPluginCheck(down, reason = "download failed")
            }
            val providers = runCatching { loadPlugin(file) }.getOrElse {
                return@withContext com.nuvio.app.readmode.FenrirPluginCheck(down, reason = "plugin doesn't load")
            }
            probe(providers)
        } finally {
            // throw the test copy away: its sites leave the app's list, the file is deleted
            runCatching {
                APIHolder.allProviders.removeAll(APIHolder.allProviders.filter { it.sourcePlugin == file.absolutePath }.toList())
            }
            file.setWritable(true)
            file.delete()
        }
    }

    private suspend fun probe(providers: List<MainAPI>): com.nuvio.app.readmode.FenrirPluginCheck {
        val down = com.nuvio.app.readmode.FenrirPluginCheck.Result.Down
        if (providers.isEmpty()) return com.nuvio.app.readmode.FenrirPluginCheck(down, reason = "no sites in it")
        val start = System.currentTimeMillis()
        val ok = withTimeoutOrNull(25_000) {
            providers.take(3).any { provider ->
                val home = if (provider.hasMainPage) {
                    provider.mainPage.firstOrNull()?.let { c ->
                        runCatching {
                            provider.getMainPage(1, MainPageRequest(c.name, c.data, c.horizontalImages))
                                ?.items?.any { it.list.isNotEmpty() } == true
                        }.getOrDefault(false)
                    } ?: false
                } else false
                home || provider.searchAny("the").isNotEmpty() || provider.searchAny("love").isNotEmpty()
            }
        }
        val seconds = (System.currentTimeMillis() - start) / 1000.0
        return when (ok) {
            null -> com.nuvio.app.readmode.FenrirPluginCheck(down, seconds, "site too slow")
            false -> com.nuvio.app.readmode.FenrirPluginCheck(down, seconds, "site not loading")
            true -> com.nuvio.app.readmode.FenrirPluginCheck(
                if (seconds > 8) com.nuvio.app.readmode.FenrirPluginCheck.Result.Slow else com.nuvio.app.readmode.FenrirPluginCheck.Result.Works,
                seconds,
            )
        }
    }

    // ------------------------------------------------------------------ loading

    private suspend fun reload() = loadMutex.withLock {
        val showNsfw = prefs().getBoolean(KEY_SHOW_NSFW, false)
        savedPlugins().forEach { saved ->
            if (synchronized(loaded) { saved.internalName in loaded }) return@forEach
            if (saved.nsfw && !showNsfw) return@forEach
            val file = fileFor(saved.internalName)
            if (!file.exists()) return@forEach
            val result = runCatching { loadPlugin(file) }
            val providers = result.getOrNull().orEmpty()
            val error = result.exceptionOrNull()?.let { e ->
                Log.w(TAG, "Failed to load ${saved.internalName}", e)
                "Couldn't load: ${e.javaClass.simpleName}"
            } ?: if (providers.isEmpty()) "No sites in this plugin" else null
            synchronized(loaded) { loaded[saved.internalName] = Loaded(saved, file, providers, error) }
        }
        publish()
    }

    private fun loadPlugin(file: File): List<MainAPI> {
        file.setReadOnly()
        val loader = PathClassLoader(file.absolutePath, app.classLoader)
        val manifestText = loader.getResourceAsStream("manifest.json")?.use { it.readBytes().decodeToString() }
            ?: error("no manifest")
        val manifest = JSONObject(manifestText)
        val className = manifest.getString("pluginClassName")
        val instance = loader.loadClass(className).getDeclaredConstructor().newInstance() as BasePlugin
        instance.filename = file.absolutePath
        if (manifest.optBoolean("requiresResources") && instance is Plugin) {
            val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
            AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, file.absolutePath)
            @Suppress("DEPRECATION")
            instance.resources = Resources(assets, app.resources.displayMetrics, app.resources.configuration)
        }
        if (instance is Plugin) instance.load(app) else instance.load()
        return APIHolder.allProviders.filter { it.sourcePlugin == file.absolutePath }.toList()
    }

    /**
     * Newer plugins only implement the page-based search(query, page); older ones only search(query).
     * search(query, 1) covers both (its default falls back to search(query)); errors → empty.
     */
    private suspend fun MainAPI.searchAny(query: String): List<SearchResponse> =
        runCatching { search(query, 1)?.items }.getOrNull()
            ?: runCatching { search(query) }.getOrNull()
            ?: emptyList()

    override fun sourceNamesOf(pluginId: String): List<String> =
        synchronized(loaded) { loaded[pluginId]?.providers?.map { it.name } }.orEmpty()

    private fun providers(): List<MainAPI> = synchronized(loaded) { loaded.values.flatMap { it.providers } }

    // ------------------------------------------------------------------ finding streams

    override suspend fun findStreams(
        query: FenrirStreamQuery,
        onFound: suspend (sourceName: String, streams: List<FenrirPluginStream>) -> Unit,
    ) = coroutineScope {
        val permits = Semaphore(4)
        providers().filter { FenrirSourceFlags.usedForStreams(id, it.name) }.map { provider ->
            async(Dispatchers.IO) {
                permits.withPermit {
                    val found = withTimeoutOrNull(35_000) {
                        runCatching { findIn(provider, query) }
                            .onFailure { Log.w(TAG, "${provider.name}: ${it.message}") }
                            .getOrNull()
                    }.orEmpty()
                    onFound(provider.name, found)
                }
            }
        }.awaitAll()
        Unit
    }

    private fun normalize(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun bestMatch(results: List<SearchResponse>, query: FenrirStreamQuery): SearchResponse? {
        val q = normalize(query.title)
        if (q.isEmpty()) return null
        return results.mapNotNull { result ->
            val name = normalize(result.name)
            var score = when {
                name == q -> 100
                name.startsWith(q) && name.length - q.length <= 12 -> 80
                q.startsWith(name) && name.length >= 4 -> 60
                name.contains(q) && q.length >= 5 -> 50
                else -> 0
            }
            if (score == 0) return@mapNotNull null
            val year = (result as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
                ?: (result as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
                ?: (result as? com.lagradost.cloudstream3.AnimeSearchResponse)?.year
            if (year != null && query.year != null) score += if (year == query.year) 15 else -20
            val type = result.type?.name.orEmpty()
            if (query.isMovie && type in setOf("TvSeries", "Anime", "AsianDrama", "Cartoon")) score -= 25
            if (!query.isMovie && type in setOf("Movie", "AnimeMovie")) score -= 25
            result to score
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    private suspend fun findIn(provider: MainAPI, query: FenrirStreamQuery): List<FenrirPluginStream> {
        val results = provider.searchAny(query.title)
        val match = bestMatch(results, query) ?: return emptyList()
        val response = provider.load(match.url) ?: return emptyList()
        val targets: List<Pair<String, String>> = when (response) {
            is MovieLoadResponse -> listOf(response.dataUrl to "")
            is TvSeriesLoadResponse -> pickEpisode(response.episodes, query)?.let { listOf(it.data to "") }.orEmpty()
            is AnimeLoadResponse -> response.episodes.mapNotNull { (dub, list) ->
                pickEpisode(list, query)?.let { it.data to dub.name }
            }
            else -> emptyList()
        }
        return targets.flatMap { (data, dub) -> links(provider, data, match.name, dub) }
    }

    private fun pickEpisode(episodes: List<Episode>, query: FenrirStreamQuery): Episode? {
        if (query.isMovie) return episodes.firstOrNull()
        val number = query.episode ?: return episodes.firstOrNull()
        val season = query.season
        return episodes.firstOrNull { it.episode == number && (season == null || it.season == null || it.season == season) }
            ?: episodes.filter { season == null || it.season == null || it.season == season }
                .sortedBy { it.episode ?: Int.MAX_VALUE }
                .getOrNull(number - 1)
    }

    private suspend fun links(provider: MainAPI, data: String, matchedName: String, dub: String): List<FenrirPluginStream> {
        val lock = Mutex()
        val subtitles = mutableListOf<Pair<String, String>>()
        val links = mutableListOf<ExtractorLink>()
        provider.loadLinks(
            data = data,
            isCasting = false,
            subtitleCallback = { sub -> synchronized(subtitles) { subtitles += sub.url to sub.lang } },
            callback = { link -> synchronized(links) { links += link } },
        )
        return lock.withLock {
            val subs = synchronized(subtitles) { subtitles.toList() }
            synchronized(links) { links.toList() }
                .filter { it.type != ExtractorLinkType.TORRENT && it.type != ExtractorLinkType.MAGNET }
                .map { link -> link.toStream(provider.name, matchedName, dub, subs) }
        }
    }

    private fun ExtractorLink.toStream(
        providerName: String,
        matchedName: String,
        dub: String,
        subs: List<Pair<String, String>>,
    ): FenrirPluginStream {
        val headers = buildMap {
            putAll(this@toStream.headers)
            if (referer.isNotBlank() && keys.none { it.equals("Referer", ignoreCase = true) }) put("Referer", referer)
        }
        val qualityLabel = if (quality in 100..4320) "${quality}p" else null
        return FenrirPluginStream(
            sourceName = providerName,
            label = listOfNotNull(name.takeIf { it.isNotBlank() }, qualityLabel).joinToString(" · ").ifBlank { "Video" },
            detail = listOfNotNull(
                "\"$matchedName\"".takeIf { matchedName.isNotBlank() },
                when (dub) { "Dubbed" -> "Dub"; "Subbed" -> "Sub"; else -> null },
            ).joinToString(" · ").ifBlank { "CloudStream" },
            url = url,
            headers = headers,
            subtitles = subs,
        )
    }

    // ------------------------------------------------------------------ browsing

    override suspend fun search(query: String): List<FenrirPluginHit> = coroutineScope {
        providers().filter { FenrirSourceFlags.usedInSearch(id, it.name) }.map { provider ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(25_000) {
                    provider.searchAny(query)
                }.orEmpty().take(12).map { result ->
                    val key = "${provider.name}|${result.url}"
                    synchronized(hits) { hits[key] = provider to result }
                    FenrirPluginHit(key, provider.name, result.name, result.posterUrl)
                }
            }
        }.awaitAll().flatten()
    }

    override suspend fun searchSource(sourceId: String, query: String): List<FenrirPluginHit> = withContext(Dispatchers.IO) {
        val provider = providers().firstOrNull { it.name == sourceId } ?: return@withContext emptyList()
        withTimeoutOrNull(25_000) {
            provider.searchAny(query)
        }.orEmpty().map { result ->
            val key = "${provider.name}|${result.url}"
            synchronized(hits) { hits[key] = provider to result }
            FenrirPluginHit(key, provider.name, result.name, result.posterUrl)
        }
    }

    override suspend fun episodes(hit: FenrirPluginHit): List<FenrirPluginEpisode> = withContext(Dispatchers.IO) {
        // After a restart the browse cache is empty: the key itself is "source|url" (Continue watching).
        val (provider, url) = synchronized(hits) { hits[hit.key] }?.let { (p, r) -> p to r.url }
            ?: providers().firstOrNull { it.name == hit.key.substringBefore("|") }?.let { it to hit.key.substringAfter("|") }
            ?: return@withContext emptyList()
        val response = runCatching { provider.load(url) }.getOrNull() ?: return@withContext emptyList()
        data class Row(val data: String, val label: String, val season: Int?, val episode: Int?)
        val list: List<Row> = when (response) {
            is MovieLoadResponse -> listOf(Row(response.dataUrl, response.name, null, null))
            is TvSeriesLoadResponse -> response.episodes.map { ep -> Row(ep.data, ep.label(), ep.season, ep.episode) }
            is AnimeLoadResponse -> response.episodes.flatMap { (dub, eps) ->
                eps.map { ep -> Row(ep.data, "${ep.label()} · ${if (dub.name == "Dubbed") "Dub" else "Sub"}", ep.season, ep.episode) }
            }
            else -> emptyList()
        }
        list.mapIndexed { index, row ->
            val key = "${hit.key}#$index"
            synchronized(episodeData) { episodeData[key] = provider to row.data }
            FenrirPluginEpisode(key, row.label, row.season, row.episode)
        }
    }

    private fun Episode.label(): String = buildString {
        if (season != null) append("S$season ")
        if (episode != null) append("E$episode")
        name?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" · "); append(it) }
        if (isEmpty()) append("Episode")
    }

    override suspend fun streams(episode: FenrirPluginEpisode): List<FenrirPluginStream> = withContext(Dispatchers.IO) {
        val (provider, data) = synchronized(episodeData) { episodeData[episode.key] } ?: return@withContext emptyList()
        runCatching { links(provider, data, "", "") }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------ home pages (like Zangetsu / CloudStream)

    override fun homeSources(): List<FenrirHomeSource> = synchronized(loaded) {
        loaded.values.flatMap { entry ->
            entry.providers.filter { it.hasMainPage }.map { provider ->
                FenrirHomeSource(
                    id = provider.name,
                    name = provider.name,
                    subtitle = listOf(entry.saved.name.takeIf { it != provider.name }, provider.lang.uppercase())
                        .filterNotNull().joinToString(" · "),
                    iconUrl = entry.saved.iconUrl,
                )
            }
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    private fun homeProvider(sourceId: String): MainAPI? =
        providers().firstOrNull { it.name == sourceId && it.hasMainPage }

    override suspend fun homeRows(sourceId: String): List<FenrirHomeRow> = coroutineScope {
        val provider = homeProvider(sourceId) ?: return@coroutineScope emptyList()
        val categories = provider.mainPage
        // Every category at once, each with its own time limit, so one slow category
        // can't hold up the rest (same as Zangetsu).
        val responses = categories.map { category ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(HOME_ROW_TIMEOUT_MS) {
                    runCatching {
                        provider.getMainPage(1, MainPageRequest(category.name, category.data, category.horizontalImages))
                    }.onFailure { Log.w(TAG, "${provider.name} home '${category.name}': ${it.message}") }.getOrNull()
                }
            }
        }.awaitAll()
        responses.flatMapIndexed { index, response ->
            val category = categories.getOrNull(index)
            response?.items.orEmpty().mapNotNull { list ->
                val items = list.list.map { it.toHomeItem(provider) }
                if (items.isEmpty()) null else FenrirHomeRow(
                    title = list.name.ifBlank { category?.name.orEmpty() },
                    categoryName = category?.name ?: list.name,
                    categoryData = category?.data.orEmpty(),
                    items = items,
                    horizontalImages = list.isHorizontalImages,
                )
            }
        }
    }

    override suspend fun homeRowPage(
        sourceId: String,
        categoryName: String,
        categoryData: String,
        page: Int,
    ): List<FenrirHomeItem> = withContext(Dispatchers.IO) {
        val provider = homeProvider(sourceId) ?: return@withContext emptyList()
        val category = provider.mainPage.firstOrNull { it.name == categoryName && it.data == categoryData }
            ?: provider.mainPage.firstOrNull { it.name == categoryName }
            ?: return@withContext emptyList()
        val response = withTimeoutOrNull(HOME_ROW_TIMEOUT_MS) {
            runCatching {
                provider.getMainPage(page, MainPageRequest(category.name, category.data, category.horizontalImages))
            }.getOrNull()
        } ?: return@withContext emptyList()
        response.items.flatMap { list -> list.list.map { it.toHomeItem(provider) } }
    }

    private fun SearchResponse.toHomeItem(provider: MainAPI): FenrirHomeItem {
        val key = "${provider.name}|$url"
        synchronized(hits) { hits[key] = provider to this }
        val dub = (this as? AnimeSearchResponse)?.dubStatus.orEmpty()
        val badge = listOfNotNull(
            "SUB".takeIf { DubStatus.Subbed in dub },
            "DUB".takeIf { DubStatus.Dubbed in dub },
        ).joinToString(" · ").ifBlank { null }
        val year = (this as? com.lagradost.cloudstream3.MovieSearchResponse)?.year
            ?: (this as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year
            ?: (this as? AnimeSearchResponse)?.year
        val typeName = type?.name.orEmpty()
        return FenrirHomeItem(
            hit = FenrirPluginHit(key, provider.name, name, posterUrl),
            badge = badge,
            year = year,
            isMovie = typeName in setOf("Movie", "AnimeMovie", "Documentary"),
        )
    }
}
