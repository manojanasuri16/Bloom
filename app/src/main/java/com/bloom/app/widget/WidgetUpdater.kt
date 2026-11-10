package com.bloom.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object WidgetUpdater {
    private const val WORK_NAME = "bloom_midnight_widget_refresh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget refresh; safe from any thread. */
    fun refreshAll(context: Context) {
        val app = context.applicationContext
        scope.launch { refreshAllNow(app) }
    }

    /** Suspending variant used by the worker and receiver. Failures are swallowed per widget. */
    suspend fun refreshAllNow(context: Context) {
        val app = context.applicationContext
        runCatching { BloomSmallWidget().updateAll(app) }
        runCatching { BloomMediumWidget().updateAll(app) }
        runCatching { BloomLargeWidget().updateAll(app) }
    }

    /** (Re)schedules a single refresh at next local midnight + 5 s. Inexact; no permissions needed. */
    fun scheduleMidnight(context: Context) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val target = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(5)
        val delay = Duration.between(now, target).coerceAtLeast(Duration.ofSeconds(5))
        val request = OneTimeWorkRequestBuilder<MidnightWidgetWorker>().setInitialDelay(delay).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this
}

/** Refreshes widgets at midnight, then chains the next run. */
class MidnightWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refreshAllNow(applicationContext)
        // Enqueue last: REPLACE on the unique name supersedes this (already finished) run.
        WidgetUpdater.scheduleMidnight(applicationContext)
        return Result.success()
    }
}

/** Clock / locale / boot / update events that make widget content stale. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetUpdater.refreshAllNow(app)
                WidgetUpdater.scheduleMidnight(app)
            } catch (_: Throwable) {
                // Never crash a system broadcast.
            } finally {
                pending.finish()
            }
        }
    }
}
