package com.nuvio.app.readmode

/**
 * Links the Watch side's Settings → Sources group to the Read side (Tsundoku): opens its
 * extension list and reads how many manga extensions and novel plugins are installed.
 * Set by the Android app; null elsewhere, and then the Read rows are hidden.
 */
object FenrirSources {
    var openReadExtensions: (() -> Unit)? = null
    var mangaExtensionCount: (() -> Int)? = null
    var novelPluginCount: (() -> Int)? = null
}
