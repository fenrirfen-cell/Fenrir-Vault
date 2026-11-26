package com.nuvio.app.features.anidrama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fenrir: keeps Ani-drama's downloaded lists on the phone so the tab opens straight away
 * instead of waiting on AniList / TMDB every time the app starts.
 *
 * The Android app plugs in the file storage with [install]. Pull-to-refresh skips the saved
 * copies (except the dub list and ID matches, which hardly change). If the network fails,
 * the last saved copy is used even when it's old.
 */
object AniDramaDiskCache {
    private var read: ((String) -> Pair<Long, String>?)? = null
    private var write: ((String, String) -> Unit)? = null

    /** Saved copies older than this (set by pull-to-refresh) are ignored for lists. */
    private var skipBefore = 0L

    const val HOUR = 60L * 60 * 1000
    const val DAY = 24 * HOUR

    fun install(read: (String) -> Pair<Long, String>?, write: (String, String) -> Unit) {
        this.read = read
        this.write = write
    }

    fun forgetLists() {
        skipBefore = AniDramaTime.nowEpochMs()
    }

    private fun fileKey(key: String): String {
        var h = 1125899906842597L
        key.forEach { h = 31 * h + it.code }
        return "${h.toULong().toString(16)}-${key.length}"
    }

    /** Saved text for [key] if it's newer than [maxAgeMs]; otherwise [load] it and save it. */
    suspend fun text(key: String, maxAgeMs: Long, survivesRefresh: Boolean = false, load: suspend () -> String): String {
        val name = fileKey(key)
        val saved = withContext(Dispatchers.Default) { runCatching { read?.invoke(name) }.getOrNull() }
        val now = AniDramaTime.nowEpochMs()
        if (saved != null) {
            val (savedAt, body) = saved
            val fresh = now - savedAt < maxAgeMs && (survivesRefresh || savedAt >= skipBefore)
            if (fresh && body.isNotBlank()) return body
        }
        return try {
            load().also { body ->
                if (body.isNotBlank() && "\"errors\":[" !in body.take(200)) {
                    withContext(Dispatchers.Default) { runCatching { write?.invoke(name, body) } }
                }
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            saved?.second?.takeIf { it.isNotBlank() } ?: throw e
        }
    }
}
