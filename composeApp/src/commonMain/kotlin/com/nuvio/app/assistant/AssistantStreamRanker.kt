package com.nuvio.app.assistant

import com.nuvio.app.features.streams.StreamItem

/**
 * Ranks the streams Nuvio found using the assistant's preferences. Streams that break a
 * must-have (wrong language, CAM copy, too big, not cached when cached-only) are dropped;
 * if nothing is left, Nuvio shows the normal stream list instead of guessing.
 */
object AssistantStreamRanker {

    private fun flag(countryCode: String): String = buildString {
        countryCode.uppercase().forEach { letter ->
            val codePoint = 0x1F1E6 + (letter - 'A')
            val offset = codePoint - 0x10000
            append(Char(0xD800 + (offset shr 10)))
            append(Char(0xDC00 + (offset and 0x3FF)))
        }
    }

    private fun languageRegex(words: String, vararg flags: String): Regex {
        val flagPart = flags.joinToString("") { "|" + flag(it) }
        return Regex("(?:\\b(?:$words)\\b$flagPart)", RegexOption.IGNORE_CASE)
    }

    private val LANGUAGES: Map<String, Regex> = mapOf(
        "en" to languageRegex("eng|english", "GB", "US"),
        "es" to languageRegex("spa|spanish|espa\u00f1ol|espanol|castellano|latino|lat|esp", "ES", "MX"),
        "fr" to languageRegex("fre|fra|french|fran\u00e7ais|francais|vf|vff|vfq|truefrench|vostfr", "FR"),
        "de" to languageRegex("ger|deu|german|deutsch", "DE"),
        "it" to languageRegex("ita|italian|italiano", "IT"),
        "pt" to languageRegex("por|portuguese|portugu\u00eas|dublado|pt-br|ptbr", "BR", "PT"),
        "ja" to languageRegex("jpn|jap|japanese", "JP"),
        "ko" to languageRegex("kor|korean", "KR"),
        "hi" to languageRegex("hin|hindi", "IN"),
        "ru" to languageRegex("rus|russian", "RU"),
        "zh" to languageRegex("chi|chinese|mandarin|cantonese", "CN"),
        "ar" to languageRegex("ara|arabic", "SA"),
    )
    private val LANGUAGE_NAMES = mapOf(
        "en" to "english", "es" to "spanish", "fr" to "french", "de" to "german", "it" to "italian",
        "pt" to "portuguese", "ja" to "japanese", "ko" to "korean", "hi" to "hindi", "ru" to "russian",
        "zh" to "chinese", "ar" to "arabic",
    )
    private val MULTI = Regex("\\b(multi|multi-audio|dual[ ._-]?audio|dual)\\b")
    private val LOW_QUALITY = Regex("\\b(cam|camrip|hdcam|ts|telesync|hdts|tc|telecine|scr|screener|dvdscr)\\b")
    private val HDR = Regex("\\b(hdr|hdr10|hdr10\\+|dv|dovi|dolby ?vision)\\b")
    private val READY = Regex("\\[(?:rd|tb|pm|ad|dl|ed|oc)\\+\\]|\u26a1|\\binstant\\b|\\bcached\\b")
    private val DUB = Regex("\\b(dub|dubbed|dual[ ._-]?audio|eng(?:lish)? audio)\\b")
    private val SUB = Regex("\\b(sub|subbed|subs|vostfr|jpn|japanese)\\b")
    private val SIZE = Regex("(\\d+(?:\\.\\d+)?)\\s?(gb|gib|mb|mib)\\b")
    private val RES_2160 = Regex("\\b(2160p|4k|uhd)\\b")
    private val RES_1080 = Regex("\\b1080[pi]?\\b")
    private val RES_720 = Regex("\\b720p?\\b")
    private val RES_480 = Regex("\\b(480p|576p|sd)\\b")

    fun rank(streams: List<StreamItem>, request: AssistantStreamRequest): List<StreamItem> =
        streams.mapIndexedNotNull { index, stream ->
            score(stream, request)?.let { Triple(stream, it, index) }
        }
            .sortedWith(compareByDescending<Triple<StreamItem, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }

