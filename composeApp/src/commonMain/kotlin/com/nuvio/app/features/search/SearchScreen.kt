package com.nuvio.app.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.firstEnabledManifestError
import com.nuvio.app.features.addons.hasPendingEnabledManifests
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.buildAddonCatalogRefreshSignature
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.home.components.posterGridColumnCountForWidth
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.home_empty_no_sources_title
import nuvio.composeapp.generated.resources.search_empty_no_sources_message
import nuvio.composeapp.generated.resources.compose_search_clear
import nuvio.composeapp.generated.resources.compose_search_discover_title
import nuvio.composeapp.generated.resources.compose_search_empty_failed_message
import nuvio.composeapp.generated.resources.compose_search_empty_failed_title
import nuvio.composeapp.generated.resources.compose_search_empty_no_results_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_results_title
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_message
import nuvio.composeapp.generated.resources.compose_search_empty_no_search_catalogs_title
import nuvio.composeapp.generated.resources.compose_search_placeholder
import nuvio.composeapp.generated.resources.compose_search_recent_searches
import nuvio.composeapp.generated.resources.compose_search_remove_recent_search
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    searchFocusRequestCount: Int = 0,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
) {
    val focusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }

    ScreenActivityEffect(listState) { screenActive ->
        if (!screenActive) {
            isSearchFocused = false
            listState.stopScroll()
        }
    }

    ScreenActivityEffect(searchFocusRequestCount) { screenActive ->
        if (screenActive && searchFocusRequestCount > 0) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        WatchedRepository.ensureLoaded()
        SearchHistoryRepository.ensureLoaded()
    }

    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val uiState by SearchRepository.uiState.collectAsStateWithLifecycle()
    val discoverUiState by SearchRepository.discoverUiState.collectAsStateWithLifecycle()
    val homeCatalogSettingsUiState by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val recentSearches by SearchHistoryRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var lastRequestedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var observedOfflineState by remember { mutableStateOf(false) }
    // MugiVerse Ani-Drama: search and Discover come from anime / K-, C-, J-drama sources only
    val aniSearch = com.nuvio.app.mugi.mugiOn() && com.nuvio.app.mugi.MugiAniMode.on.value
    var aniResults by remember { mutableStateOf<List<com.nuvio.app.features.home.HomeCatalogSection>?>(null) }
    var aniResultsQuery by remember { mutableStateOf<String?>(null) }
    var aniRegion by rememberSaveable { mutableStateOf(0) }
    var aniDiscover by remember { mutableStateOf<List<com.nuvio.app.features.home.MetaPreview>?>(null) }
    LaunchedEffect(aniSearch, aniRegion) {
        if (!aniSearch) return@LaunchedEffect
        aniDiscover = null
        aniDiscover = com.nuvio.app.features.anidrama.AniDramaSearch.discover(com.nuvio.app.features.anidrama.AniDramaSearch.regions[aniRegion])
    }
    val discoverInFocus by remember(query, listState) {
        derivedStateOf {
            query.isBlank() && listState.firstVisibleItemIndex > 0
        }
    }

    ScreenActivityEffect(scrollToTopRequests) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        scrollToTopRequests.collect {
            listState.animateScrollToItem(0)
        }
    }

    val addonRefreshKey = remember(addonsUiState.addons) {
        buildAddonCatalogRefreshSignature(addonsUiState.addons)
    }
    val addonManifestsLoading = addonsUiState.addons.hasPendingEnabledManifests()

    ScreenActivityEffect(addonRefreshKey, homeCatalogSettingsUiState.hideUnreleasedContent) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        SearchRepository.refreshDiscover(addonsUiState.addons)
    }

    ScreenActivityEffect(query, addonRefreshKey, homeCatalogSettingsUiState.hideUnreleasedContent, aniSearch) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            lastRequestedQuery = null
            aniResults = null
            aniResultsQuery = null
            SearchRepository.clear()
        } else if (aniSearch) {
            delay(350)
            aniResults = null
            aniResultsQuery = normalizedQuery
            val found = com.nuvio.app.features.anidrama.AniDramaSearch.search(normalizedQuery)
            aniResults = found
            if (found.isNotEmpty()) SearchHistoryRepository.recordSearch(normalizedQuery)
        } else {
            delay(350)
            lastRequestedQuery = normalizedQuery
            SearchRepository.search(
                query = normalizedQuery,
                addons = addonsUiState.addons,
            )
        }
    }

    ScreenActivityEffect(listState, query, discoverUiState.canLoadMore, discoverUiState.isLoading) { screenActive ->
        if (!screenActive || query.isNotBlank()) return@ScreenActivityEffect

        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= layoutInfo.totalItemsCount - 4
            }
            .distinctUntilChanged()
            .filter { it && discoverUiState.canLoadMore && !discoverUiState.isLoading }
            .collect {
                SearchRepository.loadMoreDiscover()
            }
    }

    ScreenActivityEffect(query, lastRequestedQuery, uiState.isLoading, uiState.sections) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return@ScreenActivityEffect
        if (lastRequestedQuery != normalizedQuery) return@ScreenActivityEffect
        if (uiState.isLoading || uiState.sections.isEmpty()) return@ScreenActivityEffect
        SearchHistoryRepository.recordSearch(normalizedQuery)
    }

    ScreenActivityEffect(networkStatusUiState.condition, query, addonRefreshKey) { screenActive ->
        if (!screenActive) return@ScreenActivityEffect
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (!observedOfflineState) return@ScreenActivityEffect
                observedOfflineState = false

                val normalizedQuery = query.trim()
                if (normalizedQuery.isBlank()) {
                    SearchRepository.refreshDiscover(
                        addons = addonsUiState.addons,
                        forceRefresh = true,
                    )
                } else {
                    SearchRepository.search(
                        query = normalizedQuery,
                        addons = addonsUiState.addons,
                        forceRefresh = true,
                    )
                }
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    // MugiVerse: navy and red (violet in Ani-Drama), gold title, red search field, parchment Discover
    val mugi = com.nuvio.app.mugi.mugiOn()
    val ani = mugi && com.nuvio.app.mugi.MugiAniMode.on.value
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        // the screen's colour only: the sail is drawn once, in the header (it used to show twice)
        if (mugi) com.nuvio.app.mugi.MugiSearchBackground(ani, sail = false)
        val discoverColumns = remember(maxWidth) {
            posterGridColumnCountForWidth(maxWidth)
        }
        val homeSectionPadding = remember(maxWidth) {
            homeSectionHorizontalPaddingForWidth(maxWidth.value)
        }
        val headerTitle = when {
            query.isNotBlank() -> stringResource(Res.string.compose_nav_search)
            discoverInFocus -> stringResource(Res.string.compose_search_discover_title)
            else -> stringResource(Res.string.compose_nav_search)
        }

        NuvioScreen(
            horizontalPadding = 0.dp,
            // MugiVerse: the header itself keeps clear of the status bar, so when it sticks to the
            // top while scrolling the title is never hidden under the clock and icons
            topPadding = if (mugi) 0.dp else null,
            listState = listState,
            modifier = Modifier.fillMaxSize(),
            background = if (mugi) androidx.compose.ui.graphics.Color.Transparent else null,
        ) {
        stickyHeader {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (mugi) {
                    com.nuvio.app.mugi.MugiSearchBackground(ani, Modifier.matchParentSize().nuvioConsumePointerEvents())
                } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background)
                        .nuvioConsumePointerEvents(),
                )
                }
                if (mugi) {
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    ) {
                        com.nuvio.app.mugi.MugiSearchTitle(stringResource(Res.string.compose_nav_search), Modifier.padding(top = 6.dp, bottom = 10.dp))
                        com.nuvio.app.mugi.MugiSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = if (ani) "Search anime, dramas..." else "Search movies, shows...",
                            ani = ani,
                            fieldModifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused },
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    }
                } else
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NuvioScreenHeader(
                        title = headerTitle,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        NuvioInputField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(Res.string.compose_search_placeholder),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            shape = RoundedCornerShape(NuvioTokens.Radius.xl),
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused },
                            trailingContent = if (query.isNotBlank()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(Res.string.compose_search_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        if (query.isBlank()) {
            if (isSearchFocused && recentSearches.isNotEmpty()) {
                item(key = "recent_searches") {
                    SearchRecentSection(
                        recentSearches = recentSearches,
                        onSearchPress = { recentQuery -> query = recentQuery },
                        onRemoveSearch = SearchHistoryRepository::removeSearch,
                    )
                }
            }
                discoverContent(
                    state = discoverUiState,
                    isSourceLoading = addonManifestsLoading,
                    columns = discoverColumns,
                    networkCondition = networkStatusUiState.condition,
                    onTypeSelected = SearchRepository::selectDiscoverType,
                    onCatalogSelected = SearchRepository::selectDiscoverCatalog,
                    onGenreSelected = SearchRepository::selectDiscoverGenre,
                    onRetry = {
                        NetworkStatusRepository.requestRefresh(force = true)
                        if (addonsUiState.addons.firstEnabledManifestError() != null) {
                            AddonRepository.refreshAll()
                        } else {
                            SearchRepository.refreshDiscover(
                                addons = addonsUiState.addons,
                                forceRefresh = true,
                            )
                        }
                    },
                    watchedKeys = watchedUiState.watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                    mugi = mugi,
                    ani = ani,
                    aniRegions = com.nuvio.app.features.anidrama.AniDramaSearch.regions.map { it.label },
                    aniSelected = aniRegion,
                    onAniSelected = { aniRegion = it },
                    aniItems = aniDiscover,
                )
            } else if (ani) {
                // Ani-Drama results: anime and K-, C-, J-dramas only, never regular movies or shows
                val results = aniResults
                when {
                    results == null || aniResultsQuery != query.trim() -> items(2) { HomeSkeletonRow(horizontalPadding = homeSectionPadding) }
                    results.isEmpty() -> item(key = "ani_empty") {
                        com.nuvio.app.voyage.VoyageEmpty("No anime or dramas found", "Try another title, or the name in English or romaji.")
                    }
                    else -> items(items = results, key = { it.key }) { section ->
                        HomeCatalogRowSection(
                            section = section,
                            modifier = Modifier.padding(bottom = 12.dp),
                            watchedKeys = watchedUiState.watchedKeys,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                        )
                    }
                }
            } else {
                val normalizedQuery = query.trim()
                val isWaitingForSearch = normalizedQuery.isNotBlank() && lastRequestedQuery != normalizedQuery
                when {
                    isWaitingForSearch -> {
                        items(2) {
                            HomeSkeletonRow(
                                horizontalPadding = homeSectionPadding,
                            )
                        }
                    }

                    (uiState.isLoading || addonManifestsLoading) && uiState.sections.isEmpty() -> {
                        items(2) {
                            HomeSkeletonRow(
                                horizontalPadding = homeSectionPadding,
                            )
                        }
                    }

                    uiState.sections.isEmpty() -> {
                        item {
                            SearchEmptyStateCard(
                                reason = uiState.emptyStateReason,
                                errorMessage = uiState.errorMessage,
                                networkCondition = networkStatusUiState.condition,
                                onRetry = {
                                    if (normalizedQuery.isNotBlank()) {
                                        NetworkStatusRepository.requestRefresh(force = true)
                                        if (addonsUiState.addons.firstEnabledManifestError() != null) {
                                            AddonRepository.refreshAll()
                                        } else {
                                            SearchRepository.search(
                                                query = normalizedQuery,
                                                addons = addonsUiState.addons,
                                                forceRefresh = true,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = homeSectionPadding),
                            )
                        }
                    }

                    else -> {
                        // MugiVerse Watch: movies and regular TV shows (anime lives in Ani-Drama)
                        val watchSections = if (mugi) {
                            uiState.sections.filter { it.target.contentType.lowercase() in setOf("movie", "series") }.ifEmpty { uiState.sections }
                        } else {
                            uiState.sections
                        }
                        items(
                            items = watchSections.withDuplicateSafeLazyKeys { section -> section.key },
                            key = { section -> section.lazyKey },
                        ) { keyedSection ->
                            val section = keyedSection.value
                            HomeCatalogRowSection(
                                section = section,
                                modifier = Modifier.padding(bottom = 12.dp),
                                watchedKeys = watchedUiState.watchedKeys,
                                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                                onPosterClick = onPosterClick,
                                onPosterLongClick = onPosterLongClick,
                            )
                        }
                        if (uiState.isLoading) {
                            item(key = "search_loading_more") {
                                HomeSkeletonRow(
                                    horizontalPadding = homeSectionPadding,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyStateCard(
    reason: SearchEmptyStateReason?,
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (
        reason == SearchEmptyStateReason.RequestFailed &&
        (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
    ) {
        NuvioNetworkOfflineCard(
            condition = networkCondition,
            modifier = modifier,
            onRetry = onRetry,
        )
        return
    }

    val title: String
    val message: String

    when (reason) {
        SearchEmptyStateReason.NoActiveAddons -> {
            title = stringResource(Res.string.home_empty_no_sources_title)
            message = stringResource(Res.string.search_empty_no_sources_message)
        }

        SearchEmptyStateReason.NoSearchCatalogs -> {
            title = stringResource(Res.string.compose_search_empty_no_search_catalogs_title)
            message = stringResource(Res.string.compose_search_empty_no_search_catalogs_message)
        }

        SearchEmptyStateReason.RequestFailed -> {
            title = stringResource(Res.string.compose_search_empty_failed_title)
            message = errorMessage ?: stringResource(Res.string.compose_search_empty_failed_message)
        }

        SearchEmptyStateReason.NoResults, null -> {
            title = stringResource(Res.string.compose_search_empty_no_results_title)
            message = stringResource(Res.string.compose_search_empty_no_results_message)
        }
    }

    HomeEmptyStateCard(
        modifier = modifier,
        title = title,
        message = message,
        actionLabel = if (reason == SearchEmptyStateReason.RequestFailed) {
            stringResource(Res.string.action_retry)
        } else {
            null
        },
        onActionClick = if (reason == SearchEmptyStateReason.RequestFailed) onRetry else null,
    )
}

@Composable
private fun SearchRecentSection(
    recentSearches: List<String>,
    onSearchPress: (String) -> Unit,
    onRemoveSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.compose_search_recent_searches),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        recentSearches.forEach { recentQuery ->
            SearchRecentRow(
                query = recentQuery,
                onSearchPress = { onSearchPress(recentQuery) },
                onRemovePress = { onRemoveSearch(recentQuery) },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
internal fun SearchRecentRow(
    query: String,
    onSearchPress: () -> Unit,
    onRemovePress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSearchPress)
            .padding(vertical = 2.dp)
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = query,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemovePress) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.compose_search_remove_recent_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
