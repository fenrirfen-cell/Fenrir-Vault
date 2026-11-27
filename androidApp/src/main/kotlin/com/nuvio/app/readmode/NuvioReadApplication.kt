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

    // Fenrir: plugins (CloudStream, extensions) can hook into every screen of the app. Some are
    // written for the CloudStream app itself and break here (WatchParty looks for CloudStream's
    // own screen and crashed the app on every resume). Their hooks now run guarded: a failing
    // one is switched off instead of taking the app down.
    private val guardedCallbacks = java.util.WeakHashMap<ActivityLifecycleCallbacks, ActivityLifecycleCallbacks>()

    private fun fromOutside(cb: Any): Boolean {
        val loader = cb.javaClass.classLoader ?: return false
        return loader !== classLoader && loader !== android.app.Application::class.java.classLoader
    }

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        if (!fromOutside(callback)) return super.registerActivityLifecycleCallbacks(callback)
        val guarded = GuardedLifecycleCallbacks(callback)
        synchronized(guardedCallbacks) { guardedCallbacks[callback] = guarded }
        super.registerActivityLifecycleCallbacks(guarded)
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        val guarded = synchronized(guardedCallbacks) { guardedCallbacks.remove(callback) }
        super.unregisterActivityLifecycleCallbacks(guarded ?: callback)
    }

    /**
     * Fenrir: checks add-ons, CloudStream plugins and Aniyomi extensions by themselves: when the
     * app opens (once the last check is over 4.5 hours old) and again every 4.5 hours while it runs.
     */
    private fun fenrirAutoChecks() {
        val prefs = getSharedPreferences("fenrir_auto_check", MODE_PRIVATE)
        com.nuvio.app.features.addons.FenrirAddonCheck.store = object : com.nuvio.app.features.addons.FenrirAddonCheck.Store {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun put(key: String, value: String?) {
                prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        }
        val every = 4L * 60 * 60 * 1000 + 30 * 60 * 1000
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default +
                kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> },
        ).launch {
            kotlinx.coroutines.delay(20_000) // let the app finish starting first
            while (true) {
                val now = System.currentTimeMillis()
                if (now - prefs.getLong("last_run", 0L) >= every) {
                    prefs.edit().putLong("last_run", now).apply()
                    runCatching { com.nuvio.app.features.addons.FenrirAddonCheck.runAutomatic() }
                    listOfNotNull(com.nuvio.app.readmode.FenrirPlugins.cloudStream, com.nuvio.app.readmode.FenrirPlugins.aniyomi).forEach { host ->
                        runCatching { host.test(host.state.value.installed.filter { !it.needsTrust }.map { it.id }) }
                    }
                }
                kotlinx.coroutines.delay(30L * 60 * 1000)
            }
        }
    }

    private class GuardedLifecycleCallbacks(private val inner: ActivityLifecycleCallbacks) : ActivityLifecycleCallbacks {
        @Volatile private var broken = false
        private inline fun safe(block: () -> Unit) {
            if (broken) return
            try {
                block()
            } catch (t: Throwable) {
                broken = true
                android.util.Log.w("FenrirPluginGuard", "A plugin's screen hook failed and was switched off", t)
            }
        }
        override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) = safe { inner.onActivityCreated(a, b) }
        override fun onActivityStarted(a: android.app.Activity) = safe { inner.onActivityStarted(a) }
        override fun onActivityResumed(a: android.app.Activity) = safe { inner.onActivityResumed(a) }
        override fun onActivityPaused(a: android.app.Activity) = safe { inner.onActivityPaused(a) }
        override fun onActivityStopped(a: android.app.Activity) = safe { inner.onActivityStopped(a) }
        override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) = safe { inner.onActivitySaveInstanceState(a, b) }
        override fun onActivityDestroyed(a: android.app.Activity) = safe { inner.onActivityDestroyed(a) }
        override fun onActivityPreCreated(a: android.app.Activity, b: android.os.Bundle?) = safe { inner.onActivityPreCreated(a, b) }
        override fun onActivityPostCreated(a: android.app.Activity, b: android.os.Bundle?) = safe { inner.onActivityPostCreated(a, b) }
        override fun onActivityPreResumed(a: android.app.Activity) = safe { inner.onActivityPreResumed(a) }
        override fun onActivityPostResumed(a: android.app.Activity) = safe { inner.onActivityPostResumed(a) }
    }

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

        // Ani-drama keeps its downloaded lists on the phone so the tab opens straight away.
        val aniDramaCacheDir = java.io.File(cacheDir, "anidrama").apply { mkdirs() }
        com.nuvio.app.features.anidrama.AniDramaDiskCache.install(
            read = { name ->
                val f = java.io.File(aniDramaCacheDir, name)
                if (f.isFile) f.lastModified() to f.readText() else null
            },
            write = { name, body ->
                val f = java.io.File(aniDramaCacheDir, name)
                val tmp = java.io.File(aniDramaCacheDir, "$name.tmp")
                tmp.writeText(body)
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            },
        )

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
        fenrirAutoChecks()
        // Background update check + "Update available" notifications (even with the app closed).
        runCatching { com.nuvio.app.readmode.FenrirUpdateNotifier.install(this) }
        EmbeddedHost.updateSettings = { com.nuvio.app.readmode.FenrirUpdateSettingsCard() }

        // Fenrir style (Zangetsu-like layout), on by default; the Read side follows it too.
        FenrirStyle.install(enabled = aniDramaPrefs.getBoolean(KEY_FENRIR_STYLE, true)) { on ->
            aniDramaPrefs.edit().putBoolean(KEY_FENRIR_STYLE, on).apply()
        }
        EmbeddedHost.fenrirStyle = { FenrirStyle.enabled.value }
        // MugiVerse look (compass bar, header, Three Tides opening), on by default; both sides.
        com.nuvio.app.mugi.MugiStyle.install(enabled = aniDramaPrefs.getBoolean("mugiverse_look", true)) { on ->
            aniDramaPrefs.edit().putBoolean("mugiverse_look", on).apply()
        }
        EmbeddedHost.mugi = { FenrirStyle.enabled.value && com.nuvio.app.mugi.MugiStyle.enabled.value }
        com.nuvio.app.mugi.MugiStyle.reduceMotion = {
            runCatching {
                android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
        }
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
        // Backup of both (Downloads/Fenrir Vault, kept when the app is deleted; restore in Settings → Sources).
        com.nuvio.app.readmode.FenrirPluginBackup.files = com.nuvio.app.readmode.FenrirBackupFilesAndroid(this)
        com.nuvio.app.readmode.FenrirPluginBackup.startAutoSave()
        // Which CloudStream source Home shows (picked from the FENRIR READ drop-down); none = Nuvio home.
        // Where each plugin source is used (Home menu, pinned, search, streams).
        com.nuvio.app.readmode.FenrirSourceFlags.install(aniDramaPrefs.getString(KEY_SOURCE_FLAGS, null)) { text ->
            aniDramaPrefs.edit().putString(KEY_SOURCE_FLAGS, text).apply()
        }
        // Profiles whose Home lists were already set to Fenrir's picks (then the user decides).
        com.nuvio.app.readmode.FenrirHomeFilter.installCurated(aniDramaPrefs.getString(KEY_CURATED_PROFILES, null)) { text ->
            aniDramaPrefs.edit().putString(KEY_CURATED_PROFILES, text).apply()
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
        const val KEY_CURATED_PROFILES = "fenrir_curated_home_profiles"
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
