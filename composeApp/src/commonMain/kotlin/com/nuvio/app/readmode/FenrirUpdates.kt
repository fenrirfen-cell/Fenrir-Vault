package com.nuvio.app.readmode

/**
 * Fenrir Vault's own updates: the in-app updater checks these GitHub releases (not Nuvio's) and
 * compares build numbers ("r17" in "fenrir-1.1.0-beta.2-r17"). Set by the Android app.
 */
object FenrirUpdates {
    /** "owner/repo" of the Fenrir releases, or null for normal Nuvio behaviour. */
    var repo: String? = null

    /** This install's build number (from "...-r17"). */
    var currentBuild: Int? = null

    /**
     * GitHub address listing the releases to look at: only the newest finished one ("latest",
     * never a beta) unless beta builds are switched on, then the recent ones including betas.
     */
    fun releasesUrl(repo: String): String =
        if (FenrirUpdateNotify.beta.value) "https://api.github.com/repos/$repo/releases?per_page=20"
        else "https://api.github.com/repos/$repo/releases/latest"

    fun buildOf(tagOrName: String?): Int? =
        tagOrName?.let { Regex("-r(\\d+)(?:\\D|$)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
}
