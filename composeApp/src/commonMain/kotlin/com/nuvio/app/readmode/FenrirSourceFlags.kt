package com.nuvio.app.readmode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where each plugin source is used (Settings → Sources → a plugin → "Where to use it"):
 * - home: listed in the FENRIR READ ▾ menu so its rows can be shown on Home
 * - pinned: at the top of that menu, in the order the user set
 * - search: its results show in Search
 * - streams: used when finding links to play a title
 * Keyed by "<host id>:<source name>" (e.g. "cloudstream:Anichi", "aniyomi:Some site").
 */
object FenrirSourceFlags {
    data class Flags(
        val home: Boolean,
        val pinned: Boolean = false,
        val search: Boolean = true,
        val streams: Boolean = true,
    )

    private val _flags = MutableStateFlow<Map<String, Flags>>(emptyMap())
    val flags: StateFlow<Map<String, Flags>> = _flags.asStateFlow()

    /** Pinned keys in the user's order. */
    private val _pinOrder = MutableStateFlow<List<String>>(emptyList())
    val pinOrder: StateFlow<List<String>> = _pinOrder.asStateFlow()

    private var persist: ((String) -> Unit)? = null

    fun key(hostId: String, sourceName: String) = "$hostId:$sourceName"

    /** CloudStream sources were all in the menu before, so they stay on Home unless turned off. */
    /** New sources (CloudStream and Aniyomi) show on Home right away; hide them from the menu. */
    private fun defaults(key: String) = Flags(home = true)

    fun get(key: String): Flags = _flags.value[key] ?: defaults(key)
    fun get(hostId: String, sourceName: String): Flags = get(key(hostId, sourceName))

    fun set(key: String, flags: Flags) {
        _flags.value = _flags.value + (key to flags)
        val order = _pinOrder.value
        _pinOrder.value = when {
            flags.pinned && key !in order -> order + key
            !flags.pinned && key in order -> order - key
            else -> order
        }
        save()
    }

    fun setPinOrder(keys: List<String>) {
        _pinOrder.value = keys
        save()
    }

    fun setHome(key: String, home: Boolean) {
        val f = get(key)
        // Turning back on a source that was fully turned off (e.g. by Check sources) restores it everywhere.
        val revive = home && !f.search && !f.streams
        set(
            key,
            f.copy(
                home = home,
                pinned = if (home) f.pinned else false,
                search = if (revive) true else f.search,
                streams = if (revive) true else f.streams,
            ),
        )
    }

    fun usedInSearch(hostId: String, sourceName: String) = get(hostId, sourceName).search
    fun usedForStreams(hostId: String, sourceName: String) = get(hostId, sourceName).streams

    // ---------------------------------------------------------------- saving
    // One line per source: key \t home \t pinned \t search \t streams; then "#order" \t keys...

    fun install(saved: String?, persist: (String) -> Unit) {
        this.persist = persist
        if (saved.isNullOrBlank()) return
        val map = LinkedHashMap<String, Flags>()
        var order = emptyList<String>()
        saved.lineSequence().forEach { line ->
            val parts = line.split('\t')
            if (parts.firstOrNull() == "#order") {
                order = parts.drop(1).filter { it.isNotBlank() }
            } else if (parts.size >= 5) {
                map[parts[0]] = Flags(parts[1] == "1", parts[2] == "1", parts[3] == "1", parts[4] == "1")
            }
        }
        _flags.value = map
        _pinOrder.value = order
    }

    private fun save() {
        val text = buildString {
            _flags.value.forEach { (k, f) ->
                append(k).append('\t')
                    .append(if (f.home) "1" else "0").append('\t')
                    .append(if (f.pinned) "1" else "0").append('\t')
                    .append(if (f.search) "1" else "0").append('\t')
                    .append(if (f.streams) "1" else "0").append('\n')
            }
            append("#order")
            _pinOrder.value.forEach { append('\t').append(it) }
        }
        persist?.invoke(text)
    }
}
