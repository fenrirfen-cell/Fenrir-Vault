package com.nuvio.app.features.anidrama

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.supportsResource
import com.nuvio.app.features.home.MetaPreview

/**
 * Turns an Ani-drama poster into the [MetaPreview] that Nuvio's own detail page, long-press menu
 * and library use. Anime prefers a Kitsu ID when one of your add-ons can show Kitsu pages (that's
 * what most anime stream add-ons expect); otherwise it uses IMDb or TMDB like any other show.
 */
internal fun AniDramaItem.toMetaPreview(): MetaPreview {
    val (type, id) = when (val source = source) {
        is AniDramaSource.Tmdb -> "series" to "tmdb:${source.tmdbId}"
        is AniDramaSource.AniList -> source.preferredId()
    }
    return MetaPreview(
        id = id,
        type = type,
        name = title,
        poster = poster,
        banner = backdrop,
        description = description,
        releaseInfo = year,
        genres = genres,
        rawPosterUrl = poster,
    )
}

private fun AniDramaSource.AniList.preferredId(): Pair<String, String> {
    val manifests = AddonRepository.uiState.value.addons.enabledAddons().mapNotNull { it.manifest }
    val defaultType = if (isMovie) "movie" else "series"
    val typeOrder = if (isMovie) listOf("movie", "anime", "series") else listOf("anime", "series", "movie")

    fun addonType(id: String): String? =
        typeOrder.firstOrNull { type -> manifests.any { it.supportsResource("meta", type, id) } }

    kitsuId?.let { kitsu ->
        val id = "kitsu:$kitsu"
        addonType(id)?.let { return it to id }
    }
    imdbId?.let { return defaultType to it }
    tmdbId?.let { return defaultType to "tmdb:$it" }
    val aniListId = "anilist:$aniListId"
    addonType(aniListId)?.let { return it to aniListId }
    malId?.let { mal ->
        val id = "mal:$mal"
        addonType(id)?.let { return it to id }
    }
    kitsuId?.let { return "anime" to "kitsu:$it" }
    return "anime" to aniListId
}
