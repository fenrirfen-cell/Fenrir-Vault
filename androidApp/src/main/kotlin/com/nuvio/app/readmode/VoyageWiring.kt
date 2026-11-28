package com.nuvio.app.readmode

import android.app.Application
import android.content.Context
import android.content.Intent
import com.nuvio.app.voyage.OfflineItemState
import com.nuvio.app.voyage.ReadBook
import com.nuvio.app.voyage.ReadContinue
import com.nuvio.app.voyage.ReadDownload
import com.nuvio.app.voyage.ReadHistoryRow
import com.nuvio.app.voyage.ReadSnapshot
import com.nuvio.app.voyage.VoyageActivityLog
import com.nuvio.app.voyage.VoyageBridge
import com.nuvio.app.voyage.VoyageNav
import com.nuvio.app.voyage.VoyageRange
import com.nuvio.app.voyage.VoyageScreen
import eu.kanade.tachiyomi.EmbeddedHost
import eu.kanade.tachiyomi.voyage.VoyageRead
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

/** Connects the MugiVerse voyage screens (Watch side) to the Read side's real data. */
internal object VoyageWiring {
    fun install(app: Application, modes: ReadModeController) {
        val prefs = app.getSharedPreferences("mugi_voyage", Context.MODE_PRIVATE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

        VoyageBridge.load = { key -> prefs.getString(key, null) }
        VoyageBridge.save = { key, value -> prefs.edit().putString(key, value).apply() }
        VoyageBridge.localDay = { ms -> Math.floorDiv(ms + TimeZone.getDefault().getOffset(ms), 86_400_000L) }
        VoyageBridge.rangeStartDay = { range ->
            val now = System.currentTimeMillis()
            val today = VoyageBridge.localDay(now)
            val cal = Calendar.getInstance()
            when (range) {
                VoyageRange.WEEK -> today - ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Monday first
                VoyageRange.MONTH -> today - (cal.get(Calendar.DAY_OF_MONTH) - 1)
                VoyageRange.YEAR -> today - (cal.get(Calendar.DAY_OF_YEAR) - 1)
                VoyageRange.ALL -> null
            }
        }
        VoyageBridge.freeBytes = { runCatching { android.os.StatFs(app.filesDir.path).availableBytes }.getOrNull() }

        VoyageBridge.readSnapshot = {
            withContext(Dispatchers.IO) {
                val s = VoyageRead.snapshot()
                ReadSnapshot(
                    history = s.history.map { ReadHistoryRow(it.mangaId, it.chapterId, it.readAt, it.durationMs, it.chapterRead) },
                    books = s.books.map {
                        ReadBook(it.mangaId, it.title, it.coverUrl, it.isNovel, it.favorite, it.totalChapters, it.readCount, it.lastRead, it.genres, it.language, it.downloadCount)
                    },
                    continueReading = s.continueReading.map {
                        ReadContinue(it.mangaId, it.chapterId, it.title, it.coverUrl, it.isNovel, it.chapterNumber, it.readAt, it.totalChapters, it.readCount)
                    },
                    downloadedChapters = s.downloadedChapters,
                )
            }
        }
        VoyageBridge.resumeRead = { mangaId, chapterId ->
            scope.launch {
                val target = withContext(Dispatchers.IO) { runCatching { VoyageRead.resumeChapterId(mangaId, chapterId) }.getOrDefault(chapterId) }
                modes.startFromCurrent(eu.kanade.tachiyomi.ui.reader.ReaderActivity.newIntent(app, mangaId, target))
            }
        }
        VoyageBridge.openReadBook = { mangaId ->
            modes.startFromCurrent(
                modes.readActivityIntent(app)
                    .setAction("eu.kanade.tachiyomi.SHOW_MANGA")
                    .putExtra("manga", mangaId)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
        VoyageBridge.openReadDownloadQueue = {
            modes.startFromCurrent(
                modes.readActivityIntent(app)
                    .setAction("eu.kanade.tachiyomi.SHOW_DOWNLOADS")
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
        VoyageBridge.readDownloads = flow {
            emitAll(
                VoyageRead.downloads().map { list ->
                    list.map {
                        ReadDownload(
                            chapterId = it.chapterId,
                            mangaId = it.mangaId,
                            title = it.title,
                            chapterName = it.chapterName,
                            state = when (it.state) {
                                VoyageRead.DlState.QUEUED -> OfflineItemState.QUEUED
                                VoyageRead.DlState.DOWNLOADING -> OfflineItemState.DOWNLOADING
                                VoyageRead.DlState.PAUSED -> OfflineItemState.PAUSED
                                VoyageRead.DlState.COMPLETED -> OfflineItemState.COMPLETED
                                VoyageRead.DlState.FAILED -> OfflineItemState.FAILED
                            },
                            progress = it.progress,
                        )
                    }
                },
            )
        }
        VoyageBridge.downloadNextChapters = { mangaId, count -> withContext(Dispatchers.IO) { VoyageRead.downloadNext(mangaId, count) } }
        VoyageBridge.pauseReadDownloads = { runCatching { VoyageRead.pause() } }
        VoyageBridge.resumeReadDownloads = { runCatching { VoyageRead.resume() } }
        VoyageBridge.cancelReadDownload = { id -> runCatching { VoyageRead.cancel(id) } }

        // From the Read side: open a voyage screen on the Watch side; closing it comes back to Read.
        EmbeddedHost.openVoyage = { activity, screen ->
            val target = runCatching { VoyageScreen.valueOf(screen) }.getOrNull()
            if (target != null) {
                VoyageNav.open(target, fromRead = true)
                modes.openWatch(activity)
            }
        }

        runCatching { VoyageActivityLog.start() }
    }
}
