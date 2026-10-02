package dev.asrithtanniru.claudecap.widget

import dev.asrithtanniru.claudecap.data.UsageRepository

const val MIN_REFRESH_INTERVAL_MS = 10_000L

interface RefreshGateway {
    suspend fun lastRefreshAtMillis(): Long
    suspend fun markRefreshedAt(nowMillis: Long)
    suspend fun setRefreshing(refreshing: Boolean)
    suspend fun notifyWidgetChanged()
}

/** Returns true if the refresh actually ran (false if the 10s guard blocked it). */
suspend fun performGuardedRefresh(
    gateway: RefreshGateway,
    repository: UsageRepository,
    nowMillis: Long,
    minIntervalMillis: Long = MIN_REFRESH_INTERVAL_MS
): Boolean {
    if (nowMillis - gateway.lastRefreshAtMillis() < minIntervalMillis) return false

    gateway.markRefreshedAt(nowMillis)
    gateway.setRefreshing(true)
    gateway.notifyWidgetChanged()

    repository.refresh()

    gateway.setRefreshing(false)
    gateway.notifyWidgetChanged()
    return true
}
