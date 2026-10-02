package dev.me.claudeusage

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.me.claudeusage.data.OkHttpFetcher
import dev.me.claudeusage.data.RefreshResult
import dev.me.claudeusage.data.SecureStore
import dev.me.claudeusage.data.UsageRepository
import dev.me.claudeusage.data.WebViewFetcher
import dev.me.claudeusage.widget.UsageWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private lateinit var secureStore: SecureStore
    private lateinit var repository: UsageRepository
    private lateinit var statusText: TextView
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(this)
        repository = UsageRepository(this, OkHttpFetcher(secureStore), WebViewFetcher(this, secureStore))

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#FAF9F5"))
            gravity = Gravity.CENTER
        }

        val signInButton = Button(this).apply {
            text = "Sign in"
            setOnClickListener { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) }
        }
        val refreshButton = Button(this).apply {
            text = "Refresh now"
            setOnClickListener { refreshNow() }
        }
        val showJsonButton = Button(this).apply {
            text = "Show raw JSON"
            setOnClickListener { showRawJson() }
        }
        val signOutButton = Button(this).apply {
            text = "Sign out"
            setOnClickListener { signOut() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1F1E1D"))
            setPadding(48, 96, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "Claude Usage"
                textSize = 22f
                setTextColor(Color.parseColor("#FAF9F5"))
                gravity = Gravity.CENTER
            })
            addView(statusText)
            addView(signInButton)
            addView(refreshButton)
            addView(showJsonButton)
            addView(signOutButton)
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (secureStore.getString(SecureStore.KEY_COOKIE) != null) refreshNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun updateStatus(extra: String? = null) {
        val signedIn = secureStore.getString(SecureStore.KEY_COOKIE) != null
        val base = if (signedIn) "Signed in" else "Signed out"
        statusText.text = if (extra != null) "$base · $extra" else base
    }

    private fun refreshNow() {
        updateStatus("Refreshing…")
        activityScope.launch {
            val result = repository.refresh()
            val extra = when (result) {
                is RefreshResult.Success -> "Updated"
                RefreshResult.NotSignedIn -> "Not signed in"
                RefreshResult.Blocked -> "Blocked"
                is RefreshResult.NetworkError -> "Network error"
                is RefreshResult.HttpError -> "HTTP ${result.code}"
                is RefreshResult.ParseFailed -> "Couldn't read usage"
            }
            updateStatus(extra)
            UsageWidget().updateAll(this@MainActivity)
        }
    }

    private fun showRawJson() {
        val raw = repository.lastRawJson() ?: "No data yet"
        val text = TextView(this).apply {
            text = raw
            setPadding(32, 32, 32, 32)
            setTextColor(Color.parseColor("#FAF9F5"))
        }
        AlertDialog.Builder(this)
            .setTitle("Raw response")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun signOut() {
        secureStore.clear()
        CookieManager.getInstance().removeAllCookies(null)
        updateStatus()
    }
}
