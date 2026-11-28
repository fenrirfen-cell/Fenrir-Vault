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
import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsSyncService
import com.nuvio.app.features.home.SyncHomeCatalogPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Where backup files go; the Android app provides it. */
interface FenrirBackupFiles {
    /** Shown to the user, e.g. "Downloads › Fenrir Vault". Null when automatic saving isn't possible. */
    val autoSaveLocation: String?

    /** Writes [text] to the automatic backup file [name] (kept when the app is deleted). */
    fun autoSave(text: String, name: String = FenrirPluginBackup.FILE_NAME): Boolean

    /** Reads back the automatic backup file [name] this install wrote, or null. */
    fun readAutoSave(name: String = FenrirPluginBackup.FILE_NAME): String? = null

    /** Lets the user pick where to save a copy; call the result with the file's text. */
    @Composable
    fun rememberSaveAs(onDone: (Boolean) -> Unit): (text: String) -> Unit

    /** Lets the user pick a backup file; [onRead] gets its text (null if it couldn't be read). */
    @Composable
    fun rememberOpen(onRead: (String?) -> Unit): () -> Unit
}

/**
 * Fenrir: backup of CloudStream plugins and Aniyomi extensions (repositories, installed plugins,
 * trusted extensions) and of the Stremio add-ons with the Home layout, so they come back after the
 * app is deleted and installed again, or after the Nuvio account lost them.
 * Saved automatically whenever they change; restored from Settings → Sources.
 */
