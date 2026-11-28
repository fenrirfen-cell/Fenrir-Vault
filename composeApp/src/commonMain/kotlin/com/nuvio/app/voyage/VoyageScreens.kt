package com.nuvio.app.voyage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.nuvio.app.features.anidrama.toMetaPreview
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.launch

/** What the Watch side lets Voyage screens do. */
class VoyageActions(
    val onPosterClick: ((MetaPreview) -> Unit)?,
    val onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)?,
    val onDownloadsClick: () -> Unit,
)

/** Draws the open Voyage screen over everything (Back goes to the previous one, then closes). */
@Composable
fun VoyageHost(actions: VoyageActions) {
    val screen by VoyageNav.screen.collectAsState()
    val current = screen ?: return
    val back: () -> Unit = {
        val wasRead = VoyageNav.fromRead
        if (VoyageNav.back() && wasRead) com.nuvio.app.readmode.ReadModeBridge.openRead?.invoke()
    }
    PlatformBackHandler(enabled = true, onBack = back)
    // Reading privacy: one rule for every screen here (Read itself always shows reading)
    val privacy by ReadingPrivacy.state.collectAsState()
    val surface = if (VoyageNav.fromRead) PrivacySurface.READ else PrivacySurface.WATCH
    LaunchedEffect(privacy.temporarilyUnlocked) {
        while (privacy.temporarilyUnlocked) {
            kotlinx.coroutines.delay(10_000)
            ReadingPrivacy.current() // relocks when the 5 minutes are up
        }
    }
    val showReading = privacy.canShowReading(surface)
    LaunchedEffect(Unit) {
        VoyageData.refreshRead()
        WatchedRepository.ensureLoaded()
        DownloadsRepository.ensureLoaded()
    }
    Box(
        Modifier
            .fillMaxSize()
            .voyageBackground(walnut = current == VoyageScreen.LOG || current == VoyageScreen.BOUNTIES)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize().padding(top = top)) {
            // leaving to the player or a details page: close Voyage without going back to Read
            val leave: () -> Unit = { VoyageNav.close() }
            androidx.compose.runtime.CompositionLocalProvider(LocalShowReading provides showReading, LocalPrivacySurface provides surface) {
            when (current) {
                VoyageScreen.CONTINUE -> ContinueScreen(actions, back, leave, bottom)
                VoyageScreen.LOG -> CaptainsLogScreen(back, bottom)
                VoyageScreen.BOUNTIES -> BountiesScreen(back, bottom)
                VoyageScreen.OFFLINE -> OfflineVoyageScreen(actions, back, leave, bottom)
                VoyageScreen.SURPRISE -> SurpriseScreen(actions, back, leave, bottom)
                VoyageScreen.PRIVACY -> PrivacyScreen(back, bottom)
            }
            }
        }
    }
}

/** Whether reading may be shown on this screen (reading privacy). */
val LocalShowReading = androidx.compose.runtime.staticCompositionLocalOf { true }
val LocalPrivacySurface = androidx.compose.runtime.staticCompositionLocalOf { PrivacySurface.WATCH }

/** The Read snapshot, or nothing at all while reading is private and locked. */
@Composable
private fun readIfAllowed(): ReadSnapshot? {
    val read by VoyageData.readSnapshot.collectAsState()
    return if (LocalShowReading.current) read else null
}

@Composable
private fun rememberInputs(): VoyageInputs {
    val read = readIfAllowed()
    val watched by WatchedRepository.uiState.collectAsState()
    val series by WatchedRepository.fullyWatchedSeriesKeys.collectAsState()
    val progress by WatchProgressRepository.uiState.collectAsState()
    val logVersion by VoyageActivityLog.version.collectAsState()
    val library by LibraryRepository.uiState.collectAsState()
    val downloads by DownloadsRepository.uiState.collectAsState()
    return remember(read, watched, series, progress, logVersion, library, downloads) {
        VoyageInputs(
            read = read,
            watched = watched.items,
            progress = progress.entries,
            watchLog = VoyageActivityLog.snapshot(),
            library = library.sections.flatMap { it.items }.distinctBy { it.id },
            watchDownloadsCompleted = downloads.items.count { it.status == DownloadStatus.Completed },
            fullyWatchedSeries = series.size,
        )
    }
}

/* ───────────── 1. Continue ───────────── */

private data class VoyageContinueItem(
    val id: String,
    val title: String,
    val artwork: String?,
    val mode: VoyageMode,
    val subtitle: String,
    val progress: Float?,
    val remainingText: String?,
    val lastActivity: Long,
    val watch: ContinueWatchingItem? = null,
    val read: ReadContinue? = null,
)

