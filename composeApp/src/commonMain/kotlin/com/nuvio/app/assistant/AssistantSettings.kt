package com.nuvio.app.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class AssistantAudio { DUB, SUB, EITHER }

/** The user's usual choices for picking streams. Saved on the phone only. */
@Serializable
data class AssistantPreferences(
    /** Language code ("en", "es", ...) or "any". */
    val language: String = "en",
    val animeAudio: AssistantAudio = AssistantAudio.DUB,
    val qualityOrder: List<String> = DEFAULT_QUALITY_ORDER,
    val cachedOnly: Boolean = true,
    val maxSizeGb: Double? = null,
    val skipLowQuality: Boolean = true,
    val avoidHdr: Boolean = false,
    val countdownSeconds: Int = 3,
    /** Only set for a single request ("from Torrentio"); never saved as a usual choice. */
    val addonHint: String? = null,
)

val DEFAULT_QUALITY_ORDER = listOf("1080p", "2160p", "720p", "480p")

/**
 * Fenrir assistant settings. The Android app plugs in [load] and [save] (SharedPreferences),
 * so nothing here depends on a platform or an outside service.
 */
object AssistantSettings {
    var load: (() -> String?)? = null
    var save: ((String) -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val _prefs = MutableStateFlow(AssistantPreferences())
    val prefs: StateFlow<AssistantPreferences> = _prefs.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = load?.invoke() ?: return
        runCatching { json.decodeFromString(AssistantPreferences.serializer(), raw) }
            .getOrNull()
            ?.let { _prefs.value = it.copy(addonHint = null) }
    }

    fun update(transform: (AssistantPreferences) -> AssistantPreferences) {
        ensureLoaded()
        val next = transform(_prefs.value).copy(addonHint = null)
        _prefs.value = next
        runCatching { save?.invoke(json.encodeToString(AssistantPreferences.serializer(), next)) }
    }
}
