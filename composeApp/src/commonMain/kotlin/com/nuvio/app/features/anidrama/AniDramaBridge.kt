package com.nuvio.app.features.anidrama

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fenrir Read's "Ani-drama" tab: an Otaku-style anime browser plus K-, C- and J-drama browsers.
 *
 * The Android app turns it on at startup with [install]. Until then (iOS, desktop, upstream
 * Nuvio builds) the tab is hidden and Home is left exactly as it was.
 */
object AniDramaBridge {
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _keepRowsOnHome = MutableStateFlow(false)

    /** When false (default), anime and drama rows from add-ons move off Home into the tab. */
    val keepRowsOnHome: StateFlow<Boolean> = _keepRowsOnHome.asStateFlow()

    private var persistKeepRowsOnHome: ((Boolean) -> Unit)? = null

    const val TAB_LABEL = "Ani-drama"

    fun install(keepRowsOnHome: Boolean, persist: (Boolean) -> Unit) {
        _keepRowsOnHome.value = keepRowsOnHome
        persistKeepRowsOnHome = persist
        _isAvailable.value = true
    }

    private val _openRequested = MutableStateFlow(false)

    /** True when another screen (e.g. the Read side's switch) asked to show Ani-drama. */
    val openRequested: StateFlow<Boolean> = _openRequested.asStateFlow()

    fun requestOpen() {
        _openRequested.value = true
    }

    fun consumeOpenRequest() {
        _openRequested.value = false
    }

    fun setKeepRowsOnHome(keep: Boolean) {
        _keepRowsOnHome.value = keep
        persistKeepRowsOnHome?.invoke(keep)
    }
}
