package com.nuvio.app.features.anidrama

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nuvio.app.features.settings.AppIconPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs

/** Android side of the Ani-drama episode reminders: WorkManager alerts, notifications and voice. */
object AniDramaRemindersAndroid : AniDramaReminderPlatform {
    internal const val CHANNEL_ID = "anidrama_episode_reminders"
    private const val PREFS = "fenrir_anidrama_reminders"
    private const val KEY_PAYLOAD = "reminders"
    private const val WORK_PREFIX = "anidrama_reminder:"

    internal const val KEY = "key"
    internal const val BODY = "body"
    internal const val LINK = "link"
    internal const val SPEAK = "speak"
    internal const val SPOKEN = "spoken"

    private var appContext: Context? = null

    /** Call once from Application.onCreate. */
    fun install(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context.applicationContext)
        AniDramaReminders.install(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            runCatching { AniDramaReminders.refreshAll() }
        }
    }

    override fun loadReminders(): String? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_PAYLOAD, null)

    override fun saveReminders(payload: String) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY_PAYLOAD, payload)?.apply()
    }

    override fun schedule(reminder: AniDramaReminder, fireAtEpochMs: Long) {
        val context = appContext ?: return
        val data = Data.Builder()
            .putString(KEY, reminder.key)
            .putString(BODY, reminder.notificationBody)
            .putString(LINK, reminder.deepLink)
            .putBoolean(SPEAK, reminder.speak)
            .putString(SPOKEN, reminder.spokenText)
            .build()
        enqueue(context, reminder.key, data, fireAtEpochMs - System.currentTimeMillis())
    }

    internal fun enqueue(context: Context, key: String, data: Data, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<AniDramaReminderWorker>()
            .setInputData(data)
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_PREFIX + key, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(key: String) {
        val context = appContext ?: return
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + key)
    }

    override fun speak(text: String) {
        val context = appContext ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { FenrirVoice.speak(context, text, respectSilentMode = false) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Episode reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set in Ani-drama's calendar"
            },
        )
    }
}

/** Posts the "new episode is out" notification, says it out loud if asked, then sets up the next one. */
class AniDramaReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(AniDramaRemindersAndroid.KEY) ?: return Result.failure()
        val body = inputData.getString(AniDramaRemindersAndroid.BODY).orEmpty()
        val link = inputData.getString(AniDramaRemindersAndroid.LINK).orEmpty()
        val speak = inputData.getBoolean(AniDramaRemindersAndroid.SPEAK, false)
        val spoken = inputData.getString(AniDramaRemindersAndroid.SPOKEN).orEmpty()
        val context = applicationContext
        val notificationId = abs(key.hashCode())

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val open = PendingIntent.getActivity(
                context,
                notificationId,
                Intent().apply {
                    component = AppIconPlatform.currentLauncherComponent(context)
                    action = Intent.ACTION_VIEW
                    data = android.net.Uri.parse(link)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val snooze = PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, AniDramaReminderSnoozeReceiver::class.java).apply {
                    putExtra(AniDramaRemindersAndroid.KEY, key)
                    putExtra(AniDramaRemindersAndroid.BODY, body)
                    putExtra(AniDramaRemindersAndroid.LINK, link)
                    putExtra(AniDramaRemindersAndroid.SPEAK, speak)
                    putExtra(AniDramaRemindersAndroid.SPOKEN, spoken)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, AniDramaRemindersAndroid.CHANNEL_ID)
                .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
                .setContentTitle("New episode is out")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .addAction(0, "Watch now", open)
                .addAction(0, "Snooze", snooze)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
        }

        if (speak && spoken.isNotBlank()) FenrirVoice.speak(context, spoken, respectSilentMode = true)

        // Snoozed alerts don't move the reminder on to the next episode.
        if (!inputData.getBoolean(SNOOZED, false)) AniDramaReminders.onFired(key)
        return Result.success()
    }

    companion object {
        internal const val SNOOZED = "snoozed"
    }
}

/** "Snooze" on the notification: the same alert again in an hour. */
class AniDramaReminderSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(AniDramaRemindersAndroid.KEY) ?: return
        NotificationManagerCompat.from(context).cancel(abs(key.hashCode()))
        val data = Data.Builder()
            .putString(AniDramaRemindersAndroid.KEY, key)
            .putString(AniDramaRemindersAndroid.BODY, intent.getStringExtra(AniDramaRemindersAndroid.BODY))
            .putString(AniDramaRemindersAndroid.LINK, intent.getStringExtra(AniDramaRemindersAndroid.LINK))
            .putBoolean(AniDramaRemindersAndroid.SPEAK, intent.getBooleanExtra(AniDramaRemindersAndroid.SPEAK, false))
            .putString(AniDramaRemindersAndroid.SPOKEN, intent.getStringExtra(AniDramaRemindersAndroid.SPOKEN))
            .putBoolean(AniDramaReminderWorker.SNOOZED, true)
            .build()
        val request = OneTimeWorkRequestBuilder<AniDramaReminderWorker>()
            .setInputData(data)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("anidrama_snooze:$key", ExistingWorkPolicy.REPLACE, request)
    }
}

/** Says a short sentence with the phone's text-to-speech voice. */
object FenrirVoice {
    /** True when the phone isn't on silent / vibrate and Do Not Disturb is off. */
    fun soundAllowed(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager != null && manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return false
        return true
    }

    suspend fun speak(context: Context, text: String, respectSilentMode: Boolean) {
        if (respectSilentMode && !soundAllowed(context)) return
        var tts: TextToSpeech? = null
        val ready = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { continuation ->
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
                }
            }
        } == true
        val engine = tts ?: return
        if (!ready) {
            engine.shutdown()
            return
        }
        runCatching { engine.language = Locale.getDefault() }
        withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fenrir-${text.hashCode()}")
            }
        }
        engine.shutdown()
    }
}
