package dev.asrithtanniru.claudecap

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import dev.asrithtanniru.claudecap.data.SecureStore

/** WebView login at claude.ai. Long-press the page to paste a cookie header instead. */
class LoginActivity : Activity() {

    private lateinit var secureStore: SecureStore
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled") // required: claude.ai's login and the usage page are JS apps
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(this)

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView = WebView(this).apply {
            cookieManager.setAcceptThirdPartyCookies(this, true)
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

        val hint = TextView(this).apply {
            text = "Use \"Continue with email\" below, then enter the verification code sent to " +
                "your email. Google sign-in doesn't work inside this app."
            setBackgroundColor("#262624".toColorInt())
            setTextColor("#9C9A92".toColorInt())
            textSize = 13f
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(hint)
            addView(webView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
