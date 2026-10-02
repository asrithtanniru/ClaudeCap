package dev.me.claudeusage.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

private const val REFRESH_WORK_NAME = "usage_refresh_periodic"

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(REFRESH_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodicRequest)

        val immediateRequest = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(immediateRequest)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK_NAME)
        super.onDisabled(context)
    }
}
