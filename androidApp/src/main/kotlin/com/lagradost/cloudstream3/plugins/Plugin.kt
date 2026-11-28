package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * Base class CloudStream plugins extend (normally part of the CloudStream app). Fenrir Vault
 * provides the parts plugins use to register their sites and extractors.
 */
abstract class Plugin : BasePlugin() {
    /** Called when the plugin is loaded. */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    /** The plugin's own resources, when its manifest asks for them. */
    var resources: Resources? = null

    /** Plugin settings screen; Fenrir Vault doesn't show these yet. */
    var openSettings: ((context: Context) -> Unit)? = null
}
