package com.nuvio.app.features.anidrama

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Fenrir: a catalog add-on built into the app (no server). Anime lists come from AniList (exact
 * airing times, seasons, upcoming dates) and K-drama lists from TMDB, the same data as the
 * Ani-drama tab. It installs itself once, shows up with your other add-ons, and you choose which
 * rows go on Home like any other catalog. Titles open with IDs your stream add-ons understand
 * (Kitsu, IMDb or TMDB).
 */
object FenrirCatalogs {
    const val ADDON_ID = "fenrir.catalogs"
    const val NAME = "Fenrir Catalogs"
    private const val BASE = "https://catalogs.fenrir.invalid"
    const val MANIFEST_URL = "$BASE/manifest.json"

    fun handles(url: String): Boolean = url.startsWith("$BASE/")

    /** Home row settings of this add-on ("fenrir.catalogs:type:id"); they stay on this device. */
    fun isHomeKey(key: String): Boolean = key.startsWith("$ADDON_ID:")

    private data class Cat(val id: String, val type: String, val name: String)

    private val CATALOGS = listOf(
        Cat("fenrir_anime_airing", "anime", "Anime · New episodes"),
        Cat("fenrir_anime_season", "anime", "Anime · This season"),
        Cat("fenrir_anime_upcoming", "anime", "Anime · Coming soon"),
        Cat("fenrir_anime_trending", "anime", "Anime · Trending"),
        Cat("fenrir_anime_popular", "anime", "Anime · Most popular"),
        Cat("fenrir_anime_top", "anime", "Anime · Top rated"),
        Cat("fenrir_kdrama_airing", "series", "K-drama · Airing now"),
        Cat("fenrir_kdrama_new", "series", "K-drama · New releases"),
        Cat("fenrir_kdrama_upcoming", "series", "K-drama · Coming soon"),
        Cat("fenrir_kdrama_popular", "series", "K-drama · Popular"),
        Cat("fenrir_kdrama_top", "series", "K-drama · Top rated"),
    )

    /** Answers the add-on's own links: the manifest and each catalog page. */
    suspend fun respond(url: String): String {
        val path = url.removePrefix(BASE).substringBefore("?")
        if (path == "/manifest.json") return manifest()
        // /catalog/{type}/{id}.json or /catalog/{type}/{id}/skip=30.json
        val parts = path.removePrefix("/").removeSuffix(".json").split("/")
        if (parts.size >= 3 && parts[0] == "catalog") {
            val id = parts[2].removeSuffix(".json")
            val skip = parts.getOrNull(3)?.split("&")?.firstOrNull { it.startsWith("skip=") }
                ?.removePrefix("skip=")?.toIntOrNull() ?: 0
            return catalog(id, skip)
        }
        error("Not found")
    }

    private fun manifest(): String = buildJsonObject {
        put("id", ADDON_ID)
        put("name", NAME)
        put("version", "1.0.0")
        put(
            "description",
            "Built into Fenrir. Anime from AniList (new episodes, this season, coming soon) and K-dramas from TMDB (airing now, new, coming soon), always up to date.",
        )
        put("resources", buildJsonArray { add(JsonPrimitive("catalog")) })
        put("types", buildJsonArray { add(JsonPrimitive("anime")); add(JsonPrimitive("series")) })
        put("idPrefixes", JsonArray(emptyList()))
        put(
            "catalogs",
            buildJsonArray {
                CATALOGS.forEach { c ->
                    add(
                        buildJsonObject {
                            put("type", c.type); put("id", c.id); put("name", c.name)
                            put("extra", buildJsonArray { add(buildJsonObject { put("name", "skip") }) })
                        },
                    )
                }
            },
        )
    }.toString()

    private suspend fun catalog(id: String, skip: Int): String {
        val items: List<AniDramaItem> = when {
            id.startsWith("fenrir_anime_") -> anime(id, skip)
            id.startsWith("fenrir_kdrama_") -> kdrama(id, skip)
            else -> emptyList()
        }
        return buildJsonObject {
            put("metas", buildJsonArray { items.forEach { add(it.toMetaJson()) } })
        }.toString()
    }

