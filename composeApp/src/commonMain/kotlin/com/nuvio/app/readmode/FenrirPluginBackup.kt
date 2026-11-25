package com.nuvio.app.readmode

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Where backup files go; the Android app provides it. */
interface FenrirBackupFiles {
    /** Shown to the user, e.g. "Downloads › Fenrir Vault". Null when automatic saving isn't possible. */
    val autoSaveLocation: String?

    /** Writes [text] to the automatic backup file (kept when the app is deleted). */
    fun autoSave(text: String): Boolean

    /** Lets the user pick where to save a copy; call the result with the file's text. */
    @Composable
    fun rememberSaveAs(onDone: (Boolean) -> Unit): (text: String) -> Unit

    /** Lets the user pick a backup file; [onRead] gets its text (null if it couldn't be read). */
    @Composable
    fun rememberOpen(onRead: (String?) -> Unit): () -> Unit
}

/**
 * Fenrir: backup of CloudStream plugins and Aniyomi extensions (repositories, installed plugins,
 * trusted extensions), so they come back after the app is deleted and installed again.
 * Saved automatically whenever they change; restored from Settings → Sources.
 */
object FenrirPluginBackup {
    const val FILE_NAME = "fenrir-plugins-backup.json"
    private const val KIND = "fenrir-vault-plugins"
    private const val VERSION = 1

    var files: FenrirBackupFiles? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _lastSaved = MutableStateFlow<Long?>(null)
    /** When the automatic backup was last written (epoch ms), this session. */
    val lastSaved: StateFlow<Long?> = _lastSaved.asStateFlow()

    /** True while a restore is running (so it isn't saved half-way). */
    @kotlin.concurrent.Volatile
    private var restoring = false

    fun build(): String = buildJsonObject {
        put("kind", KIND)
        put("version", VERSION)
        put("savedAt", kotlin.time.Clock.System.now().toEpochMilliseconds())
        putJsonObject("hosts") {
            FenrirPlugins.all.forEach { host ->
                host.backup()?.let { data ->
                    runCatching { json.parseToJsonElement(data) }.getOrNull()?.let { put(host.id, it) }
                }
            }
        }
    }.toString()

    /** Something worth keeping: at least one repository or plugin. */
    private fun hasContent(): Boolean = FenrirPlugins.all.any { host ->
        val s = host.state.value
        s.repos.isNotEmpty() || s.installed.isNotEmpty()
    }

    /** Is [text] one of our backup files? */
    fun isBackup(text: String): Boolean = runCatching {
        json.parseToJsonElement(text).jsonObject["kind"]?.jsonPrimitive?.content == KIND
    }.getOrDefault(false)

    /** Puts a backup back. Returns a short message for the user. */
    suspend fun restore(text: String): String {
        val root: JsonObject = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return "That file isn't a Fenrir plugin backup."
        if (root["kind"]?.jsonPrimitive?.content != KIND) return "That file isn't a Fenrir plugin backup."
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 0
        if (version > VERSION) return "This backup is from a newer Fenrir Vault. Update the app first."
        val hosts = root["hosts"] as? JsonObject ?: return "This backup is empty."
        restoring = true
        try {
            var count = 0
            FenrirPlugins.all.forEach { host ->
                val data = hosts[host.id] ?: return@forEach
                count += runCatching { host.restore(data.toString()) }.getOrDefault(0)
            }
            return if (count == 0) "Backup restored. Your repositories are back; nothing new to install."
            else "Backup restored: $count plugin(s) and extension(s) are back."
        } finally {
            restoring = false
            saveNow()
        }
    }

    private fun saveNow() {
        if (restoring || !hasContent()) return
        val target = files ?: return
        if (runCatching { target.autoSave(build()) }.getOrDefault(false)) {
            _lastSaved.value = kotlin.time.Clock.System.now().toEpochMilliseconds()
        }
    }

    /** Call once after [FenrirPlugins] is set up: saves a few seconds after anything changes. */
    fun startAutoSave() {
        val hosts = FenrirPlugins.all
        if (hosts.isEmpty()) return
        scope.launch {
            combine(
                hosts.map { host ->
                    host.state.map { s ->
                        if (s.busy) null
                        else Triple(s.repos.sorted(), s.installed.map { "${it.id}:${it.version}:${it.needsTrust}" }.sorted(), s.showNsfw)
                    }
                },
            ) { it.toList() }
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    if (snapshot.any { it == null }) return@collectLatest // wait until nothing is loading
                    delay(4_000)
                    saveNow()
                }
        }
    }
}