    private fun score(stream: StreamItem, request: AssistantStreamRequest): Int? {
        val prefs = request.prefs
        val text = stream.searchText()
        val parsed = stream.clientResolve?.stream?.raw?.parsed
        val parsedLanguages = parsed?.languages.orEmpty().map { it.lowercase() }
        var score = 0

        if (!matchesEpisode(stream, text, request.season, request.episode)) return null

        if (prefs.skipLowQuality && LOW_QUALITY.containsMatchIn(text)) return null

        val sizeGb = stream.sizeBytes(text)?.let { it / (1024.0 * 1024.0 * 1024.0) }
        if (prefs.maxSizeGb != null && sizeGb != null && sizeGb > prefs.maxSizeGb) return null

        val ready = stream.isReady(text)
        if (prefs.cachedOnly && !ready) return null
        if (ready) score += 50

        val target = prefs.language
        if (target != "any" && !request.relaxLanguage) {
            val mentioned = LANGUAGES.filter { (code, regex) ->
                regex.containsMatchIn(text) || code in parsedLanguages ||
                    LANGUAGE_NAMES[code]?.let { it in parsedLanguages } == true
            }.keys
            val multi = MULTI.containsMatchIn(text) || parsedLanguages.any { it.startsWith("multi") || it.startsWith("dual") }
            when {
                target in mentioned -> score += 30
                multi -> score += 10
                // Most English releases are not labelled at all: unlabelled counts as original language.
                target == "en" && mentioned.isEmpty() -> Unit
                else -> return null
            }
        }

        val resolution = stream.resolution(text, parsed?.resolution)
        val rank = resolution?.let { prefs.qualityOrder.indexOf(it) } ?: -1
        score += if (rank >= 0) (prefs.qualityOrder.size - rank) * 100 else 10

        if (prefs.avoidHdr && HDR.containsMatchIn(text)) score -= 60
        when (prefs.animeAudio) {
            AssistantAudio.DUB -> if (DUB.containsMatchIn(text)) score += 40
            AssistantAudio.SUB -> if (SUB.containsMatchIn(text)) score += 40
            AssistantAudio.EITHER -> Unit
        }
        prefs.addonHint?.let { hint ->
            if (stream.addonName.lowercase().contains(hint.lowercase())) score += 500
        }
        return score
    }

    private val EPISODE_TAG = Regex("\\bs(\\d{1,2})\\s?e(\\d{1,4})(?:\\s?-\\s?e?(\\d{1,4}))?\\b")
    private val CROSS_TAG = Regex("\\b(\\d{1,2})x(\\d{2,3})\\b")
    private val WORDY_TAG = Regex("\\bseason (\\d{1,2}) episode (\\d{1,4})\\b")
    private val SEASON_TAG = Regex("\\b(?:s(\\d{1,2})|season (\\d{1,2}))\\b")

    /**
     * Strict episode check. A stream clearly labelled as another episode or season is rejected;
     * the right episode, a pack of the right season, or an unlabelled stream is accepted.
     * For movies (season == null) anything labelled with an episode is rejected.
     */
    private fun matchesEpisode(stream: StreamItem, text: String, season: Int?, episode: Int?): Boolean {
        val resolve = stream.clientResolve
        val parsed = resolve?.stream?.raw?.parsed
        if (season == null || episode == null) {
            if (resolve?.episode != null || parsed?.episodes?.isNotEmpty() == true) return false
            return !EPISODE_TAG.containsMatchIn(text) && !WORDY_TAG.containsMatchIn(text)
        }
        if (resolve?.season != null && resolve.episode != null) {
            return resolve.season == season && resolve.episode == episode
        }
        if (parsed != null && parsed.episodes.isNotEmpty()) {
            return (parsed.seasons.isEmpty() || season in parsed.seasons) && episode in parsed.episodes
        }
        val tagged = mutableListOf<Triple<Int, Int, Int>>()
        EPISODE_TAG.findAll(text).forEach { m ->
            val s = m.groupValues[1].toInt()
            val first = m.groupValues[2].toInt()
            val last = m.groupValues[3].toIntOrNull() ?: first
            tagged += Triple(s, minOf(first, last), maxOf(first, last))
        }
        CROSS_TAG.findAll(text).forEach { m ->
            val e = m.groupValues[2].toInt()
            tagged += Triple(m.groupValues[1].toInt(), e, e)
        }
        WORDY_TAG.findAll(text).forEach { m ->
            val e = m.groupValues[2].toInt()
            tagged += Triple(m.groupValues[1].toInt(), e, e)
        }
        if (tagged.isNotEmpty()) {
            return tagged.any { (s, from, to) -> s == season && episode in from..to }
        }
        if (parsed != null && parsed.seasons.isNotEmpty()) return season in parsed.seasons
        val packSeasons = SEASON_TAG.findAll(text)
            .mapNotNull { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() }
            .toList()
        if (packSeasons.isNotEmpty()) return season in packSeasons
        return true
    }

