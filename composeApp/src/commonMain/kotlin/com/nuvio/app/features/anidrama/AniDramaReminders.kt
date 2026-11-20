package com.nuvio.app.features.anidrama

import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** When a reminder fires, relative to the episode coming out. */
enum class ReminderTiming(val label: String) {
    Release("When it's out"),
    HourAfter("1 hour after"),
    NextMorning("Next morning 9 AM"),
}

/** A "remind me about new episodes" setting for one show. */
@Serializable
data class AniDramaReminder(
    val key: String,
    val title: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val metaType: String,
    val metaId: String,
    /** "anilist" or "tmdb". */
    val sourceKind: String,
    val sourceId: Int,
    val speak: Boolean = true,
    val timing: String = ReminderTiming.Release.name,
    val network: String? = null,
    val nextEpisode: Int? = null,
    val nextAirEpochMs: Long? = null,
) {
    val timingValue: ReminderTiming get() = ReminderTiming.entries.firstOrNull { it.name == timing } ?: ReminderTiming.Release
    val deepLink: String get() = buildMetaDeepLinkUrl(type = metaType, id = metaId)

    val notificationBody: String
        get() = listOfNotNull(title, nextEpisode?.let { "Episode $it" }, network).joinToString(" · ")

    val spokenText: String
        get() = if (nextEpisode != null) "New episode: $title, episode $nextEpisode, is out now."
        else "New episode of $title is out now."
}

/** What the phone does for reminders (set by the Android app). */
interface AniDramaReminderPlatform {
    fun loadReminders(): String?
    fun saveReminders(payload: String)
    /** Schedule (or replace) the alert for [reminder] at [fireAtEpochMs]. */
    fun schedule(reminder: AniDramaReminder, fireAtEpochMs: Long)
    fun cancel(key: String)
    /** Say [text] out loud now (used by "Test voice"). */
    fun speak(text: String)
}

/** Episode reminders for the Ani-drama calendar. Android only; does nothing elsewhere. */
object AniDramaReminders {
    private val log = Logger.withTag("AniDramaReminders")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(AniDramaReminder.serializer())

    private const val HOUR_MS = 60 * 60 * 1000L

    private val _reminders = MutableStateFlow<Map<String, AniDramaReminder>>(emptyMap())
    val reminders: StateFlow<Map<String, AniDramaReminder>> = _reminders.asStateFlow()

    private var platform: AniDramaReminderPlatform? = null

    val isAvailable: Boolean get() = platform != null

    fun install(platform: AniDramaReminderPlatform) {
        this.platform = platform
        val stored = platform.loadReminders()?.let { payload ->
            runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
        }.orEmpty()
        _reminders.value = stored.associateBy { it.key }
    }

    fun testVoice(text: String = "New episode: Moon River, episode 7, is out now.") {
        platform?.speak(text)
    }

    /** Saves [reminder] and schedules its next alert. */
    fun set(reminder: AniDramaReminder) {
        _reminders.value = _reminders.value + (reminder.key to reminder)
        persist()
        schedule(reminder)
    }

    fun remove(key: String) {
        _reminders.value = _reminders.value - key
        persist()
        platform?.cancel(key)
    }

    /**
     * After an alert fires: assume the next episode comes a week later (dramas and anime are
     * usually weekly) until [refreshAll] checks the real date the next time the app opens.
     */
    fun onFired(key: String) {
        val current = _reminders.value[key] ?: return
        val next = current.copy(
            nextEpisode = current.nextEpisode?.plus(1),
            nextAirEpochMs = current.nextAirEpochMs?.plus(7 * 24 * HOUR_MS),
        )
        _reminders.value = _reminders.value + (key to next)
        persist()
        schedule(next)
    }

    /** Looks up the real next episode of every reminded show and reschedules. */
    suspend fun refreshAll() {
        _reminders.value.values.forEach { reminder ->
            val next = runCatching {
                when (reminder.sourceKind) {
                    "anilist" -> AnimeRepository.nextEpisode(reminder.sourceId)
                    else -> DramaRepository.nextEpisode(reminder.sourceId)
                }
            }.onFailure { log.w { "Next episode lookup failed for ${reminder.title}: ${it.message}" } }
                .getOrNull() ?: return@forEach
            val updated = reminder.copy(nextEpisode = next.first, nextAirEpochMs = next.second)
            if (updated != reminder) {
                _reminders.value = _reminders.value + (reminder.key to updated)
                schedule(updated)
            }
        }
        persist()
    }

    fun fireAtEpochMs(reminder: AniDramaReminder): Long? {
        val airAt = reminder.nextAirEpochMs ?: return null
        return when (reminder.timingValue) {
            ReminderTiming.Release -> airAt
            ReminderTiming.HourAfter -> airAt + HOUR_MS
            ReminderTiming.NextMorning -> {
                val day = AniDramaTime.localDayIsoOf(airAt)
                AniDramaTime.localStartOfDayMs(AniDramaTime.addDays(day, 1)) + 9 * HOUR_MS
            }
        }
    }

    private fun schedule(reminder: AniDramaReminder) {
        val platform = platform ?: return
        val fireAt = fireAtEpochMs(reminder)
        if (fireAt == null || fireAt < AniDramaTime.nowEpochMs() - HOUR_MS) {
            platform.cancel(reminder.key)
            return
        }
        platform.schedule(reminder, fireAt)
    }

    private fun persist() {
        platform?.saveReminders(json.encodeToString(serializer, _reminders.value.values.toList()))
    }
}
