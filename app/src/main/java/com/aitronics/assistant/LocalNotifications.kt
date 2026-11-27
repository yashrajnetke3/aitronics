package com.aitronics.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class AssistantNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = this
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var connected: AssistantNotificationListener? = null

        fun summary(context: Context): String {
            val manager = context.getSystemService(NotificationManager::class.java)
            val component = ComponentName(context, AssistantNotificationListener::class.java)
            val granted = if (android.os.Build.VERSION.SDK_INT >= 27) {
                manager.isNotificationListenerAccessGranted(component)
            } else {
                android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                    .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
            }
            if (!granted) {
                return "Enable AITRONICS in Android notification access settings to summarize notifications."
            }
            val service = connected ?: return "Notification access is connecting. Try again shortly."
            val visible = try {
                service.activeNotifications?.filter {
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
                }
            } catch (_: SecurityException) {
                null
            } ?: return "Android could not provide the current notifications."
            if (visible.isEmpty()) return "You have no active notifications."
            val pm = context.packageManager
            val groups = visible.groupingBy { it.packageName }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            val accounts = groups.take(5).joinToString(", ") { (pkg, count) ->
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    pkg
                }
                "$count from $name"
            }
            val suffix = if (groups.size > 5) ", plus ${groups.size - 5} other apps" else ""
            val noun = if (visible.size == 1) "notification" else "notifications"
            return "${visible.size} active $noun. $accounts$suffix."
        }
    }
}

object Reminders {
    private const val channelId = "aitronics_reminders"
    internal const val textKey = "reminder_text"
    private val prefix = Regex("""^remind me\b""", RegexOption.IGNORE_CASE)
    private val duration = """(\d+)\s+(minute|hour|day)s?"""
    private val timeFirst = Regex("""^remind me in $duration to (.+)$""", RegexOption.IGNORE_CASE)
    private val textFirst = Regex("""^remind me to (.+) in $duration$""", RegexOption.IGNORE_CASE)
    private val main = Executor { runnable -> Handler(Looper.getMainLooper()).post(runnable) }

    internal fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun handle(context: Context, command: String, onReply: (String) -> Unit): Boolean {
        val input = command.trim()
        if (!prefix.containsMatchIn(input)) return false
        val first = timeFirst.matchEntire(input)
        val second = if (first == null) textFirst.matchEntire(input) else null
        if (first == null && second == null) {
            onReply("Try “remind me in 10 minutes to water the plants”.")
            return true
        }
        val values = (first ?: requireNotNull(second)).groupValues
        val text = values[if (first != null) 3 else 1].trim()
        val amount = values[if (first != null) 1 else 2].toLongOrNull()
        val unit = values[if (first != null) 2 else 3].lowercase(Locale.ROOT)
        val minutesPerUnit = when (unit) {
            "day" -> 1440L
            "hour" -> 60L
            else -> 1L
        }
        if (amount == null || amount !in 1L..(525600L / minutesPerUnit) || text.length !in 1..500) {
            onReply("Use a delay from one minute to 365 days and a reminder of up to 500 characters.")
            return true
        }
        val app = context.applicationContext
        try {
            if (!notificationsEnabled(app)) {
                onReply("Enable reminder notifications for AITRONICS in settings, then repeat your command.")
                return true
            }
            val work = OneTimeWorkRequest.Builder(ReminderWorker::class.java)
                .setInputData(Data.Builder().putString(textKey, text).build())
                .setInitialDelay(amount * minutesPerUnit, TimeUnit.MINUTES)
                .build()
            val result = WorkManager.getInstance(app).enqueue(work).result
            result.addListener({
                onReply(if (runCatching { result.get() }.isSuccess) {
                    "Reminder scheduled for $amount $unit${if (amount == 1L) "" else "s"} from now. Android may delay delivery."
                } else "Android could not schedule this reminder.")
            }, main)
        } catch (_: RuntimeException) {
            onReply("Android could not schedule this reminder.")
        }
        return true
    }

    internal fun deliver(context: Context, tag: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AITRONICS reminder")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(tag, 1, notification)
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val text = inputData.getString(Reminders.textKey) ?: return Result.failure()
        return try {
            if (!Reminders.notificationsEnabled(applicationContext)) return Result.failure()
            Reminders.deliver(applicationContext, id.toString(), text)
            Result.success()
        } catch (_: SecurityException) {
            Result.failure()
        }
    }
}
