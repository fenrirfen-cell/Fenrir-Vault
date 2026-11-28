package com.nuvio.app.voyage

import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ───────────── colours and modes ───────────── */

object VoyageColors {
    val Gold = Color(0xFFD4AF65)
    val BrightGold = Color(0xFFFFD478)
    val Ivory = Color(0xFFF5E8D0)
    val Secondary = Color(0xFFBDAE98)
    val Navy = Color(0xFF06141E)
    val Walnut = Color(0xFF21140C)
    val Burgundy = Color(0xFF941C29)
    val Purple = Color(0xFF8B32D6)
    val Watch = Color(0xFFE53945)
    val AniDrama = Color(0xFFAB55EA)
    val Read = Color(0xFFEAB84D)
}

enum class VoyageMode(val label: String) { ALL("All"), WATCH("Watch"), ANI_DRAMA("Ani-Drama"), READ("Read") }

fun VoyageMode.accent(): Color = when (this) {
    VoyageMode.ALL -> VoyageColors.Gold
    VoyageMode.WATCH -> VoyageColors.Watch
    VoyageMode.ANI_DRAMA -> VoyageColors.AniDrama
    VoyageMode.READ -> VoyageColors.Read
}

/** Anime ids from the anime trackers, or an "anime" type, belong to Ani-Drama. */
fun voyageModeForWatch(id: String, type: String?, genres: List<String> = emptyList()): VoyageMode {
    val anime = id.startsWith("kitsu:") || id.startsWith("mal:") || id.startsWith("anilist:") || id.startsWith("anidb:") ||
        type.equals("anime", ignoreCase = true) || genres.any { it.equals("anime", ignoreCase = true) }
    return if (anime) VoyageMode.ANI_DRAMA else VoyageMode.WATCH
}

/* ───────────── the Read side, as plain data (filled in by the app) ───────────── */

data class ReadHistoryRow(
    val mangaId: Long,
    val chapterId: Long,
    val readAt: Long,
    val durationMs: Long,
    val chapterRead: Boolean,
)

data class ReadBook(
    val mangaId: Long,
    val title: String,
    val coverUrl: String?,
    val isNovel: Boolean,
    val favorite: Boolean,
    val totalChapters: Long,
    val readCount: Long,
    val lastRead: Long,
    val genres: List<String>,
    val language: String?,
    val downloadCount: Long,
)

data class ReadContinue(
    val mangaId: Long,
    val chapterId: Long,
    val title: String,
    val coverUrl: String?,
    val isNovel: Boolean,
    val chapterNumber: Double,
    val readAt: Long,
    val totalChapters: Long,
    val readCount: Long,
)

data class ReadSnapshot(
    val history: List<ReadHistoryRow>,
    val books: List<ReadBook>,
    val continueReading: List<ReadContinue>,
    val downloadedChapters: Int,
)

enum class OfflineItemState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class ReadDownload(
    val chapterId: Long,
    val mangaId: Long,
    val title: String,
    val chapterName: String,
    val state: OfflineItemState,
    val progress: Int,
)

/** Hooks the app fills in (Read data, opening Read screens, storage, the clock's local day). */
object VoyageBridge {
    var readSnapshot: (suspend () -> ReadSnapshot)? = null
    var resumeRead: ((mangaId: Long, chapterId: Long) -> Unit)? = null
    var openReadBook: ((mangaId: Long) -> Unit)? = null
    var readDownloads: Flow<List<ReadDownload>>? = null
    var downloadNextChapters: (suspend (mangaId: Long, count: Int) -> Int)? = null
    var pauseReadDownloads: (() -> Unit)? = null
    var resumeReadDownloads: (() -> Unit)? = null
    var cancelReadDownload: ((chapterId: Long) -> Unit)? = null
    var openReadDownloadQueue: (() -> Unit)? = null
    var freeBytes: (() -> Long?)? = null
    var load: (String) -> String? = { null }
    var save: (String, String) -> Unit = { _, _ -> }

    /** Local calendar day number (days since 1970-01-01 in the phone's time zone). */
    var localDay: (Long) -> Long = { it / 86_400_000L }

    /** First local day of this week (Monday), month and year, for the time ranges. */
    var rangeStartDay: (VoyageRange) -> Long? = { range ->
        val today = localDay(nowMs())
        when (range) {
            VoyageRange.WEEK -> today - ((today + 3) % 7)
            VoyageRange.MONTH -> today - 29
            VoyageRange.YEAR -> today - 364
            VoyageRange.ALL -> null
        }
    }
}

fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

/** Monday = 0 … Sunday = 6, for a local day number. */
fun weekdayOf(day: Long): Int = (((day + 3) % 7) + 7).toInt() % 7

enum class VoyageRange(val label: String) { WEEK("This week"), MONTH("This month"), YEAR("This year"), ALL("All time") }

/* ───────────── which Voyage screen is open ───────────── */

