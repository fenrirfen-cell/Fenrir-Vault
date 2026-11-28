package com.nuvio.app.voyage

import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DailyVoyageActivity(val day: Long, val readingMinutes: Int, val watchingMinutes: Int)

data class CaptainStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val chaptersRead: Int,
    val booksCompleted: Int,
    val readingMinutes: Long,
    val watchingMinutes: Long,
    val moviesCompleted: Int,
    val episodesCompleted: Int,
    val animeEpisodesCompleted: Int,
    val favoriteGenre: String?,
    val favoriteGenreShare: Int?,
    val weeklyActivity: List<DailyVoyageActivity>,
    val activeDays: Int,
    val hasAnyData: Boolean,
)

/** Everything the stats are made from (all real, all local). */
data class VoyageInputs(
    val read: ReadSnapshot?,
    val watched: List<WatchedItem>,
    val progress: List<WatchProgressEntry>,
    val watchLog: Map<Long, Pair<Long, Long>>,
    val library: List<LibraryItem>,
    val watchDownloadsCompleted: Int,
    val fullyWatchedSeries: Int,
)

private fun WatchedItem.mode(): VoyageMode = voyageModeForWatch(id, type)
private fun WatchedItem.isMovie(): Boolean = type.equals("movie", ignoreCase = true)
private fun WatchedItem.isEpisode(): Boolean = season != null && episode != null || !videoId.isNullOrBlank() && !isMovie()

/** Days with genuine activity in [mode]. */
fun activeDays(inputs: VoyageInputs, mode: VoyageMode): Set<Long> {
    val d = HashSet<Long>()
    val day = VoyageBridge.localDay
    val today = day(nowMs())
    if (mode == VoyageMode.ALL || mode == VoyageMode.READ) {
        inputs.read?.history?.forEach { if (it.readAt > 0) d += day(it.readAt) }
    }
    if (mode != VoyageMode.READ) {
        inputs.watched.forEach { w -> if (mode == VoyageMode.ALL || w.mode() == mode) d += day(w.markedAtEpochMs) }
        inputs.progress.forEach { p ->
            if (p.lastUpdatedEpochMs > 0 && (mode == VoyageMode.ALL || voyageModeForWatch(p.parentMetaId, p.parentMetaType) == mode)) {
                d += day(p.lastUpdatedEpochMs)
            }
        }
        inputs.watchLog.forEach { (k, v) ->
            val m = when (mode) {
                VoyageMode.WATCH -> v.first
                VoyageMode.ANI_DRAMA -> v.second
                else -> v.first + v.second
            }
            if (m > 0) d += k
        }
    }
    return d.filter { it <= today }.toSet()
}

/** (current, best) streak in days. Today not done yet doesn't break the current one. */
fun streaks(days: Set<Long>): Pair<Int, Int> {
    if (days.isEmpty()) return 0 to 0
    val today = VoyageBridge.localDay(nowMs())
    var cur = 0
    var d = if (today in days) today else today - 1
    while (d in days) { cur++; d-- }
    var best = 0
    var run = 0
    var prev: Long? = null
    days.sorted().forEach { x ->
        run = if (prev != null && x == prev!! + 1) run + 1 else 1
        if (run > best) best = run
        prev = x
    }
    return cur to maxOf(best, cur)
}

