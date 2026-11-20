package com.nuvio.app.assistant

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons

enum class AssistantAction { PLAY, RESUME, NEXT, LATEST }

/** One understood request, e.g. "next episode of The Rookie in English". */
data class AssistantCommand(
    val action: AssistantAction,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val year: String? = null,
    val wantsMovie: Boolean = false,
    val wantsSeries: Boolean = false,
    val fromStart: Boolean = false,
    val language: String? = null,
    val audio: AssistantAudio? = null,
    val qualityFirst: String? = null,
    val maxSizeGb: Double? = null,
    val cachedOnly: Boolean? = null,
    val avoidHdr: Boolean? = null,
    val addonHint: String? = null,
    val unsupported: Boolean = false,
) {
    fun effectivePreferences(base: AssistantPreferences): AssistantPreferences = base.copy(
        language = language ?: base.language,
        animeAudio = audio ?: base.animeAudio,
        qualityOrder = qualityFirst?.let { first -> listOf(first) + base.qualityOrder.filter { it != first } }
            ?: base.qualityOrder,
        maxSizeGb = maxSizeGb ?: base.maxSizeGb,
        cachedOnly = cachedOnly ?: base.cachedOnly,
        avoidHdr = avoidHdr ?: base.avoidHdr,
        addonHint = addonHint,
    )

    /** "English subs" etc.: the audio language is not enforced. */
    val relaxLanguage: Boolean
        get() = audio == AssistantAudio.SUB
}

/** Fenrir's own command understanding: plain rules, no AI service, works offline. */
object AssistantCommandParser {

    private const val LANGUAGE_WORDS =
        "english|eng|spanish|espa\u00f1ol|espanol|castellano|latino|french|fran\u00e7ais|francais|german|deutsch|" +
            "italian|italiano|portuguese|portugu\u00eas|portugues|brazilian|japanese|korean|hindi|russian|chinese|mandarin|arabic"

    fun languageCode(word: String): String? = when (word.lowercase()) {
        "english", "eng" -> "en"
        "spanish", "espa\u00f1ol", "espanol", "castellano", "latino" -> "es"
        "french", "fran\u00e7ais", "francais" -> "fr"
        "german", "deutsch" -> "de"
        "italian", "italiano" -> "it"
        "portuguese", "portugu\u00eas", "portugues", "brazilian" -> "pt"
        "japanese" -> "ja"
        "korean" -> "ko"
        "hindi" -> "hi"
        "russian" -> "ru"
        "chinese", "mandarin" -> "zh"
        "arabic" -> "ar"
        else -> null
    }

