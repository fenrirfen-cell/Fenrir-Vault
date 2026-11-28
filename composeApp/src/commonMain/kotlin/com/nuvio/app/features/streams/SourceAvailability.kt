package com.nuvio.app.features.streams

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.servers.ServerStreams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** How many links each of your sources found for one title / episode ("Found in" on detail pages). */
data class SourceAvailabilityState(
    /** Source name to link count, in your add-on order. Null count = still checking. */
    val sources: List<Pair<String, Int?>> = emptyList(),
) {
    val isChecking: Boolean get() = sources.any { it.second == null }
}

/**
 * Fenrir Vault: checks which of your installed add-ons, plugins and servers have a title. It joins
 * the same shared search the stream screen uses, so pressing Play afterwards reuses the results.
 */
object SourceAvailability {

    fun check(type: String, videoId: String, season: Int?, episode: Int?): Flow<SourceAvailabilityState> = flow {
        val pluginState = if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.initialize()
            PluginRepository.uiState.value
        } else {
            null
        }
        val isNativeServerRequest = ServerStreams.isNativeRequest(videoId)
        val serverSources = ServerStreams.sources(type, videoId, season, episode, false)
        val installedAddons = if (isNativeServerRequest) emptyList() else AddonRepository.uiState.value.addons.enabledAddons()
        val pluginScrapers = if (pluginState != null && !isNativeServerRequest) {
            PluginRepository.getEnabledScrapersForType(type)
        } else {
            emptyList()
        }
        val pluginGroups = if (pluginState != null) {
            pluginScrapers.toPluginProviderGroups(
                repositories = pluginState.repositories,
                groupByRepository = pluginState.groupStreamsByRepository,
            )
        } else {
            emptyList()
        }
        val streamAddons = installedAddons.mapNotNull { addon ->
            val manifest = addon.manifest ?: return@mapNotNull null
            if (!manifest.supportsStream(type, videoId)) return@mapNotNull null
            InstalledStreamAddonTarget(
                addonName = addon.displayTitle.ifBlank { manifest.name },
                addonId = addon.streamAddonInstanceId(manifest.id),
                manifest = manifest,
            )
        }

        val names = LinkedHashMap<String, String>()
        streamAddons.forEach { names[it.addonId] = it.addonName }
        pluginGroups.forEach { names[it.addonId] = it.addonName }
        serverSources.forEach { source -> source.loadingGroup().let { names[it.addonId] = it.addonName } }
        if (names.isEmpty()) {
            emit(SourceAvailabilityState())
            return@flow
        }

        val counts = HashMap<String, Int>()
        val pluginRemaining = pluginGroups.associate { it.addonId to it.scrapers.size }.toMutableMap()
        val done = HashSet<String>()
        fun state() = SourceAvailabilityState(
            names.map { (id, name) -> name to (if (id in done) counts[id] ?: 0 else null) },
        )
        emit(state())

        val targets = StreamSearchTargets(
            type = type,
            videoId = videoId,
            season = season,
            episode = episode,
            streamAddons = streamAddons,
            serverSources = serverSources,
            pluginProviderGroups = pluginGroups,
        )
        StreamSearchSessions.search(
            profileId = ProfileRepository.activeProfileId,
            targets = targets,
            forceRefresh = false,
        ).collect { completion ->
            when (completion) {
                is StreamLoadCompletion.Addon -> {
                    val id = completion.group.addonId
                    counts[id] = (counts[id] ?: 0) + completion.group.streams.size
                    done += id
                }
                is StreamLoadCompletion.PluginScraper -> {
                    val id = completion.addonId
                    counts[id] = (counts[id] ?: 0) + completion.streams.size
                    val left = (pluginRemaining[id] ?: 1) - 1
                    pluginRemaining[id] = left
                    if (left <= 0) done += id
                }
            }
            emit(state())
        }
        // Anything that never reported counts as nothing found.
        names.keys.forEach { done += it }
        emit(state())
    }
}
