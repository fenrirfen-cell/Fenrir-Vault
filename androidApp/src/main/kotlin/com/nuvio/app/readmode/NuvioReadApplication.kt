package com.nuvio.app.readmode

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
        FenrirUpdates.repo = "fenrirfen-cell/NuvioMobile1.0.0"
        FenrirUpdates.currentBuild = runCatching {
            FenrirUpdates.buildOf(packageManager.getPackageInfo(packageName, 0).versionName)
        }.getOrNull()

        // Fenrir style (Zangetsu-like layout), on by default; the Read side follows it too.
        FenrirStyle.install(enabled = aniDramaPrefs.getBoolean(KEY_FENRIR_STYLE, true)) { on ->
            aniDramaPrefs.edit().putBoolean(KEY_FENRIR_STYLE, on).apply()
        }
        EmbeddedHost.fenrirStyle = { FenrirStyle.enabled.value }

        // Settings → Sources: open the Read side's extensions and show how many are installed.
        FenrirSources.openReadExtensions = modes::openReadExtensions
        FenrirSources.mangaExtensionCount = EmbeddedHost::mangaExtensionCount
        FenrirSources.novelPluginCount = EmbeddedHost::novelPluginCount

        // Aniyomi extensions (anime plugins from the user's own repositories).
        FenrirPlugins.aniyomi = AniyomiPluginHost(this)
        // CloudStream plugins (.cs3 from the user's own repositories).
        FenrirPlugins.cloudStream = CloudStreamPluginHost(this)
        // Which CloudStream source Home shows (picked from the FENRIR READ drop-down); none = Nuvio home.
        com.nuvio.app.readmode.FenrirSourceHome.install(aniDramaPrefs.getString(KEY_HOME_SOURCE, null)) { id ->
            aniDramaPrefs.edit().apply { if (id == null) remove(KEY_HOME_SOURCE) else putString(KEY_HOME_SOURCE, id) }.apply()
        }
    }

    private companion object {
        const val KEY_KEEP_ROWS_ON_HOME = "keep_rows_on_home"
        const val KEY_SOURCE_FALLBACK = "source_fallback"
        const val KEY_FENRIR_STYLE = "fenrir_style"
        const val KEY_HOME_SOURCE = "home_cloudstream_source"
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
}
