package com.nuvio.app.readmode

/**
 * Pull down at the top of Home: reloads Home and checks for a new Fenrir Vault version.
 * The app sets [checkForUpdates] once its updater exists.
 */
object FenrirPullRefresh {
    var checkForUpdates: (() -> Unit)? = null

    /** The updater's state, so the Read side can tell when a check found a new version. */
    /** Start (or install, once downloaded) and close the update, for the Read side's pill. */
    var downloadUpdate: (() -> Unit)? = null
    var installUpdate: (() -> Unit)? = null
    var dismissUpdate: (() -> Unit)? = null

    var updaterState: kotlinx.coroutines.flow.StateFlow<com.nuvio.app.features.updater.AppUpdaterUiState>? = null
}
