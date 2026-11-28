package com.nuvio.app.mugi

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.nuvio.app.voyage.VoyageBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** MugiVerse Appearance & Motion choices (kept on this phone). Sound effects start off. */
@Serializable
data class MugiMotionSettings(
    val animations: Boolean = true,
    val haptics: Boolean = true,
    val sounds: Boolean = false,
    val reduceDecorations: Boolean = false,
    val lowPower: Boolean = false,
)

object MugiMotion {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _settings = MutableStateFlow(MugiMotionSettings())
    val settings: StateFlow<MugiMotionSettings> = _settings.asStateFlow()

    /** Set by the Android app: a short system click sound (only used when sound effects are on). */
    var playClick: (() -> Unit)? = null

    /** Reads the saved choices (called once the app's storage is wired up). */
    fun load() {
        VoyageBridge.load(KEY)?.let { raw ->
            runCatching { json.decodeFromString(MugiMotionSettings.serializer(), raw) }.getOrNull()?.let { _settings.value = it }
        }
    }

    fun update(next: MugiMotionSettings) {
        _settings.value = next
        VoyageBridge.save(KEY, json.encodeToString(MugiMotionSettings.serializer(), next))
    }

    /** True when motion should be skipped: animations off, low-power mode, or Android's "Remove animations". */
    fun reduceMotion(): Boolean {
        val s = _settings.value
        return !s.animations || s.lowPower || MugiStyle.systemReduceMotion()
    }

    /** False when the extra background details (sails, maps, ship's wheel...) should be left out. */
    fun showDecorations(): Boolean {
        val s = _settings.value
        return !s.reduceDecorations && !s.lowPower
    }

    /** A tap's feedback: a light vibration and/or a click, each only if switched on. */
    fun tap(haptic: HapticFeedback?) {
        val s = _settings.value
        if (s.haptics) runCatching { haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        if (s.sounds) runCatching { playClick?.invoke() }
    }

    private const val KEY = "mugi_motion"
}
