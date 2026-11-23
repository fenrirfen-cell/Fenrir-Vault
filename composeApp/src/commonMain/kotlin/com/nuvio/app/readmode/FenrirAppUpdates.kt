package com.nuvio.app.readmode

import com.nuvio.app.features.updater.AppUpdaterController

/**
 * One app-wide updater, shared by Watch and Read. Before, the updater only existed once the Watch
 * side had been opened, so opening straight on Read never found updates.
 */
object FenrirAppUpdates {
    val controller: AppUpdaterController by lazy { AppUpdaterController(kotlinx.coroutines.MainScope()) }

    /** Point the Read side's hooks at the shared updater (safe to call more than once). */
    fun wire() {
        val c = controller
        FenrirPullRefresh.checkForUpdates = { c.checkForUpdates(force = false, showNoUpdateFeedback = false) }
        FenrirPullRefresh.updaterState = c.uiState
        FenrirPullRefresh.downloadUpdate = c::downloadUpdate
        FenrirPullRefresh.installUpdate = c::installDownloadedUpdate
        FenrirPullRefresh.dismissUpdate = c::dismissDialog
    }
}
