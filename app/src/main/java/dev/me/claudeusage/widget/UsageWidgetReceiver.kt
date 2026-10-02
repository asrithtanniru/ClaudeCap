package dev.me.claudeusage.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        RefreshScheduler.schedule(
            context,
            RefreshScheduler.savedIntervalHours(context),
            ExistingPeriodicWorkPolicy.KEEP
        )

        val immediateRequest = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(immediateRequest)
    }

    override fun onDisabled(context: Context) {
        RefreshScheduler.cancel(context)
        super.onDisabled(context)
    }
}
