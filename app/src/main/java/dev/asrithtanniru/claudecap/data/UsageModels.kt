package dev.asrithtanniru.claudecap.data

import java.time.Instant

data class UsageSnapshot(
    val sessionPct: Double,
    val sessionResetsAt: Instant?,
    val weeklyPct: Double?,
    val weeklyResetsAt: Instant?,
    val fetchedAt: Instant
)

sealed interface FetchResult {
    data class Success(val rawJson: String) : FetchResult
    data object NotSignedIn : FetchResult
    data object Blocked : FetchResult
    data class NetworkError(val message: String) : FetchResult
    data class Http(val code: Int) : FetchResult
}

sealed interface ParseResult {
    data class Success(val snapshot: UsageSnapshot) : ParseResult
    data class Failure(val rawJson: String) : ParseResult
}

sealed interface RefreshResult {
    data class Success(val snapshot: UsageSnapshot) : RefreshResult
    data object NotSignedIn : RefreshResult
    data object Blocked : RefreshResult
    data class NetworkError(val message: String) : RefreshResult
    data class HttpError(val code: Int) : RefreshResult
    data class ParseFailed(val rawJson: String) : RefreshResult
}
