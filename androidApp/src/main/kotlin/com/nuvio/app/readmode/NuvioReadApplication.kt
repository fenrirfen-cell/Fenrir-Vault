package com.nuvio.app.readmode

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.Context
import coil3.ImageLoader
import com.nuvio.android.R
import com.nuvio.app.NuvioImageLoaderComponents
import com.nuvio.app.features.anidrama.AniDramaBridge
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.EmbeddedHost

/**
 * Application class for Nuvio with Read mode.
 *
 * An Android app can only have one Application class, so this one extends Tsundoku's [App]
 * (which sets up Tsundoku's database, sources, dependency injection, notifications and so on)
 * and adds what Nuvio's own `NuvioApplication` does: Nuvio's Coil components. Everything else
 * Nuvio needs is set up by its MainActivity, as before.
 */
class NuvioReadApplication : App() {

    override fun onCreate() {
        super.onCreate()

        val modes = ReadModeController(this)
        registerActivityLifecycleCallbacks(modes)

        ReadModeBridge.watchLabel = getString(R.string.read_mode_watch)
        ReadModeBridge.readLabel = getString(R.string.read_mode_read)
        ReadModeBridge.openRead = modes::openReadFromCurrentActivity

        EmbeddedHost.watchLabel = ReadModeBridge.watchLabel
        EmbeddedHost.readLabel = ReadModeBridge.readLabel
        EmbeddedHost.openWatch = modes::openWatch
        EmbeddedHost.openAniDrama = modes::openAniDrama

        MainActivityLaunchRedirect.handler = modes::redirectLaunchIfNeeded

        // Ani-drama tab: anime + K/C/J-drama browser. Remembers whether its rows also stay on Home.
        val aniDramaPrefs = getSharedPreferences("fenrir_anidrama", MODE_PRIVATE)
        AniDramaBridge.install(
            keepRowsOnHome = aniDramaPrefs.getBoolean(KEY_KEEP_ROWS_ON_HOME, false),
        ) { keep -> aniDramaPrefs.edit().putBoolean(KEY_KEEP_ROWS_ON_HOME, keep).apply() }

        // Try the next source automatically when a link fails (on by default in Fenrir).
        com.nuvio.app.features.player.FenrirSourceFallback.install(
            enabled = aniDramaPrefs.getBoolean(KEY_SOURCE_FALLBACK, true),
        ) { on -> aniDramaPrefs.edit().putBoolean(KEY_SOURCE_FALLBACK, on).apply() }

        // Episode reminders from the Ani-drama calendar (notification + optional spoken alert).
        com.nuvio.app.features.anidrama.AniDramaRemindersAndroid.install(this)

        // In-app updates come from Fenrir Read's own GitHub releases, compared by build number.
        FenrirUpdates.repo = "fenrirfen-cell/Fenrir-Vault"
        FenrirUpdates.currentBuild = runCatching {
            FenrirUpdates.buildOf(packageManager.getPackageInfo(packageName, 0).versionName)
        }.getOrNull()
        fenrirWhatsNew()
        // Background update check + "Update available" notifications (even with the app closed).
        runCatching { com.nuvio.app.readmode.FenrirUpdateNotifier.install(this) }
        EmbeddedHost.updateSettings = { com.nuvio.app.readmode.FenrirUpdateSettingsCard() }

        // Fenrir style (Zangetsu-like layout), on by default; the Read side follows it too.
        FenrirStyle.install(enabled = aniDramaPrefs.getBoolean(KEY_FENRIR_STYLE, true)) { on ->
            aniDramaPrefs.edit().putBoolean(KEY_FENRIR_STYLE, on).apply()
        }
        EmbeddedHost.fenrirStyle = { FenrirStyle.enabled.value }
        // The opening animation also plays when the app opens straight on Read (once per start).
        EmbeddedHost.splash = {
            com.nuvio.app.readmode.FenrirSplash()
            com.nuvio.app.readmode.FenrirWhatsNewCard()
        }
        com.nuvio.app.fenrirInstallHowl(this)
        // Pull to refresh on the Read side also checks quietly for a new Fenrir Read version.
        // When there is one, the Read side shows a small pill with an Update button.
        val updateScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> })
        var mirrorJob: kotlinx.coroutines.Job? = null
        EmbeddedHost.checkAppUpdate = check@{ onFound ->
            // works even if the Watch side was never opened; never lets a check close the app
            if (runCatching { com.nuvio.app.readmode.FenrirAppUpdates.wire() }.isFailure) return@check
            val check = com.nuvio.app.readmode.FenrirPullRefresh.checkForUpdates
            val state = com.nuvio.app.readmode.FenrirPullRefresh.updaterState
            if (check != null && state != null) {
                runCatching { check() }
                mirrorJob?.cancel()
                mirrorJob = updateScope.launch {
                    kotlinx.coroutines.delay(400)
                    kotlinx.coroutines.withTimeoutOrNull(20_000) { state.first { !it.isChecking } } ?: return@launch
                    // Mirror the updater into the Read side until the pill is closed.
                    state.collect { s ->
                        val update = s.update
                        EmbeddedHost.appUpdate.value = if (s.showDialog && update != null) {
                            EmbeddedHost.AppUpdateInfo(
                                version = update.tag,
                                progress = s.downloadProgress,
                                downloading = s.isDownloading,
                                ready = s.downloadedApkPath != null,
                                error = s.errorMessage,
                            )
                        } else {
                            null
                        }
                        if (s.showDialog && update != null && !s.isDownloading) onFound(update.tag)
                    }
                }
            }
        }
        EmbeddedHost.startAppUpdate = {
            val ready = EmbeddedHost.appUpdate.value?.ready == true
            if (ready) {
                com.nuvio.app.readmode.FenrirPullRefresh.installUpdate?.invoke()
            } else {
                com.nuvio.app.readmode.FenrirPullRefresh.downloadUpdate?.invoke()
            }
        }
        EmbeddedHost.dismissAppUpdate = {
            com.nuvio.app.readmode.FenrirPullRefresh.dismissUpdate?.invoke()
            EmbeddedHost.appUpdate.value = null
        }

        // Settings → Sources: open the Read side's extensions and show how many are installed.
        FenrirSources.openReadExtensions = modes::openReadExtensions
        FenrirSources.mangaExtensionCount = EmbeddedHost::mangaExtensionCount
        FenrirSources.novelPluginCount = EmbeddedHost::novelPluginCount

        // Aniyomi extensions (anime plugins from the user's own repositories).
        FenrirPlugins.aniyomi = AniyomiPluginHost(this)
        // CloudStream plugins (.cs3 from the user's own repositories).
        FenrirPlugins.cloudStream = CloudStreamPluginHost(this)
        // Which CloudStream source Home shows (picked from the FENRIR READ drop-down); none = Nuvio home.
        // Where each plugin source is used (Home menu, pinned, search, streams).
        com.nuvio.app.readmode.FenrirSourceFlags.install(aniDramaPrefs.getString(KEY_SOURCE_FLAGS, null)) { text ->
            aniDramaPrefs.edit().putString(KEY_SOURCE_FLAGS, text).apply()
        }
        // "Links from" on show pages (All / Nuvio add-ons / Plugins), per title.
        com.nuvio.app.readmode.FenrirLinkChoice.install(aniDramaPrefs.getString(KEY_LINK_CHOICE, null)) { text ->
            aniDramaPrefs.edit().putString(KEY_LINK_CHOICE, text).apply()
        }
        com.nuvio.app.readmode.FenrirSourceHome.install(aniDramaPrefs.getString(KEY_HOME_SOURCE, null)) { id ->
            aniDramaPrefs.edit().apply { if (id == null) remove(KEY_HOME_SOURCE) else putString(KEY_HOME_SOURCE, id) }.apply()
        }
    }

    private companion object {
        const val KEY_KEEP_ROWS_ON_HOME = "keep_rows_on_home"
        const val KEY_SOURCE_FALLBACK = "source_fallback"
        const val KEY_FENRIR_STYLE = "fenrir_style"
        const val KEY_HOME_SOURCE = "home_cloudstream_source"
        const val KEY_SOURCE_FLAGS = "fenrir_source_flags"
        const val KEY_LINK_CHOICE = "fenrir_link_choice"
    }

    /**
     * One image loader for both halves: Tsundoku's (network client, cover fetchers, memory cache,
     * AVIF/JXL decoder) plus Nuvio's artwork interceptors and GIF/SVG decoders. Nuvio's interceptors
     * only act on Nuvio's own requests, and Tsundoku's network fetcher serves plain image URLs for
     * both, so Nuvio's separate Ktor fetcher is not added.
     */
    override fun newImageLoader(context: Context): ImageLoader {
        val tsundokuLoader = super.newImageLoader(context)
        val components = tsundokuLoader.components.newBuilder().apply {
            NuvioImageLoaderComponents.addInterceptors(this)
            NuvioImageLoaderComponents.addDecoders(this)
        }.build()
        return tsundokuLoader.newBuilder()
            .components(components)
            .build()
    }

    /** After an update, fetch this build's "What's new" from its GitHub release (once). */
    private fun fenrirWhatsNew() {
        val build = FenrirUpdates.currentBuild ?: return
        val repo = FenrirUpdates.repo ?: return
        val prefs = getSharedPreferences("fenrir_vault", MODE_PRIVATE)
        com.nuvio.app.readmode.FenrirWhatsNew.markSeen = { b -> prefs.edit().putInt("whats_new_seen", b).apply() }
        val seen = prefs.getInt("whats_new_seen", -1)
        if (seen >= build) return
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        // A fresh install isn't an update: nothing to announce.
        if (seen < 0 && info != null && info.firstInstallTime == info.lastUpdateTime) {
            prefs.edit().putInt("whats_new_seen", build).apply()
            return
        }
        Thread {
            runCatching {
                val conn = java.net.URL("https://api.github.com/repos/$repo/releases?per_page=10").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(text)
                for (i in 0 until arr.length()) {
                    val rel = arr.getJSONObject(i)
                    if (FenrirUpdates.buildOf(rel.optString("tag_name")) == build) {
                        val bullets = com.nuvio.app.readmode.FenrirWhatsNew.parse(rel.optString("body"))
                        if (bullets.isEmpty()) {
                            prefs.edit().putInt("whats_new_seen", build).apply()
                        } else {
                            com.nuvio.app.readmode.FenrirWhatsNew.notes.value =
                                com.nuvio.app.readmode.FenrirWhatsNew.Notes(build, bullets)
                        }
                        break
                    }
                }
            }
        }.start()
    }
}
