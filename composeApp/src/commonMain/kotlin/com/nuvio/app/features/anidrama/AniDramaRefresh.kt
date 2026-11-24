package com.nuvio.app.features.anidrama

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fenrir: reloading the Ani-drama tab. Pull down to refresh it yourself; it also refreshes by
 * itself when you come back to it after [STALE_MS], so airing lists and new episodes stay current.
 */
internal object AniDramaRefresh {
    private const val STALE_MS = 30L * 60 * 1000

    /** Bumped on every refresh; the screen rebuilds its lists when it changes. */
    val generation = MutableStateFlow(0)
    private var lastLoadMs = 0L

    fun refresh() {
        AnimeRepository.clearLists()
        DramaRepository.clearLists()
        lastLoadMs = AniDramaTime.nowEpochMs()
        generation.value += 1
    }

    /** Called when the tab opens: refresh if what's shown is old. */
    fun refreshIfStale() {
        val now = AniDramaTime.nowEpochMs()
        if (lastLoadMs == 0L) {
            lastLoadMs = now
        } else if (now - lastLoadMs > STALE_MS) {
            refresh()
        }
    }
}