    fun parse(input: String): AssistantCommand? {
        var text = " " + input.lowercase()
            .replace('\u2019', '\'')
            .replace(Regex("[?!.,;:\"]"), " ") + " "

        fun take(regex: Regex): MatchResult? {
            val match = regex.find(text) ?: return null
            text = text.replaceRange(match.range, " ")
            return match
        }

        fun has(regex: Regex): Boolean = take(regex) != null
        val mentionsEpisode = Regex("\\b(episode|ep|eps)\\b").containsMatchIn(text)

        if (Regex("\\b(something|anything|stuff) (like|similar to)\\b|\\brecommend\\b|\\bsuggest\\b").containsMatchIn(text)) {
            return AssistantCommand(action = AssistantAction.PLAY, title = "", unsupported = true)
        }

        val fromStart = has(Regex("\\b(from the (start|beginning)|start over|from scratch|restart it|from episode start)\\b"))

        // Language and audio
        var language: String? = null
        var audio: AssistantAudio? = null
        take(Regex("\\b(?:in |with )?($LANGUAGE_WORDS)\\s+(dub|dubbed|audio|sub|subs|subbed|subtitles|subtitled)\\b"))?.let { m ->
            val code = languageCode(m.groupValues[1])
            val kind = m.groupValues[2]
            if (kind.startsWith("sub")) {
                audio = AssistantAudio.SUB
                language = "any"
            } else {
                audio = AssistantAudio.DUB
                language = code
            }
        }
        if (language == null) {
            take(Regex("\\b(?:in |with )?($LANGUAGE_WORDS)(?: audio| language| version)?\\b"))?.let { m ->
                language = languageCode(m.groupValues[1])
            }
        }
        if (audio == null) {
            if (has(Regex("\\b(dub|dubbed)\\b"))) audio = AssistantAudio.DUB
            else if (has(Regex("\\b(sub|subs|subbed|subtitled|subtitles)\\b"))) {
                audio = AssistantAudio.SUB
                if (language == null) language = "any"
            }
        }
        if (has(Regex("\\b(any|whatever) language\\b"))) language = "any"

        // Quality, size, cache, HDR
        val qualityFirst = when {
            has(Regex("\\b(?:in )?(4k|2160p|uhd|ultra hd)\\b")) -> "2160p"
            has(Regex("\\b(?:in )?(1080p|1080|full hd|fhd)\\b")) -> "1080p"
            has(Regex("\\b(?:in )?(720p|720)\\b")) -> "720p"
            has(Regex("\\b(?:in )?(480p|sd quality)\\b")) -> "480p"
            else -> null
        }
        val maxSizeGb = take(Regex("\\b(?:under|less than|below|max|smaller than|no bigger than)\\s+(\\d+(?:\\.\\d+)?)\\s*(gb|gigs?|mb)\\b"))
            ?.let { m ->
                val value = m.groupValues[1].toDoubleOrNull()
                if (value == null) null else if (m.groupValues[2] == "mb") value / 1024.0 else value
            }
        val cachedOnly = when {
            has(Regex("\\b(any link|uncached|not cached|even if not cached)\\b")) -> false
            has(Regex("\\b(cached(?: only)?|instant)\\b")) -> true
            else -> null
        }
        val avoidHdr = when {
            has(Regex("\\b(?:no|not|without) hdr\\b|\\bsdr\\b")) -> true
            has(Regex("\\b(?:in |with )?(hdr|dolby vision)\\b")) -> false
            else -> null
        }

        // Add-on ("from Torrentio")
        var addonHint: String? = null
        Regex("\\b(?:from|on|using|via|with) ([a-z0-9][a-z0-9+-]{2,})\\b").findAll(text).toList().forEach { m ->
            if (addonHint != null) return@forEach
            val word = m.groupValues[1]
            val known = runCatching {
                AddonRepository.uiState.value.addons.enabledAddons().any { addon ->
                    addon.displayTitle.lowercase().contains(word)
                }
            }.getOrDefault(false)
            if (known) {
                addonHint = word
                text = text.replace(m.value, " ")
            }
        }

        // Season / episode
        var season: Int? = null
        var episode: Int? = null
        take(Regex("\\bs(\\d{1,2})\\s*e(\\d{1,4})\\b"))?.let { m ->
            season = m.groupValues[1].toInt(); episode = m.groupValues[2].toInt()
        } ?: take(Regex("\\b(\\d{1,2})x(\\d{1,3})\\b"))?.let { m ->
            season = m.groupValues[1].toInt(); episode = m.groupValues[2].toInt()
        } ?: take(Regex("\\bseason (\\d{1,2})(?:,? (?:episode|ep|e) ?(\\d{1,4}))?\\b"))?.let { m ->
            season = m.groupValues[1].toInt(); episode = m.groupValues[2].toIntOrNull()
        } ?: take(Regex("\\b(?:episode|ep) ?(\\d{1,4})(?: of season (\\d{1,2}))?\\b"))?.let { m ->
            episode = m.groupValues[1].toInt(); season = m.groupValues[2].toIntOrNull()
        }

        // What to play
        var action = when {
            has(Regex("\\b(?:the )?next (?:episode|ep|one)(?: of)?\\b")) -> AssistantAction.NEXT
            has(Regex("\\b(?:the )?(?:latest|newest|new|last|most recent) (?:episode|ep)(?: of)?\\b")) -> AssistantAction.LATEST
            has(Regex("\\b(?:the )?(?:current|same) (?:episode|ep|one)(?: of)?\\b|\\b(?:continue|resume|keep watching|where i left off|where i left|pick up)\\b")) -> AssistantAction.RESUME
            else -> AssistantAction.PLAY
        }

        // Kept in the title too ("Blade Runner 2049"); only used to break ties.
        val year = Regex("\\b(19[2-9]\\d|20[0-4]\\d)\\b").find(text)?.groupValues?.get(1)

        val wantsMovie = Regex("\\b(movie|film)\\b").containsMatchIn(text)
        val wantsSeries = Regex("\\b(show|series|anime|episode)\\b").containsMatchIn(text) ||
            season != null || episode != null || action == AssistantAction.NEXT || action == AssistantAction.LATEST
        take(Regex("\\b(?:the movie|the film|the show|the series|tv show|tv series|the anime)\\b"))

        // Filler words around the title
        val leading = Regex(
            "^\\s*(?:hey fenrir|fenrir|ok|okay|hey|please|can you|could you|would you|will you|i want to|i wanna|" +
                "i'd like to|id like to|let's|lets|go ahead and|just|play|put on|watch|start|stream|open|find|show me|queue|throw on|the episode of|episode of)\\b",
        )
        var guard = 0
        while (guard++ < 12) {
            val match = leading.find(text) ?: break
            text = text.replaceRange(match.range, " ")
        }
        val trailing = Regex("\\b(?:please|for me|now|right now|thanks|thank you|episode|ep|again)\\s*$")
        guard = 0
        while (guard++ < 8) {
            val match = trailing.find(text) ?: break
            text = text.replaceRange(match.range, " ")
        }
        // "play the next The Rookie episode", "The Rookie next episode", "latest Frieren episode"
        if (action == AssistantAction.PLAY && mentionsEpisode) {
            val keyword = Regex("^\\s*(?:the )?(next|latest|newest|current)\\b").find(text)
                ?: Regex("\\b(next|latest|newest|current)(?: one)?\\s*$").find(text)
            if (keyword != null) {
                action = when (keyword.groupValues[1]) {
                    "next" -> AssistantAction.NEXT
                    "current" -> AssistantAction.RESUME
                    else -> AssistantAction.LATEST
                }
                text = text.replaceRange(keyword.range, " ")
            }
        }
        text = text.replace(Regex("^\\s*(?:movie|film)\\s+"), " ")
            .replace(Regex("\\s+(?:movie|film|show|series)\\s*$"), " ")

        val title = text.replace(Regex("\\s+"), " ").trim()
        if (title.isEmpty() && action == AssistantAction.PLAY && season == null && episode == null) return null

        return AssistantCommand(
            action = action,
            title = title,
            season = season,
            episode = episode,
            year = year,
            wantsMovie = wantsMovie && !wantsSeries,
            wantsSeries = wantsSeries && !wantsMovie,
            fromStart = fromStart,
            language = language,
            audio = audio,
            qualityFirst = qualityFirst,
            maxSizeGb = maxSizeGb,
            cachedOnly = cachedOnly,
            avoidHdr = avoidHdr,
            addonHint = addonHint,
        )
    }
}
