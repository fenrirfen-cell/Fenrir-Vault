package com.nuvio.app.readmode

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Fenrir: keeps plugin / extension / add-on check results (Works, Slow, Down), runs a few tests
 * at a time and remembers results for a day (per version) so lists don't start blank.
 */
internal class FenrirCheckStore(context: Context, name: String, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("fenrir_checks_$name", Context.MODE_PRIVATE)
    private val permits = Semaphore(3)
    private val _checks = MutableStateFlow<Map<String, FenrirPluginCheck>>(emptyMap())
    val checks: StateFlow<Map<String, FenrirPluginCheck>> = _checks.asStateFlow()

    init {
        val now = System.currentTimeMillis()
        _checks.value = prefs.all.mapNotNull { (id, raw) ->
            val p = (raw as? String)?.split('\t') ?: return@mapNotNull null
            if (p.size < 5) return@mapNotNull null
            val at = p[4].toLongOrNull() ?: return@mapNotNull null
            if (now - at > 24 * 60 * 60 * 1000L) return@mapNotNull null
            val result = runCatching { FenrirPluginCheck.Result.valueOf(p[1]) }.getOrNull() ?: return@mapNotNull null
            if (result == FenrirPluginCheck.Result.Testing) return@mapNotNull null
            id to FenrirPluginCheck(result, p[2].toDoubleOrNull() ?: 0.0, p[3].ifBlank { null }, version = p[0])
        }.toMap()
    }

    /** Drops results saved for another version than the one now listed. */
    fun forgetOtherVersions(versionOf: (String) -> String?) {
        _checks.update { old -> old.filter { (id, c) -> c.version == null || versionOf(id)?.let { it == c.version } ?: true } }
    }

    fun test(ids: List<String>, versionOf: (String) -> String?, run: suspend (String) -> FenrirPluginCheck) {
        val todo = ids.filter { _checks.value[it]?.result != FenrirPluginCheck.Result.Testing }
        if (todo.isEmpty()) return
        _checks.update { old -> old + todo.associateWith { FenrirPluginCheck(FenrirPluginCheck.Result.Testing) } }
        todo.forEach { id ->
            scope.launch {
                val version = versionOf(id).orEmpty()
                val result = permits.withPermit {
                    runCatching { run(id) }.getOrElse { FenrirPluginCheck(FenrirPluginCheck.Result.Down, reason = "test failed") }
                }.copy(version = version)
                _checks.update { it + (id to result) }
                prefs.edit().putString(
                    id,
                    listOf(version, result.result.name, result.seconds, result.reason.orEmpty(), System.currentTimeMillis()).joinToString("\t"),
                ).apply()
            }
        }
    }
}
