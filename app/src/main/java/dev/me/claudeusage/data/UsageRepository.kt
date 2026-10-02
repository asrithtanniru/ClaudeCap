package dev.me.claudeusage.data

import android.content.Context
import org.json.JSONObject
import java.time.Instant

class UsageRepository(
    context: Context,
    private val fetcher: UsageFetcher
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun refresh(): RefreshResult {
        return when (val fetchResult = fetcher.fetch()) {
            is FetchResult.Success -> {
                prefs.edit().putString(KEY_RAW_JSON, fetchResult.rawJson).apply()
                when (val parsed = UsageParser.parse(fetchResult.rawJson)) {
                    is ParseResult.Success -> {
                        persistSnapshot(parsed.snapshot)
                        RefreshResult.Success(parsed.snapshot)
                    }
                    is ParseResult.Failure -> RefreshResult.ParseFailed(fetchResult.rawJson)
                }
            }
            is FetchResult.NotSignedIn -> RefreshResult.NotSignedIn
            is FetchResult.Blocked -> RefreshResult.Blocked
            is FetchResult.NetworkError -> RefreshResult.NetworkError(fetchResult.message)
            is FetchResult.Http -> RefreshResult.HttpError(fetchResult.code)
        }
    }

    fun lastRawJson(): String? = prefs.getString(KEY_RAW_JSON, null)

    fun lastSnapshot(): UsageSnapshot? {
        val json = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return try {
            val obj = JSONObject(json)
            UsageSnapshot(
                sessionPct = obj.getDouble("sessionPct"),
                sessionResetsAt = obj.optString("sessionResetsAt", "").takeIf { it.isNotEmpty() }?.let { Instant.parse(it) },
                weeklyPct = if (obj.has("weeklyPct")) obj.getDouble("weeklyPct") else null,
                weeklyResetsAt = obj.optString("weeklyResetsAt", "").takeIf { it.isNotEmpty() }?.let { Instant.parse(it) },
                fetchedAt = Instant.parse(obj.getString("fetchedAt"))
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun persistSnapshot(snapshot: UsageSnapshot) {
        val obj = JSONObject()
        obj.put("sessionPct", snapshot.sessionPct)
        snapshot.sessionResetsAt?.let { obj.put("sessionResetsAt", it.toString()) }
        snapshot.weeklyPct?.let { obj.put("weeklyPct", it) }
        snapshot.weeklyResetsAt?.let { obj.put("weeklyResetsAt", it.toString()) }
        obj.put("fetchedAt", snapshot.fetchedAt.toString())
        prefs.edit().putString(KEY_SNAPSHOT, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "usage_prefs"
        private const val KEY_RAW_JSON = "raw_json"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