@Composable
private fun ContinueScreen(actions: VoyageActions, onBack: () -> Unit, leave: () -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val watchItems by VoyageData.watchContinue.collectAsState()
    val progress by WatchProgressRepository.uiState.collectAsState()
    val read = readIfAllowed()
    val loading by VoyageData.readLoading.collectAsState()
    val items = remember(watchItems, progress, read) {
        val lastByVideo = progress.entries.associate { it.videoId to it.lastUpdatedEpochMs }
        val lastByParent = progress.entries.groupBy { it.parentMetaId }.mapValues { (_, v) -> v.maxOf { it.lastUpdatedEpochMs } }
        val w = watchItems.filter { !it.isReleaseAlert }.map { item ->
            val fraction = (item.resumeProgressFraction ?: item.progressFraction).takeIf { it.isFinite() && item.durationMs > 0 }?.coerceIn(0f, 1f)
            val leftMs = if (item.durationMs > 0) (item.durationMs - item.resumePositionMs).coerceAtLeast(0) else 0L
            val ep = if (item.seasonNumber != null && item.episodeNumber != null) "S${item.seasonNumber} E${item.episodeNumber}" else null
            VoyageContinueItem(
                id = "w:${item.parentMetaId}:${item.videoId}",
                title = item.title,
                artwork = item.poster ?: item.rawPosterUrl ?: item.imageUrl,
                mode = voyageModeForWatch(item.parentMetaId, item.parentMetaType),
                subtitle = listOfNotNull(ep, if (item.isNextUp) "Up next" else null).joinToString(" · ").ifBlank { item.subtitle },
                progress = fraction,
                remainingText = when {
                    item.durationMs <= 0 -> null
                    leftMs >= 3_600_000 -> "${leftMs / 3_600_000}h ${(leftMs % 3_600_000) / 60_000}m left"
                    else -> "${(leftMs / 60_000).coerceAtLeast(1)} min left"
                },
                lastActivity = lastByVideo[item.videoId] ?: lastByParent[item.parentMetaId] ?: 0L,
                watch = item,
            )
        }
        val r = read?.continueReading.orEmpty().map { c ->
            val n = c.chapterNumber
            val chapter = if (n >= 0) "Chapter " + (if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()) else "Keep reading"
            VoyageContinueItem(
                id = "r:${c.mangaId}",
                title = c.title,
                artwork = c.coverUrl,
                mode = VoyageMode.READ,
                subtitle = if (c.totalChapters > 0) "$chapter of ${c.totalChapters}" else chapter,
                progress = if (c.totalChapters > 0) (c.readCount.toFloat() / c.totalChapters).coerceIn(0f, 1f) else null,
                remainingText = if (c.isNovel) "Novel" else "Manga",
                lastActivity = c.readAt,
                read = c,
            )
        }
        (w + r).sortedByDescending { it.lastActivity }
    }
    val showReading = LocalShowReading.current
    val privacy by ReadingPrivacy.state.collectAsState()
    val privateOn = privacy.settings.keepReadingPrivate && LocalPrivacySurface.current != PrivacySurface.READ
    val scope = rememberCoroutineScope()
    var authError by remember { mutableStateOf<String?>(null) }
    val tabs = if (showReading) VoyageMode.entries else VoyageMode.entries.filter { it != VoyageMode.READ }
    // opens on the mode you came from (Read → Read, Ani-Drama → Ani-Drama, else Watch)
    var tab by remember {
        mutableStateOf(
            when {
                VoyageNav.fromRead -> VoyageMode.READ
                com.nuvio.app.mugi.MugiAniMode.on.value -> VoyageMode.ANI_DRAMA
                else -> VoyageMode.WATCH
            },
        )
    }
    if (tab !in tabs) tab = VoyageMode.ALL // relocked while on Read
    val shown = items.filter { tab == VoyageMode.ALL || it.mode == tab }
    val unlock: () -> Unit = {
        scope.launch { authError = ReadingPrivacy.requestUnlock() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item(key = "head") {
            Box {
                VoyageHeader("Continue", onBack, decoration = { VoyageShip(Modifier.size(width = 110.dp, height = 90.dp)) })
                if (privateOn) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = if (showReading) "Lock reading again" else "Unlock reading") {
                                if (showReading) ReadingPrivacy.lock() else unlock()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(38.dp).clip(CircleShape).border(1.dp, VoyageColors.Gold, CircleShape), contentAlignment = Alignment.Center) {
                            Text(if (showReading) "🔓" else "🔒", fontSize = 16.sp)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, VoyageColors.Gold, RoundedCornerShape(22.dp)),
            ) {
                tabs.forEach { m ->
                    val on = m == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (on) {
                                    when (m) {
                                        VoyageMode.WATCH -> Brush.verticalGradient(listOf(Color(0xFFC92D35), Color(0xFF7A141B)))
                                        VoyageMode.ANI_DRAMA -> Brush.verticalGradient(listOf(Color(0xFF993BDF), Color(0xFF5A1E8C)))
                                        else -> Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFA87832)))
                                    }
                                } else {
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                },
                            )
                            .clickable { tab = m },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            m.label,
                            color = if (on && (m == VoyageMode.ALL || m == VoyageMode.READ)) Color(0xFF2B1A0C) else VoyageColors.Ivory,
                            fontFamily = VoyageSerif,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                VoyageEmpty(
                    if (loading) "Gathering your voyage…" else "Nothing to continue yet",
                    "Start an episode, a film or a chapter and it will wait for you here.",
                )
            }
        }
        items(shown, key = { it.id }) { item ->
            ContinueCard(item) {
                val w = item.watch
                val r = item.read
                if (w != null) {
                    leave()
                    actions.onContinueWatchingClick?.invoke(w)
                } else if (r != null) {
                    VoyageNav.close()
                    VoyageBridge.resumeRead?.invoke(r.mangaId, r.chapterId)
                }
            }
        }
        if (privateOn) {
            // shown whether or not anything is hidden, and never says how many
            item(key = "private-footer") {
                PrivateReadingFooter(showReading, authError, onUnlock = unlock, onLock = { ReadingPrivacy.lock() })
            }
        }
    }
}