    private fun StreamItem.searchText(): String = buildString {
        append(addonName).append(' ')
        append(name.orEmpty()).append(' ')
        append(title.orEmpty()).append(' ')
        append(description.orEmpty()).append(' ')
        append(behaviorHints.filename.orEmpty()).append(' ')
        debridCacheStatus?.cachedName?.let { append(it).append(' ') }
        clientResolve?.let { resolve ->
            append(resolve.torrentName.orEmpty()).append(' ')
            append(resolve.filename.orEmpty()).append(' ')
            resolve.stream?.raw?.let { raw ->
                append(raw.torrentName.orEmpty()).append(' ')
                append(raw.filename.orEmpty()).append(' ')
                raw.parsed?.let { parsed ->
                    append(parsed.resolution.orEmpty()).append(' ')
                    append(parsed.quality.orEmpty()).append(' ')
                    parsed.languages.forEach { append(it).append(' ') }
                    parsed.audio.forEach { append(it).append(' ') }
                    parsed.hdr.forEach { append(it).append(' ') }
                }
            }
        }
    }.lowercase().replace('.', ' ').replace('_', ' ')

    private fun StreamItem.sizeBytes(text: String): Long? {
        behaviorHints.videoSize?.takeIf { it > 0 }?.let { return it }
        debridCacheStatus?.cachedSize?.takeIf { it > 0 }?.let { return it }
        clientResolve?.stream?.raw?.size?.takeIf { it > 0 }?.let { return it }
        val match = SIZE.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val bytes = if (unit.startsWith("g")) value * 1024 * 1024 * 1024 else value * 1024 * 1024
        return bytes.toLong()
    }

    private fun StreamItem.isReady(text: String): Boolean =
        playableDirectUrl != null || isDirectDebridStream || isCachedDebridTorrentStream || READY.containsMatchIn(text)

    private fun StreamItem.resolution(text: String, parsedResolution: String?): String? {
        val source = (parsedResolution.orEmpty() + " " + text).lowercase()
        return when {
            RES_2160.containsMatchIn(source) -> "2160p"
            RES_1080.containsMatchIn(source) -> "1080p"
            RES_720.containsMatchIn(source) -> "720p"
            RES_480.containsMatchIn(source) -> "480p"
            else -> null
        }
    }

    private val DISPLAY_LANGUAGE = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
        "pt" to "Portuguese", "ja" to "Japanese", "ko" to "Korean", "hi" to "Hindi", "ru" to "Russian",
        "zh" to "Chinese", "ar" to "Arabic",
    )

    fun languageLabel(code: String): String = if (code == "any") "Any language" else DISPLAY_LANGUAGE[code] ?: code

    /** Short summary for the countdown card, e.g. "English - 1080p - cached". */
    fun describe(request: AssistantStreamRequest): String {
        val prefs = request.prefs
        val parts = mutableListOf<String>()
        if (request.relaxLanguage) parts += "subtitled" else parts += languageLabel(prefs.language)
        prefs.qualityOrder.firstOrNull()?.let { parts += if (it == "2160p") "4K" else it }
        if (prefs.cachedOnly) parts += "cached"
        prefs.maxSizeGb?.let { parts += "under ${formatGb(it)} GB" }
        if (prefs.avoidHdr) parts += "no HDR"
        prefs.addonHint?.let { parts += "from $it" }
        return parts.joinToString(" \u00b7 ")
    }

    private fun formatGb(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else ((value * 10).toLong() / 10.0).toString()
}
