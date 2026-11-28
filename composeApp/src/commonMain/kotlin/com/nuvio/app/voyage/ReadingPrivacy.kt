package com.nuvio.app.voyage

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

object MugiPrivacyColors {
    val Navy = Color(0xFF06141E)
    val Surface = Color(0xFF10202D)
    val Gold = Color(0xFFD4AF65)
    val BrightGold = Color(0xFFFFD478)
    val Ivory = Color(0xFFF5E8D0)
    val Secondary = Color(0xFFB8B9B9)
    val Red = Color(0xFFC92D35)
    val Purple = Color(0xFF993BDF)
}

@Serializable
data class ReadingPrivacySettings(
    val keepReadingPrivate: Boolean = false,
    val requireAuthentication: Boolean = true,
)

enum class PrivacySurface { WATCH, ANI_DRAMA, READ }

data class ReadingPrivacyState(val settings: ReadingPrivacySettings, val temporarilyUnlocked: Boolean = false) {
    fun canShowReading(surface: PrivacySurface): Boolean =
        surface == PrivacySurface.READ || !settings.keepReadingPrivate || temporarilyUnlocked
}

/**
 * One privacy rule for every cross-mode screen (Continue, Surprise Me, Captain's Log, Bounties,
 * Offline Voyage): reading is left out of the data before any screen is built.
 *
 * The unlock lives in memory only: it ends after 5 minutes (monotonic clock), when the app goes to
 * the background, on "Lock again", on any settings change, and when the app process ends.
 */
object ReadingPrivacy {
    private const val KEY = "reading_privacy"
    private val json = Json { ignoreUnknownKeys = true }
    private val timeout = 5.minutes

    private val _state = MutableStateFlow(ReadingPrivacyState(ReadingPrivacySettings()))
    val state: StateFlow<ReadingPrivacyState> = _state.asStateFlow()
    private var unlockedAt: TimeSource.Monotonic.ValueTimeMark? = null
    private var loaded = false
    private var asking = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val s = runCatching { VoyageBridge.load(KEY)?.let { json.decodeFromString(ReadingPrivacySettings.serializer(), it) } }.getOrNull()
        if (s != null) _state.value = ReadingPrivacyState(s)
    }

    /** Current state, relocking first if the 5 minutes are up. */
    fun current(): ReadingPrivacyState {
        ensureLoaded()
        val mark = unlockedAt
        if (_state.value.temporarilyUnlocked && (mark == null || mark.elapsedNow() >= timeout)) lock()
        return _state.value
    }

    fun canShowReading(surface: PrivacySurface): Boolean = current().canShowReading(surface)

    fun lock() {
        unlockedAt = null
        if (_state.value.temporarilyUnlocked) _state.value = _state.value.copy(temporarilyUnlocked = false)
    }

    fun onAppBackground() = lock()

    /** Shows the phone's own fingerprint / PIN prompt (when asked for). Returns null on success. */
    suspend fun requestUnlock(): String? {
        ensureLoaded()
        if (_state.value.settings.requireAuthentication) {
            val auth = VoyageBridge.authenticate ?: return "Unlocking isn't available here."
            if (asking) return null
            asking = true
            val err = try { auth("Unlock private reading") } finally { asking = false }
            if (err != null) return err
        }
        unlockedAt = TimeSource.Monotonic.markNow()
        _state.value = _state.value.copy(temporarilyUnlocked = true)
        return null
    }

    /** Saves new settings (relocks). Lowering protection while a lock is required asks first. */
    suspend fun update(next: ReadingPrivacySettings): String? {
        ensureLoaded()
        val now = _state.value.settings
        val lowering = (now.keepReadingPrivate && !next.keepReadingPrivate) || (now.requireAuthentication && !next.requireAuthentication)
        if (lowering && now.keepReadingPrivate && now.requireAuthentication) {
            val auth = VoyageBridge.authenticate ?: return "Unlocking isn't available here."
            if (asking) return null
            asking = true
            val err = try { auth("Change reading privacy") } finally { asking = false }
            if (err != null) return err
        }
        unlockedAt = null
        _state.value = ReadingPrivacyState(next, temporarilyUnlocked = false)
        runCatching { VoyageBridge.save(KEY, json.encodeToString(ReadingPrivacySettings.serializer(), next)) }
        return null
    }
}
