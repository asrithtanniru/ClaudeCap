package dev.me.claudeusage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpFetcher(
    private val secureStore: SecureStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : UsageFetcher {

    override suspend fun fetch(): FetchResult {
        val cookie = secureStore.getString(SecureStore.KEY_COOKIE) ?: return FetchResult.NotSignedIn
        val userAgent = secureStore.getString(SecureStore.KEY_USER_AGENT) ?: return FetchResult.NotSignedIn
        val orgId = secureStore.getString(SecureStore.KEY_ORG_ID) ?: return FetchResult.NotSignedIn

        val request = Request.Builder()
            .url("https://claude.ai/api/organizations/$orgId/usage")
            .header("Cookie", cookie)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Referer", "https://claude.ai/settings/usage")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    when {
                        response.code == 401 -> FetchResult.NotSignedIn
                        response.code == 403 -> FetchResult.Blocked
                        body.trimStart().startsWith("<") -> FetchResult.Blocked
                        !response.isSuccessful -> FetchResult.Http(response.code)
                        else -> FetchResult.Success(body)
                    }
                }
            } catch (e: IOException) {
                FetchResult.NetworkError(e.message ?: "network error")
            }
        }
    }
}