    private suspend fun anime(id: String, skip: Int): List<AniDramaItem> {
        val per = 30
        val page = skip / per + 1
        if (id == "fenrir_anime_airing") {
            // newest episode first, each with its episode number and day
            val all = AnimeRepository.airingLatest()?.items.orEmpty()
            return all.drop(skip).take(per)
        }
        val (season, year) = AniDramaTime.currentAnimeSeason()
        val query = when (id) {
            "fenrir_anime_season" -> AniListClient.MediaQuery(sort = "POPULARITY_DESC", season = season, seasonYear = year)
            "fenrir_anime_upcoming" -> AniListClient.MediaQuery(sort = "START_DATE", status = "NOT_YET_RELEASED", startGreater = AnimeRepository.fuzzyToday())
            "fenrir_anime_trending" -> AniListClient.MediaQuery(sort = "TRENDING_DESC")
            "fenrir_anime_popular" -> AniListClient.MediaQuery(sort = "POPULARITY_DESC", ranked = true)
            "fenrir_anime_top" -> AniListClient.MediaQuery(sort = "SCORE_DESC", ranked = true)
            else -> return emptyList()
        }
        return AnimeRepository.page(query, page)?.items.orEmpty()
    }

    private suspend fun kdrama(id: String, skip: Int): List<AniDramaItem> {
        val page = skip / 20 + 1
        val chip = when (id) {
            "fenrir_kdrama_airing" -> DramaChip.AiringNow
            "fenrir_kdrama_new" -> DramaChip.New
            "fenrir_kdrama_upcoming" -> DramaChip.Upcoming
            "fenrir_kdrama_popular" -> DramaChip.Popular
            "fenrir_kdrama_top" -> DramaChip.TopRated
            else -> return emptyList()
        }
        return DramaRepository.page(AniDramaRegion.KDrama, DramaRepository.Filter(chip), page)?.items.orEmpty()
    }

    private fun AniDramaItem.toMetaJson() = buildJsonObject {
        val meta: MetaPreview = toMetaPreview()
        put("id", meta.id)
        put("type", meta.type)
        put("name", title)
        poster?.let { put("poster", it) }
        backdrop?.let { put("background", it) }
        description?.let { put("description", it) }
        // the useful line: "EP 7 · Fri", "Starts Dec 3", or the year
        (caption ?: year)?.let { put("releaseInfo", it) }
        groupDate?.let { put("released", it) }
        badge?.takeIf { b -> b.firstOrNull()?.isDigit() == true }?.let { put("imdbRating", it) }
        if (genres.isNotEmpty()) put("genres", buildJsonArray { genres.forEach { add(JsonPrimitive(it)) } })
    }

    /** Adds the built-in add-on once per profile; if you remove it later it stays removed. */
    suspend fun ensureInstalled(isDone: (String) -> Boolean, markDone: (String) -> Unit) {
        AddonRepository.initialize()
        // Signed in: wait until your account's add-ons are loaded, so adding this one never
        // replaces them (it stays on this device and is never sent to your Nuvio account).
        if (!AddonRepository.awaitReadyForChanges(90_000)) return
        withTimeoutOrNull(20_000) { AddonRepository.uiState.first { st -> st.addons.none { it.isRefreshing } } }
        val key = "builtin_catalogs_${ProfileRepository.activeProfileId}"
        if (isDone(key)) return
        if (AddonRepository.uiState.value.addons.any { it.manifestUrl == MANIFEST_URL }) {
            markDone(key); return
        }
        val result = runCatching { AddonRepository.addAddon(MANIFEST_URL) }.getOrNull()
        if (result != null && result !is com.nuvio.app.features.addons.AddAddonResult.Error) markDone(key)
    }
}
