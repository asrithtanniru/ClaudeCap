package dev.me.claudeusage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Best-effort fetch of the signed-in account's email, for display only.
 * Endpoint shape is unverified (same caveat as the usage endpoint) -- if claude.ai's
 * account endpoint differs from this guess, this silently returns null instead of
 * breaking anything else.
 */
object AccountFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchEmail(secureStore: SecureStore): String? {
        val cookie = secureStore.getString(SecureStore.KEY_COOKIE) ?: return null
        val userAgent = secureStore.getString(SecureStore.KEY_USER_AGENT) ?: return null

        val request = Request.Builder()
            .url("https://claude.ai/api/account")
            .header("Cookie", cookie)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    if (body.trimStart().startsWith("<")) return@use null
                    findEmail(JSONObject(body))
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun findEmail(value: Any?): String? = when (value) {
        is JSONObject -> {
            val keys = value.keys()
            var found: String? = null
            while (keys.hasNext() && found == null) {
                val key = keys.next()
                found = if (key.lowercase().contains("email")) {
                    (value.opt(key) as? String)?.takeIf { it.contains("@") }
                } else {
                    findEmail(value.opt(key))
                }
            }
            found
        }
        is JSONArray -> {
            var found: String? = null
            for (i in 0 until value.length()) {
                found = findEmail(value.opt(i))
                if (found != null) break
            }
            found
        }
        else -> null
    }
}
