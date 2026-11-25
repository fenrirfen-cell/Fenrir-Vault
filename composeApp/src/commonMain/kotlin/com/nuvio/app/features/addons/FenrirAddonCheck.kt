package com.nuvio.app.features.addons

import com.nuvio.app.features.trakt.TraktPlatformClock
import com.nuvio.app.readmode.FenrirPluginCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fenrir: tries each installed add-on (Stremio / Nuvio) right now: a stream add-on is asked for
 * links to a well-known film (or show), a catalog add-on for its first list, a details add-on for
 * a film's details. Results: Works / Slow / Down, by manifest link.
 */
object FenrirAddonCheck {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val permits = Semaphore(3)
    private val _checks = MutableStateFlow<Map<String, FenrirPluginCheck>>(emptyMap())
    val checks: StateFlow<Map<String, FenrirPluginCheck>> = _checks.asStateFlow()

    // Well-known titles almost every add-on has (The Shawshank Redemption, Breaking Bad S1E1).
    private const val MOVIE_ID = "tt0111161"
    private const val SERIES_ID = "tt0903747:1:1"
    private const val SERIES_META_ID = "tt0903747"

    /** Saves small values between app starts (set by the Android app). */
    interface Store {
        fun get(key: String): String?
        fun put(key: String, value: String?)
    }
    var store: Store? = null

    /** Add-ons the check turned off itself (after 2 days of not answering); turned back on once they answer. */
    private const val KEY_AUTO_OFF = "auto_off"
    private const val TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1000L

    fun autoOff(): Set<String> = store?.get(KEY_AUTO_OFF)?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    private fun setAutoOff(urls: Set<String>) = store?.put(KEY_AUTO_OFF, urls.joinToString("\n"))

    fun test(addons: List<ManagedAddon>) {
        val todo = addons.filter { it.manifest != null && _checks.value[it.manifestUrl]?.result != FenrirPluginCheck.Result.Testing }
        if (todo.isEmpty()) return
        _checks.update { old -> old + todo.associate { it.manifestUrl to FenrirPluginCheck(FenrirPluginCheck.Result.Testing) } }
        todo.forEach { addon ->
            scope.launch { _checks.update { it + (addon.manifestUrl to run(addon)) } }
        }
    }

    private suspend fun run(addon: ManagedAddon): FenrirPluginCheck = permits.withPermit {
        runCatching { testOne(addon) }.getOrElse { FenrirPluginCheck(FenrirPluginCheck.Result.Down, reason = NOT_ANSWERING) }
    }

    private const val NOT_ANSWERING = "not answering"
    private const val TOO_SLOW = "too slow"

    /**
     * The automatic check (on app start and every few hours): tests every add-on, the ones it turned
     * off too. An add-on whose server doesn't answer is only turned off once it has kept failing for
     * 2 days; "answered but found nothing" never turns one off. Turned back on as soon as it answers.
     */
    suspend fun runAutomatic() {
        AddonRepository.initialize()
        // give the saved add-ons a moment to load their details
        withTimeoutOrNull(30_000) {
            AddonRepository.uiState.first { st -> st.addons.isNotEmpty() && st.addons.none { it.isRefreshing } }
        }
        val off = autoOff()
        val addons = AddonRepository.uiState.value.addons.filter { it.manifest != null && (it.enabled || it.manifestUrl in off) }
        if (addons.isEmpty()) return
        _checks.update { old -> old + addons.associate { it.manifestUrl to FenrirPluginCheck(FenrirPluginCheck.Result.Testing) } }
        val results = kotlinx.coroutines.coroutineScope {
            addons.map { a -> kotlinx.coroutines.async { a to run(a) } }.map { it.await() }
        }
        _checks.update { old -> old + results.associate { (a, r) -> a.manifestUrl to r } }
        // Nothing answered at all: most likely no internet, so change nothing.
        if (results.all { it.second.result == FenrirPluginCheck.Result.Down && it.second.reason in setOf(NOT_ANSWERING, TOO_SLOW) }) return
        val now = TraktPlatformClock.nowEpochMs()
        val newOff = off.toMutableSet()
        results.forEach { (addon, r) ->
            val key = "down_" + addon.manifestUrl
            val serverFailing = r.result == FenrirPluginCheck.Result.Down && r.reason in setOf(NOT_ANSWERING, TOO_SLOW)
            if (serverFailing) {
                val since = store?.get(key)?.toLongOrNull() ?: now.also { store?.put(key, it.toString()) }
                if (now - since >= TWO_DAYS_MS && addon.enabled) {
                    AddonRepository.setAddonEnabled(addon.manifestUrl, false)
                    newOff += addon.manifestUrl
                }
            } else {
                store?.put(key, null)
                if (addon.manifestUrl in newOff && r.result != FenrirPluginCheck.Result.Down) {
                    AddonRepository.setAddonEnabled(addon.manifestUrl, true)
                    newOff -= addon.manifestUrl
                }
            }
        }
        if (newOff != off) setAutoOff(newOff)
    }

