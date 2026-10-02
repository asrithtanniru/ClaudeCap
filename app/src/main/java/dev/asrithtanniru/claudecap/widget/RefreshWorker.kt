package dev.asrithtanniru.claudecap.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        buildRepository(applicationContext).refresh()
        UsageWidget().updateAll(applicationContext)
        return Result.success()
    }
}