fun captainStats(inputs: VoyageInputs, mode: VoyageMode, range: VoyageRange): CaptainStats {
    val day = VoyageBridge.localDay
    val start = VoyageBridge.rangeStartDay(range)
    fun inRange(ms: Long) = ms > 0 && (start == null || day(ms) >= start)
    fun dayInRange(d: Long) = start == null || d >= start
    val readOn = mode == VoyageMode.ALL || mode == VoyageMode.READ
    val watchOn = mode != VoyageMode.READ

    val history = if (readOn) inputs.read?.history.orEmpty() else emptyList()
    val rangedHistory = history.filter { inRange(it.readAt) }
    val chaptersRead = rangedHistory.count { it.chapterRead }
    val readingMinutes = rangedHistory.sumOf { it.durationMs } / 60_000
    val books = if (readOn) inputs.read?.books.orEmpty() else emptyList()
    val booksCompleted = books.count { it.totalChapters > 0 && it.readCount >= it.totalChapters && inRange(it.lastRead) }

    val watched = if (watchOn) inputs.watched.filter { mode == VoyageMode.ALL || it.mode() == mode } else emptyList()
    val rangedWatched = watched.filter { inRange(it.markedAtEpochMs) }
    val movies = rangedWatched.count { it.isMovie() }
    val episodes = rangedWatched.count { it.isEpisode() && it.mode() == VoyageMode.WATCH }
    val animeEps = rangedWatched.count { it.isEpisode() && it.mode() == VoyageMode.ANI_DRAMA }
    val watchingMinutes = if (watchOn) {
        inputs.watchLog.filterKeys { dayInRange(it) }.values.sumOf { (w, a) ->
            when (mode) {
                VoyageMode.WATCH -> w
                VoyageMode.ANI_DRAMA -> a
                else -> w + a
            }
        }
    } else {
        0L
    }

    // favourite genre: from the books read in range (weighted by chapters), plus saved shows
    val genreCount = HashMap<String, Int>()
    if (readOn) {
        val chaptersByBook = rangedHistory.groupingBy { it.mangaId }.eachCount()
        books.forEach { b -> val n = chaptersByBook[b.mangaId] ?: 0; if (n > 0) b.genres.forEach { g -> genreCount[g.trim()] = (genreCount[g.trim()] ?: 0) + n } }
    }
    if (watchOn) {
        val watchedIds = rangedWatched.map { it.id }.toSet()
        inputs.library.filter { it.id in watchedIds }.forEach { item -> item.genres.forEach { g -> genreCount[g.trim()] = (genreCount[g.trim()] ?: 0) + 1 } }
    }
    genreCount.remove("")
    val top = genreCount.maxByOrNull { it.value }
    val share = top?.let { (it.value * 100) / genreCount.values.sum().coerceAtLeast(1) }

    // last 7 local days
    val today = day(nowMs())
    val readByDay = history.groupBy { day(it.readAt) }.mapValues { (_, rows) -> (rows.sumOf { it.durationMs } / 60_000).toInt() }
    val week = (6 downTo 0).map { back ->
        val d = today - back
        val w = if (watchOn) inputs.watchLog[d]?.let { (wm, am) -> when (mode) { VoyageMode.WATCH -> wm; VoyageMode.ANI_DRAMA -> am; else -> wm + am } } ?: 0L else 0L
        DailyVoyageActivity(d, readByDay[d] ?: 0, w.toInt())
    }

    val days = activeDays(inputs, mode)
    val (cur, best) = streaks(days)
    return CaptainStats(
        currentStreak = cur,
        bestStreak = best,
        chaptersRead = chaptersRead,
        booksCompleted = booksCompleted,
        readingMinutes = readingMinutes,
        watchingMinutes = watchingMinutes,
        moviesCompleted = movies,
        episodesCompleted = episodes,
        animeEpisodesCompleted = animeEps,
        favoriteGenre = top?.key,
        favoriteGenreShare = share,
        weeklyActivity = week,
        activeDays = days.count { dayInRange(it) },
        hasAnyData = days.isNotEmpty() || chaptersRead > 0 || movies + episodes + animeEps > 0,
    )
}

/* ───────────── bounties ───────────── */

enum class BountyType {
    CHAPTERS_READ, BOOKS_COMPLETED, MOVIES_COMPLETED, EPISODES_COMPLETED, ACTIVE_DAYS,
    GENRES_EXPLORED, LANGUAGES_EXPLORED, CONTENT_COMPLETED, OFFLINE_ITEMS,
}

enum class BountyEmblem { BOOKS, LIBRARY, FILM, SHIP, ANIME, GLOBE, COMPASS, CALENDAR, CHEST }

data class BountyDefinition(
    val id: String,
    val name: String,
    val description: String,
    val type: BountyType,
    val target: Long,
    val reward: Long,
    val emblem: BountyEmblem,
    /** Only count anime episodes (for Anime Navigator). */
    val animeOnly: Boolean = false,
)

data class BountyProgress(val definition: BountyDefinition, val current: Long, val unlockedAt: Long? = null) {
    val unlocked: Boolean get() = unlockedAt != null
    val progress: Float
        get() = if (definition.target > 0) (current.toDouble() / definition.target.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f
}

/** Fictional achievement points: no money, nothing to buy. */
val BOUNTIES = listOf(
    BountyDefinition("bookworm", "Bookworm", "Read 100 chapters", BountyType.CHAPTERS_READ, 100, 30_000_000, BountyEmblem.BOOKS),
    BountyDefinition("librarian", "Librarian", "Complete 50 books", BountyType.BOOKS_COMPLETED, 50, 80_000_000, BountyEmblem.LIBRARY),
    BountyDefinition("film_captain", "Film Captain", "Complete 25 movies", BountyType.MOVIES_COMPLETED, 25, 40_000_000, BountyEmblem.FILM),
    BountyDefinition("grand_line", "Grand Line", "Complete 100 titles across Watch and Read", BountyType.CONTENT_COMPLETED, 100, 150_000_000, BountyEmblem.SHIP),
    BountyDefinition("anime_navigator", "Anime Navigator", "Complete 100 anime episodes", BountyType.EPISODES_COMPLETED, 100, 50_000_000, BountyEmblem.ANIME, animeOnly = true),
    BountyDefinition("polyglot", "Polyglot", "Read in 3 different languages", BountyType.LANGUAGES_EXPLORED, 3, 20_000_000, BountyEmblem.GLOBE),
    BountyDefinition("genre_explorer", "Genre Explorer", "Explore 10 genres", BountyType.GENRES_EXPLORED, 10, 25_000_000, BountyEmblem.COMPASS),
    BountyDefinition("voyage_veteran", "Voyage Veteran", "Be active on 30 different days", BountyType.ACTIVE_DAYS, 30, 60_000_000, BountyEmblem.CALENDAR),
    BountyDefinition("offline_explorer", "Offline Explorer", "Prepare 10 items for offline use", BountyType.OFFLINE_ITEMS, 10, 15_000_000, BountyEmblem.CHEST),
)

@Serializable
private data class StoredUnlocks(val unlocked: Map<String, Long> = emptyMap())

/** Unlock dates, kept forever; each bounty is granted once. */
object BountyStore {
    private const val KEY = "voyage_bounties"
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: MutableMap<String, Long>? = null

