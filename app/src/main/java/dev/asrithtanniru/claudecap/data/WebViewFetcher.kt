package dev.asrithtanniru.claudecap.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Runs the usage fetch as same-origin JS inside a hidden WebView, for when Cloudflare blocks OkHttp. */
class WebViewFetcher(
    private val context: Context,
    private val secureStore: SecureStore
) : UsageFetcher {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun fetch(): FetchResult {
        val orgId = secureStore.getString(SecureStore.KEY_ORG_ID) ?: return FetchResult.NotSignedIn
        secureStore.getString(SecureStore.KEY_COOKIE) ?: return FetchResult.NotSignedIn

        val deferred = CompletableDeferred<FetchResult>()
        var webView: WebView? = null

        withContext(Dispatchers.Main) {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(ResultBridge(deferred), "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(fetchScript(orgId), null)
                    }
                }
                loadUrl(SETTINGS_URL)
            }
        }

        val result = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: FetchResult.NetworkError("WebView timeout")

        Handler(Looper.getMainLooper()).post { webView?.destroy() }
        return result
    }

    private fun fetchScript(orgId: String) = """
        fetch('/api/organizations/$orgId/usage',{credentials:'include'})
            .then(r=>r.text())
            .then(t=>Android.onResult(t))
            .catch(e=>Android.onError(String(e)));
    """.trimIndent()

    private class ResultBridge(private val deferred: CompletableDeferred<FetchResult>) {
        @JavascriptInterface
        fun onResult(json: String) {
            deferred.complete(FetchResult.Success(json))
        }

        @JavascriptInterface
        fun onError(message: String) {
            deferred.complete(FetchResult.NetworkError(message))
        }
    }

    companion object {
        private const val SETTINGS_URL = "https://claude.ai/settings/usage"
        private const val TIMEOUT_MS = 20_000L
    }
}
