package com.nuvio.app.readmode

/**
 * Pull down at the top of Home: reloads Home and checks for a new Fenrir Read version.
 * The app sets [checkForUpdates] once its updater exists.
 */
object FenrirPullRefresh {
    var checkForUpdates: (() -> Unit)? = null
}
