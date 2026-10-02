package dev.me.claudeusage.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.time.Instant

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    companion object {
        private const val PREFS_NAME = "usage_prefs"
    }
}

class UsageRepository(
    private val store: KeyValueStore,
    private val primaryFetcher: UsageFetcher,
    private val fallbackFetcher: UsageFetcher
) {
    constructor(context: Context, primaryFetcher: UsageFetcher, fallbackFetcher: UsageFetcher) :
        this(SharedPrefsStore(context), primaryFetcher, fallbackFetcher)

    suspend fun refresh(): RefreshResult {
        var lastResult: FetchResult = FetchResult.Blocked
        for ((name, fetcher) in fetcherOrder()) {
            lastResult = fetcher.fetch()
            if (lastResult is FetchResult.Blocked) continue
            store.putString(KEY_PREFERRED_FETCHER, name)
            return handle(lastResult)
        }
        return handle(lastResult)
    }

    fun lastRawJson(): String? = store.getString(KEY_RAW_JSON)

    fun lastResultKind(): String? = store.getString(KEY_LAST_RESULT)

    fun lastSnapshot(): UsageSnapshot? {
        val json = store.getString(KEY_SNAPSHOT) ?: return null
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

    private fun fetcherOrder(): List<Pair<String, UsageFetcher>> {
        val primary = PRIMARY_NAME to primaryFetcher
        val fallback = FALLBACK_NAME to fallbackFetcher
        return if (store.getString(KEY_PREFERRED_FETCHER) == FALLBACK_NAME) {
            listOf(fallback, primary)
        } else {
            listOf(primary, fallback)
        }
    }

    private fun handle(fetchResult: FetchResult): RefreshResult = when (fetchResult) {
        is FetchResult.Success -> {
            store.putString(KEY_RAW_JSON, fetchResult.rawJson)
            when (val parsed = UsageParser.parse(fetchResult.rawJson)) {
                is ParseResult.Success -> {
                    persistSnapshot(parsed.snapshot)
                    store.putString(KEY_LAST_RESULT, RESULT_SUCCESS)
                    RefreshResult.Success(parsed.snapshot)
                }
                is ParseResult.Failure -> {
                    store.putString(KEY_LAST_RESULT, RESULT_PARSE_FAILED)
                    RefreshResult.ParseFailed(fetchResult.rawJson)
                }
            }
        }
        is FetchResult.NotSignedIn -> {
            store.putString(KEY_LAST_RESULT, RESULT_NOT_SIGNED_IN)
            RefreshResult.NotSignedIn
        }
        is FetchResult.Blocked -> {
            store.putString(KEY_LAST_RESULT, RESULT_BLOCKED)
            RefreshResult.Blocked
        }
        is FetchResult.NetworkError -> {
            store.putString(KEY_LAST_RESULT, RESULT_NETWORK_ERROR)
            RefreshResult.NetworkError(fetchResult.message)
        }
        is FetchResult.Http -> {
            store.putString(KEY_LAST_RESULT, RESULT_HTTP_ERROR)
            RefreshResult.HttpError(fetchResult.code)
        }
    }

    private fun persistSnapshot(snapshot: UsageSnapshot) {
        val obj = JSONObject()
        obj.put("sessionPct", snapshot.sessionPct)
        snapshot.sessionResetsAt?.let { obj.put("sessionResetsAt", it.toString()) }
        snapshot.weeklyPct?.let { obj.put("weeklyPct", it) }
        snapshot.weeklyResetsAt?.let { obj.put("weeklyResetsAt", it.toString()) }
        obj.put("fetchedAt", snapshot.fetchedAt.toString())
        store.putString(KEY_SNAPSHOT, obj.toString())
    }

    companion object {
        private const val KEY_RAW_JSON = "raw_json"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_PREFERRED_FETCHER = "preferred_fetcher"
        private const val KEY_LAST_RESULT = "last_result"
        private const val PRIMARY_NAME = "okhttp"
        private const val FALLBACK_NAME = "webview"

        const val RESULT_SUCCESS = "success"
        const val RESULT_NOT_SIGNED_IN = "not_signed_in"
        const val RESULT_BLOCKED = "blocked"
        const val RESULT_NETWORK_ERROR = "network_error"
        const val RESULT_HTTP_ERROR = "http_error"
        const val RESULT_PARSE_FAILED = "parse_failed"
    }
}
