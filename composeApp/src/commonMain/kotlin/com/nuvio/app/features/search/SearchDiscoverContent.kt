package com.nuvio.app.features.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridRow
import com.nuvio.app.features.home.components.PosterGridSkeletonRow
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.discoverContent(
    state: DiscoverUiState,
    isSourceLoading: Boolean,
    columns: Int,
    networkCondition: NetworkCondition,
    onTypeSelected: (String) -> Unit,
    onCatalogSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onRetry: (() -> Unit)? = null,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    mugi: Boolean = false,
    ani: Boolean = false,
    aniRegions: List<String> = emptyList(),
    aniSelected: Int = 0,
    onAniSelected: (Int) -> Unit = {},
    aniItems: List<MetaPreview>? = null,
) {
    if (mugi && ani) {
        // Ani-Drama Discover: anime and K-, C-, J-dramas only (no regular Movies / TV Shows)
        item(key = "mugi-discover-banner") {
            Box(Modifier.fillMaxWidth()) {
                com.nuvio.app.mugi.MugiDiscoverBanner(stringResource(Res.string.compose_search_discover_title))
                com.nuvio.app.voyage.SurpriseChestButton(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), size = 34.dp)
            }
        }
        item(key = "mugi-ani-categories") {
            com.nuvio.app.mugi.MugiSegmentedSelector(
                options = aniRegions,
                selectedIndex = aniSelected,
                selectedColor = com.nuvio.app.mugi.MugiScreenColors.Purple,
                surface = com.nuvio.app.mugi.MugiScreenColors.AniSurface,
                onSelected = onAniSelected,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "mugi-ani-provider") {
            com.nuvio.app.mugi.MugiProviderLabel(
                "Trending · ${aniRegions.getOrNull(aniSelected).orEmpty()}",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        val list = aniItems
        when {
            list == null -> items(2) {
                PosterGridSkeletonRow(columns = columns, modifier = Modifier.padding(horizontal = 16.dp))
            }
            list.isEmpty() -> item(key = "mugi-ani-empty") {
                com.nuvio.app.voyage.VoyageEmpty("Nothing to show right now", "Check your connection and try again.")
            }
            else -> items(count = (list.size + columns - 1) / columns) { rowIndex ->
                val firstIndex = rowIndex * columns
                PosterGridRow(
                    items = list.subList(firstIndex, minOf(firstIndex + columns, list.size)),
                    columns = columns,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
        }
        return
    }
    if (mugi) {
        item(key = "mugi-discover-banner") {
            Box(Modifier.fillMaxWidth()) {
                com.nuvio.app.mugi.MugiDiscoverBanner(stringResource(Res.string.compose_search_discover_title))
                // Surprise Me
                com.nuvio.app.voyage.SurpriseChestButton(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), size = 34.dp)
            }
        }
        item(key = "mugi-discover-filters") {
            MugiDiscoverFilters(state, ani, onTypeSelected, onCatalogSelected, onGenreSelected, Modifier.padding(horizontal = 16.dp))
        }
        state.selectedCatalog?.let { selectedCatalog ->
            item(key = "mugi-discover-provider") {
                com.nuvio.app.mugi.MugiProviderLabel(
                    "${selectedCatalog.addonName} • ${selectedCatalog.type.displayTypeLabel()}",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
    if (!mugi) item {
        DiscoverSectionHeader(modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (!mugi) item {
        DiscoverFilterRow(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp),
            onTypeSelected = onTypeSelected,
            onCatalogSelected = onCatalogSelected,
            onGenreSelected = onGenreSelected,
        )
    }
    if (!mugi) state.selectedCatalog?.let { selectedCatalog ->
        item {
            Text(
                text = stringResource(
                    Res.string.discover_catalog_context,
                    selectedCatalog.addonName,
                    selectedCatalog.type.displayTypeLabel(),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when {
        (state.isLoading || isSourceLoading) && state.items.isEmpty() -> {
            items(2) {
                PosterGridSkeletonRow(
                    columns = columns,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        state.items.isEmpty() -> {
            item {
                DiscoverEmptyStateCard(
                    reason = state.emptyStateReason,
                    errorMessage = state.errorMessage,
                    networkCondition = networkCondition,
                    onRetry = onRetry,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        else -> {
            items(count = (state.items.size + columns - 1) / columns) { rowIndex ->
                val firstIndex = rowIndex * columns
                PosterGridRow(
                    items = state.items.subList(firstIndex, minOf(firstIndex + columns, state.items.size)),
                    columns = columns,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    watchedKeys = watchedKeys,
                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    onPosterClick = onPosterClick,
                    onPosterLongClick = onPosterLongClick,
                )
            }
            if (state.isLoading) {
                item {
                    CatalogLoadingFooter(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * MugiVerse: the types as one capsule (Movies | TV Shows on Watch; every type in Ani-Drama), with the
 * catalog and genre drop-downs underneath. Same selection calls as the chips.
 */
@Composable
private fun MugiDiscoverFilters(
    state: DiscoverUiState,
    ani: Boolean,
    onTypeSelected: (String) -> Unit,
    onCatalogSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val watchTypes = listOf("movie", "series")
    val types = if (ani) {
        state.typeOptions
    } else {
        watchTypes.mapNotNull { t -> state.typeOptions.firstOrNull { it.equals(t, ignoreCase = true) } }.ifEmpty { state.typeOptions }
    }
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (types.isNotEmpty()) {
            val labels = types.map { t ->
                when (t.lowercase()) {
                    "movie" -> "Movies"
                    "series" -> if (ani) "Series" else "TV Shows"
                    else -> t.displayTypeLabel()
                }
            }
            com.nuvio.app.mugi.MugiSegmentedSelector(
                options = labels,
                selectedIndex = types.indexOfFirst { it.equals(state.selectedType, ignoreCase = true) },
                selectedColor = if (ani) com.nuvio.app.mugi.MugiScreenColors.Purple else com.nuvio.app.mugi.MugiScreenColors.Red,
                surface = if (ani) com.nuvio.app.mugi.MugiScreenColors.AniSurface else com.nuvio.app.mugi.MugiScreenColors.WatchSurface,
                onSelected = { i -> onTypeSelected(types[i]) },
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_select_catalog),
                label = state.selectedCatalog?.catalogName ?: stringResource(Res.string.discover_catalog),
                selectedKey = state.selectedCatalogKey,
                options = state.catalogOptions.map { option -> NuvioDropdownOption(key = option.key, label = option.catalogName) },
                enabled = state.catalogOptions.isNotEmpty(),
                onSelected = { onCatalogSelected(it.key) },
            )
            val selectedCatalog = state.selectedCatalog
            val genreOptions = buildList {
                if (selectedCatalog?.genreRequired != true) {
                    add(NuvioDropdownOption(key = "", label = stringResource(Res.string.discover_all_genres)))
                }
                addAll(state.genreOptions.map { genre -> NuvioDropdownOption(key = genre, label = genre) })
            }
            NuvioDropdownChip(
                title = stringResource(Res.string.discover_select_genre),
                label = state.selectedGenre ?: stringResource(Res.string.discover_all_genres),
                selectedKey = state.selectedGenre ?: "",
                options = genreOptions,
                enabled = genreOptions.size > 1 || selectedCatalog?.genreRequired == true,
                onSelected = { option -> onGenreSelected(option.key.ifBlank { null }) },
            )
            // types that aren't in the capsule (e.g. channels) stay reachable
            val extra = state.typeOptions.filter { t -> types.none { it.equals(t, ignoreCase = true) } }
            if (extra.isNotEmpty()) {
                NuvioDropdownChip(
                    title = stringResource(Res.string.discover_select_type),
                    label = state.selectedType?.takeIf { s -> extra.any { it.equals(s, ignoreCase = true) } }?.displayTypeLabel()
                        ?: stringResource(Res.string.discover_type),
                    selectedKey = state.selectedType,
                    options = extra.map { NuvioDropdownOption(key = it, label = it.displayTypeLabel()) },
                    enabled = true,
                    onSelected = { onTypeSelected(it.key) },
                )
            }
        }
    }
}

@Composable
private fun DiscoverSectionHeader(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.compose_search_discover_title),
        modifier = modifier,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun DiscoverFilterRow(
    state: DiscoverUiState,
    onTypeSelected: (String) -> Unit,
    onCatalogSelected: (String) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NuvioDropdownChip(
            title = stringResource(Res.string.discover_select_type),
            label = state.selectedType?.displayTypeLabel() ?: stringResource(Res.string.discover_type),
            selectedKey = state.selectedType,
            options = state.typeOptions.map { NuvioDropdownOption(key = it, label = it.displayTypeLabel()) },
            enabled = state.typeOptions.isNotEmpty(),
            onSelected = { onTypeSelected(it.key) },
        )
        NuvioDropdownChip(
            title = stringResource(Res.string.discover_select_catalog),
            label = state.selectedCatalog?.catalogName ?: stringResource(Res.string.discover_catalog),
            selectedKey = state.selectedCatalogKey,
            options = state.catalogOptions.map { option -> NuvioDropdownOption(key = option.key, label = option.catalogName) },
            enabled = state.catalogOptions.isNotEmpty(),
            onSelected = { onCatalogSelected(it.key) },
        )

        val selectedCatalog = state.selectedCatalog
        val genreOptions = buildList {
            if (selectedCatalog?.genreRequired != true) {
                add(NuvioDropdownOption(key = "", label = stringResource(Res.string.discover_all_genres)))
            }
            addAll(state.genreOptions.map { genre -> NuvioDropdownOption(key = genre, label = genre) })
        }
        NuvioDropdownChip(
            title = stringResource(Res.string.discover_select_genre),
            label = state.selectedGenre ?: stringResource(Res.string.discover_all_genres),
            selectedKey = state.selectedGenre ?: "",
            options = genreOptions,
            enabled = genreOptions.size > 1 || selectedCatalog?.genreRequired == true,
            onSelected = { option ->
                onGenreSelected(option.key.ifBlank { null })
            },
        )
    }
}

@Composable
private fun CatalogLoadingFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun DiscoverEmptyStateCard(
    reason: DiscoverEmptyStateReason?,
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (
        reason == DiscoverEmptyStateReason.RequestFailed &&
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
        DiscoverEmptyStateReason.NoActiveAddons -> {
            title = stringResource(Res.string.home_empty_no_sources_title)
            message = stringResource(Res.string.search_empty_no_sources_message)
        }

        DiscoverEmptyStateReason.NoDiscoverCatalogs -> {
            title = stringResource(Res.string.discover_empty_no_catalogs_title)
            message = stringResource(Res.string.discover_empty_no_catalogs_message)
        }

        DiscoverEmptyStateReason.RequestFailed -> {
            title = stringResource(Res.string.discover_empty_load_failed_title)
            message = errorMessage ?: stringResource(Res.string.discover_empty_load_failed_message)
        }

        DiscoverEmptyStateReason.NoResults, null -> {
            title = stringResource(Res.string.discover_empty_no_results_title)
            message = stringResource(Res.string.discover_empty_no_results_message)
        }
    }

    HomeEmptyStateCard(
        modifier = modifier,
        title = title,
        message = message,
        actionLabel = if (reason == DiscoverEmptyStateReason.RequestFailed) {
            stringResource(Res.string.action_retry)
        } else {
            null
        },
        onActionClick = if (reason == DiscoverEmptyStateReason.RequestFailed) onRetry else null,
    )
}

@Composable
private fun String.displayTypeLabel(): String =
    when (lowercase()) {
        "movie" -> stringResource(Res.string.media_movies)
        "series" -> stringResource(Res.string.media_series)
        "anime" -> stringResource(Res.string.media_anime)
        "channel" -> stringResource(Res.string.media_channels)
        "tv" -> stringResource(Res.string.media_tv)
        else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
