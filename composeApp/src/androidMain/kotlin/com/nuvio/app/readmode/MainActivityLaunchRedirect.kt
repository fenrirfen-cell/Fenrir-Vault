package com.nuvio.app.readmode

import android.app.Activity

/**
 * Lets the Android app send a cold launch straight to Read mode when that was the last mode used.
 *
 * Called at the very start of [com.nuvio.app.MainActivity.onCreate] for fresh launches. If the
 * handler returns true it has already started the Read screen, and MainActivity finishes itself
 * without setting anything up. Null (the default) means "never redirect".
 */
object MainActivityLaunchRedirect {
    @Volatile
    var handler: ((Activity) -> Boolean)? = null
}
