package com.bloom.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bloom.app.MainActivity
import com.bloom.app.R
import com.bloom.app.data.BloomRepository
import com.bloom.app.domain.BloomData
import com.bloom.app.domain.CycleEngine
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Local reminders via WorkManager one-time jobs (no exact-alarm permission, no network). Wording is
 * deliberately neutral so lock-screen previews never reveal health information.
 */
object Reminders {
    private const val CHANNEL = "bloom_reminders"
    private const val W_DAILY = "bloom_daily"
    private const val W_PERIOD = "bloom_period"
    private const val PERIOD_HOUR = 9

    fun createChannels(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Optional gentle reminders from Bloom"
        ch.lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun reschedule(context: Context, data: BloomData, now: LocalDateTime = LocalDateTime.now()) {
        val wm = WorkManager.getInstance(context)
        val s = data.settings
        if (s.dailyReminder) {
            var t = now.toLocalDate().atTime(LocalTime.of(s.dailyReminderHour, s.dailyReminderMinute))
            if (!t.isAfter(now)) t = t.plusDays(1)
            enqueue(wm, W_DAILY, t, now, "daily")
        } else wm.cancelUniqueWork(W_DAILY)

        val fire = if (s.periodReminder && data.settings.onboardingDone) {
            val e = CycleEngine(data.logs, s, now.toLocalDate().toEpochDay())
            e.forecasts(2).map {
                LocalDate.ofEpochDay(it.periodWindowStart - s.periodReminderDaysBefore).atTime(PERIOD_HOUR, 0)
            }.firstOrNull { it.isAfter(now) }
        } else null
        if (fire != null) enqueue(wm, W_PERIOD, fire, now, "period") else wm.cancelUniqueWork(W_PERIOD)
    }

    private fun enqueue(wm: WorkManager, name: String, at: LocalDateTime, now: LocalDateTime, kind: String) {
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(1_000)
        wm.enqueueUniqueWork(
            name, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("kind" to kind)).build(),
        )
    }

    internal fun post(context: Context, kind: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val text = if (kind == "period") "A gentle heads-up: it may be time to prepare." else "Time for your daily check-in."
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra("open_log", kind == "daily"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Bloom").setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_SECRET).build()
        runCatching { NotificationManagerCompat.from(context).notify(if (kind == "period") 2 else 1, n) }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Reminders.post(applicationContext, inputData.getString("kind") ?: "daily")
        Reminders.reschedule(applicationContext, BloomRepository.get(applicationContext).snapshot())
        return Result.success()
    }
}
