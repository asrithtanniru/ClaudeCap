package dev.me.claudeusage

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import dev.me.claudeusage.data.SecureStore

/** WebView login at claude.ai. Long-press the page to paste a cookie header instead. */
class LoginActivity : Activity() {

    private lateinit var secureStore: SecureStore
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    tryCaptureSession()
                }
            }
            setOnLongClickListener { showPasteCookieDialog(); true }
            loadUrl(LOGIN_URL)
        }
        setContentView(webView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun tryCaptureSession() {
        val cookie = CookieManager.getInstance().getCookie(COOKIE_DOMAIN) ?: return
        if (!cookie.contains("sessionKey=")) return

        secureStore.putString(SecureStore.KEY_COOKIE, cookie)
        secureStore.putString(SecureStore.KEY_USER_AGENT, webView.settings.userAgentString)
        extractCookieValue(cookie, "lastActiveOrg")?.let {
            secureStore.putString(SecureStore.KEY_ORG_ID, it)
        }
        finish()
    }

    private fun showPasteCookieDialog() {
        val cookieInput = EditText(this).apply { hint = "Cookie header value" }
        val orgInput = EditText(this).apply { hint = "Org id" }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            addView(cookieInput)
            addView(orgInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Paste cookie")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val cookie = cookieInput.text.toString().trim()
                val orgId = orgInput.text.toString().trim()
                if (cookie.contains("sessionKey=")) {
                    secureStore.putString(SecureStore.KEY_COOKIE, cookie)
                    secureStore.putString(SecureStore.KEY_USER_AGENT, webView.settings.userAgentString)
                    if (orgId.isNotEmpty()) secureStore.putString(SecureStore.KEY_ORG_ID, orgId)
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val LOGIN_URL = "https://claude.ai/login"
        private const val COOKIE_DOMAIN = "https://claude.ai"

        fun extractCookieValue(cookieHeader: String, name: String): String? {
            val prefix = "$name="
            return cookieHeader.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith(prefix) }
                ?.substringAfter(prefix)
        }
    }
}
