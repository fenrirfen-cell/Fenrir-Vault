package com.nuvio.app.readmode

/**
 * Fenrir Read's own updates: the in-app updater checks these GitHub releases (not Nuvio's) and
 * compares build numbers ("r17" in "fenrir-1.1.0-beta.2-r17"). Set by the Android app.
 */
object FenrirUpdates {
    /** "owner/repo" of the Fenrir releases, or null for normal Nuvio behaviour. */
    var repo: String? = null

    /** This install's build number (from "...-r17"). */
    var currentBuild: Int? = null

    fun buildOf(tagOrName: String?): Int? =
        tagOrName?.let { Regex("-r(\\d+)(?:\\D|$)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
}