@Composable
private fun PrivateReadingFooter(unlocked: Boolean, error: String?, onUnlock: () -> Unit, onLock: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(40.dp).height(1.dp).background(VoyageColors.Gold.copy(alpha = 0.5f)))
            Spacer(Modifier.width(10.dp))
            Text(if (unlocked) "🔓" else "🔒", fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                if (unlocked) "Reading shown for a few minutes" else "Reading is private",
                color = MugiPrivacyColors.Ivory,
                fontFamily = VoyageSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
            Text(
                if (unlocked) "Lock again" else "Unlock",
                color = MugiPrivacyColors.BrightGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = if (unlocked) onLock else onUnlock).padding(horizontal = 8.dp, vertical = 12.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(40.dp).height(1.dp).background(VoyageColors.Gold.copy(alpha = 0.5f)))
        }
        error?.let { Text(it, color = Color(0xFFF2B8A8), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp)) }
    }
}

@Composable
private fun ContinueCard(item: VoyageContinueItem, onResume: () -> Unit) {
    val accent = item.mode.accent()
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp).ropeFrame().padding(9.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x99081218)).clickable(onClick = onResume).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(92.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).border(1.dp, VoyageColors.Gold, RoundedCornerShape(8.dp)).background(Color(0xFF101D27))) {
                item.artwork?.let { AsyncImage(model = it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val label = when (item.mode) {
                    VoyageMode.READ -> "📖 READ · ${item.remainingText?.uppercase() ?: ""}".trimEnd(' ', '·')
                    VoyageMode.ANI_DRAMA -> "▶ ANI-DRAMA"
                    else -> "▶ WATCH"
                }
                Text(label, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text(item.title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.subtitle.takeIf { it.isNotBlank() }, item.remainingText.takeIf { item.mode != VoyageMode.READ }).joinToString(" · "),
                    color = VoyageColors.Secondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                )
                item.progress?.let { VoyageProgressBar(it, item.mode, Modifier.padding(top = 2.dp)) }
                Spacer(Modifier.height(6.dp))
                VoyageButton("Resume ▶", if (item.mode == VoyageMode.READ) VoyageColors.Read else if (item.mode == VoyageMode.ANI_DRAMA) VoyageColors.Purple else VoyageColors.Burgundy, Modifier.align(Alignment.End), onClick = onResume)
            }
        }
    }
}

/* ───────────── 2. Captain's Log ───────────── */

