package com.nuvio.app.features.anidrama

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection

/**
 * The Ani-drama tab: Otaku-style anime browsing (AniList) and K-/C-/J-drama browsing (TMDB),
 * plus the anime and drama rows from your add-ons that used to sit on Home.
 */
@Composable
fun AniDramaScreen(
    modifier: Modifier = Modifier,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    onCatalogClick: ((HomeCatalogSection) -> Unit)? = null,
) {
    var regionIndex by rememberSaveable { mutableIntStateOf(0) }
    var animeChipIndex by rememberSaveable { mutableIntStateOf(0) }
    var dramaChipIndex by rememberSaveable { mutableIntStateOf(0) }
    var audioIndex by rememberSaveable { mutableIntStateOf(AnimeAudioFilter.Both.ordinal) }
    var airingExpanded by rememberSaveable { mutableStateOf(false) }

    val region = AniDramaRegion.entries[regionIndex]
    val animeChip = AnimeChip.entries[animeChipIndex]
    val dramaChip = DramaChip.entries[dramaChipIndex]
    val audio = AnimeAudioFilter.entries[audioIndex]

    val openItem: (AniDramaItem) -> Unit = { item -> onPosterClick?.invoke(item.toMetaPreview()) }
    val longPressItem: (AniDramaItem) -> Unit = { item -> onPosterLongClick?.invoke(item.toMetaPreview()) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Room for the Watch / Ani-drama / Read switch drawn above this screen.
        if (com.nuvio.app.readmode.ReadModeBridge.isAvailable) {
            Spacer(Modifier.height(com.nuvio.app.readmode.modeBarHeight()))
        } else {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp))
            Text(
                text = AniDramaBridge.TAB_LABEL,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        AniDramaSegmented(
            options = AniDramaRegion.entries.map { it.label },
            selectedIndex = regionIndex,
            onSelect = { index ->
                if (index != regionIndex) {
                    regionIndex = index
                    airingExpanded = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(10.dp))
        ChipRows(
            labels = if (region == AniDramaRegion.Anime) AnimeChip.entries.map { it.label } else DramaChip.entries.map { it.label },
            selectedIndex = if (region == AniDramaRegion.Anime) animeChipIndex else dramaChipIndex,
            onSelect = { index ->
                airingExpanded = false
                if (region == AniDramaRegion.Anime) animeChipIndex = index else dramaChipIndex = index
            },
        )
        if (region == AniDramaRegion.Anime) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AniDramaSegmented(
                    options = AnimeAudioFilter.entries.map { it.label },
                    selectedIndex = audioIndex,
                    onSelect = { audioIndex = it },
                    compact = true,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (region == AniDramaRegion.Anime) {
                AnimeContent(
                    chip = animeChip,
                    audio = audio,
                    airingExpanded = airingExpanded,
                    onExpandAiring = { airingExpanded = it },
                    onItemClick = openItem,
                    onItemLongClick = longPressItem,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                    onCatalogClick = onCatalogClick,
                )
            } else {
                DramaContent(
                    region = region,
                    chip = dramaChip,
                    airingExpanded = airingExpanded,
                    onExpandAiring = { airingExpanded = it },
                    onItemClick = openItem,
                    onItemLongClick = longPressItem,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                    onCatalogClick = onCatalogClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRows(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            AniDramaChip(label = label, selected = index == selectedIndex, onClick = { onSelect(index) })
        }
    }
}

// ---------------------------------------------------------------- Anime

@Composable
private fun AnimeContent(
    chip: AnimeChip,
    audio: AnimeAudioFilter,
    airingExpanded: Boolean,
    onExpandAiring: (Boolean) -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
) {
    when (chip) {
        AnimeChip.Airing -> if (airingExpanded) {
            AnimeGrid(
                query = AniListClient.MediaQuery(sort = "LATEST_EPISODE", status = "RELEASING"),
                audio = audio,
                grouping = AniDramaGrouping.Week,
                loader = { page -> if (page == 1) AnimeRepository.airingLatest() else AniDramaPage(emptyList(), false) },
                title = "Airing now · latest episode first",
                onBack = { onExpandAiring(false) },
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        } else {
            AnimeOverview(
                audio = audio,
                onSeeAllAiring = { onExpandAiring(true) },
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
                onCatalogClick = onCatalogClick,
            )
        }
        AnimeChip.Trending -> AnimeGrid(AniListClient.MediaQuery(sort = "TRENDING_DESC"), audio, onItemClick = onItemClick, onItemLongClick = onItemLongClick)
        AnimeChip.Popular -> AnimeGrid(AniListClient.MediaQuery(sort = "POPULARITY_DESC", ranked = true), audio, onItemClick = onItemClick, onItemLongClick = onItemLongClick)
        AnimeChip.Top100 -> AnimeGrid(AniListClient.MediaQuery(sort = "SCORE_DESC", ranked = true), audio, maxItems = 100, onItemClick = onItemClick, onItemLongClick = onItemLongClick)
        AnimeChip.Upcoming -> AnimeGrid(
            AniListClient.MediaQuery(sort = "START_DATE", status = "NOT_YET_RELEASED", startGreater = AnimeRepository.fuzzyToday()),
            audio,
            grouping = AniDramaGrouping.MonthSoonest,
            onItemClick = onItemClick, onItemLongClick = onItemLongClick)
        AnimeChip.Seasons -> AnimeSeasons(audio, onItemClick, onItemLongClick)
        AnimeChip.Genres -> AnimeGenres(audio, onItemClick, onItemLongClick)
        AnimeChip.Calendar -> AniDramaCalendarView(
            key = "anime-calendar",
            loader = { start, days -> AnimeRepository.schedule(start, days) },
            filter = { AnimeRepository.applyAudioFilter(listOf(it.item), audio).isNotEmpty() },
            showDubBadge = true,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
        )
    }
}

@Composable
private fun AnimeOverview(
    audio: AnimeAudioFilter,
    onSeeAllAiring: () -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
) {
    val airing = rememberAniDramaPagedList(key = "anime-overview-airing") { page ->
        AnimeRepository.page(AniListClient.MediaQuery(sort = "POPULARITY_DESC", status = "RELEASING"), page)
    }
    val (schedule, _) = rememberAniDramaLoad("anime-overview-schedule") { AnimeRepository.weekSchedule() }
    val todayIndex = remember { AniDramaTime.weekdayOfIso(AniDramaTime.todayIso()) ?: 0 }
    val todayItems = remember(schedule.value, audio) {
        schedule.value.orEmpty()
            .filter { it.weekday == todayIndex }
            .sortedBy { it.sortKey }
            .map { entry -> entry.item.copy(caption = entry.episodeLabel.substringBefore(" ·")) }
            .let { AnimeRepository.applyAudioFilter(it, audio) }
            .distinctBy { it.key }
    }

    OverviewColumn(
        region = AniDramaRegion.Anime,
        onPosterClick = onPosterClick,
        onPosterLongClick = onPosterLongClick,
        onCatalogClick = onCatalogClick,
    ) {
        item(key = "airing") {
            AniDramaShelf(
                title = "Airing now",
                items = AnimeRepository.applyAudioFilter(airing.items, audio),
                loading = airing.loading,
                showDubBadge = true,
                onSeeAll = onSeeAllAiring,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
        item(key = "today") {
            AniDramaShelf(
                title = "Airing today",
                items = todayItems,
                loading = schedule.loading,
                showDubBadge = true,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
    }
}

@Composable
private fun AnimeGrid(
    query: AniListClient.MediaQuery,
    audio: AnimeAudioFilter,
    maxItems: Int = 600,
    grouping: AniDramaGrouping = AniDramaGrouping.None,
    loader: (suspend (Int) -> AniDramaPage?)? = null,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    val list = rememberAniDramaPagedList(key = query, maxItems = maxItems) { page ->
        loader?.invoke(page) ?: AnimeRepository.page(query, page)
    }
    val visible = AnimeRepository.applyAudioFilter(list.items, audio)
    AniDramaGrid(
        items = visible,
        list = list,
        stateKey = query,
        grouping = grouping,
        showDubBadge = true,
        title = title,
        onBack = onBack,
        header = header,
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick,
    )
}

@Composable
private fun AnimeSeasons(
    audio: AnimeAudioFilter,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    val current = remember { AniDramaTime.currentAnimeSeason() }
    var seasonIndex by rememberSaveable { mutableIntStateOf(current.first.ordinal) }
    var year by rememberSaveable { mutableIntStateOf(current.second) }
    val season = AnimeSeason.entries[seasonIndex]
    AnimeGrid(
        query = AniListClient.MediaQuery(sort = "POPULARITY_DESC", season = season, seasonYear = year),
        audio = audio,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(left = true) { year-- }
                    Text(
                        text = "${season.label} $year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    StepButton(left = false) { if (year < current.second + 1) year++ }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimeSeason.entries.forEach { option ->
                        AniDramaChip(option.label, option == season, onClick = { seasonIndex = option.ordinal })
                    }
                }
            }
        },
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick,
    )
}

@Composable
private fun AnimeGenres(
    audio: AnimeAudioFilter,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    var genre by rememberSaveable { mutableStateOf(AniListClient.genres.first()) }
    var newest by rememberSaveable { mutableStateOf(true) }
    AnimeGrid(
        query = if (newest) {
            AniListClient.MediaQuery(sort = "START_DATE_DESC", genre = genre, startLesser = AnimeRepository.fuzzyToday(1))
        } else {
            AniListClient.MediaQuery(sort = "POPULARITY_DESC", genre = genre)
        },
        audio = audio,
        grouping = if (newest) AniDramaGrouping.MonthNewest else AniDramaGrouping.None,
        header = {
            BrowseHeader(
                labels = AniListClient.genres,
                selected = genre,
                onSelect = { genre = it },
                newest = newest,
                onNewestChange = { newest = it },
            )
        },
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick,
    )
}

// ---------------------------------------------------------------- Dramas

@Composable
private fun DramaContent(
    region: AniDramaRegion,
    chip: DramaChip,
    airingExpanded: Boolean,
    onExpandAiring: (Boolean) -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
) {
    when (chip) {
        DramaChip.AiringNow -> if (airingExpanded) {
            DramaGrid(
                region = region,
                filter = DramaRepository.Filter(DramaChip.AiringNow),
                grouping = AniDramaGrouping.Week,
                title = "Airing now · latest episode first",
                onBack = { onExpandAiring(false) },
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        } else {
            DramaOverview(region, { onExpandAiring(true) }, onItemClick, onItemLongClick, onPosterClick, onPosterLongClick, onCatalogClick)
        }
        DramaChip.New, DramaChip.Upcoming, DramaChip.Popular, DramaChip.TopRated -> DramaGrid(
            region = region,
            filter = DramaRepository.Filter(chip),
            grouping = when (chip) {
                DramaChip.New -> AniDramaGrouping.MonthNewest
                DramaChip.Upcoming -> AniDramaGrouping.MonthSoonest
                else -> AniDramaGrouping.None
            },
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
        )
        DramaChip.Genres -> DramaGenres(region, onItemClick, onItemLongClick)
        DramaChip.Networks -> DramaNetworks(region, onItemClick, onItemLongClick)
        DramaChip.Calendar -> AniDramaCalendarView(
            key = "drama-calendar-$region",
            loader = { start, days -> DramaRepository.schedule(region, start, days) },
            filter = { true },
            showDubBadge = false,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
        )
    }
}

@Composable
private fun DramaOverview(
    region: AniDramaRegion,
    onSeeAllAiring: () -> Unit,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
) {
    val (schedule, _) = rememberAniDramaLoad("drama-overview-schedule-$region") { DramaRepository.weekSchedule(region) }
    val todayIso = remember { AniDramaTime.todayIso() }
    val monthStart = remember { todayIso.take(8) + "01" }
    val monthDays = remember {
        AniDramaTime.daysInMonth(todayIso.take(4).toIntOrNull() ?: 2026, todayIso.substring(5, 7).toIntOrNull() ?: 1)
    }
    val (month, _) = rememberAniDramaLoad("drama-overview-month-$region-$monthStart") {
        DramaRepository.schedule(region, monthStart, monthDays)
    }
    val (comingSoon, _) = rememberAniDramaLoad("drama-overview-soon-$region") { DramaRepository.comingSoon(region) }
    val airing = rememberAniDramaPagedList(key = "drama-overview-airing-$region") { page ->
        DramaRepository.page(region, DramaRepository.Filter(DramaChip.AiringNow), page)
    }

    /** Latest released episode per show, newest first, with the real air date on the poster. */
    fun released(entries: List<AniDramaScheduleEntry>?): List<AniDramaItem> =
        entries.orEmpty()
            .filter { it.dateIso <= todayIso }
            .sortedWith(compareByDescending<AniDramaScheduleEntry> { it.dateIso }.thenBy { it.sortKey })
            .distinctBy { it.item.key }
            .map { entry ->
                entry.item.copy(caption = "EP ${entry.episode} · ${AniDramaTime.shortDayLabel(entry.dateIso)}")
            }

    val releasedThisWeek = remember(schedule.value) { released(schedule.value) }
    val releasedThisMonth = remember(month.value) { released(month.value) }
    val premieredThisMonth = remember(month.value) {
        month.value.orEmpty()
            .filter { it.isPremiere && it.dateIso <= todayIso }
            .sortedByDescending { it.dateIso }
            .distinctBy { it.item.key }
            .map { it.item.copy(caption = "Premiered ${AniDramaTime.shortDayLabel(it.dateIso)}") }
    }

    OverviewColumn(
        region = region,
        onPosterClick = onPosterClick,
        onPosterLongClick = onPosterLongClick,
        onCatalogClick = onCatalogClick,
    ) {
        item(key = "released-week") {
            AniDramaShelf(
                title = "Released this week",
                items = releasedThisWeek,
                loading = schedule.loading,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
        item(key = "released-month") {
            AniDramaShelf(
                title = "Released this month",
                items = releasedThisMonth,
                loading = month.loading,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
        item(key = "premiered-month") {
            AniDramaShelf(
                title = "Premiered this month",
                items = premieredThisMonth,
                loading = month.loading,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
        item(key = "coming-soon") {
            AniDramaShelf(
                title = "Coming soon",
                items = comingSoon.value.orEmpty(),
                loading = comingSoon.loading,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
        item(key = "airing") {
            AniDramaShelf(
                title = "Airing now",
                items = airing.items,
                loading = airing.loading,
                onSeeAll = onSeeAllAiring,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
            )
        }
    }
}

@Composable
private fun DramaGrid(
    region: AniDramaRegion,
    filter: DramaRepository.Filter,
    grouping: AniDramaGrouping = AniDramaGrouping.None,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    val key = "$region|$filter"
    val list = rememberAniDramaPagedList(key = "$key|$grouping") { page ->
        if (filter.chip == DramaChip.AiringNow && grouping == AniDramaGrouping.Week) {
            if (page == 1) DramaRepository.airingLatest(region) else AniDramaPage(emptyList(), false)
        } else {
            DramaRepository.page(region, filter, page)
        }
    }
    AniDramaGrid(
        items = list.items,
        list = list,
        stateKey = "$key|$grouping",
        grouping = grouping,
        showDubBadge = false,
        title = title,
        onBack = onBack,
        header = header,
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick,
    )
}

@Composable
private fun DramaGenres(
    region: AniDramaRegion,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    val (genres, retry) = rememberAniDramaLoad("drama-genres-$region") { DramaRepository.genres(region).takeIf { it.isNotEmpty() } }
    var selectedName by rememberSaveable(region) { mutableStateOf<String?>(null) }
    var newest by rememberSaveable(region) { mutableStateOf(true) }
    val options = genres.value.orEmpty()
    val selected = options.firstOrNull { it.name == selectedName } ?: options.firstOrNull()
    when {
        selected != null -> DramaGrid(
            region = region,
            filter = DramaRepository.Filter(
                chip = DramaChip.Genres,
                genreId = selected.id.takeIf { it > 0 },
                keywordId = (-selected.id).takeIf { it > 0 },
                newest = newest,
            ),
            grouping = if (newest) AniDramaGrouping.MonthNewest else AniDramaGrouping.None,
            header = {
                BrowseHeader(options.map { it.name }, selected.name, { selectedName = it }, newest, { newest = it })
            },
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
        )
        genres.loading -> AniDramaLoading(Modifier.fillMaxWidth().height(200.dp))
        else -> AniDramaMessage("Couldn't load genres.", Modifier.padding(horizontal = 16.dp), onRetry = retry)
    }
}

@Composable
private fun DramaNetworks(
    region: AniDramaRegion,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    val (networks, retry) = rememberAniDramaLoad("drama-networks-$region") { DramaRepository.networks(region).takeIf { it.isNotEmpty() } }
    var selectedName by rememberSaveable(region) { mutableStateOf<String?>(null) }
    var newest by rememberSaveable(region) { mutableStateOf(true) }
    val options = networks.value.orEmpty()
    val selected = options.firstOrNull { it.name == selectedName } ?: options.firstOrNull()
    when {
        selected != null -> DramaGrid(
            region = region,
            filter = DramaRepository.Filter(chip = DramaChip.Networks, networkId = selected.id, newest = newest),
            grouping = if (newest) AniDramaGrouping.MonthNewest else AniDramaGrouping.None,
            header = {
                BrowseHeader(options.map { it.name }, selected.name, { selectedName = it }, newest, { newest = it })
            },
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
        )
        networks.loading -> AniDramaLoading(Modifier.fillMaxWidth().height(200.dp))
        else -> AniDramaMessage("Couldn't load networks.", Modifier.padding(horizontal = 16.dp), onRetry = retry)
    }
}

// ---------------------------------------------------------------- Shared views

/** Overview layout: the region's own shelves, then add-on rows moved from Home, then the Home switch. */
@Composable
private fun OverviewColumn(
    region: AniDramaRegion,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    shelves: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val homeState by HomeRepository.uiState.collectAsStateWithLifecycle()
    val keepOnHome by AniDramaBridge.keepRowsOnHome.collectAsStateWithLifecycle()
    val addonSections = remember(homeState.sections, region) { AniDramaRows.sectionsFor(region, homeState.sections) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = nuvioSafeBottomPadding(24.dp)),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        shelves()
        if (addonSections.isNotEmpty()) {
            item(key = "addons-title") {
                Text(
                    text = "From your add-ons",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(addonSections, key = { "addon-${it.key}" }) { section ->
                HomeCatalogRowSection(
                    section = section,
                    onViewAllClick = onCatalogClick?.let { open -> { open(section) } },
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
        }
        item(key = "keep-on-home") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { AniDramaBridge.setKeepRowsOnHome(!keepOnHome) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Also show these rows on Home",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Anime and drama rows from your add-ons live here. Turn on to keep them on Home too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = keepOnHome, onCheckedChange = { AniDramaBridge.setKeepRowsOnHome(it) })
            }
        }
    }
}

/** Poster grid that keeps loading as you scroll, with an optional title/back row and header. */
@Composable
private fun AniDramaGrid(
    items: List<AniDramaItem>,
    list: AniDramaPagedList,
    stateKey: Any,
    grouping: AniDramaGrouping = AniDramaGrouping.None,
    showDubBadge: Boolean,
    title: String?,
    onBack: (() -> Unit)?,
    header: (@Composable () -> Unit)?,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    val gridState: LazyGridState = remember(stateKey) { LazyGridState() }
    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 9
        }
    }
    // Keep loading while near the bottom, or while a filter (Dub/Sub) hides most of a page.
    LaunchedEffect(list, nearEnd, items.size, list.loading) {
        if (!list.loading && list.hasMore && !list.failed && (nearEnd || items.size < 18)) list.loadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = nuvioSafeBottomPadding(24.dp)),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(6.dp).size(22.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (header != null) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        if (grouping == AniDramaGrouping.None) {
            gridItems(items, showDubBadge, onItemClick, onItemLongClick)
        } else {
            AniDramaSections.build(items, grouping).forEach { section ->
                item(key = "section-${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(section.title, section.note)
                }
                gridItems(section.items, showDubBadge, onItemClick, onItemLongClick)
            }
        }
        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            when {
                list.loading -> AniDramaLoading(Modifier.fillMaxWidth().height(80.dp))
                list.failed -> AniDramaMessage("Couldn't load this list.", onRetry = list::retry)
                items.isEmpty() && !list.hasMore -> AniDramaMessage("Nothing found.")
                else -> Spacer(Modifier.height(1.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.gridItems(
    posters: List<AniDramaItem>,
    showDubBadge: Boolean,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
) {
    items(count = posters.size, key = { index -> posters[index].key }) { index ->
        val item = posters[index]
        AniDramaPosterTile(
            item = item,
            onClick = { onItemClick(item) },
            onLongClick = { onItemLongClick(item) },
            showDubBadge = showDubBadge,
        )
    }
}

/** This week's schedule with a Mon–Sun day picker (today selected first). */
@Composable
private fun AniDramaScheduleView(
    key: String,
    loader: suspend () -> List<AniDramaScheduleEntry>,
    filter: (AniDramaScheduleEntry) -> Boolean,
    showDubBadge: Boolean,
    onItemClick: (AniDramaItem) -> Unit,
    onItemLongClick: (AniDramaItem) -> Unit,
    showGenreFilter: Boolean = false,
) {
    val (state, retry) = rememberAniDramaLoad(key) { loader() }
    val todayIndex = remember { AniDramaTime.weekdayOfIso(AniDramaTime.todayIso()) ?: 0 }
    var day by rememberSaveable(key) { mutableIntStateOf(todayIndex) }
    var genre by rememberSaveable(key) { mutableStateOf<String?>(null) }
    val dayEntries = state.value.orEmpty().filter { it.weekday == day && filter(it) }
    val genres = if (showGenreFilter) dayEntries.flatMap { it.genres }.distinct().sorted() else emptyList()
    val entries = dayEntries
        .filter { genre == null || genre in it.genres }
        .sortedBy { it.sortKey }
        .distinctBy { it.item.key + it.episodeLabel }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = nuvioSafeBottomPadding(24.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "days") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AniDramaTime.weekdayShort.forEachIndexed { index, label ->
                    val selected = index == day
                    Text(
                        text = if (index == todayIndex) "$label•" else label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                            .clickable { day = index; genre = null }
                            .padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        if (genres.isNotEmpty()) {
            item(key = "genres") {
                ScrollingChips(
                    labels = listOf("All") + genres,
                    selected = genre ?: "All",
                    horizontalPadding = 16.dp,
                    onSelect = { genre = it.takeIf { label -> label != "All" } },
                )
            }
        }
        when {
            state.loading -> item(key = "loading") { AniDramaLoading(Modifier.fillMaxWidth().height(160.dp)) }
            state.failed -> item(key = "failed") {
                AniDramaMessage("Couldn't load the schedule.", Modifier.padding(horizontal = 16.dp), onRetry = retry)
            }
            entries.isEmpty() -> item(key = "empty") {
                AniDramaMessage("Nothing airs on this day.", Modifier.padding(horizontal = 16.dp))
            }
            else -> items(entries, key = { it.item.key + it.episodeLabel + it.sortKey }) { entry ->
                ScheduleRow(entry, showDubBadge, onClick = { onItemClick(entry.item) }, onLongClick = { onItemLongClick(entry.item) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleRow(
    entry: AniDramaScheduleEntry,
    showDubBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            entry.item.poster?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.episodeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDubBadge && entry.item.hasEnglishDub) {
            Text(
                text = "DUB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** A month / week header inside a grid: "November 2026 ........ 9 shows". */
@Composable
private fun SectionHeader(title: String, note: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Genre / network chips with a Newest | Popular switch under them. */
@Composable
private fun BrowseHeader(
    labels: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    newest: Boolean,
    onNewestChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScrollingChips(labels = labels, selected = selected, onSelect = onSelect)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AniDramaSegmented(
                options = listOf("Newest", "Popular"),
                selectedIndex = if (newest) 0 else 1,
                onSelect = { onNewestChange(it == 0) },
                compact = true,
            )
        }
    }
}

@Composable
private fun ScrollingChips(
    labels: List<String>,
    selected: String,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        items(labels, key = { it }) { label ->
            AniDramaChip(label = label, selected = label == selected, onClick = { onSelect(label) })
        }
    }
}

@Composable
private fun StepButton(left: Boolean, onClick: () -> Unit) {
    Icon(
        imageVector = if (left) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
        contentDescription = if (left) "Previous year" else "Next year",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .size(20.dp),
    )
}
