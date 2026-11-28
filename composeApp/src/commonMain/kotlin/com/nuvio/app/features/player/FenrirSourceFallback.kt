package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MugiVerse: "try the next source automatically" for any link, including ones you picked
 * yourself (Nuvio's own fallback only covers auto-selected sources). The Android app turns it on
 * with [install]; elsewhere it stays off and Nuvio behaves as before.
 */
object FenrirSourceFallback {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    var available: Boolean = false
        private set

    private var persist: ((Boolean) -> Unit)? = null

    fun install(enabled: Boolean, persist: (Boolean) -> Unit) {
        available = true
        _enabled.value = enabled
        this.persist = persist
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        persist?.invoke(enabled)
    }

    /** Whether Fenrir's fallback applies with these player settings (internal player only). */
    internal fun active(settings: PlayerSettingsUiState): Boolean =
        available && _enabled.value && !settings.externalPlayerEnabled

    /** Settings to pick the next source with: your add-on order when you pick sources by hand. */
    internal fun candidateSettings(settings: PlayerSettingsUiState): PlayerSettingsUiState =
        if (settings.streamAutoPlayMode == StreamAutoPlayMode.MANUAL) {
            settings.copy(
                streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
                streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,
            )
        } else {
            settings
        }
}
