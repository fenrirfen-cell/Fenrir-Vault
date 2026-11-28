package com.nuvio.app

import android.content.Intent
import android.content.res.Configuration
import com.nuvio.app.features.autosync.AutoSyncPreferencesAndroid
import android.os.Bundle
import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nuvio.app.core.auth.AuthStorage
import com.nuvio.app.core.network.ServerConfigurationStorage
import com.nuvio.app.core.diagnostics.SentryInitializer
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.core.storage.PlatformLocalAccountDataCleaner
import com.nuvio.app.core.sync.SyncClientIdentityStorage
import com.nuvio.app.features.addons.AddonHttpClientProvider
import com.nuvio.app.features.addons.AddonStorage
import com.nuvio.app.features.collection.CollectionMobileSettingsStorage
import com.nuvio.app.features.collection.CollectionStorage
import com.nuvio.app.features.debrid.DebridSettingsStorage
import com.nuvio.app.features.downloads.DownloadsLiveStatusPlatform
import com.nuvio.app.features.downloads.DownloadsPlatformDownloader
import com.nuvio.app.features.downloads.DownloadsStorage
import com.nuvio.app.features.library.LibraryDisplaySettingsStorage
import com.nuvio.app.features.membership.MemberAssetStorage
import com.nuvio.app.features.library.LibraryStorage
import com.nuvio.app.features.details.MetaScreenSettingsStorage
import com.nuvio.app.features.home.HomeCatalogSettingsStorage
import com.nuvio.app.features.mdblist.MdbListSettingsStorage
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsStorage
import com.nuvio.app.features.player.PlayerSettingsStorage
import com.nuvio.app.features.player.PlayerTrackPreferenceStorage
import com.nuvio.app.features.player.ExternalPlayerPlatform
import com.nuvio.app.features.player.SubtitleFileCache
import com.nuvio.app.features.player.PlayerPictureInPictureManager
import com.nuvio.app.features.player.PipRemoteActionReceiver
import com.nuvio.app.features.p2p.P2pSettingsStorage
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.plugins.PluginStorage
import com.nuvio.app.features.profiles.AvatarStorage
import com.nuvio.app.features.profiles.ProfilePinCacheStorage
import com.nuvio.app.features.profiles.ProfileStorage
import com.nuvio.app.features.details.SeasonViewModeStorage
import com.nuvio.app.features.search.DiscoverSelectionStorage
import com.nuvio.app.features.search.SearchHistoryStorage
import com.nuvio.app.features.settings.SentrySettingsStorage
import com.nuvio.app.features.settings.AppIconPlatform
import com.nuvio.app.features.settings.ThemeSettingsStorage
import com.nuvio.app.features.trakt.TraktAuthStorage
import com.nuvio.app.features.trakt.TraktCommentsStorage
import com.nuvio.app.features.trakt.TraktLibraryStorage
import com.nuvio.app.features.trakt.TraktSettingsStorage
import com.nuvio.app.features.mdblist.PlatformMdbListAuthPersistence
import com.nuvio.app.features.mdblist.PlatformMdbListSyncStorage
import com.nuvio.app.features.servers.ServerStorage
import com.nuvio.app.features.simkl.SimklAuthStorage
import com.nuvio.app.features.simkl.SimklSyncStorage
import com.nuvio.app.features.tmdb.TmdbSettingsStorage
import com.nuvio.app.features.updater.AndroidAppUpdaterPlatform
import com.nuvio.app.core.ui.CardDepthStyleStorage
import com.nuvio.app.core.ui.PosterCardStyleStorage
import com.nuvio.app.core.poster.CustomPosterUrlStorage
import com.nuvio.app.features.watched.WatchedStorage
import com.nuvio.app.features.streams.StreamLinkCacheStorage
import com.nuvio.app.features.streams.StreamBadgeSettingsStorage
import com.nuvio.app.features.streams.BingeGroupCacheStorage
import com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentStorage
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesStorage
import com.nuvio.app.features.watchprogress.ResumePromptStorage
import com.nuvio.app.features.watchprogress.WatchProgressStorage
import com.nuvio.app.readmode.MainActivityLaunchRedirect

