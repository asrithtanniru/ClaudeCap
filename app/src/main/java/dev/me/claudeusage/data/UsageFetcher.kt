package dev.me.claudeusage.data

interface UsageFetcher {
    suspend fun fetch(): FetchResult
}
