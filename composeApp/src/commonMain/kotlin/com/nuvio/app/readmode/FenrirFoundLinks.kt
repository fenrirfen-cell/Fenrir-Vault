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

    private fun add(mine: Entry?, site: String, streams: List<FenrirPluginStream>) = synchronized(lock) {
        mine?.sites?.getOrPut(site) { mutableListOf() }?.addAll(streams)
    }

    private fun finish(mine: Entry?) = synchronized(lock) { mine?.complete = true }

    /** A search that was stopped half-way (a link was picked, the screen closed) isn't kept as done. */
    private fun drop(k: String, mine: Entry?) = synchronized(lock) {
        // only our own search: a newer one (Reload) may have taken its place meanwhile
        if (mine != null && entries[k] === mine) entries.remove(k)
    }

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
        // links already passed to onFound, per site, so nothing is sent twice
        val sent = HashMap<String, MutableSet<String>>()
        suspend fun send(site: String, streams: List<FenrirPluginStream>) {
            val seen = sent.getOrPut(site) { HashSet() }
            val fresh = streams.filter { seen.add(it.url) }
            if (fresh.isNotEmpty()) onFound(site, fresh)
        }
        var force = forceRefresh
        // At most twice: a search followed here can be stopped elsewhere (a link was picked, the
        // screen closed); then this one searches by itself instead of keeping half the links.
        repeat(2) {
            if (claim(k, force)) {
                val mine = synchronized(lock) { entries[k] }
                try {
                    host.findStreams(q) { site, found ->
                        if (found.isNotEmpty()) add(mine, site, found)
                        if (found.isEmpty()) onFound(site, found) else send(site, found)
                    }
                    finish(mine)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // only some sites had answered: next time search again instead of showing just those
                    drop(k, mine)
                    throw e
                } catch (e: Throwable) {
                    finish(mine)
                    throw e
                }
                return
            }
            force = false
            // Follow the results (and a search still running) instead of searching again.
            val until = now() + FOLLOW_MS
            while (true) {
                val current = synchronized(lock) {
                    entries[k]?.let { e -> e.sites.mapValues { it.value.toList() } to e.complete }
                } ?: break // stopped half-way elsewhere: search again ourselves
                val (snapshot, complete) = current
                for ((site, list) in snapshot) send(site, list)
                if (complete || now() > until) return
                delay(250)
            }
        }
    }
}
