package com.nuvio.app.assistant

import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Everything needed to open Nuvio's normal stream screen for one title or episode. */
data class AssistantLaunch(
    val type: String,
    val videoId: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val resumePositionMs: Long? = null,
    val resumeProgressFraction: Float? = null,
    val startFromBeginning: Boolean = false,
)

/** How the stream screen should rank streams for an assistant request. */
data class AssistantStreamRequest(
    val prefs: AssistantPreferences,
    /** True when the user asked for subtitles ("English subs"): audio language is not enforced. */
    val relaxLanguage: Boolean,
    /** The exact episode asked for (null for movies): streams labelled otherwise are dropped. */
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * Link between the assistant and the rest of the app.
 *  - [launcher] is set by MainAppContent and opens the stream screen.
 *  - [startVoice] is set by the Android activity (phone speech-to-text).
 *  - [arm]/[claim]: the stream screen asks whether the request it is loading came from the
 *    assistant, and if so ranks streams with the assistant's preferences.
 */
object AssistantPlayback {
    var launcher: ((AssistantLaunch) -> Unit)? = null
    var startVoice: (((String) -> Unit) -> Unit)? = null

    private class Armed(
        val launch: AssistantLaunch,
        val request: AssistantStreamRequest,
        val mark: TimeMark,
    )

    private var armed: Armed? = null

    fun arm(launch: AssistantLaunch, request: AssistantStreamRequest) {
        armed = Armed(launch, request, TimeSource.Monotonic.markNow())
    }

    fun claim(
        videoId: String,
        parentMetaId: String?,
        season: Int?,
        episode: Int?,
    ): AssistantStreamRequest? {
        val current = armed ?: return null
        if (current.mark.elapsedNow() > 3.minutes) {
            armed = null
            return null
        }
        val launch = current.launch
        val sameItem = videoId == launch.videoId ||
            (
                parentMetaId != null &&
                    parentMetaId == launch.parentMetaId &&
                    season == launch.season &&
                    episode == launch.episode
                )
        return if (sameItem) current.request else null
    }
}
