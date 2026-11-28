package com.nuvio.app.features.anidrama

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * MugiVerse Ani-Drama search and Discover: anime (AniList) and Korean, Chinese and Japanese
 * dramas (TMDB, by country) only — never the regular movie and TV catalogs Watch uses.
 */
internal object AniDramaSearch {
    val regions = listOf(AniDramaRegion.Anime, AniDramaRegion.KDrama, AniDramaRegion.CDrama, AniDramaRegion.JDrama)

    private fun target(region: AniDramaRegion) = CatalogTarget.Addon(
        manifestUrl = "mugiverse://anidrama",
        contentType = if (region == AniDramaRegion.Anime) "anime" else "series",
        catalogId = "anidrama-${region.name.lowercase()}",
    )

    /** Search results as rows: Anime, K-Drama, C-Drama, J-Drama (empty rows left out). */
    suspend fun search(text: String): List<HomeCatalogSection> = coroutineScope {
        val anime = async { runCatching { AnimeRepository.search(text) }.getOrNull().orEmpty() }
        val dramas = async { runCatching { DramaRepository.search(text) }.getOrNull().orEmpty() }
        val byRegion = mapOf(AniDramaRegion.Anime to anime.await()) + dramas.await()
        regions.mapNotNull { region ->
            val items = byRegion[region].orEmpty().map { it.toMetaPreview() }.distinctBy { it.id }
            if (items.isEmpty()) {
                null
            } else {
                HomeCatalogSection(
                    key = "anidrama-search-${region.name}",
                    title = region.label,
                    subtitle = "",
                    addonName = "MugiVerse",
                    target = target(region),
                    items = items,
                )
            }
        }
    }

    /** Discover in Ani-Drama: what's trending in one region. */
    suspend fun discover(region: AniDramaRegion): List<MetaPreview> = runCatching {
        val page = if (region == AniDramaRegion.Anime) {
            AnimeRepository.page(AniListClient.MediaQuery(sort = "TRENDING_DESC"), 1)
        } else {
            DramaRepository.page(region, DramaRepository.Filter(DramaChip.Popular), 1)
        }
        page?.items.orEmpty().map { it.toMetaPreview() }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}
