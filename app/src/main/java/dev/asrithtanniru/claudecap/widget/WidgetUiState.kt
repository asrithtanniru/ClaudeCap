package dev.asrithtanniru.claudecap.widget

import dev.asrithtanniru.claudecap.data.UsageRepository
import dev.asrithtanniru.claudecap.data.UsageSnapshot
import java.time.Duration
import java.time.Instant

sealed interface WidgetUiState {
    data object SignedOut : WidgetUiState
    data object Loading : WidgetUiState
    data object Error : WidgetUiState
    data class Content(
        val snapshot: UsageSnapshot,
        val refreshing: Boolean,
        val offline: Boolean,
        val stale: Boolean
    ) : WidgetUiState
}

private val STALE_THRESHOLD: Duration = Duration.ofHours(13)

fun computeUiState(
    signedIn: Boolean,
    snapshot: UsageSnapshot?,
    lastResultKind: String?,
    refreshing: Boolean,
    now: Instant
): WidgetUiState {
    if (!signedIn) return WidgetUiState.SignedOut

    if (snapshot == null) {
        val isErrorKind = lastResultKind == UsageRepository.RESULT_PARSE_FAILED ||
            lastResultKind == UsageRepository.RESULT_HTTP_ERROR
        return if (isErrorKind) WidgetUiState.Error else WidgetUiState.Loading
    }

    val stale = Duration.between(snapshot.fetchedAt, now) > STALE_THRESHOLD
    val offline = lastResultKind == UsageRepository.RESULT_NETWORK_ERROR ||
        lastResultKind == UsageRepository.RESULT_BLOCKED
    return WidgetUiState.Content(snapshot, refreshing, offline, stale)
}