    /** Turning an add-on on or off by hand ends the automatic handling of it. */
    fun userToggled(manifestUrl: String) {
        if (manifestUrl in autoOff()) setAutoOff(autoOff() - manifestUrl)
        store?.put("down_$manifestUrl", null)
    }

    private fun supports(manifest: AddonManifest, resource: String): List<String>? {
        val r = manifest.resources.firstOrNull { it.name == resource } ?: return null
        return r.types.ifEmpty { manifest.types }
    }

    /** A title the add-on can be asked about, by the kind of ids it takes (IMDb or Kitsu). */
    private fun streamTestId(manifest: AddonManifest, types: List<String>): Pair<String, String>? {
        val prefixes = manifest.resources.firstOrNull { it.name == "stream" }?.idPrefixes.orEmpty().ifEmpty { manifest.idPrefixes }
        val imdb = prefixes.isEmpty() || prefixes.any { "tt".startsWith(it) || it.startsWith("tt") }
        return when {
            imdb && ("movie" in types || types.isEmpty()) -> "movie" to MOVIE_ID
            imdb && "series" in types -> "series" to SERIES_ID
            prefixes.any { it.startsWith("kitsu") } -> (if ("series" in types || "anime" in types) (types.firstOrNull { it == "series" || it == "anime" } ?: "series") else types.first()) to "kitsu:1:1"
            else -> null
        }
    }

    private suspend fun testOne(addon: ManagedAddon): FenrirPluginCheck {
        val manifest = addon.manifest ?: return FenrirPluginCheck(FenrirPluginCheck.Result.Down, reason = "couldn't read it")
        val start = TraktPlatformClock.nowEpochMs()
        var what = "answer"
        val ok: Boolean? = withTimeoutOrNull(20_000) {
            val streamTypes = supports(manifest, "stream")
            val metaTypes = supports(manifest, "meta")
            val catalog = manifest.catalogs.firstOrNull { c -> c.extra.none { it.isRequired } }
            when {
                streamTypes != null && streamTestId(manifest, streamTypes) != null -> {
                    what = "links"
                    val (type, id) = streamTestId(manifest, streamTypes)!!
                    val body = fetchAddonResponseText(buildAddonResourceUrl(addon.manifestUrl, "stream", type, id), forceRefresh = true)
                    body.contains("\"url\"") || body.contains("\"infoHash\"") || body.contains("\"externalUrl\"") || body.contains("\"ytId\"")
                }
                catalog != null -> {
                    what = "titles"
                    val body = fetchAddonResponseText(buildAddonResourceUrl(addon.manifestUrl, "catalog", catalog.type, catalog.id), forceRefresh = true)
                    body.contains("\"id\"")
                }
                metaTypes != null -> {
                    what = "details"
                    val (type, id) = if ("movie" in metaTypes || metaTypes.isEmpty()) "movie" to MOVIE_ID else "series" to SERIES_META_ID
                    val body = fetchAddonResponseText(buildAddonResourceUrl(addon.manifestUrl, "meta", type, id), forceRefresh = true)
                    body.contains("\"name\"")
                }
                else -> {
                    // subtitles and other helpers: answering its own manifest is the best we can check
                    fetchAddonResponseText(addon.manifestUrl, forceRefresh = true).contains("\"id\"")
                }
            }
        }
        val seconds = (TraktPlatformClock.nowEpochMs() - start) / 1000.0
        return when (ok) {
            null -> FenrirPluginCheck(FenrirPluginCheck.Result.Down, seconds, TOO_SLOW)
            false -> FenrirPluginCheck(FenrirPluginCheck.Result.Down, seconds, "no $what found")
            true -> FenrirPluginCheck(if (seconds > 8) FenrirPluginCheck.Result.Slow else FenrirPluginCheck.Result.Works, seconds)
        }
    }
}
