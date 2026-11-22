package com.nuvio.app.readmode

/**
 * Pull down at the top of Home: reloads Home and checks for a new Fenrir Read version.
 * The app sets [checkForUpdates] once its updater exists.
 */
object FenrirPullRefresh {
    var checkForUpdates: (() -> Unit)? = null

    /** The updater's state, so the Read side can tell when a check found a new version. */
    var updaterState: kotlinx.coroutines.flow.StateFlow<com.nuvio.app.features.updater.AppUpdaterUiState>? = null
}