enum class VoyageScreen { CONTINUE, LOG, BOUNTIES, OFFLINE, SURPRISE }

object VoyageNav {
    private val _screen = MutableStateFlow<VoyageScreen?>(null)
    val screen: StateFlow<VoyageScreen?> = _screen.asStateFlow()
    private val stack = ArrayDeque<VoyageScreen>()

    /** True when the screen was opened from the Read side (closing it goes back to Read). */
    var fromRead: Boolean = false
        private set

    fun open(screen: VoyageScreen, fromRead: Boolean = false) {
        if (_screen.value == null) {
            stack.clear()
            this.fromRead = fromRead
        } else {
            _screen.value?.let { stack.addLast(it) }
        }
        _screen.value = screen
    }

    /** Back: the previous Voyage screen, or close. Returns true when everything closed. */
    fun back(): Boolean {
        val prev = stack.removeLastOrNull()
        _screen.value = prev
        return prev == null
    }

    fun close() {
        stack.clear()
        _screen.value = null
    }
}

/** Watch-side Continue items, published by Home (all of them, before the Movies / TV filter). */
object VoyageData {
    val watchContinue = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    val readSnapshot = MutableStateFlow<ReadSnapshot?>(null)
    val readLoading = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun refreshRead() {
        val fetch = VoyageBridge.readSnapshot ?: return
        if (job?.isActive == true) return
        readLoading.value = true
        job = scope.launch {
            runCatching { fetch() }.onSuccess { readSnapshot.value = it }
            readLoading.value = false
        }
    }
}

/* ───────────── watching time, recorded honestly ───────────── */

@Serializable
private data class StoredDay(val day: Long, val watchMs: Long = 0, val aniMs: Long = 0)

@Serializable
private data class StoredLog(val days: List<StoredDay> = emptyList())

/**
 * Adds real watching time per local day: only when a video's saved position moves forward by a
 * believable amount between two saves (a seek or a jump back counts as nothing).
 */
object VoyageActivityLog {
    private const val KEY = "voyage_watch_log"
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastPos = HashMap<String, Long>()
    private val days = HashMap<Long, LongArray>() // [watchMs, aniMs]
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private var started = false
    private var saveJob: Job? = null

    fun start() {
        if (started) return
        started = true
        runCatching {
            VoyageBridge.load(KEY)?.let { raw ->
                json.decodeFromString(StoredLog.serializer(), raw).days.forEach { days[it.day] = longArrayOf(it.watchMs, it.aniMs) }
            }
        }
        scope.launch {
            WatchProgressRepository.uiState.collect { state ->
                var changed = false
                state.entries.forEach { e ->
                    val key = e.videoId.ifBlank { e.parentMetaId }
                    val prev = lastPos[key]
                    lastPos[key] = e.lastPositionMs
                    if (prev != null) {
                        val delta = e.lastPositionMs - prev
                        // between two saves a few minutes pass; anything bigger is a seek
                        if (delta in 1_000L..15L * 60_000L) {
                            val day = VoyageBridge.localDay(e.lastUpdatedEpochMs.takeIf { it > 0 } ?: nowMs())
                            val slot = days.getOrPut(day) { longArrayOf(0, 0) }
                            if (voyageModeForWatch(e.parentMetaId, e.parentMetaType) == VoyageMode.ANI_DRAMA) slot[1] += delta else slot[0] += delta
                            changed = true
                        }
                    }
                }
                if (changed) {
                    _version.value++
                    scheduleSave()
                }
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(4_000)
            val out = StoredLog(days.entries.sortedBy { it.key }.map { StoredDay(it.key, it.value[0], it.value[1]) })
            runCatching { VoyageBridge.save(KEY, json.encodeToString(StoredLog.serializer(), out)) }
        }
    }

    /** day → (watch minutes, ani minutes) */
    fun snapshot(): Map<Long, Pair<Long, Long>> = days.mapValues { it.value[0] / 60_000 to it.value[1] / 60_000 }
}

/* ───────────── small helpers ───────────── */

/** "2h 10m", "45 min", "PT1H5M", "90" → minutes, or null when unknown. */
fun parseRuntimeMinutes(raw: String?): Int? {
    val s = raw?.trim()?.lowercase() ?: return null
    if (s.isEmpty()) return null
    s.toIntOrNull()?.let { return it.takeIf { m -> m > 0 } }
    val h = Regex("(\\d+)\\s*h").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val m = Regex("(\\d+)\\s*m").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val total = h * 60 + m
    return total.takeIf { it > 0 }
}

fun formatMinutes(min: Long): String = when {
    min <= 0 -> "0m"
    min < 60 -> "${min}m"
    else -> "${min / 60}h" + if (min % 60 != 0L) " ${min % 60}m" else ""
}

fun MetaPreview.voyageMode(): VoyageMode = voyageModeForWatch(id, type, genres)
