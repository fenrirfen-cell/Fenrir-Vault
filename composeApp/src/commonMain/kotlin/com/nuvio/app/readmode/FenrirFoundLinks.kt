package com.nuvio.app.readmode

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.delay

/**
 * Fenrir: links the plugin sites found for an episode, shared by the show page ("Found in"), the
 * links screen and the player's Sources list. A site found a moment ago shows straight away, and a
 * search that's still running is followed instead of being started again.
 */
object FenrirFoundLinks {
    private const val KEEP_MS = 15 * 60 * 1000L
    private const val FOLLOW_MS = 90 * 1000L

    private class Entry(val at: Long) {
        val sites = LinkedHashMap<String, MutableList<FenrirPluginStream>>()
        var complete = false
    }

    private val entries = HashMap<String, Entry>()
    private val lock = SynchronizedObject()

    private fun now() = com.nuvio.app.features.trakt.TraktPlatformClock.nowEpochMs()

    private fun key(hostId: String, q: FenrirStreamQuery) =
        "$hostId|${q.title.lowercase()}|${q.year}|${q.isMovie}|${q.season}|${q.episode}"

    /**
     * True when the caller should run the search itself (nothing recent to follow). A finished
     * search that found nothing isn't reused, so trying again really searches again; [force] (the
     * Reload button) always searches again.
     */
    private fun claim(k: String, force: Boolean): Boolean = synchronized(lock) {
        val e = entries[k]
        val reusable = e != null && now() - e.at < KEEP_MS && !(e.complete && e.sites.isEmpty())
        if (!force && reusable) return false
        entries[k] = Entry(now())
        if (entries.size > 60) entries.keys.firstOrNull()?.let { entries.remove(it) }
        true
    }

    private fun add(k: String, site: String, streams: List<FenrirPluginStream>) = synchronized(lock) {
        entries[k]?.sites?.getOrPut(site) { mutableListOf() }?.addAll(streams)
    }

    private fun finish(k: String) = synchronized(lock) { entries[k]?.complete = true }

    /** Links per site already known for this episode (whether or not the search is done). */
    fun known(host: FenrirPluginHost, q: FenrirStreamQuery): Map<String, List<FenrirPluginStream>> = synchronized(lock) {
        val e = entries[key(host.id, q)]?.takeIf { now() - it.at < KEEP_MS } ?: return emptyMap()
        e.sites.mapValues { it.value.toList() }
    }

    /** [FenrirPluginHost.findStreams], shared: recent results come straight away. */
    suspend fun find(
        host: FenrirPluginHost,
        q: FenrirStreamQuery,
        forceRefresh: Boolean = false,
        onFound: suspend (site: String, streams: List<FenrirPluginStream>) -> Unit,
    ) {
        val k = key(host.id, q)
        if (claim(k, forceRefresh)) {
            try {
                host.findStreams(q) { site, found ->
                    if (found.isNotEmpty()) add(k, site, found)
                    onFound(site, found)
                }
            } finally {
                finish(k)
            }
            return
        }
        // Follow the results (and a search still running) instead of searching again.
        val sent = HashMap<String, Int>()
        val until = now() + FOLLOW_MS
        while (true) {
            val (snapshot, complete) = synchronized(lock) {
                val e = entries[k] ?: return
                e.sites.mapValues { it.value.toList() } to e.complete
            }
            for ((site, list) in snapshot) {
                val from = sent[site] ?: 0
                if (list.size > from) {
                    sent[site] = list.size
                    onFound(site, list.subList(from, list.size))
                }
            }
            if (complete || now() > until) return
            delay(250)
        }
    }
}