    private fun all(): MutableMap<String, Long> = cache ?: runCatching {
        VoyageBridge.load(KEY)?.let { json.decodeFromString(StoredUnlocks.serializer(), it).unlocked.toMutableMap() }
    }.getOrNull().orEmpty().toMutableMap().also { cache = it }

    fun unlockedAt(id: String): Long? = all()[id]

    /** Records a first unlock; returns true if it is new. */
    fun unlock(id: String, at: Long): Boolean {
        val map = all()
        if (map.containsKey(id)) return false
        map[id] = at
        runCatching { VoyageBridge.save(KEY, json.encodeToString(StoredUnlocks.serializer(), StoredUnlocks(map))) }
        return true
    }
}

fun bountyCurrent(def: BountyDefinition, inputs: VoyageInputs): Long {
    val read = inputs.read
    return when (def.type) {
        BountyType.CHAPTERS_READ -> read?.history?.count { it.chapterRead }?.toLong() ?: 0
        BountyType.BOOKS_COMPLETED -> read?.books?.count { it.totalChapters > 0 && it.readCount >= it.totalChapters }?.toLong() ?: 0
        BountyType.MOVIES_COMPLETED -> inputs.watched.count { it.isMovie() }.toLong()
        BountyType.EPISODES_COMPLETED -> inputs.watched.count { it.isEpisode() && (!def.animeOnly || it.mode() == VoyageMode.ANI_DRAMA) }.toLong()
        BountyType.ACTIVE_DAYS -> activeDays(inputs, VoyageMode.ALL).size.toLong()
        BountyType.GENRES_EXPLORED -> {
            val g = HashSet<String>()
            read?.books?.filter { it.readCount > 0 }?.forEach { b -> b.genres.forEach { g += it.trim().lowercase() } }
            val watchedIds = inputs.watched.map { it.id }.toSet()
            inputs.library.filter { it.id in watchedIds }.forEach { i -> i.genres.forEach { g += it.trim().lowercase() } }
            g.remove("")
            g.size.toLong()
        }
        BountyType.LANGUAGES_EXPLORED -> read?.books?.filter { it.readCount > 0 }
            ?.mapNotNull { it.language?.lowercase()?.takeIf { l -> l.isNotBlank() && l != "all" && l != "other" } }
            ?.toSet()?.size?.toLong() ?: 0
        BountyType.CONTENT_COMPLETED -> (inputs.watched.count { it.isMovie() } + inputs.fullyWatchedSeries +
            (read?.books?.count { it.totalChapters > 0 && it.readCount >= it.totalChapters } ?: 0)).toLong()
        BountyType.OFFLINE_ITEMS -> (inputs.watchDownloadsCompleted + (read?.downloadedChapters ?: 0)).toLong()
    }
}

/** Current progress of every bounty; records first unlocks as they happen. */
fun bountyBoard(inputs: VoyageInputs): List<BountyProgress> = BOUNTIES.map { def ->
    val cur = bountyCurrent(def, inputs)
    if (cur >= def.target) BountyStore.unlock(def.id, nowMs())
    BountyProgress(def, cur, BountyStore.unlockedAt(def.id))
}

/* ───────────── surprise ───────────── */

enum class SurpriseSource { MY_LIST, DISCOVER }

data class SurpriseCandidate(
    val id: String,
    val title: String,
    val artwork: String?,
    val mode: VoyageMode,
    val genre: String?,
    val durationMinutes: Int?,
    val completed: Boolean,
    val available: Boolean,
    val meta: com.nuvio.app.features.home.MetaPreview? = null,
    val readMangaId: Long? = null,
    val caption: String? = null,
)

fun chooseSurprise(
    candidates: List<SurpriseCandidate>,
    mode: VoyageMode,
    maxMinutes: Int?,
    excludedIds: Set<String>,
): SurpriseCandidate? = candidates.filter { item ->
    (mode == VoyageMode.ALL || item.mode == mode) &&
        (maxMinutes == null || (item.durationMinutes != null && item.durationMinutes <= maxMinutes)) &&
        item.available && !item.completed && item.id !in excludedIds
}.randomOrNull()


/** Bounties that would give away reading (hidden while reading is private). */
fun BountyDefinition.isReading(): Boolean =
    type == BountyType.CHAPTERS_READ || type == BountyType.BOOKS_COMPLETED || type == BountyType.LANGUAGES_EXPLORED
