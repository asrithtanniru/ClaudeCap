package dev.asrithtanniru.claudecap.widget

import dev.asrithtanniru.claudecap.data.FetchResult
import dev.asrithtanniru.claudecap.data.KeyValueStore
import dev.asrithtanniru.claudecap.data.UsageFetcher
import dev.asrithtanniru.claudecap.data.UsageRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGateway(private var lastRefreshAt: Long = 0L) : RefreshGateway {
    val refreshingHistory = mutableListOf<Boolean>()
    var notifyCount = 0

    override suspend fun lastRefreshAtMillis(): Long = lastRefreshAt
    override suspend fun markRefreshedAt(nowMillis: Long) {
        lastRefreshAt = nowMillis
    }
    override suspend fun setRefreshing(refreshing: Boolean) {
        refreshingHistory.add(refreshing)
    }
    override suspend fun notifyWidgetChanged() {
        notifyCount++
    }
}

private class InMemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
}

private class AlwaysSuccessFetcher : UsageFetcher {
    var callCount = 0
    override suspend fun fetch(): FetchResult {
        callCount++
        return FetchResult.Success("""{"five_hour":{"utilization":0.5,"resets_at":"2026-10-02T18:00:00Z"}}""")
    }
}

class GuardedRefreshTest {

    @Test
    fun `refresh proceeds when outside the guard window`() = runBlocking {
        val gateway = FakeGateway(lastRefreshAt = 0L)
        val fetcher = AlwaysSuccessFetcher()
        val repo = UsageRepository(InMemoryStore(), fetcher, fetcher)

        val ran = performGuardedRefresh(gateway, repo, nowMillis = 20_000L)

        assertTrue(ran)
        assertEquals(1, fetcher.callCount)
        assertEquals(listOf(true, false), gateway.refreshingHistory)
        assertEquals(2, gateway.notifyCount)
    }

    @Test
    fun `refresh is blocked within 10s of the last one`() = runBlocking {
        val gateway = FakeGateway(lastRefreshAt = 5_000L)
        val fetcher = AlwaysSuccessFetcher()
        val repo = UsageRepository(InMemoryStore(), fetcher, fetcher)

        val ran = performGuardedRefresh(gateway, repo, nowMillis = 10_000L)

        assertFalse(ran)
        assertEquals(0, fetcher.callCount)
        assertTrue(gateway.refreshingHistory.isEmpty())
        assertEquals(0, gateway.notifyCount)
    }

    @Test
    fun `refresh proceeds exactly at the guard boundary`() = runBlocking {
        val gateway = FakeGateway(lastRefreshAt = 0L)
        val fetcher = AlwaysSuccessFetcher()
        val repo = UsageRepository(InMemoryStore(), fetcher, fetcher)

        val ran = performGuardedRefresh(gateway, repo, nowMillis = MIN_REFRESH_INTERVAL_MS)

        assertTrue(ran)
        assertEquals(1, fetcher.callCount)
    }
}
