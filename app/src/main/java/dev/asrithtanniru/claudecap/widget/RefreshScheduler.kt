package dev.asrithtanniru.claudecap.widget

import android.content.Context
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/** Shared by the widget receiver (onEnabled/onDisabled) and the app's interval picker. */
object RefreshScheduler {
    const val WORK_NAME = "usage_refresh_periodic"
    const val DEFAULT_INTERVAL_HOURS = 12L
    val ALLOWED_INTERVAL_HOURS = listOf(3L, 6L, 12L, 24L)

    private const val PREFS_NAME = "app_settings"
    private const val KEY_INTERVAL_HOURS = "refresh_interval_hours"

    fun savedIntervalHours(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)

    fun saveIntervalHours(context: Context, hours: Long) {
        prefs(context).edit { putLong(KEY_INTERVAL_HOURS, hours) }
    }

    fun schedule(context: Context, hours: Long, policy: ExistingPeriodicWorkPolicy) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
