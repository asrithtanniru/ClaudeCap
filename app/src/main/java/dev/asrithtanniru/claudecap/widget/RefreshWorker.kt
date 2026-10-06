package dev.asrithtanniru.claudecap.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = buildRepository(applicationContext)
        repository.refresh()
        BackgroundDataHint.record(applicationContext, repository.lastResultKind())
        UsageWidget().updateAll(applicationContext)
        return Result.success()
    }
}
