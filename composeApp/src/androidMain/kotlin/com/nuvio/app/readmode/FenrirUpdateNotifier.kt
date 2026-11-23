package com.nuvio.app.readmode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Fenrir: background update notifications. Every few hours (Android picks a good moment, only
 * with internet) it looks at the latest GitHub release. When it's a newer build than this one:
 * "Update available" (Update / What's new). Update downloads with a progress notification, then
 * "Update ready to install" opens Android's installer. Each new version is announced once.
 */
object FenrirUpdateNotifier {
    private const val PREFS = "fenrir_update_notify"
    private const val CHANNEL = "fenrir_updates"
    private const val CHECK_WORK = "fenrir_update_check"
    private const val DOWNLOAD_WORK = "fenrir_update_download"
    internal const val NOTIF_ID = 47_101
    internal const val ACTION_DOWNLOAD = "com.fenrirread.action.UPDATE_DOWNLOAD"

    private lateinit var app: Context

    fun install(context: Context) {
        app = context.applicationContext
        // The updater must be ready even when the app opens straight on Read (it used to be set up
        // only by the Watch screen, so an update check from Read closed the app).
        runCatching { com.nuvio.app.features.updater.AndroidAppUpdaterPlatform.initialize(app) }
        val p = prefs()
        FenrirUpdateNotify.enabled.value = p.getBoolean("enabled", true)
        FenrirUpdateNotify.hours.value = p.getInt("hours", 6)
        FenrirUpdateNotify.prefetch.value = p.getBoolean("prefetch", false)
        FenrirUpdateNotify.onChange = {
            prefs().edit()
                .putBoolean("enabled", FenrirUpdateNotify.enabled.value)
                .putInt("hours", FenrirUpdateNotify.hours.value)
                .putBoolean("prefetch", FenrirUpdateNotify.prefetch.value)
                .apply()
            runCatching { schedule(replace = true) }
        }
        FenrirUpdateNotify.openBatterySettings = { openBattery() }
        runCatching { schedule(replace = false) }
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun schedule(replace: Boolean) {
        val wm = WorkManager.getInstance(app)
        if (!FenrirUpdateNotify.enabled.value || FenrirUpdates.repo == null) {
            wm.cancelUniqueWork(CHECK_WORK)
            return
        }
        val hours = FenrirUpdateNotify.hours.value.coerceIn(1, 24).toLong()
        val request = PeriodicWorkRequestBuilder<FenrirUpdateCheckWorker>(hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(20, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            CHECK_WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun openBattery() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    // ---- release lookup ----

    internal class Release(val tag: String, val build: Int, val pageUrl: String?, val apkUrl: String, val apkName: String, val size: Long)

    internal fun fetchLatest(): Release? {
        val repo = FenrirUpdates.repo ?: return null
        val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FenrirVault")
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        try {
            if (conn.responseCode !in 200..299) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            val build = FenrirUpdates.buildOf(tag) ?: FenrirUpdates.buildOf(json.optString("name")) ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return Release(tag, build, json.optString("html_url").ifBlank { null }, a.optString("browser_download_url"), name, a.optLong("size", -1))
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    internal fun isNewer(r: Release): Boolean {
        val current = FenrirUpdates.currentBuild ?: return false
        return r.build > current
    }

    internal fun lastNotified(): String? = prefs().getString("last_notified", null)
    internal fun setLastNotified(tag: String) = prefs().edit().putString("last_notified", tag).apply()
    internal fun prefetchOn(): Boolean = prefs().getBoolean("prefetch", false)

    // ---- notifications ----

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "New versions of Fenrir Vault"
                    },
                )
            }
        }
    }

    private fun base(ctx: Context): NotificationCompat.Builder {
        ensureChannel(ctx)
        val logo = runCatching {
            val id = ctx.resources.getIdentifier("ic_launcher_fenrir", "mipmap", ctx.packageName)
            if (id != 0) BitmapFactory.decodeResource(ctx.resources, id) else null
        }.getOrNull()
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setColor(0xFF9650E6.toInt())
            .apply { if (logo != null) setLargeIcon(logo) }
            .setOnlyAlertOnce(true)
    }

    private fun post(ctx: Context, n: NotificationCompat.Builder) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n.build()) }
    }

    private fun immutable() = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    internal fun notifyAvailable(ctx: Context, r: Release) {
        val download = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, FenrirUpdateActionReceiver::class.java).setAction(ACTION_DOWNLOAD),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable(),
        )
        val notes = r.pageUrl?.let {
            PendingIntent.getActivity(ctx, 2, Intent(Intent.ACTION_VIEW, Uri.parse(it)), PendingIntent.FLAG_UPDATE_CURRENT or immutable())
        }
        val n = base(ctx)
            .setContentTitle("Update available")
            .setContentText("Fenrir Vault ${shortTag(r.tag)} is ready to download")
            .setContentIntent(download)
            .setAutoCancel(true)
            .addAction(0, "Update", download)
            .apply { if (notes != null) addAction(0, "What's new", notes) }
        post(ctx, n)
    }

    internal fun notifyProgress(ctx: Context, r: Release, done: Long, total: Long) {
        val n = base(ctx)
            .setContentTitle("Downloading update")
            .setContentText(
                if (total > 0) "${shortTag(r.tag)} · ${done / 1_048_576} MB of ${total / 1_048_576} MB" else shortTag(r.tag),
            )
            .setOngoing(true)
            .setProgress(100, if (total > 0) ((done * 100) / total).toInt() else 0, total <= 0)
        post(ctx, n)
    }

    internal fun notifyReady(ctx: Context, r: Release, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 3, install, PendingIntent.FLAG_UPDATE_CURRENT or immutable())
        val n = base(ctx)
            .setContentTitle("Update ready to install")
            .setContentText("Fenrir Vault ${shortTag(r.tag)} · tap to install")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .addAction(0, "Install", pi)
        post(ctx, n)
    }

    internal fun notifyFailed(ctx: Context) {
        val retry = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, FenrirUpdateActionReceiver::class.java).setAction(ACTION_DOWNLOAD),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable(),
        )
        post(ctx, base(ctx).setContentTitle("Update download stopped").setContentText("Tap to try again").setContentIntent(retry).setAutoCancel(true))
    }

    private fun shortTag(tag: String): String = Regex("-r(\\d+)").find(tag)?.let { "r${it.groupValues[1]}" } ?: tag

    // ---- download ----

    internal fun startDownload(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<FenrirUpdateDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("notify_available_after" to false))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(DOWNLOAD_WORK, ExistingWorkPolicy.KEEP, request)
    }

    internal suspend fun download(ctx: Context, r: Release): File? = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val safe = r.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val out = File(dir, safe)
        if (r.size > 0 && out.exists() && out.length() == r.size) return@withContext out
        dir.listFiles()?.forEach { if (it.name != safe) it.delete() }
        val part = File(dir, "$safe.part")
        runCatching {
            var url = URL(r.apkUrl)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "FenrirVault")
                    connectTimeout = 20_000
                    readTimeout = 30_000
                }
                val code = conn.responseCode
                if (code in 300..399 && hops < 5) {
                    url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); hops++; continue
                }
                break
            }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
            var done = 0L
            var lastTick = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 700) { lastTick = now; notifyProgress(ctx, r, done, total) }
                    }
                }
            }
            conn.disconnect()
            if (total > 0 && done != total) error("incomplete")
            part.renameTo(out)
            out
        }.getOrElse {
            part.delete()
            null
        }
    }
}

