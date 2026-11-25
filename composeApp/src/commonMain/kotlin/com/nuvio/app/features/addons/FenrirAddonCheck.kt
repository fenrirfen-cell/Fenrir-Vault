package com.nuvio.app.features.addons

import com.nuvio.app.features.trakt.TraktPlatformClock
import com.nuvio.app.readmode.FenrirPluginCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun test(addons: List<ManagedAddon>) {
        val todo = addons.filter { it.manifest != null && _checks.value[it.manifestUrl]?.result != FenrirPluginCheck.Result.Testing }
        if (todo.isEmpty()) return
        _checks.update { old -> old + todo.associate { it.manifestUrl to FenrirPluginCheck(FenrirPluginCheck.Result.Testing) } }
        todo.forEach { addon ->
            scope.launch {
                val result = permits.withPermit {
                    runCatching { testOne(addon) }.getOrElse { FenrirPluginCheck(FenrirPluginCheck.Result.Down, reason = "not answering") }
                }
                _checks.update { it + (addon.manifestUrl to result) }
            }
        }
    }

    private fun supports(manifest: AddonManifest, resource: String): List<String>? {
        val r = manifest.resources.firstOrNull { it.name == resource } ?: return null
        return r.types.ifEmpty { manifest.types }
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
                streamTypes != null -> {
                    what = "links"
                    val (type, id) = if ("movie" in streamTypes || streamTypes.isEmpty()) "movie" to MOVIE_ID else "series" to SERIES_ID
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
            null -> FenrirPluginCheck(FenrirPluginCheck.Result.Down, seconds, "too slow")
            false -> FenrirPluginCheck(FenrirPluginCheck.Result.Down, seconds, "no $what found")
            true -> FenrirPluginCheck(if (seconds > 8) FenrirPluginCheck.Result.Slow else FenrirPluginCheck.Result.Works, seconds)
        }
    }
}
