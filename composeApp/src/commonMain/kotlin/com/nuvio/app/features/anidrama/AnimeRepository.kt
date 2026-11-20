package com.nuvio.app.features.anidrama

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpPostJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Anime side of the Ani-drama tab: AniList lists, English-dub info and ID matching. */
internal object AnimeRepository {
    private val log = Logger.withTag("AniDramaAnime")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Community list of anime with an English dub (MyAnimeList IDs). Loaded once, used as-is. */
    private const val DUB_LIST_URL = "https://raw.githubusercontent.com/MAL-Dubs/MAL-Dubs/main/data/dubInfo.json"

    /** Matches AniList IDs to Kitsu / IMDb / TMDB IDs (https://arm.haglund.dev). */
    private const val ID_MAP_URL = "https://arm.haglund.dev/api/v2/ids?include=anilist,kitsu,imdb,themoviedb,myanimelist"

    private val dubMutex = Mutex()
    private var dubbedMalIds: Set<Int>? = null

    private val idMutex = Mutex()
    private val idCache = HashMap<Int, MappedIds>()

    private data class MappedIds(val kitsu: Int?, val imdb: String?, val tmdb: Int?)

    private val pageCache = HashMap<String, Pair<List<AniDramaItem>, Boolean>>()

    suspend fun dubbedIds(): Set<Int> = dubMutex.withLock {
        dubbedMalIds?.let { return@withLock it }
        val ids = runCatching {
            val root = json.parseToJsonElement(httpGetText(DUB_LIST_URL)).jsonObject
            root["dubbed"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.toSet().orEmpty()
        }.onFailure { log.w { "Dub list failed: ${it.message}" } }.getOrNull()
        if (ids != null && ids.isNotEmpty()) dubbedMalIds = ids
        ids.orEmpty()
    }

    /**
     * One page of a list, already matched to add-on IDs and marked dub / no dub.
     * Results are kept for this app session so flipping between chips is instant.
     */
    suspend fun page(query: AniListClient.MediaQuery, page: Int): AniDramaPage? {
        val cacheKey = "$query#$page"
        pageCache[cacheKey]?.let { (items, more) -> return AniDramaPage(items, more) }
        val (media, hasMore) = AniListClient.page(query, page) ?: return null
        val dubbed = dubbedIds()
        mapIds(media.map { it.id })
        val items = media.map { it.toItem(dubbed, captionFor(it, query)) }
        pageCache[cacheKey] = items to hasMore
        return AniDramaPage(items, hasMore)
    }

    /** This week's episodes, Monday to Sunday in the phone's time zone. */
    suspend fun weekSchedule(): List<AniDramaScheduleEntry> {
        val weekStart = AniDramaTime.mondayOfThisWeek()
        val from = AniDramaTime.localStartOfDayMs(weekStart) / 1000 - 1
        val to = AniDramaTime.localStartOfDayMs(AniDramaTime.addDays(weekStart, 7)) / 1000
        val airings = AniListClient.schedule(from, to)
        val dubbed = dubbedIds()
        mapIds(airings.mapNotNull { it.media?.id }.distinct())
        return airings.mapNotNull { airing ->
            val media = airing.media ?: return@mapNotNull null
            val (weekday, time) = AniDramaTime.localWeekdayAndTime(airing.airingAt, weekStart) ?: return@mapNotNull null
            val item = media.toItem(dubbed, caption = "EP ${airing.episode}")
            AniDramaScheduleEntry(
                item = item,
                weekday = weekday,
                timeLabel = time,
                episodeLabel = buildString {
                    append("EP ${airing.episode} airs $time")
                    append(if (item.hasEnglishDub) " · Dub" else " · Sub")
                },
                sortKey = airing.airingAt,
                genres = media.genres,
            )
        }
    }

    fun applyAudioFilter(items: List<AniDramaItem>, filter: AnimeAudioFilter): List<AniDramaItem> = when (filter) {
        AnimeAudioFilter.Both -> items
        AnimeAudioFilter.Dub -> items.filter { it.hasEnglishDub }
        AnimeAudioFilter.Sub -> items.filterNot { it.hasEnglishDub }
    }

    private fun captionFor(media: AniListMedia, query: AniListClient.MediaQuery): String {
        val format = media.format?.toFormatLabel()
        val next = media.nextAiringEpisode
        val episodePart = when {
            next != null && query.status == "RELEASING" -> "EP ${next.episode - 1}".takeIf { next.episode > 1 } ?: "Starts soon"
            next != null -> "EP ${next.episode} next"
            media.episodes != null && media.format != "MOVIE" -> "${media.episodes} EP"
            else -> null
        }
        val yearPart = media.seasonYear?.toString() ?: media.startDate?.year?.toString()
        return listOfNotNull(episodePart, format, if (episodePart == null) yearPart else null).joinToString(" · ")
    }

    private fun String.toFormatLabel(): String = when (this) {
        "TV" -> "TV"
        "TV_SHORT" -> "Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        else -> this.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun AniListMedia.toItem(dubbed: Set<Int>, caption: String?): AniDramaItem {
        val ids = idCache[id]
        return AniDramaItem(
            key = "anilist:$id",
            title = displayTitle,
            poster = coverImage?.extraLarge ?: coverImage?.large,
            backdrop = bannerImage,
            source = AniDramaSource.AniList(
                aniListId = id,
                malId = idMal,
                isMovie = format == "MOVIE",
                kitsuId = ids?.kitsu,
                imdbId = ids?.imdb,
                tmdbId = ids?.tmdb,
            ),
            badge = averageScore?.let { score -> "${score / 10}.${score % 10}" },
            caption = caption,
            year = (seasonYear ?: startDate?.year)?.toString(),
            description = description?.stripHtml(),
            genres = genres,
            hasEnglishDub = idMal != null && idMal in dubbed,
        )
    }

    /** Looks up Kitsu / IMDb / TMDB IDs for AniList IDs not seen yet (100 per request). */
    private suspend fun mapIds(aniListIds: List<Int>) = idMutex.withLock {
        val missing = aniListIds.filter { it !in idCache }.distinct()
        missing.chunked(100).forEach { chunk ->
            val body = buildJsonArray {
                chunk.forEach { id -> add(buildJsonObject { put("anilist", id) }) }
            }.toString()
            val results = runCatching { json.parseToJsonElement(httpPostJson(ID_MAP_URL, body)).jsonArray }
                .onFailure { log.w { "ID matching failed: ${it.message}" } }
                .getOrNull() ?: return@forEach
            results.forEachIndexed { index, element ->
                val aniListId = chunk.getOrNull(index) ?: return@forEachIndexed
                val obj = element as? JsonObject
                idCache[aniListId] = if (obj == null || element is JsonNull) {
                    MappedIds(null, null, null)
                } else {
                    MappedIds(
                        kitsu = obj["kitsu"]?.jsonPrimitive?.intOrNull,
                        imdb = obj["imdb"]?.jsonPrimitive?.content?.takeIf { it.startsWith("tt") },
                        tmdb = obj["themoviedb"]?.jsonPrimitive?.intOrNull,
                    )
                }
            }
        }
    }

    private fun String.stripHtml(): String =
        replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
            .trim()
}
