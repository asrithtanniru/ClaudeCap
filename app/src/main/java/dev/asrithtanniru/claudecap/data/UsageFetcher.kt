package dev.asrithtanniru.claudecap.data

interface UsageFetcher {
    suspend fun fetch(): FetchResult
}
