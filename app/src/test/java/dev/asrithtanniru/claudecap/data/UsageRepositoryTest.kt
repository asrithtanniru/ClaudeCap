package dev.asrithtanniru.claudecap.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
}

private class FakeFetcher(private val results: MutableList<FetchResult>) : UsageFetcher {
    var callCount = 0
    override suspend fun fetch(): FetchResult {
        callCount++
        return results.removeAt(0)
    }
}

private const val SAMPLE_JSON =
    """{"limits":[{"kind":"session","percent":50,"resets_at":"2026-10-02T18:00:00Z"}]}"""

class UsageRepositoryTest {

    @Test
    fun `primary success returns snapshot without touching fallback`() = runBlocking {
        val primary = FakeFetcher(mutableListOf(FetchResult.Success(SAMPLE_JSON)))
        val fallback = FakeFetcher(mutableListOf())
        val repo = UsageRepository(InMemoryStore(), primary, fallback)

        val result = repo.refresh()

        assertTrue(result is RefreshResult.Success)
        assertEquals(1, primary.callCount)
        assertEquals(0, fallback.callCount)
    }

    @Test
    fun `primary blocked falls back to webview and remembers preference`() = runBlocking {
        val primary = FakeFetcher(mutableListOf(FetchResult.Blocked, FetchResult.Blocked))
        val fallback = FakeFetcher(mutableListOf(FetchResult.Success(SAMPLE_JSON), FetchResult.Success(SAMPLE_JSON)))
        val store = InMemoryStore()
        val repo = UsageRepository(store, primary, fallback)

        val first = repo.refresh()
        assertTrue(first is RefreshResult.Success)
        assertEquals(1, primary.callCount)
        assertEquals(1, fallback.callCount)

        // Second refresh should try the fallback first since it worked last time.
        val second = repo.refresh()
        assertTrue(second is RefreshResult.Success)
        assertEquals(1, primary.callCount)
        assertEquals(2, fallback.callCount)
    }

    @Test
    fun `primary network error falls back to webview too`() = runBlocking {
        val primary = FakeFetcher(mutableListOf(FetchResult.NetworkError("connection reset")))
        val fallback = FakeFetcher(mutableListOf(FetchResult.Success(SAMPLE_JSON)))
        val repo = UsageRepository(InMemoryStore(), primary, fallback)

        val result = repo.refresh()

        assertTrue(result is RefreshResult.Success)
        assertEquals(1, primary.callCount)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `both blocked returns Blocked`() = runBlocking {
        val primary = FakeFetcher(mutableListOf(FetchResult.Blocked))
        val fallback = FakeFetcher(mutableListOf(FetchResult.Blocked))
        val repo = UsageRepository(InMemoryStore(), primary, fallback)

        val result = repo.refresh()

        assertTrue(result is RefreshResult.Blocked)
    }

    @Test
    fun `not signed in is returned without trying fallback`() = runBlocking {
        val primary = FakeFetcher(mutableListOf(FetchResult.NotSignedIn))
        val fallback = FakeFetcher(mutableListOf())
        val repo = UsageRepository(InMemoryStore(), primary, fallback)

        val result = repo.refresh()

        assertTrue(result is RefreshResult.NotSignedIn)
        assertEquals(0, fallback.callCount)
    }
}
