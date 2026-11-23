package com.nuvio.app.readmode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Howl when the app opens": the wolf howl on a fresh start (the phone side plays it and saves the choice). */
object FenrirHowl {
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Set by the Android side to save the choice. */
    var save: ((Boolean) -> Unit)? = null

    /** Set by the Android side to play the howl once (used for a preview when turning it on). */
    var play: (() -> Unit)? = null

    fun load(value: Boolean) { _enabled.value = value }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        save?.invoke(on)
        if (on) play?.invoke()
    }
}
