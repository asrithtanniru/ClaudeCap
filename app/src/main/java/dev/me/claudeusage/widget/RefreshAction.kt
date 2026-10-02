package dev.me.claudeusage.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        setRefreshing(context, glanceId, true)
        UsageWidget().update(context, glanceId)

        buildRepository(context).refresh()

        setRefreshing(context, glanceId, false)
        UsageWidget().update(context, glanceId)
    }
}
