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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.ui.draw.drawBehind
import nuvio.composeapp.generated.resources.parchment_chart
import nuvio.composeapp.generated.resources.surprise_nautical_map
import nuvio.composeapp.generated.resources.mugi_chest_open_front
import nuvio.composeapp.generated.resources.surprise_card_parchment
import nuvio.composeapp.generated.resources.surprise_button_red
import nuvio.composeapp.generated.resources.surprise_button_blue
import nuvio.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
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
                VoyageScreen.APPEARANCE -> AppearanceMotionScreen(back, bottom)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // the little sailing ship instead of a flame: days in a row at sea
                    VoyageShip(Modifier.size(38.dp), small = true)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${stats.currentStreak} ${if (stats.currentStreak == 1) "day" else "days"}",
                        color = VoyageColors.BrightGold,
                        fontFamily = VoyageSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                    )
                }
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
                    stats.favoriteGenreShare?.let { Text("$it% of your ${range.phrase}", color = VoyageColors.Secondary, fontSize = 12.5.sp) }
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
    val ink = Color(0xFF3A2210)
    val empty = days.all { it.readingMinutes + it.watchingMinutes <= 0 }
    ParchmentPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(210.dp)) {
        Column(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 18.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
                    days.forEach { d ->
                        val total = d.readingMinutes + d.watchingMinutes
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (total > 0) {
                                Text(formatMinutes(total.toLong()), color = ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    Modifier
                                        .width(18.dp)
                                        .fillMaxHeight((total.toFloat() / max).coerceIn(0.04f, 0.85f))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(Brush.verticalGradient(listOf(Color(0xFFB0782E), Color(0xFF6E4219)))),
                                )
                            }
                        }
                    }
                }
                if (empty) {
                    Text(
                        "No reading or watching yet this week",
                        color = ink,
                        fontFamily = VoyageSerif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color(0xFF6E4A22)))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                days.forEach { d ->
                    Text(labels[weekdayOf(d.day)], color = ink, fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
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
        // two posters across on phones, more on tablets and unfolded foldables
        val cols = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
            item(key = "head") { VoyageHeader("Bounties", onBack, subtitle = "Complete challenges, earn rewards, and grow your pirate reputation!", decoration = { VoyageWheel(Modifier.size(84.dp)) }) }
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                WantedPoster(p, Modifier.width(180.dp)) { }
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Storage, contentDescription = null, tint = VoyageColors.Gold, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Storage", color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${formatBytes(usedWatch)} used" + (free?.let { " · ${formatBytes(it)} free" } ?: ""),
                        color = VoyageColors.Secondary,
                        fontSize = 12.5.sp,
                    )
                }
                val wifiGet = VoyageBridge.wifiOnly
                val wifiSet = VoyageBridge.setWifiOnly
                if (wifiGet != null && wifiSet != null) {
                    var wifiOnly by remember { mutableStateOf(wifiGet()) }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(VoyageColors.Gold.copy(alpha = 0.25f)))
                    Row(
                        Modifier.fillMaxWidth().clickable { wifiOnly = !wifiOnly; wifiSet(wifiOnly) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Wifi, contentDescription = null, tint = VoyageColors.Gold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Download over Wi-Fi only", color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Episodes and chapters wait for Wi-Fi instead of using mobile data", color = VoyageColors.Secondary, fontSize = 12.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = wifiOnly,
                            onCheckedChange = { wifiOnly = it; wifiSet(it) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = MugiPrivacyColors.Ivory,
                                checkedTrackColor = MugiPrivacyColors.Gold,
                                uncheckedThumbColor = MugiPrivacyColors.Secondary,
                                uncheckedTrackColor = Color(0xFF29343D),
                            ),
                        )
                    }
                }
            }
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
                line = d.chapterName,
                status = when (d.state) {
                    OfflineItemState.DOWNLOADING -> "Downloading… ${d.progress}%"
                    OfflineItemState.FAILED -> "Download failed"
                    else -> d.state.label()
                },
                statusColor = if (d.state == OfflineItemState.FAILED) Color(0xFFE5676C) else null,
                artwork = null,
                mode = VoyageMode.READ,
                progress = if (d.state == OfflineItemState.FAILED) null else d.progress / 100f,
                actions = listOf("Cancel" to { VoyageBridge.cancelReadDownload?.invoke(d.chapterId); Unit }),
                primary = when (d.state) {
                    OfflineItemState.DOWNLOADING, OfflineItemState.QUEUED -> OfflinePrimary.PAUSE to { VoyageBridge.pauseReadDownloads?.invoke(); Unit }
                    OfflineItemState.PAUSED -> OfflinePrimary.RESUME to { VoyageBridge.resumeReadDownloads?.invoke(); Unit }
                    // the reading queue retries failed chapters when it starts again
                    OfflineItemState.FAILED -> OfflinePrimary.RETRY to { VoyageBridge.resumeReadDownloads?.invoke(); Unit }
                    OfflineItemState.COMPLETED -> null
                },
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
                line = if (first.isEpisode) "${list.size} episodes ready" + (eps.takeIf { it.isNotEmpty() }?.let { " · ${it.first()}–${it.last()}" } ?: "") else "Movie",
                status = "Downloaded · ${formatBytes(list.sumOf { it.totalBytes ?: it.downloadedBytes })}",
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
                status = "Downloaded",
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
    val total = d.totalBytes?.takeIf { it > 0 }
    val frac = total?.let { (d.downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    val sizes = if (total != null) "${formatBytes(d.downloadedBytes)} / ${formatBytes(total)}" else formatBytes(d.downloadedBytes)
    val status = when (d.status) {
        DownloadStatus.Downloading -> "Downloading… " + (frac?.let { "${(it * 100).toInt()}% ($sizes)" } ?: sizes)
        DownloadStatus.Paused -> d.errorMessage?.takeIf { it.isNotBlank() } ?: "Paused · $sizes"
        DownloadStatus.Failed -> "Download failed" + (d.errorMessage?.takeIf { it.isNotBlank() }?.let { " · ${it.take(60)}" } ?: "")
        DownloadStatus.Completed -> "Downloaded · ${formatBytes(total ?: d.downloadedBytes)}"
    }
    val primary = when (d.status) {
        DownloadStatus.Downloading -> OfflinePrimary.PAUSE to { DownloadsRepository.pauseDownload(d.id) }
        DownloadStatus.Paused -> OfflinePrimary.RESUME to { DownloadsRepository.resumeDownload(d.id) }
        DownloadStatus.Failed -> OfflinePrimary.RETRY to { DownloadsRepository.retryDownload(d.id) }
        DownloadStatus.Completed -> null
    }
    OfflineRow(
        title = d.title,
        line = if (d.isEpisode) "S${d.seasonNumber} • E${d.episodeNumber}" + (d.episodeTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") else "Movie" + (total?.let { " • ${formatBytes(it)}" } ?: ""),
        status = status,
        statusColor = if (d.status == DownloadStatus.Failed) Color(0xFFE5676C) else null,
        artwork = d.poster,
        mode = voyageModeForWatch(d.parentMetaId, d.parentMetaType),
        progress = if (d.status == DownloadStatus.Failed) null else frac,
        actions = listOf("Cancel" to { DownloadsRepository.cancelDownload(d.id) }),
        primary = primary,
    )
}

private enum class OfflinePrimary { PAUSE, RESUME, RETRY }

@Composable
private fun OfflineRow(
    title: String,
    line: String,
    artwork: String?,
    mode: VoyageMode,
    progress: Float?,
    actions: List<Pair<String, () -> Unit>>,
    ready: Boolean = false,
    status: String? = null,
    statusColor: Color? = null,
    primary: Pair<OfflinePrimary, () -> Unit>? = null,
) {
    val accent = mode.accent()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (mode == VoyageMode.READ) Color(0xCC2A1A0E) else Color(0xCC0E1B26))
            .border(1.dp, VoyageColors.Gold.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).border(1.dp, VoyageColors.Gold, RoundedCornerShape(6.dp)).background(Color(0xFF101D27))) {
            artwork?.let { AsyncImage(model = it, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = VoyageColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = VoyageColors.Secondary, fontSize = 12.5.sp, maxLines = 2)
            progress?.let { VoyageProgressBar(it, mode, Modifier.padding(top = 2.dp).fillMaxWidth()) }
            status?.let {
                Text(
                    it,
                    color = statusColor ?: if (ready) Color(0xFF7FCB8A) else VoyageColors.Ivory.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (actions.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { (label, act) ->
                        Text(
                            label,
                            color = VoyageColors.Gold,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, VoyageColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable { com.nuvio.app.mugi.MugiMotion.tap(haptic); act() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        val p = primary
        when {
            p != null && p.first == OfflinePrimary.RETRY -> Text(
                "Retry",
                color = Color(0xFFFFD9D6),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF7A1A20))
                    .border(1.dp, Color(0xFFE5676C), RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Retry download") { com.nuvio.app.mugi.MugiMotion.tap(haptic); p.second() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            p != null -> Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, VoyageColors.Gold, CircleShape)
                    .clickable(onClickLabel = if (p.first == OfflinePrimary.PAUSE) "Pause download" else "Resume download") {
                        com.nuvio.app.mugi.MugiMotion.tap(haptic)
                        p.second()
                    },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    if (p.first == OfflinePrimary.PAUSE) androidx.compose.material.icons.Icons.Rounded.Pause else androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = VoyageColors.Gold,
                    modifier = Modifier.size(24.dp),
                )
            }
            ready -> Box(Modifier.size(40.dp).clip(CircleShape).border(1.5.dp, Color(0xFF7FCB8A), CircleShape), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = "Downloaded", tint = Color(0xFF7FCB8A), modifier = Modifier.size(22.dp))
            }
        }
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
    // the chest: closed → opening (the pick is chosen but hidden) → open (the pick is revealed)
    var chest by remember { mutableStateOf(ChestAnimationState.CLOSED) }
    var chestKey by remember { mutableStateOf(0L) }
    var reveal by remember { mutableStateOf(false) }
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
    if (!showReading && pick?.mode == VoyageMode.READ) { pick = null; reveal = false; chest = ChestAnimationState.CLOSED }
    fun closeChest() {
        pick = null
        reveal = false
        chest = ChestAnimationState.CLOSED
    }
    val openChest: () -> Unit = openChest@{
        if (chest == ChestAnimationState.OPENING) return@openChest // no double taps while it opens
        val c = chooseSurprise(candidates, mode, maxMinutes, seen)
        if (c == null) {
            noMatch = true
        } else {
            // chosen first, kept hidden until the lid is fully open
            seen += c.id
            pick = c
            reveal = false
            noMatch = false
            chest = ChestAnimationState.OPENING
            chestKey++
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF06141E))) {
    // the faint nautical map behind everything (left out with fewer decorations)
    if (com.nuvio.app.mugi.MugiMotion.showDecorations()) {
        Image(
            org.jetbrains.compose.resources.painterResource(nuvio.composeapp.generated.resources.Res.drawable.surprise_nautical_map),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item(key = "head") { VoyageHeader("Surprise Me", onBack, decoration = { CompassRose(Modifier.size(80.dp)) }) }
        item(key = "mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                VoyageModeChips(mode, { mode = it; closeChest() }, modes = if (showReading) VoyageMode.entries else VoyageMode.entries.filter { it != VoyageMode.READ })
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VoyagePill("My List", source == SurpriseSource.MY_LIST, VoyageColors.Gold, Modifier.weight(1f)) { source = SurpriseSource.MY_LIST; closeChest() }
                    VoyagePill("Discover", source == SurpriseSource.DISCOVER, VoyageColors.Gold, Modifier.weight(1f)) { source = SurpriseSource.DISCOVER; closeChest() }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Int?>(null, 15, 30, 60).forEach { m ->
                        VoyagePill(m?.let { "$it min" } ?: "Any", maxMinutes == m, VoyageColors.Burgundy, Modifier.weight(1f)) { maxMinutes = m; closeChest() }
                    }
                }
            }
        }
        item(key = "chest") {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                // the chest as large as the screen allows (the pick rises out of it)
                val chestSize = minOf(maxWidth - 32.dp, 400.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    SurpriseChestStage(
                        chest = chest,
                        chestKey = chestKey,
                        reduceMotion = reduceMotion,
                        pick = pick,
                        reveal = reveal,
                        size = chestSize,
                        onOpen = openChest,
                        onOpened = {
                            chest = ChestAnimationState.OPEN
                            reveal = true
                        },
                    )
                    if (chest == ChestAnimationState.CLOSED) {
                        VoyageButton("Open the chest", VoyageColors.Gold, Modifier.padding(horizontal = 48.dp).fillMaxWidth(), onClick = openChest)
                        Text("Find your next adventure", color = VoyageColors.Secondary, fontFamily = VoyageSerif, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    val p = pick
                    if (reveal && p != null) {
                        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SurprisePlateButton("View details", red = true, Modifier.weight(1f)) {
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
                            SurprisePlateButton("↻  Try again", red = false, Modifier.weight(1f)) {
                                if (chest == ChestAnimationState.OPENING) return@SurprisePlateButton
                                // clear the last pick, show the closed chest, then open it on a new one
                                closeChest()
                                scope.launch {
                                    kotlinx.coroutines.delay(if (reduceMotion) 0 else 180)
                                    openChest()
                                }
                            }
                        }
                    }
                }
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
    }
    }
}

/**
 * The Surprise Me chest with the pick rising out of it. Every chest frame shares one 724 × 724
 * canvas (never stretched). Once the lid is open the parchment card slides up from inside the
 * box (hidden behind the front until it clears the rim), then settles over the front panel.
 */
@Composable
private fun SurpriseChestStage(
    chest: ChestAnimationState,
    chestKey: Long,
    reduceMotion: Boolean,
    pick: SurpriseCandidate?,
    reveal: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onOpen: () -> Unit,
    onOpened: () -> Unit,
) {
    val unit = size / 724f
    fun u(v: Float) = unit * v
    val rise = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(reveal, chestKey) {
        if (!reveal) {
            rise.snapTo(0f)
        } else if (reduceMotion) {
            rise.snapTo(1f)
        } else {
            rise.snapTo(0f)
            rise.animateTo(1f, tween(500, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    val t = rise.value
    // card: 550 × 478 canvas units, centred; it rises from below the rim (top 436) to top 150 and
    // stays tucked inside the box, so the chest front and keyhole remain in front of it
    val cardW = 550f
    val cardH = 478f
    val cardX = (724f - cardW) / 2f
    val cardTop = 436f + (150f - 436f) * androidx.compose.animation.core.FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
    Box(Modifier.size(size)) {
        MugiTreasureChest(
            opening = chest != ChestAnimationState.CLOSED,
            animationKey = chestKey,
            reduceMotion = reduceMotion,
            modifier = Modifier.matchParentSize(),
            onClick = if (chest == ChestAnimationState.CLOSED) onOpen else null,
            onOpened = onOpened,
        )
        val p = pick
        if (reveal && p != null) {
            // inside the box: clipped at the rim, under the chest front
            Box(Modifier.size(size, u(440f)).clipToBounds()) {
                SurpriseCard(p, visibleFraction = (440f - 150f) / cardH, modifier = Modifier.offset(u(cardX), u(cardTop)).size(u(cardW), u(cardH)))
            }
        }
        if (chest == ChestAnimationState.OPEN) {
            Image(
                painterResource(Res.drawable.mugi_chest_open_front),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** The pick on the parchment card: poster, title, type and year, genres, length, rating, description. */
@Composable
private fun SurpriseCard(p: SurpriseCandidate, visibleFraction: Float, modifier: Modifier) {
    val ink = Color(0xFF38200F)
    val meta = p.meta
    Box(modifier) {
        Image(painterResource(Res.drawable.surprise_card_parchment), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
        // the details sit in the part of the card that shows above the rim
        Row(Modifier.fillMaxWidth().fillMaxHeight(visibleFraction).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))
                    .border(1.dp, if (p.mode == VoyageMode.ANI_DRAMA) VoyageColors.Purple else VoyageColors.Gold, RoundedCornerShape(6.dp))
                    .background(Color(0xFF101D27)),
            ) {
                p.artwork?.let { AsyncImage(model = it, contentDescription = p.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.title, color = ink, fontFamily = VoyageSerif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val kind = when {
                    p.mode == VoyageMode.READ -> "Book"
                    meta?.type.equals("movie", true) -> "Movie"
                    p.mode == VoyageMode.ANI_DRAMA -> "Series"
                    else -> "Series"
                }
                Text(listOfNotNull(kind, meta?.releaseInfo?.takeIf { it.isNotBlank() }).joinToString(" • "), color = ink, fontSize = 11.5.sp, maxLines = 1)
                val genres = (meta?.genres?.take(2)?.joinToString(", ") ?: p.genre)?.takeIf { it.isNotBlank() }
                genres?.let { Text(it, color = ink, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val lengthAndRating = listOfNotNull(
                    p.durationMinutes?.let { "⏱ ${formatMinutes(it.toLong())}" },
                    meta?.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it / 10" },
                ).joinToString("   ")
                if (lengthAndRating.isNotBlank()) Text(lengthAndRating, color = ink, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val about = meta?.description?.takeIf { it.isNotBlank() } ?: p.caption
                about?.let { Text(it, color = ink.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** View details (red plate) and Try again (blue plate with a gold edge), from the supplied artwork. */
@Composable
private fun SurprisePlateButton(text: String, red: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier
            .aspectRatio(498f / 139f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = text) { com.nuvio.app.mugi.MugiMotion.tap(haptic); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(if (red) Res.drawable.surprise_button_red else Res.drawable.surprise_button_blue),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
        Text(text, color = Color(0xFFF5E8D0), fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
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


/* ───────────── Appearance & Motion ───────────── */

@Composable
private fun AppearanceMotionScreen(onBack: () -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    val s by com.nuvio.app.mugi.MugiMotion.settings.collectAsState()
    fun set(next: com.nuvio.app.mugi.MugiMotionSettings) = com.nuvio.app.mugi.MugiMotion.update(next)
    val systemOff = remember { com.nuvio.app.mugi.MugiStyle.systemReduceMotion() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item(key = "head") {
            VoyageHeader("Appearance & Motion", onBack, subtitle = "How MugiVerse looks, feels and moves on this phone")
        }
        item(key = "rows") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clip(RoundedCornerShape(14.dp))
                    .background(MugiPrivacyColors.Surface).border(1.dp, MugiPrivacyColors.Gold, RoundedCornerShape(14.dp)),
            ) {
                MotionRow(androidx.compose.material.icons.Icons.Rounded.PlayCircleOutline, "Animations",
                    if (systemOff) "Your phone's \"Remove animations\" is on, so MugiVerse stays still" else "Smooth transitions and motion (chest, doors, chips)",
                    s.animations && !systemOff, enabled = !systemOff) { set(s.copy(animations = it)) }
                MotionDivider()
                MotionRow(androidx.compose.material.icons.Icons.Rounded.Vibration, "Haptic feedback", "A light vibration for taps and selections", s.haptics) { set(s.copy(haptics = it)) }
                MotionDivider()
                MotionRow(androidx.compose.material.icons.Icons.Rounded.VolumeUp, "Sound effects", "A short click for buttons and switches (off by default)", s.sounds) { set(s.copy(sounds = it)) }
                MotionDivider()
                MotionRow(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, "Reduce decorations", "A cleaner look: no sails, maps or ship's wheel in the background", s.reduceDecorations) { set(s.copy(reduceDecorations = it)) }
                MotionDivider()
                MotionRow(androidx.compose.material.icons.Icons.Rounded.BatterySaver, "Low-power mode", "No animations or background details, to save battery", s.lowPower) { set(s.copy(lowPower = it)) }
            }
        }
        item(key = "note") {
            Text(
                "These only change how things look and move. Nothing about your library, downloads or privacy changes.",
                color = VoyageColors.Secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MotionDivider() = Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(MugiPrivacyColors.Gold.copy(alpha = 0.25f)))

@Composable
private fun MotionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { com.nuvio.app.mugi.MugiMotion.tap(haptic); onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = VoyageColors.Gold, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MugiPrivacyColors.Ivory, fontFamily = VoyageSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(description, color = MugiPrivacyColors.Secondary, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { com.nuvio.app.mugi.MugiMotion.tap(haptic); onChange(it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MugiPrivacyColors.Ivory,
                checkedTrackColor = MugiPrivacyColors.Gold,
                uncheckedThumbColor = MugiPrivacyColors.Secondary,
                uncheckedTrackColor = Color(0xFF29343D),
            ),
        )
    }
}