object FenrirPluginBackup {
    const val FILE_NAME = "fenrir-plugins-backup.json"
    /** The bigger backup, kept when a save would have fewer add-ons or plugins (say, after they were wiped). */
    const val PREVIOUS_FILE_NAME = "fenrir-plugins-backup-previous.json"
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
        // Stremio add-ons (in their order) and the Home rows, as on this profile
        val addons = AddonRepository.uiState.value.addons
            .filter { it.manifestUrl != com.nuvio.app.features.anidrama.FenrirCatalogs.MANIFEST_URL }
        if (addons.isNotEmpty()) {
            putJsonObject("nuvio") {
                putJsonArray("addons") {
                    addons.forEach { a ->
                        addJsonObject {
                            put("url", a.manifestUrl)
                            put("enabled", a.enabled)
                            a.manifest?.name?.let { put("name", it) }
                        }
                    }
                }
                runCatching {
                    json.encodeToJsonElement(SyncHomeCatalogPayload.serializer(), HomeCatalogSettingsRepository.exportToSyncPayload())
                }.getOrNull()?.let { put("home", it) }
            }
        }
    }.toString()

    /** Something worth keeping: at least one repository or plugin. */
    private fun hasContent(): Boolean = FenrirPlugins.all.any { host ->
        val s = host.state.value
        s.repos.isNotEmpty() || s.installed.isNotEmpty()
    } || AddonRepository.uiState.value.addons.any { it.manifestUrl != com.nuvio.app.features.anidrama.FenrirCatalogs.MANIFEST_URL }

    /** Is [text] one of our backup files? */
    fun isBackup(text: String): Boolean = runCatching {
        json.parseToJsonElement(text).jsonObject["kind"]?.jsonPrimitive?.content == KIND
    }.getOrDefault(false)

    /** Puts a backup back. Returns a short message for the user. */
    suspend fun restore(text: String): String {
        val root: JsonObject = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return "That file isn't a Fenrir backup."
        if (root["kind"]?.jsonPrimitive?.content != KIND) return "That file isn't a Fenrir backup."
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 0
        if (version > VERSION) return "This backup is from a newer MugiVerse. Update the app first."
        val hosts = root["hosts"] as? JsonObject
        val nuvio = root["nuvio"] as? JsonObject
        if (hosts == null && nuvio == null) return "This backup is empty."
        restoring = true
        try {
            var count = 0
            FenrirPlugins.all.forEach { host ->
                val data = hosts?.get(host.id) ?: return@forEach
                count += runCatching { host.restore(data.toString()) }.getOrDefault(0)
            }
            val addons = nuvio?.let { runCatching { restoreNuvio(it) }.getOrDefault(0) } ?: 0
            val parts = listOfNotNull(
                count.takeIf { it > 0 }?.let { "$it plugin(s) and extension(s)" },
                addons.takeIf { it > 0 }?.let { "$it Stremio add-on(s)" },
            )
            return if (parts.isEmpty()) "Backup restored. Everything in it was already here."
            else "Backup restored: ${parts.joinToString(" and ")} are back."
        } finally {
            restoring = false
            saveNow()
        }
    }

    /** Adds the backup's Stremio add-ons that are missing, then puts the Home rows back. */
    private suspend fun restoreNuvio(nuvio: JsonObject): Int {
        // signed in: wait until the account's own add-ons are loaded, so nothing is overwritten
        AddonRepository.initialize()
        if (!AddonRepository.awaitReadyForChanges(60_000)) return 0
        val wanted = (nuvio["addons"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            url to (o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true)
        }
        var added = 0
        for ((url, enabled) in wanted) {
            if (AddonRepository.uiState.value.addons.any { it.manifestUrl == url }) continue
            val result = runCatching { AddonRepository.addAddon(url) }.getOrNull()
            val ok = when (result) {
                is AddAddonResult.Success -> true
                // it was installed before, so it had been agreed to then
                is AddAddonResult.TrackingConsentRequired ->
                    AddonRepository.installWithTrackingConsent(result.manifest) is AddAddonResult.Success
                else -> false
            }
            if (ok) {
                added++
                if (!enabled) AddonRepository.setAddonEnabled(url, false)
            }
        }
        // Home rows: only when this phone's layout isn't set up yet or add-ons came back
        val home = nuvio["home"]
        if (home != null && added > 0) {
            runCatching { json.decodeFromJsonElement(SyncHomeCatalogPayload.serializer(), home) }.getOrNull()?.let { payload ->
                HomeCatalogSettingsRepository.applyFromRemote(payload)
                HomeCatalogSettingsSyncService.triggerPush()
            }
        }
        return added
    }

    /** How many add-ons, plugins and extensions a backup holds. */
    private fun itemCount(text: String): Int = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val hosts = root["hosts"] as? JsonObject
        val pluginItems = hosts?.values?.sumOf { h ->
            val o = h as? JsonObject ?: return@sumOf 0
            listOf("installed", "inApp", "trusted").sumOf { (o[it] as? JsonArray)?.size ?: 0 }
        } ?: 0
        val addons = ((root["nuvio"] as? JsonObject)?.get("addons") as? JsonArray)?.size ?: 0
        pluginItems + addons
    }.getOrDefault(0)

    private fun saveNow() {
        if (restoring || !hasContent()) return
        val target = files ?: return
        val text = build()
        // Something shrank (add-ons wiped by a sync problem, plugins removed): keep the bigger
        // backup as the "previous" file before overwriting it, so it can still be restored.
        runCatching {
            val existing = target.readAutoSave()
            if (existing != null && itemCount(existing) > itemCount(text)) {
                target.autoSave(existing, PREVIOUS_FILE_NAME)
            }
        }
        if (runCatching { target.autoSave(text) }.getOrDefault(false)) {
            _lastSaved.value = kotlin.time.Clock.System.now().toEpochMilliseconds()
        }
    }

    /** Call once after [FenrirPlugins] is set up: saves a few seconds after anything changes. */
    fun startAutoSave() {
        val hosts = FenrirPlugins.all
        scope.launch {
            combine(
                hosts.map { host ->
                    host.state.map { s ->
                        if (s.busy) null
                        else listOf(s.repos.sorted(), s.installed.map { "${it.id}:${it.version}:${it.needsTrust}" }.sorted(), s.showNsfw).toString()
                    }
                } + AddonRepository.uiState.map { s ->
                    // Stremio add-ons too (address, on/off, order)
                    s.addons.joinToString("|") { "${it.manifestUrl}:${it.enabled}" }
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
