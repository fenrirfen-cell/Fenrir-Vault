package com.nuvio.app.assistant

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Works out exactly what to play for a command: which title (your watch history first, then
 * your add-ons' search), and for shows which episode and where to resume.
 */
object AssistantResolver {

    sealed interface Result {
        data class Ready(val launch: AssistantLaunch, val heading: String, val detail: String) : Result
        data class Choices(val message: String, val options: List<MetaPreview>) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun resolve(command: AssistantCommand): Result {
        if (command.unsupported) {
            return Result.Failed(
                "I can only play things by name for now. Try \"play Dune\" or \"next episode of The Rookie\".",
            )
        }
        runCatching { WatchProgressRepository.continueWatching() }
        val entries = WatchProgressRepository.uiState.value.entries
        val latestPerTitle = entries
            .groupBy { it.parentMetaId }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.lastUpdatedEpochMs } }
            .sortedByDescending { it.lastUpdatedEpochMs }

        if (command.title.isBlank()) {
            val recent = latestPerTitle.firstOrNull()
                ?: return Result.Failed("Tell me which title to play.")
            return resolveMeta(command, recent.parentMetaType, recent.parentMetaId, recent.title, recent.poster, recent.background, recent.logo)
        }

        val fromHistory = latestPerTitle
            .map { it to maxOf(titleScore(command.title, it.title), containedScore(command.title, it.title)) }
            .maxByOrNull { it.second }
        if (fromHistory != null && fromHistory.second >= 0.85) {
            val entry = fromHistory.first
            return resolveMeta(command, entry.parentMetaType, entry.parentMetaId, entry.title, entry.poster, entry.background, entry.logo)
        }

        val results = searchAddons(command)
        if (results.isEmpty()) {
            if (fromHistory != null && fromHistory.second >= 0.6) {
                val entry = fromHistory.first
                return resolveMeta(command, entry.parentMetaType, entry.parentMetaId, entry.title, entry.poster, entry.background, entry.logo)
            }
            return Result.Failed("I couldn't find \"${command.title}\" in your add-ons.")
        }

        val scored = results
            .distinctBy { normalizeTitle(it.name) + "|" + it.type + "|" + it.releaseInfo.orEmpty().take(4) }
            .map { item ->
                var score = titleScore(command.title, item.name)
                if (command.year != null && item.releaseInfo?.contains(command.year) == true) score += 0.08
                item to score
            }
            .sortedByDescending { it.second }
        val best = scored.first()
        val runnerUp = scored.drop(1).firstOrNull()
        val clearWinner = best.second >= 0.65 &&
            (runnerUp == null || runnerUp.second < best.second - 0.04)
        if (clearWinner) {
            val item = best.first
            return resolveMeta(command, item.type, item.id, item.name, item.poster, item.banner, item.logo)
        }
        val options = scored.filter { it.second >= 0.3 }.ifEmpty { scored }.take(6).map { it.first }
        return Result.Choices("Which one did you mean?", options)
    }

    suspend fun resolveMeta(
        command: AssistantCommand,
        type: String,
        id: String,
        name: String,
        poster: String?,
        background: String?,
        logo: String?,
    ): Result {
        val details = runCatching { MetaDetailsRepository.fetch(type, id) }.getOrNull()
        val title = details?.name?.takeIf { it.isNotBlank() } ?: name
        val artLogo = details?.logo ?: logo
        val artPoster = details?.poster ?: poster
        val artBackground = details?.background ?: background

        val episodes = details?.videos.orEmpty()
            .filter { it.season != null && it.episode != null }
            .sortedWith(compareBy<MetaVideo>({ it.season }, { it.episode }))
        val titleEntries = WatchProgressRepository.uiState.value.entries.filter { it.parentMetaId == id }
        val latest = titleEntries.maxByOrNull { it.lastUpdatedEpochMs }
        val isEpisodic = episodes.isNotEmpty() || latest?.isEpisode == true

        if (!isEpisodic) {
            if (command.season != null || command.episode != null) {
                return Result.Failed("$title doesn't have episodes.")
            }
            val entry = runCatching { WatchProgressRepository.progressForVideo(videoId = id, parentMetaId = id) }.getOrNull()
            val (position, fraction) = if (command.fromStart) null to null else entry.resumePoint()
            return Result.Ready(
                launch = AssistantLaunch(
                    type = type,
                    videoId = id,
                    parentMetaId = id,
                    parentMetaType = type,
                    title = title,
                    logo = artLogo,
                    poster = artPoster,
                    background = artBackground,
                    resumePositionMs = position,
                    resumeProgressFraction = fraction,
                    startFromBeginning = command.fromStart,
                ),
                heading = title,
                detail = describeResume(position, fraction),
            )
        }

        if (episodes.isEmpty()) {
            // Episode list unavailable: we can still resume the episode in progress.
            val entry = latest ?: return Result.Failed("I couldn't load the episodes of $title.")
            if (command.action == AssistantAction.NEXT || command.action == AssistantAction.LATEST ||
                command.season != null || command.episode != null
            ) {
                return Result.Failed("I couldn't load the episode list of $title right now.")
            }
            val (position, fraction) = if (command.fromStart) null to null else entry.resumePoint()
            return ready(command, type, id, title, artLogo, artPoster, artBackground, entry.videoId,
                entry.seasonNumber, entry.episodeNumber, entry.episodeTitle, entry.episodeThumbnail, position, fraction)
        }

        val regular = episodes.filter { (it.season ?: 0) > 0 }.ifEmpty { episodes }
        val today = CurrentDateProvider.todayIsoDate()
        val explicit = command.season != null || command.episode != null
        val target: MetaVideo? = when {
            explicit -> {
                val season = command.season ?: latest?.seasonNumber ?: 1
                if (command.episode == null) {
                    regular.firstOrNull { it.season == season }
                } else {
                    episodes.firstOrNull { it.season == season && it.episode == command.episode }
                }
            }
            command.action == AssistantAction.LATEST ->
                regular.lastOrNull { video -> video.released?.take(10)?.let { it <= today } ?: false }
                    ?: regular.lastOrNull()
            command.action == AssistantAction.NEXT ->
                if (latest == null) regular.firstOrNull() else nextAfter(regular, latest.seasonNumber, latest.episodeNumber)
            else -> when {
                latest == null -> regular.firstOrNull()
                latest.isResumable ->
                    episodes.firstOrNull { it.season == latest.seasonNumber && it.episode == latest.episodeNumber }
                        ?: nextAfter(regular, latest.seasonNumber, latest.episodeNumber)
                else -> nextAfter(regular, latest.seasonNumber, latest.episodeNumber)
            }
        }

        if (target == null) {
            return when {
                explicit -> Result.Failed(
                    "I couldn't find " +
                        listOfNotNull(command.season?.let { "season $it" }, command.episode?.let { "episode $it" }).joinToString(" ") +
                        " of $title.",
                )
                latest != null -> Result.Failed("You're all caught up on $title. There's no next episode yet.")
                else -> Result.Failed("I couldn't find any episodes of $title.")
            }
        }

        val released = target.released?.take(10)
        if (!explicit && released != null && released > today) {
            return Result.Failed("The next episode of $title (S${target.season} E${target.episode}) comes out on $released.")
        }

        val entry = runCatching {
            WatchProgressRepository.progressForVideo(
                videoId = target.id,
                parentMetaId = id,
                seasonNumber = target.season,
                episodeNumber = target.episode,
            )
        }.getOrNull()
        val (position, fraction) = if (command.fromStart) null to null else entry.resumePoint()
        val videoId = target.id.ifBlank {
            buildPlaybackVideoId(parentMetaId = id, seasonNumber = target.season, episodeNumber = target.episode)
        }
        return ready(command, type, id, title, artLogo, artPoster, artBackground, videoId,
            target.season, target.episode, target.title, target.thumbnail, position, fraction)
    }

    private fun ready(
        command: AssistantCommand,
        type: String,
        id: String,
        title: String,
        logo: String?,
        poster: String?,
        background: String?,
        videoId: String,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        episodeThumbnail: String?,
        position: Long?,
        fraction: Float?,
    ): Result.Ready {
        val code = if (season != null && episode != null) " \u00b7 S$season E$episode" else ""
        val detail = listOfNotNull(
            episodeTitle?.takeIf { it.isNotBlank() && !it.equals("Episode $episode", ignoreCase = true) },
            describeResume(position, fraction),
        ).joinToString(" \u00b7 ")
        return Result.Ready(
            launch = AssistantLaunch(
                type = type,
                videoId = videoId,
                parentMetaId = id,
                parentMetaType = type,
                title = title,
                logo = logo,
                poster = poster,
                background = background,
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                episodeThumbnail = episodeThumbnail,
                resumePositionMs = position,
                resumeProgressFraction = fraction,
                startFromBeginning = command.fromStart,
            ),
            heading = title + code,
            detail = detail,
        )
    }

    private fun nextAfter(list: List<MetaVideo>, season: Int?, episode: Int?): MetaVideo? {
        if (season == null || episode == null) return list.firstOrNull()
        return list.firstOrNull { video ->
            val s = video.season ?: return@firstOrNull false
            val e = video.episode ?: return@firstOrNull false
            s > season || (s == season && e > episode)
        }
    }

    private fun WatchProgressEntry?.resumePoint(): Pair<Long?, Float?> {
        if (this == null || !isResumable) return null to null
        val percent = normalizedProgressPercent
        return if (durationMs <= 0L && percent != null && percent > 0f) {
            0L to (percent / 100f)
        } else if (lastPositionMs > 0L) {
            lastPositionMs to null
        } else {
            null to null
        }
    }

    private fun describeResume(position: Long?, fraction: Float?): String = when {
        position != null && position > 0L -> "Resume at ${formatTime(position)}"
        fraction != null && fraction > 0f -> "Resume at ${(fraction * 100).toInt()}%"
        else -> "From the start"
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        fun two(n: Long) = if (n < 10) "0$n" else "$n"
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }

    private suspend fun searchAddons(command: AssistantCommand): List<MetaPreview> = coroutineScope {
        val manifests = AddonRepository.uiState.value.addons.enabledAddons().mapNotNull { it.manifest }
        val requests = manifests.flatMap { manifest ->
            manifest.catalogs
                .filter { catalog ->
                    catalog.extra.any { it.name == "search" } &&
                        catalog.extra.none { it.isRequired && it.name != "search" }
                }
                .filter { catalog ->
                    when {
                        command.wantsMovie -> catalog.type == "movie"
                        command.wantsSeries -> catalog.type != "movie"
                        else -> true
                    }
                }
                .map { catalog -> manifest to catalog }
        }.take(24)
        requests.map { (manifest, catalog) ->
            async {
                withTimeoutOrNull(8_000L) {
                    runCatching {
                        fetchCatalogPage(
                            manifestUrl = manifest.transportUrl,
                            type = catalog.type,
                            catalogId = catalog.id,
                            search = command.title,
                            forceRefresh = false,
                        ).items.take(10)
                    }.getOrNull()
                }.orEmpty()
            }
        }.awaitAll().flatten().distinctBy { "${it.type}:${it.id}" }
    }

    /** A title from your history said with extra words around it, e.g. "next the rookie". */
    private fun containedScore(query: String, candidate: String): Double {
        val queryWords = normalizeTitle(query).split(' ').filter { it.isNotBlank() }.toSet()
        val titleWords = normalizeTitle(candidate).split(' ')
            .filter { it.isNotBlank() && it != "the" && it != "a" && it != "of" }
            .toSet()
        if (titleWords.isEmpty() || titleWords.none { it.length >= 4 }) return 0.0
        return if (queryWords.containsAll(titleWords)) 0.9 else 0.0
    }

    internal fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    internal fun titleScore(query: String, candidate: String): Double {
        val q = normalizeTitle(query).removePrefix("the ")
        val c = normalizeTitle(candidate).removePrefix("the ")
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0
        val queryWords = q.split(' ').toSet()
        val candidateWords = c.split(' ').toSet()
        val overlap = queryWords.intersect(candidateWords).size.toDouble()
        var score = 0.8 * overlap / queryWords.union(candidateWords).size
        if (candidateWords.containsAll(queryWords)) {
            score = maxOf(score, 0.7 + 0.2 * queryWords.size / candidateWords.size)
        }
        if (c.startsWith(q) || q.startsWith(c)) {
            score = maxOf(score, 0.6 + 0.3 * minOf(q.length, c.length) / maxOf(q.length, c.length))
        }
        return score.coerceAtMost(0.97)
    }
}
