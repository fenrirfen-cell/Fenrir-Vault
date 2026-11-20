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

        MainActivityLaunchRedirect.handler = modes::redirectLaunchIfNeeded

        // Ani-drama tab: anime + K/C/J-drama browser. Remembers whether its rows also stay on Home.
        val aniDramaPrefs = getSharedPreferences("fenrir_anidrama", MODE_PRIVATE)
        AniDramaBridge.install(
            keepRowsOnHome = aniDramaPrefs.getBoolean(KEY_KEEP_ROWS_ON_HOME, false),
        ) { keep -> aniDramaPrefs.edit().putBoolean(KEY_KEEP_ROWS_ON_HOME, keep).apply() }
    }

    private companion object {
        const val KEY_KEEP_ROWS_ON_HOME = "keep_rows_on_home"
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