/** Every few hours: is there a newer release? Announce it (once), or download it first if asked. */
class FenrirUpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = runCatching { check() }.getOrDefault(Result.success())

    private suspend fun check(): Result {
        val r = withContext(Dispatchers.IO) { runCatching { FenrirUpdateNotifier.fetchLatest() }.getOrNull() } ?: return Result.success()
        if (!FenrirUpdateNotifier.isNewer(r) || FenrirUpdateNotifier.lastNotified() == r.tag) return Result.success()
        if (FenrirUpdateNotifier.prefetchOn()) {
            val apk = FenrirUpdateNotifier.download(applicationContext, r)
            if (apk != null) FenrirUpdateNotifier.notifyReady(applicationContext, r, apk) else FenrirUpdateNotifier.notifyAvailable(applicationContext, r)
        } else {
            FenrirUpdateNotifier.notifyAvailable(applicationContext, r)
        }
        FenrirUpdateNotifier.setLastNotified(r.tag)
        return Result.success()
    }
}

/** "Update" was tapped: download with a progress notification, then "ready to install". */
class FenrirUpdateDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = runCatching { fetch() }.getOrDefault(Result.success())

    private suspend fun fetch(): Result {
        val r = withContext(Dispatchers.IO) { runCatching { FenrirUpdateNotifier.fetchLatest() }.getOrNull() }
        if (r == null || !FenrirUpdateNotifier.isNewer(r)) {
            FenrirUpdateNotifier.notifyFailed(applicationContext)
            return Result.success()
        }
        val apk = FenrirUpdateNotifier.download(applicationContext, r)
        if (apk != null) FenrirUpdateNotifier.notifyReady(applicationContext, r, apk) else FenrirUpdateNotifier.notifyFailed(applicationContext)
        return Result.success()
    }
}

class FenrirUpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FenrirUpdateNotifier.ACTION_DOWNLOAD) runCatching { FenrirUpdateNotifier.startDownload(context) }
    }
}
