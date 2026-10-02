package dev.me.claudeusage.widget

import dev.me.claudeusage.data.UsageRepository
import dev.me.claudeusage.data.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetUiStateTest {

    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Test
    fun `signed out takes priority over everything else`() {
        val state = computeUiState(signedIn = false, snapshot = null, lastResultKind = null, refreshing = false, now = now)
        assertEquals(WidgetUiState.SignedOut, state)
    }

    @Test
    fun `no snapshot and no error is loading`() {
        val state = computeUiState(signedIn = true, snapshot = null, lastResultKind = null, refreshing = false, now = now)
        assertEquals(WidgetUiState.Loading, state)
    }

    @Test
    fun `no snapshot with parse failure is error`() {
        val state = computeUiState(
            signedIn = true, snapshot = null, lastResultKind = UsageRepository.RESULT_PARSE_FAILED,
            refreshing = false, now = now
        )
        assertEquals(WidgetUiState.Error, state)
    }

    @Test
    fun `fresh snapshot is content not stale not offline`() {
        val snapshot = UsageSnapshot(37.0, null, 52.0, null, fetchedAt = now.minusSeconds(60))
        val state = computeUiState(signedIn = true, snapshot = snapshot, lastResultKind = UsageRepository.RESULT_SUCCESS, refreshing = false, now = now)
        val content = state as WidgetUiState.Content
        assertTrue(!content.stale)
        assertTrue(!content.offline)
    }

    @Test
    fun `snapshot older than 13h is stale`() {
        val snapshot = UsageSnapshot(37.0, null, 52.0, null, fetchedAt = now.minusSeconds(14 * 3600))
        val state = computeUiState(signedIn = true, snapshot = snapshot, lastResultKind = UsageRepository.RESULT_SUCCESS, refreshing = false, now = now)
        assertTrue((state as WidgetUiState.Content).stale)
    }

    @Test
    fun `network error with existing snapshot shows offline but keeps data`() {
        val snapshot = UsageSnapshot(37.0, null, 52.0, null, fetchedAt = now.minusSeconds(60))
        val state = computeUiState(
            signedIn = true, snapshot = snapshot, lastResultKind = UsageRepository.RESULT_NETWORK_ERROR,
            refreshing = false, now = now
        )
        val content = state as WidgetUiState.Content
        assertTrue(content.offline)
        assertEquals(snapshot, content.snapshot)
    }

    @Test
    fun `refreshing flag passes through to content state`() {
        val snapshot = UsageSnapshot(37.0, null, 52.0, null, fetchedAt = now)
        val state = computeUiState(signedIn = true, snapshot = snapshot, lastResultKind = UsageRepository.RESULT_SUCCESS, refreshing = true, now = now)
        assertTrue((state as WidgetUiState.Content).refreshing)
    }
}