@Composable
private fun CaptainsLogScreen(onBack: () -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val inputs = rememberInputs()
    var mode by remember { mutableStateOf(VoyageMode.ALL) }
    var range by remember { mutableStateOf(VoyageRange.WEEK) }
    val loading by VoyageData.readLoading.collectAsState()
    val showReading = LocalShowReading.current
    if (!showReading && mode == VoyageMode.READ) mode = VoyageMode.ALL
    val stats = remember(inputs, mode, range) { captainStats(inputs, mode, range) }
    val board = remember(inputs, showReading) { bountyBoard(inputs).filter { showReading || !it.definition.isReading() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item(key = "head") {
            VoyageHeader(
                "Captain's Log",
                onBack,
                subtitle = if (showReading) "Your reading and watching, kept on this phone only" else "🔒 Watching only · reading is private",
                decoration = { VoyageShip(Modifier.size(width = 96.dp, height = 84.dp)) },
            )
        }
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                VoyageModeChips(mode, { mode = it }, modes = if (showReading) VoyageMode.entries else VoyageMode.entries.filter { it != VoyageMode.READ })
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VoyageRange.entries.forEach { r -> VoyagePill(r.label, r == range, VoyageColors.Gold, Modifier.weight(1f)) { range = r } }
                }
            }
        }
        if (!stats.hasAnyData) {
            item(key = "empty") {
                VoyageEmpty(
                    if (loading) "Opening the log…" else "Your log is still blank",
                    "Read a chapter or watch something and your voyage starts here. Watching time is counted from now on.",
                )
            }
        }
        item(key = "streak") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).woodPanel().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("CURRENT VOYAGE", color = VoyageColors.Gold, fontFamily = VoyageDisplay, fontSize = 13.sp, letterSpacing = 3.sp)
                Text(
                    "🔥 ${stats.currentStreak} ${if (stats.currentStreak == 1) "day" else "days"}",
                    color = VoyageColors.BrightGold,
                    fontFamily = VoyageSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                )
                Text("Best voyage: ${stats.bestStreak} ${if (stats.bestStreak == 1) "day" else "days"}", color = VoyageColors.Ivory, fontSize = 13.sp)
            }
        }
        item(key = "stats") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).woodPanel().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    VoyageStatCell(stats.chaptersRead.toString(), "chapters")
                    VoyageStatCell(formatMinutes(stats.readingMinutes), "reading")
                    VoyageStatCell(formatMinutes(stats.watchingMinutes), "watching")
                }
                Row(Modifier.fillMaxWidth()) {
                    VoyageStatCell(stats.booksCompleted.toString(), "books\nfinished")
                    VoyageStatCell(stats.moviesCompleted.toString(), "movies")
                    VoyageStatCell(stats.episodesCompleted.toString(), "TV\nepisodes")
                    VoyageStatCell(stats.animeEpisodesCompleted.toString(), "anime\nepisodes")
                }
            }
        }
        item(key = "week-title") { VoyageSectionTitle("This week") }
        item(key = "week") { WeekChart(stats.weeklyActivity) }
        item(key = "genre") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).woodPanel().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚓", fontSize = 24.sp, color = VoyageColors.Gold)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stats.favoriteGenre?.let { "Top genre: $it" } ?: "Top genre: not enough yet", color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    stats.favoriteGenreShare?.let { Text("$it% of your ${range.label.lowercase()}", color = VoyageColors.Secondary, fontSize = 12.5.sp) }
                }
            }
        }
        item(key = "bounties") {
            val earned = board.count { it.unlocked }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .ropeFrame(18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { VoyageNav.open(VoyageScreen.BOUNTIES) }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$earned ${if (earned == 1) "Milestone" else "Milestones"} Earned", color = VoyageColors.BrightGold, fontFamily = VoyageDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("See your bounties ›", color = VoyageColors.Ivory, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun WeekChart(days: List<DailyVoyageActivity>) {
    val max = (days.maxOfOrNull { it.readingMinutes + it.watchingMinutes } ?: 0).coerceAtLeast(30)
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    ParchmentPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(190.dp)) {
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
                days.forEach { d ->
                    val total = d.readingMinutes + d.watchingMinutes
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                        if (total > 0) Text(formatMinutes(total.toLong()), color = Color(0xFF4A2E16), fontSize = 9.sp)
                        val frac = total.toFloat() / max
                        Box(
                            Modifier
                                .width(20.dp)
                                .fillMaxHeight(frac.coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFFF6D88E), Color(0xFFA87832)))),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF6E4A22)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                days.forEach { d -> Text(labels[weekdayOf(d.day)], color = Color(0xFF3A2210), fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}

/* ───────────── 3. Bounties ───────────── */

@Composable
private fun BountiesScreen(onBack: () -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val inputs = rememberInputs()
    val showReading = LocalShowReading.current
    val board = remember(inputs, showReading) { bountyBoard(inputs).filter { showReading || !it.definition.isReading() } }
    var open by remember { mutableStateOf<BountyProgress?>(null) }
    val total = board.filter { it.unlocked }.sumOf { it.definition.reward }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = when {
            maxWidth >= 840.dp -> 5
            maxWidth >= 600.dp -> 4
            else -> 3
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
            item(key = "head") { VoyageHeader("Bounties", onBack, subtitle = "Milestones you've earned across Watch and Read", decoration = { VoyageWheel(Modifier.size(84.dp)) }) }
            item(key = "total") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).ropeFrame(22.dp).padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val earned = board.count { it.unlocked }
                    Text("$earned ${if (earned == 1) "Milestone" else "Milestones"} Earned", color = VoyageColors.BrightGold, fontFamily = VoyageDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Total bounty: ฿ ${formatBounty(total)}", color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontSize = 14.sp)
                    Text("Bounty points are just for fun: they have no money value.", color = VoyageColors.Secondary, fontSize = 11.sp)
                }
            }
            items(board.chunked(cols), key = { row -> row.first().definition.id }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { p -> WantedPoster(p, Modifier.weight(1f)) { open = p } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    open?.let { p ->
        Dialog(onDismissRequest = { open = null }) {
            Column(
                Modifier.fillMaxWidth().woodPanel(16.dp).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WantedPoster(p, Modifier.width(150.dp)) { }
                Text(p.definition.name, color = VoyageColors.BrightGold, fontFamily = VoyageDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(p.definition.description, color = VoyageColors.Ivory, fontSize = 14.sp, textAlign = TextAlign.Center)
                Text("Progress: ${p.current.coerceAtMost(p.definition.target)} / ${p.definition.target}", color = VoyageColors.Secondary, fontSize = 13.sp)
                Text(
                    p.unlockedAt?.let { "Earned on ${formatDay(VoyageBridge.localDay(it))}" } ?: "Not earned yet",
                    color = if (p.unlocked) VoyageColors.Gold else VoyageColors.Secondary,
                    fontFamily = VoyageSerif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                )
                Text("Bounty: ฿ ${formatBounty(p.definition.reward)}", color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontSize = 15.sp)
                VoyageButton("Close", VoyageColors.Gold) { open = null }
            }
        }
    }
}

private fun formatBounty(v: Long): String = v.toString().reversed().chunked(3).joinToString(",").reversed()

/** Local day number → "Nov 28, 2026". */
fun formatDay(day: Long): String {
    // civil-from-days (Howard Hinnant)
    val z = day + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${names[(m - 1).toInt()]} $d, $year"
}

/* ───────────── 4. Offline Voyage ───────────── */

private data class PrepTarget(val key: String, val title: String, val artwork: String?, val mode: VoyageMode, val next: String, val watch: ContinueWatchingItem? = null, val readMangaId: Long? = null)

@Composable
private fun OfflineVoyageScreen(actions: VoyageActions, onBack: () -> Unit, leave: () -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val watchItems by VoyageData.watchContinue.collectAsState()
    val read = readIfAllowed()
    val downloads by DownloadsRepository.uiState.collectAsState()
    val readQueueFlow = remember { VoyageBridge.readDownloads ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val readQueueAll by readQueueFlow.collectAsState(emptyList())
    val readQueue = if (LocalShowReading.current) readQueueAll else emptyList()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val targets = remember(watchItems, read) {
        val w = watchItems.filter { !it.isReleaseAlert && it.seasonNumber != null }.distinctBy { it.parentMetaId }.map {
            PrepTarget("w:${it.parentMetaId}", it.title, it.poster ?: it.rawPosterUrl ?: it.imageUrl, voyageModeForWatch(it.parentMetaId, it.parentMetaType),
                "Next up: S${it.seasonNumber} E${it.episodeNumber}", watch = it)
        }
        val r = read?.continueReading.orEmpty().filter { it.totalChapters == 0L || it.readCount < it.totalChapters }.map {
            PrepTarget("r:${it.mangaId}", it.title, it.coverUrl, VoyageMode.READ, if (it.totalChapters > 0) "${it.totalChapters - it.readCount} chapters left" else "Keep reading", readMangaId = it.mangaId)
        }
        r + w
    }
    var pickedKey by remember { mutableStateOf<String?>(null) }
    val picked = targets.firstOrNull { it.key == pickedKey } ?: targets.firstOrNull()
    val counts = if (picked?.mode == VoyageMode.READ) listOf(5, 10, 20) else listOf(3, 5, 10)
    var countIndex by remember { mutableIntStateOf(1) }
    val free = remember { VoyageBridge.freeBytes?.invoke() }
    val usedWatch = downloads.items.filter { it.status == DownloadStatus.Completed }.sumOf { it.totalBytes ?: it.downloadedBytes }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item(key = "head") { VoyageHeader("Offline Voyage", onBack, decoration = { VoyageShip(Modifier.size(width = 110.dp, height = 90.dp)) }) }
        item(key = "prep") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xCC0E1B26)).border(1.dp, VoyageColors.Gold, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (picked == null) {
                    Text("Start a show or a book first; then you can get its next episodes or chapters ready here.", color = VoyageColors.Secondary, fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(70.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).border(1.dp, VoyageColors.Gold, RoundedCornerShape(8.dp)).background(Color(0xFF101D27))) {
                            picked.artwork?.let { AsyncImage(model = it, contentDescription = picked.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(picked.title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2)
                            Text(picked.next, color = VoyageColors.Secondary, fontSize = 13.sp)
                            Text(picked.mode.label, color = picked.mode.accent(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (targets.size > 1) {
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Change:", color = VoyageColors.Secondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(targets, key = { it.key }) { t ->
                                    VoyagePill(t.title.take(18), t.key == picked.key, t.mode.accent()) { pickedKey = t.key; countIndex = 1 }
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(VoyageColors.Gold.copy(alpha = 0.3f)))
                    Text("Download the next", color = VoyageColors.Ivory, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        counts.forEachIndexed { i, n -> VoyagePill("$n", i == countIndex, VoyageColors.Burgundy) { countIndex = i } }
                        Text(if (picked.mode == VoyageMode.READ) "chapters" else "episodes", color = VoyageColors.Secondary, fontSize = 13.sp)
                    }
                    Text(
                        buildString {
                            append(if (picked.mode == VoyageMode.READ) "Quality: as the source gives it" else "Quality: the source you pick")
                            append(" · Size: unknown until it starts")
                            free?.let { append(" · Free: ${formatBytes(it)}") }
                        },
                        color = VoyageColors.Secondary,
                        fontSize = 12.sp,
                    )
                    Text("Wi-Fi only follows your download settings.", color = VoyageColors.Secondary, fontSize = 12.sp)
                    VoyageButton("⤓ Prepare for the trip", VoyageColors.Burgundy, Modifier.fillMaxWidth()) {
                        val n = counts[countIndex.coerceIn(0, counts.lastIndex)]
                        val mangaId = picked.readMangaId
                        if (mangaId != null) {
                            scope.launch {
                                val queued = runCatching { VoyageBridge.downloadNextChapters?.invoke(mangaId, n) }.getOrNull()
                                message = when {
                                    queued == null -> "Reading downloads aren't available right now."
                                    queued == 0 -> "Those chapters are already downloaded or queued."
                                    else -> "Queued $queued ${if (queued == 1) "chapter" else "chapters"} for ${picked.title}."
                                }
                            }
                        } else {
                            // Watch: each episode needs a source; the show's page has the episodes and their sources
                            val w = picked.watch
                            if (w != null) {
                                leave()
                                actions.onPosterClick?.invoke(MetaPreview(id = w.parentMetaId, type = w.parentMetaType, name = w.title, poster = w.poster))
                            }
                        }
                    }
                    if (picked.mode != VoyageMode.READ) {
                        Text("Episodes download one by one: pick a source on each episode and it appears below.", color = VoyageColors.Secondary, fontSize = 11.5.sp)
                    }
                    message?.let { Text(it, color = VoyageColors.Gold, fontSize = 13.sp) }
                }
            }
        }
        item(key = "storage") {
            Text(
                "Watch downloads use ${formatBytes(usedWatch)}" + (free?.let { " · ${formatBytes(it)} free on this phone" } ?: ""),
                color = VoyageColors.Secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        // in progress (both sides)
        val active = downloads.items.filter { it.status != DownloadStatus.Completed }
        if (active.isNotEmpty() || readQueue.isNotEmpty()) {
            item(key = "active-title") { VoyageSectionTitle("On the way") }
        }
        items(active, key = { "wa:${it.id}" }) { d -> WatchDownloadRow(d) }
        if (readQueue.isNotEmpty()) {
            item(key = "rq-actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoyagePill("Pause reading downloads", false) { VoyageBridge.pauseReadDownloads?.invoke() }
                    VoyagePill("Resume", false) { VoyageBridge.resumeReadDownloads?.invoke() }
                }
            }
        }
        items(readQueue, key = { "rq:${it.chapterId}" }) { d ->
            OfflineRow(
                title = d.title,
                line = "${d.chapterName} · ${d.state.label()}" + if (d.state == OfflineItemState.DOWNLOADING) " ${d.progress}%" else "",
                artwork = null,
                mode = VoyageMode.READ,
                progress = d.progress / 100f,
                actions = listOf("Cancel" to { VoyageBridge.cancelReadDownload?.invoke(d.chapterId); Unit }),
            )
        }
        // ready
        val readyWatch = downloads.items.filter { it.status == DownloadStatus.Completed }.groupBy { it.parentMetaId }
        val readyRead = read?.books.orEmpty().filter { it.downloadCount > 0 }
        item(key = "ready-title") { VoyageSectionTitle("Ready offline") }
        if (readyWatch.isEmpty() && readyRead.isEmpty()) {
            item(key = "ready-empty") { VoyageEmpty("Nothing on board yet", "Downloads you finish show up here, ready without internet.") }
        }
        items(readyWatch.entries.toList(), key = { "rw:${it.key}" }) { (_, list) ->
            val first = list.first()
            val eps = list.mapNotNull { e -> e.episodeNumber?.let { "E$it" } }
            OfflineRow(
                title = first.title,
                line = if (first.isEpisode) "${list.size} episodes ready" + (eps.takeIf { it.isNotEmpty() }?.let { " · ${it.first()}–${it.last()}" } ?: "") else "Ready to watch",
                artwork = first.poster,
                mode = voyageModeForWatch(first.parentMetaId, first.parentMetaType),
                progress = null,
                actions = listOf("Delete" to { list.forEach { DownloadsRepository.cancelDownload(it.id) } }),
                ready = true,
            )
        }
        items(readyRead, key = { "rr:${it.mangaId}" }) { b ->
            OfflineRow(
                title = b.title,
                line = "${b.downloadCount} ${if (b.downloadCount == 1L) "chapter" else "chapters"} ready",
                artwork = b.coverUrl,
                mode = VoyageMode.READ,
                progress = null,
                actions = listOf("Manage" to { VoyageNav.close(); VoyageBridge.openReadBook?.invoke(b.mangaId); Unit }),
                ready = true,
            )
        }
        item(key = "manage") {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoyageButton("All Watch downloads", VoyageColors.Navy.copy(red = 0.1f), Modifier.weight(1f)) { leave(); actions.onDownloadsClick() }
                if (VoyageBridge.openReadDownloadQueue != null) {
                    VoyageButton("Reading queue", VoyageColors.Read, Modifier.weight(1f)) { VoyageNav.close(); VoyageBridge.openReadDownloadQueue?.invoke() }
                }
            }
        }
    }
}

private fun OfflineItemState.label(): String = when (this) {
    OfflineItemState.QUEUED -> "Queued"
    OfflineItemState.DOWNLOADING -> "Downloading"
    OfflineItemState.PAUSED -> "Paused"
    OfflineItemState.COMPLETED -> "Ready"
    OfflineItemState.FAILED -> "Failed"
}

@Composable
private fun WatchDownloadRow(d: DownloadItem) {
    val frac = d.totalBytes?.takeIf { it > 0 }?.let { (d.downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    val state = when (d.status) {
        DownloadStatus.Downloading -> "Downloading" + (frac?.let { " ${(it * 100).toInt()}%" } ?: "")
        DownloadStatus.Paused -> "Paused"
        DownloadStatus.Failed -> "Failed"
        DownloadStatus.Completed -> "Ready"
    }
    val acts = buildList<Pair<String, () -> Unit>> {
        when (d.status) {
            DownloadStatus.Downloading -> add("Pause" to { DownloadsRepository.pauseDownload(d.id) })
            DownloadStatus.Paused -> add("Resume" to { DownloadsRepository.resumeDownload(d.id) })
            DownloadStatus.Failed -> add("Retry" to { DownloadsRepository.retryDownload(d.id) })
            else -> Unit
        }
        add("Cancel" to { DownloadsRepository.cancelDownload(d.id) })
    }
    OfflineRow(
        title = d.title,
        line = listOfNotNull(if (d.isEpisode) "S${d.seasonNumber} E${d.episodeNumber}" else null, state, d.totalBytes?.let { formatBytes(it) } ?: "Unknown size").joinToString(" · "),
        artwork = d.poster,
        mode = voyageModeForWatch(d.parentMetaId, d.parentMetaType),
        progress = frac,
        actions = acts,
    )
}

@Composable
private fun OfflineRow(
    title: String,
    line: String,
    artwork: String?,
    mode: VoyageMode,
    progress: Float?,
    actions: List<Pair<String, () -> Unit>>,
    ready: Boolean = false,
) {
    val accent = mode.accent()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (mode == VoyageMode.READ) Color(0xCC2A1A0E) else Color(0xCC0E1B26))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).border(1.dp, VoyageColors.Gold, RoundedCornerShape(6.dp)).background(Color(0xFF101D27))) {
            artwork?.let { AsyncImage(model = it, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = VoyageColors.Secondary, fontSize = 12.sp, maxLines = 2)
            progress?.let {
                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(it).fillMaxHeight().background(accent))
                }
            }
            if (actions.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { (label, act) ->
                        Text(
                            label,
                            color = VoyageColors.Gold,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, VoyageColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).clickable(onClick = act).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (ready) Text("✓", color = VoyageColors.Gold, fontSize = 18.sp)
    }
}

fun formatBytes(b: Long): String = when {
    b <= 0 -> "0 MB"
    b >= 1L shl 30 -> "${((b * 10) / (1L shl 30)) / 10.0} GB"
    else -> "${b / (1L shl 20)} MB"
}

/* ───────────── 5. Surprise Me ───────────── */

@Composable
private fun SurpriseScreen(actions: VoyageActions, onBack: () -> Unit, leave: () -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    var mode by remember { mutableStateOf(if (com.nuvio.app.mugi.MugiAniMode.on.value) VoyageMode.ANI_DRAMA else VoyageMode.ALL) }
    var source by remember { mutableStateOf(SurpriseSource.MY_LIST) }
    var maxMinutes by remember { mutableStateOf<Int?>(null) }
    var opened by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<SurpriseCandidate?>(null) }
    var noMatch by remember { mutableStateOf(false) }
    val seen = remember { mutableSetOf<String>() }
    val read = readIfAllowed()
    val library by LibraryRepository.uiState.collectAsState()
    val watched by WatchedRepository.uiState.collectAsState()
    val series by WatchedRepository.fullyWatchedSeriesKeys.collectAsState()
    val home by HomeRepository.uiState.collectAsState()
    val discover by SearchRepository.discoverUiState.collectAsState()
    var aniLatest by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val reduceMotion = remember { com.nuvio.app.mugi.MugiStyle.reduceMotion() }
    LaunchedEffect(source, mode) {
        if (source == SurpriseSource.DISCOVER && (mode == VoyageMode.ANI_DRAMA || mode == VoyageMode.ALL) && aniLatest.isEmpty()) {
            aniLatest = runCatching {
                com.nuvio.app.features.anidrama.AnimeRepository.airingLatest()?.items.orEmpty().map { it.toMetaPreview() }
            }.getOrDefault(emptyList())
        }
    }
    val candidates = remember(source, read, library, watched, series, home, discover, aniLatest) {
        val watchedIds = watched.items.map { it.id }.toSet()
        fun MetaPreview.toCandidate(): SurpriseCandidate {
            val m = voyageMode()
            val done = (type.equals("movie", true) && id in watchedIds) || series.any { it.contains(id) }
            return SurpriseCandidate(id = "m:$id", title = name, artwork = poster, mode = m, genre = genres.firstOrNull(),
                durationMinutes = parseRuntimeMinutes(runtime), completed = done, available = true, meta = this,
                caption = listOfNotNull(genres.firstOrNull(), releaseInfo).joinToString(" · "))
        }
        val books = read?.books.orEmpty()
        when (source) {
            SurpriseSource.MY_LIST -> {
                val w = library.sections.flatMap { it.items }.distinctBy { it.id }.map { li ->
                    val meta = MetaPreview(id = li.id, type = li.type, name = li.name, poster = li.poster, releaseInfo = li.releaseInfo, genres = li.genres)
                    meta.toCandidate()
                }
                val r = books.filter { it.favorite && (it.totalChapters == 0L || it.readCount < it.totalChapters) }.map { b ->
                    SurpriseCandidate(id = "b:${b.mangaId}", title = b.title, artwork = b.coverUrl, mode = VoyageMode.READ, genre = b.genres.firstOrNull(),
                        durationMinutes = null, completed = false, available = true, readMangaId = b.mangaId,
                        caption = listOfNotNull(b.genres.firstOrNull(), if (b.isNovel) "Novel" else "Manga", if (b.readCount > 0) "Ch. ${b.readCount + 1}" else "Not started").joinToString(" · "))
                }
                w + r
            }
            SurpriseSource.DISCOVER -> {
                val metas = (home.heroItems + home.sections.flatMap { it.items } + discover.items + aniLatest).distinctBy { it.id }
                val r = books.filter { it.readCount == 0L }.map { b ->
                    SurpriseCandidate(id = "b:${b.mangaId}", title = b.title, artwork = b.coverUrl, mode = VoyageMode.READ, genre = b.genres.firstOrNull(),
                        durationMinutes = null, completed = false, available = true, readMangaId = b.mangaId,
                        caption = listOfNotNull(b.genres.firstOrNull(), "From your shelf, not started").joinToString(" · "))
                }
                metas.map { it.toCandidate() } + r
            }
        }
    }
    val showReading = LocalShowReading.current
    if (!showReading && mode == VoyageMode.READ) mode = VoyageMode.ALL
    if (!showReading && pick?.mode == VoyageMode.READ) { pick = null; opened = false }
    val openChest: () -> Unit = {
        val c = chooseSurprise(candidates, mode, maxMinutes, seen)
        if (c == null) {
            noMatch = true
        } else {
            seen += c.id
            pick = c
            noMatch = false
            opened = true
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item(key = "head") { VoyageHeader("Surprise Me", onBack, decoration = { CompassRose(Modifier.size(90.dp)) }) }
        item(key = "mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VoyageSectionTitle("Choose a mode")
                VoyageModeChips(mode, { mode = it; opened = false; pick = null }, modes = if (showReading) VoyageMode.entries else VoyageMode.entries.filter { it != VoyageMode.READ })
                VoyageSectionTitle("Source")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VoyagePill("✓ My List".takeIf { source == SurpriseSource.MY_LIST } ?: "My List", source == SurpriseSource.MY_LIST, VoyageColors.Gold, Modifier.weight(1f)) { source = SurpriseSource.MY_LIST; opened = false; pick = null }
                    VoyagePill("Discover", source == SurpriseSource.DISCOVER, VoyageColors.Gold, Modifier.weight(1f)) { source = SurpriseSource.DISCOVER; opened = false; pick = null }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Int?>(null, 15, 30, 60).forEach { m ->
                        VoyagePill(m?.let { "$it min" } ?: "Any length", maxMinutes == m, VoyageColors.Gold, Modifier.weight(1f)) { maxMinutes = m; opened = false; pick = null }
                    }
                }
            }
        }
        item(key = "chest") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SurpriseChestAnimation(opened = opened, reduceMotion = reduceMotion, onClick = openChest)
                if (!opened) {
                    VoyageButton("Open the chest", VoyageColors.Gold, Modifier.padding(horizontal = 40.dp).fillMaxWidth(), onClick = openChest)
                }
                Text("Find your next adventure", color = VoyageColors.Secondary, fontFamily = VoyageSerif, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (noMatch) {
            item(key = "nomatch") {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    VoyageEmpty(
                        "The chest is empty",
                        if (maxMinutes != null) "Nothing here has a known length that short. Try another length or clear the filters." else "Nothing matches these filters yet. Clear them or try Discover.",
                    )
                    VoyageButton("Clear filters", VoyageColors.Gold) { mode = VoyageMode.ALL; maxMinutes = null; seen.clear(); noMatch = false }
                }
            }
        }
        item(key = "result") {
            AnimatedVisibility(
                visible = opened && pick != null,
                enter = fadeIn(tween(if (reduceMotion) 0 else 250, delayMillis = if (reduceMotion) 0 else 750)) +
                    slideInVertically(tween(if (reduceMotion) 0 else 250, delayMillis = if (reduceMotion) 0 else 750)) { it / 3 },
            ) {
                val p = pick ?: return@AnimatedVisibility
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).ropeFrame().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).border(1.dp, if (p.mode == VoyageMode.ANI_DRAMA) VoyageColors.Purple else VoyageColors.Gold, RoundedCornerShape(8.dp)).background(Color(0xFF101D27))) {
                        p.artwork?.let { AsyncImage(model = it, contentDescription = p.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        p.caption?.takeIf { it.isNotBlank() }?.let { Text(it, color = VoyageColors.Secondary, fontSize = 12.5.sp, maxLines = 2) }
                        p.durationMinutes?.let { Text("$it min", color = VoyageColors.Secondary, fontSize = 12.5.sp) }
                        Text(
                            p.mode.label.uppercase(),
                            color = VoyageColors.Ivory,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(p.mode.accent()).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoyagePill("View details", false, VoyageColors.Gold) {
                                val meta = p.meta
                                val book = p.readMangaId
                                if (meta != null) {
                                    leave()
                                    actions.onPosterClick?.invoke(meta)
                                } else if (book != null) {
                                    VoyageNav.close()
                                    VoyageBridge.openReadBook?.invoke(book)
                                }
                            }
                            VoyagePill("↻ Try again", false, VoyageColors.Gold) {
                                opened = false
                                pick = null
                                scope.launch {
                                    kotlinx.coroutines.delay(if (reduceMotion) 0 else 320)
                                    openChest()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassRose(modifier: Modifier = Modifier) {
    BountyEmblemArt(BountyEmblem.COMPASS, earned = true, modifier = modifier.alpha(0.6f))
}


/* ───────────── Privacy ───────────── */

@Composable
fun PrivacySettingCard(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MugiPrivacyColors.Surface)
            .border(1.dp, MugiPrivacyColors.Gold, shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MugiPrivacyColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(description, color = MugiPrivacyColors.Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MugiPrivacyColors.Ivory,
                checkedTrackColor = MugiPrivacyColors.Gold,
                uncheckedThumbColor = MugiPrivacyColors.Secondary,
                uncheckedTrackColor = Color(0xFF29343D),
            ),
        )
    }
}

@Composable
private fun PrivacyScreen(onBack: () -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val privacy by ReadingPrivacy.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val s = privacy.settings
    fun apply(next: ReadingPrivacySettings) {
        scope.launch { error = ReadingPrivacy.update(next) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item(key = "head") { VoyageHeader("Privacy", onBack, decoration = { VoyageLantern(Modifier.padding(top = 18.dp, end = 18.dp).size(width = 46.dp, height = 96.dp)) }) }
        item(key = "cards") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PrivacySettingCard(
                    "Keep my reading private",
                    "Hide books from Continue, Surprise Me and Captain's Log outside Read mode.",
                    s.keepReadingPrivate,
                    { apply(s.copy(keepReadingPrivate = it)) },
                )
                PrivacySettingCard(
                    "Ask to unlock",
                    "Require fingerprint, PIN or pattern before showing private reading again.",
                    s.requireAuthentication,
                    { apply(s.copy(requireAuthentication = it)) },
                    enabled = s.keepReadingPrivate,
                )
                Text(
                    "Books remain available in Read. Incognito (no new reading history) is in Read → More.",
                    color = MugiPrivacyColors.Secondary,
                    fontSize = 13.sp,
                )
                error?.let { Text(it, color = Color(0xFFF2B8A8), fontSize = 13.sp) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .woodPanel(14.dp)
                        .clickable { VoyageNav.open(VoyageScreen.LOG) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📖", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Captain's Log", color = Color(0xFFF2D592), fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (privacy.canShowReading(PrivacySurface.WATCH)) "Watching and reading" else "Watching only", color = VoyageColors.Secondary, fontSize = 13.sp)
                    }
                    Text("›", color = VoyageColors.Gold, fontSize = 24.sp)
                }
            }
        }
    }
}
