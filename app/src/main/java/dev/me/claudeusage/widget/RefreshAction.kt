package dev.me.claudeusage.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

private val LAST_REFRESH_AT_KEY = longPreferencesKey("last_refresh_at_millis")

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val gateway = GlanceRefreshGateway(context, glanceId)
        performGuardedRefresh(gateway, buildRepository(context), System.currentTimeMillis())
    }
}

private class GlanceRefreshGateway(
    private val context: Context,
    private val glanceId: GlanceId
) : RefreshGateway {

    override suspend fun lastRefreshAtMillis(): Long =
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[LAST_REFRESH_AT_KEY] ?: 0L

    override suspend fun markRefreshedAt(nowMillis: Long) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { set(LAST_REFRESH_AT_KEY, nowMillis) }
        }
    }

    override suspend fun setRefreshing(refreshing: Boolean) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { set(REFRESHING_KEY, refreshing) }
        }
    }

    override suspend fun notifyWidgetChanged() {
        UsageWidget().update(context, glanceId)
    }
}
