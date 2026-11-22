package com.nuvio.app.readmode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * Checks whether plugin sources (CloudStream, Aniyomi) actually work: loads the site's home page,
 * and if that gives nothing, tries a search. Working / Slow / Not working, per source.
 */
object FenrirSourceHealth {
    enum class Status { Checking, Working, Slow, Broken }

    private val _status = MutableStateFlow<Map<String, Status>>(emptyMap())
    /** Keyed by the home source id ("Anichi", "ay:123…"). */
    val status: StateFlow<Map<String, Status>> = _status.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun checkAll() {
        if (_running.value) return
        _running.value = true
        scope.launch {
            try {
                val targets = FenrirPlugins.all.flatMap { host -> host.homeSources().map { host to it } }
                _status.update { old -> old + targets.associate { it.second.id to Status.Checking } }
                val permits = Semaphore(4)
                targets.map { (host, source) ->
                    async { permits.withPermit { check(host, source) } }
                }.awaitAll()
            } finally {
                _running.value = false
            }
        }
    }

    private suspend fun check(host: FenrirPluginHost, source: FenrirHomeSource) {
        val started = TimeSource.Monotonic.markNow()
        val works = withTimeoutOrNull(20_000) {
            val rows = runCatching { host.homeRows(source.id) }.getOrNull().orEmpty()
            if (rows.any { it.items.isNotEmpty() }) {
                true
            } else {
                runCatching { host.searchSource(source.id, "the") }.getOrNull().orEmpty().isNotEmpty()
            }
        } ?: false
        val seconds = started.elapsedNow().inWholeMilliseconds / 1000.0
        val result = when {
            !works -> Status.Broken
            seconds > 8 -> Status.Slow
            else -> Status.Working
        }
        _status.update { it + (source.id to result) }
    }
}
