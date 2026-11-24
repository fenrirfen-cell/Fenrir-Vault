package com.nuvio.app.readmode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fenrir: where Play gets its links for each show or movie ("Links from" on the show page):
 * everything, only the Nuvio add-ons, or only the CloudStream / Aniyomi plugins. Remembered per
 * title; titles never set use [Mode.All].
 */
object FenrirLinkChoice {
    enum class Mode(val label: String) { All("All"), Nuvio("Nuvio add-ons"), Plugins("Plugins") }

    private val _modes = MutableStateFlow<Map<String, Mode>>(emptyMap())
    val modes: StateFlow<Map<String, Mode>> = _modes.asStateFlow()

    private var persist: ((String) -> Unit)? = null

    fun modeFor(metaId: String?): Mode = metaId?.let { _modes.value[it] } ?: Mode.All

    fun set(metaId: String, mode: Mode) {
        _modes.value = if (mode == Mode.All) _modes.value - metaId else _modes.value + (metaId to mode)
        persist?.invoke(_modes.value.entries.joinToString("\n") { (id, m) -> "$id\t${m.name}" })
    }

    /** A plugin site tapped under "Found in": the next Play of [metaId] uses only that site. */
    private var onlySite: Triple<String, String, String>? = null // metaId, host id, site name

    fun playOnlyFrom(metaId: String, hostId: String, site: String) {
        onlySite = Triple(metaId, hostId, site)
    }

    /** What changes Play's links for [metaId] (so a changed choice reloads the links). */
    fun requestTag(metaId: String?): String =
        modeFor(metaId).name + (onlySite?.takeIf { it.first == metaId }?.let { "|${it.second}:${it.third}" } ?: "")

    /** Takes (and clears) the one-time site pick for [metaId]: host id to site name. */
    fun takeOnlySite(metaId: String?): Pair<String, String>? {
        val pick = onlySite ?: return null
        onlySite = null
        return if (pick.first == metaId) pick.second to pick.third else null
    }

    fun install(saved: String?, persist: (String) -> Unit) {
        this.persist = persist
        if (saved.isNullOrBlank()) return
        _modes.value = saved.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 2) return@mapNotNull null
            val mode = Mode.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            parts[0] to mode
        }.toMap()
    }
}
