package com.nuvio.app.features.anidrama

import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeUiState
import com.nuvio.app.features.home.stableKey

/**
 * Decides which add-on rows on Home are anime or Asian-drama rows. Those rows move into the
 * Ani-drama tab (unless the user chose to keep them on Home too).
 */
object AniDramaRows {

    private val animeWords = listOf(
        "anime", "kitsu", "anilist", "myanimelist", "my anime list", "donghua",
        "crunchyroll", "hidive",
    )
    private val koreanWords = listOf("k-drama", "kdrama", "k drama", "korean", "korea")
    private val chineseWords = listOf("c-drama", "cdrama", "c drama", "chinese", "china", "mandarin", "wuxia", "xianxia")
    private val japaneseWords = listOf("j-drama", "jdrama", "j drama", "japanese drama", "dorama", "japan")
    private val asianWords = listOf("asian drama", "asian series", "mydramalist", "dramacool", "kisskh", "viki")

    /** Which side of the tab a Home row belongs to, or null if it stays on Home. */
    fun regionOf(section: HomeCatalogSection): AniDramaRegion? {
        val type = section.target.contentType.lowercase()
        val text = " ${section.title} ${section.subtitle} ${section.addonName} ".lowercase()
        if (type == "anime" || animeWords.any { it in text }) return AniDramaRegion.Anime
        if (koreanWords.any { it in text }) return AniDramaRegion.KDrama
        if (chineseWords.any { it in text }) return AniDramaRegion.CDrama
        if (japaneseWords.any { it in text }) return AniDramaRegion.JDrama
        if (asianWords.any { it in text }) return AniDramaRegion.KDrama
        return null
    }

    /** Home rows that belong to [region]. */
    fun sectionsFor(region: AniDramaRegion, sections: List<HomeCatalogSection>): List<HomeCatalogSection> =
        sections.filter { regionOf(it) == region }

    /**
     * Home without the moved rows. Hero banner items that only came from moved rows are dropped
     * too, so anime and dramas don't keep showing up at the top of Home.
     */
    fun filterHome(state: HomeUiState, tabAvailable: Boolean, keepRowsOnHome: Boolean): HomeUiState {
        if (!tabAvailable || keepRowsOnHome) return state
        val (moved, kept) = state.sections.partition { regionOf(it) != null }
        if (moved.isEmpty()) return state
        val keptKeys = kept.flatMapTo(HashSet()) { section -> section.items.map { it.stableKey() } }
        val movedKeys = moved.flatMapTo(HashSet()) { section -> section.items.map { it.stableKey() } }
        val hero = state.heroItems.filter { item ->
            val key = item.stableKey()
            key !in movedKeys || key in keptKeys
        }
        return state.copy(sections = kept, heroItems = hero)
    }
}
