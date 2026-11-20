package com.nuvio.app.readmode

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.lang.ref.WeakReference
import com.nuvio.app.MainActivity as WatchActivity
import eu.kanade.tachiyomi.ui.main.MainActivity as ReadActivity

/**
 * Switches between Watch (Nuvio's MainActivity) and Read (Tsundoku's MainActivity).
 *
 * Both main screens stay alive in the same task once opened, so switching is instant and each
 * side keeps its place. The last side used is remembered, and a launch from the home screen icon
 * reopens it.
 */
internal class ReadModeController(
    private val app: Application,
) : Application.ActivityLifecycleCallbacks {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentActivity: WeakReference<Activity>? = null

    private var lastMode: String
        get() = prefs.getString(KEY_LAST_MODE, MODE_WATCH) ?: MODE_WATCH
        set(value) = prefs.edit().putString(KEY_LAST_MODE, value).apply()

    /** From the Watch side (called by Nuvio's switch). */
    fun openReadFromCurrentActivity() {
        val activity = currentActivity?.get() ?: return
        val intent = Intent(activity, ReadActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        activity.startActivity(intent, fade(activity))
    }

    /** From the Read side (called by Tsundoku's switch). */
    fun openWatch(activity: Activity) {
        val intent = Intent()
            .setComponent(watchComponent())
            .putExtra(EXTRA_OPEN_WATCH, true)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        activity.startActivity(intent, fade(activity))
    }

    /** From the Read side's switch: go to the Watch side and open the Ani-drama tab. */
    fun openAniDrama(activity: Activity) {
        com.nuvio.app.features.anidrama.AniDramaBridge.requestOpen()
        openWatch(activity)
    }

    /**
     * Called at the start of Nuvio's MainActivity. On a launch from the home screen icon, when Read
     * was the last mode used, opens Read instead and returns true (MainActivity then finishes).
     */
    fun redirectLaunchIfNeeded(activity: Activity): Boolean {
        val intent = activity.intent ?: return false
        val isIconLaunch = intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (!isIconLaunch || intent.getBooleanExtra(EXTRA_OPEN_WATCH, false)) return false
        if (!activity.isTaskRoot || lastMode != MODE_READ) return false

        activity.startActivity(
            Intent(activity, ReadActivity::class.java),
            ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle(),
        )
        return true
    }

    /**
     * The Nuvio activity that is currently the launcher entry (Nuvio swaps between several to
     * change the app icon), so an already-open one is reused.
     */
    private fun watchComponent(): ComponentName =
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.component
            ?: ComponentName(app, WatchActivity::class.java)

    private fun fade(activity: Activity): Bundle =
        ActivityOptions.makeCustomAnimation(activity, android.R.anim.fade_in, android.R.anim.fade_out)
            .toBundle()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Back on the root of either main screen leaves the app (like pressing Home) instead of
        // closing that screen and revealing the other side underneath. Added before the screens
        // add their own back handling, so it only runs when nothing else handles back.
        if (activity is ReadActivity || activity is WatchActivity) {
            (activity as ComponentActivity).onBackPressedDispatcher.addCallback(
                activity,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        activity.moveTaskToBack(true)
                    }
                },
            )
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
        when (activity) {
            is ReadActivity -> lastMode = MODE_READ
            is WatchActivity -> lastMode = MODE_WATCH
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity?.get() === activity) currentActivity = null
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val PREFS_NAME = "nuvio_read_mode"
        const val KEY_LAST_MODE = "last_mode"
        const val MODE_WATCH = "watch"
        const val MODE_READ = "read"
        const val EXTRA_OPEN_WATCH = "com.nuvio.app.readmode.OPEN_WATCH"
    }
}