open class MainActivity : AppCompatActivity() {
    // Fenrir: windows a source's own code tries to open are blocked (see SourcePopupGuard)
    private var fenrirGuardedWindowManager: Any? = null

    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name)
        if (name != android.content.Context.WINDOW_SERVICE || service == null) return service
        return fenrirGuardedWindowManager
            ?: com.nuvio.app.core.SourcePopupGuard.wrap(this, service).also { fenrirGuardedWindowManager = it }
    }

    private var pipRemoteActionReceiver: PipRemoteActionReceiver? = null
    private var redirectedToReadMode = false

    private fun fenrirHowlSetup() = fenrirInstallHowl(applicationContext)

    private fun fenrirHowl(quietCheck: Boolean) {
        if (!com.nuvio.app.readmode.FenrirHowl.enabled.value) return
        val am = getSystemService(android.media.AudioManager::class.java) ?: return
        if (quietCheck && (am.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL || am.isMusicActive)) return
        fenrirPlayHowl(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // MugiVerse: a wolf howl on a fresh start (setting in Settings; quiet on silent/vibrate).
        fenrirHowlSetup()
        // The opening animation starts the howl in time with the wolf; going straight to Read has no animation.
        // Read mode: a fresh launch may go straight to the Read screen instead.
        if (savedInstanceState == null && MainActivityLaunchRedirect.handler?.invoke(this) == true) {
            redirectedToReadMode = true
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        enableEdgeToEdge(
            // MugiVerse screens are dark: light status bar icons
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(
                scrim = 0xFF020404.toInt(),
            ),
        )
        ThemeSettingsStorage.initialize(applicationContext)
        AppIconPlatform.initialize(applicationContext)
        SentrySettingsStorage.initialize(applicationContext)
        SentryInitializer.start(application)
        super.onCreate(savedInstanceState)
        AutoSyncPreferencesAndroid.initialize(applicationContext) // AutoSync hook
        window.setBackgroundDrawableResource(R.color.nuvio_background)
        pipRemoteActionReceiver = PipRemoteActionReceiver.register(this)
        SyncClientIdentityStorage.initialize(applicationContext)
        AddonHttpClientProvider.initialize(applicationContext)
        AddonStorage.initialize(applicationContext)
        AuthStorage.initialize(applicationContext)
        ServerConfigurationStorage.initialize(applicationContext)
        LibraryStorage.initialize(applicationContext)
        WatchedStorage.initialize(applicationContext)
        MetaScreenSettingsStorage.initialize(applicationContext)
        com.nuvio.app.features.shuffle.EpisodeShuffleStorage.initialize(applicationContext)
        HomeCatalogSettingsStorage.initialize(applicationContext)
        // Fenrir assistant: preferences storage and voice input (phone's own speech-to-text).
        val fenrirAssistantPrefs = applicationContext.getSharedPreferences("fenrir_assistant", android.content.Context.MODE_PRIVATE)
        com.nuvio.app.assistant.AssistantSettings.load = { fenrirAssistantPrefs.getString("prefs", null) }
        com.nuvio.app.assistant.AssistantSettings.save = { value -> fenrirAssistantPrefs.edit().putString("prefs", value).apply() }
        com.nuvio.app.assistant.AssistantPlayback.startVoice = { onSpoken ->
            var voiceLauncher: androidx.activity.result.ActivityResultLauncher<Intent>? = null
            voiceLauncher = activityResultRegistry.register(
                "fenrir_voice_" + System.nanoTime(),
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                voiceLauncher?.unregister()
                val spoken = result.data
                    ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) onSpoken(spoken)
            }
            val voiceIntent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Ask Fenrir")
            runCatching { voiceLauncher?.launch(voiceIntent) }.onFailure {
                voiceLauncher?.unregister()
                android.widget.Toast.makeText(this, "Voice input isn't available on this phone", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        PlayerSettingsStorage.initialize(applicationContext)
        PlayerTrackPreferenceStorage.initialize(applicationContext)
        P2pSettingsStorage.initialize(applicationContext)
        P2pStreamingEngine.initialize(applicationContext)
        ExternalPlayerPlatform.initialize(applicationContext)
        SubtitleFileCache.initialize(applicationContext)
        ProfileStorage.initialize(applicationContext)
        AvatarStorage.initialize(applicationContext)
        ProfilePinCacheStorage.initialize(applicationContext)
        MemberAssetStorage.initialize(applicationContext)
        DiscoverSelectionStorage.initialize(applicationContext)
        SearchHistoryStorage.initialize(applicationContext)
        SeasonViewModeStorage.initialize(applicationContext)
        PosterCardStyleStorage.initialize(applicationContext)
        CustomPosterUrlStorage.initialize(applicationContext)
        CardDepthStyleStorage.initialize(applicationContext)
        DebridSettingsStorage.initialize(applicationContext)
        TmdbSettingsStorage.initialize(applicationContext)
        MdbListSettingsStorage.initialize(applicationContext)
        TraktAuthStorage.initialize(applicationContext)
        TraktCommentsStorage.initialize(applicationContext)
        TraktLibraryStorage.initialize(applicationContext)
        TraktSettingsStorage.initialize(applicationContext)
        PlatformMdbListAuthPersistence.initialize(applicationContext)
        ServerStorage.initialize(applicationContext)
        PlatformMdbListSyncStorage.initialize(applicationContext)
        SimklAuthStorage.initialize(applicationContext)
        SimklSyncStorage.initialize(applicationContext)
        LibraryDisplaySettingsStorage.initialize(applicationContext)
        ContinueWatchingPreferencesStorage.initialize(applicationContext)
        ResumePromptStorage.initialize(applicationContext)
        ContinueWatchingEnrichmentStorage.initialize(applicationContext)
        EpisodeReleaseNotificationsStorage.initialize(applicationContext)
        WatchProgressStorage.initialize(applicationContext)
        StreamLinkCacheStorage.initialize(applicationContext)
        StreamBadgeSettingsStorage.initialize(applicationContext)
        BingeGroupCacheStorage.initialize(applicationContext)
        PluginStorage.initialize(applicationContext)
        CollectionMobileSettingsStorage.initialize(applicationContext)
        CollectionStorage.initialize(applicationContext)
        DownloadsStorage.initialize(applicationContext)
        DownloadsPlatformDownloader.initialize(applicationContext)
        DownloadsLiveStatusPlatform.initialize(applicationContext)
        AndroidAppUpdaterPlatform.initialize(applicationContext)
        PlatformLocalAccountDataCleaner.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.bindActivity(this)
        handleIncomingAppIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAppIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlayerPictureInPictureManager.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerPictureInPictureManager.onPictureInPictureModeChanged(this, isInPictureInPictureMode)
    }

    override fun onDestroy() {
        if (redirectedToReadMode) {
            super.onDestroy()
            return
        }
        EpisodeReleaseNotificationPlatform.unbindActivity(this)
        val receiver = pipRemoteActionReceiver
        if (receiver != null) {
            runCatching { unregisterReceiver(receiver) }
            pipRemoteActionReceiver = null
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (EpisodeReleaseNotificationPlatform.handlePermissionRequestResult(requestCode, grantResults)) {
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun handleIncomingAppIntent(intent: Intent?) {
        val appUrl = (intent?.dataString ?: intent?.getStringExtra(LinkActivity.EXTRA_LINK))?.trim().orEmpty()
        if (appUrl.isBlank()) return
        handleAppUrl(appUrl)
    }
}


/** Plays the howl once; any problem just means no sound. */
private fun fenrirPlayHowl(context: android.content.Context) {
    runCatching {
        val player = android.media.MediaPlayer()
        player.setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        context.resources.openRawResourceFd(R.raw.fenrir_howl).use { fd ->
            player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        player.setVolume(0.7f, 0.7f)
        player.setOnCompletionListener { runCatching { it.release() } }
        player.setOnErrorListener { mp, _, _ -> runCatching { mp.release() }; true }
        player.setOnPreparedListener { it.start() }
        player.prepareAsync()
    }
}

/** The opening howl: only when it's on and the phone isn't on silent / vibrate or playing music. */
private fun fenrirOpenHowl(context: android.content.Context) {
    if (!com.nuvio.app.readmode.FenrirHowl.enabled.value) return
    val am = context.getSystemService(android.media.AudioManager::class.java) ?: return
    if (am.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL || am.isMusicActive) return
    fenrirPlayHowl(context)
}

/** Loads the howl setting and wires play / save; called from the Application too, so it works when the app reopens on Read. */
fun fenrirInstallHowl(context: android.content.Context) {
    val app = context.applicationContext
    val prefs = app.getSharedPreferences("fenrir_vault", android.content.Context.MODE_PRIVATE)
    com.nuvio.app.readmode.FenrirHowl.load(prefs.getBoolean("howl_on_open", true))
    com.nuvio.app.readmode.FenrirHowl.save = { on -> prefs.edit().putBoolean("howl_on_open", on).apply() }
    com.nuvio.app.readmode.FenrirHowl.play = { fenrirPlayHowl(app) }
    com.nuvio.app.readmode.FenrirHowl.openHowl = { fenrirOpenHowl(app) }
    com.nuvio.app.readmode.FenrirSplashVideo.player = { onEnd -> com.nuvio.app.readmode.FenrirSplashFilm(onEnd) }
}
