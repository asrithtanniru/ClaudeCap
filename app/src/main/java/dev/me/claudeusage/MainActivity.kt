package dev.me.claudeusage

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.me.claudeusage.data.SecureStore

class MainActivity : Activity() {

    private lateinit var secureStore: SecureStore
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(this)

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
            setOnClickListener { /* wired up in Phase 3 */ }
        }
        val showJsonButton = Button(this).apply {
            text = "Show raw JSON"
            setOnClickListener { /* wired up in Phase 3 */ }
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
    }

    private fun updateStatus() {
        val signedIn = secureStore.getString(SecureStore.KEY_COOKIE) != null
        statusText.text = if (signedIn) "Signed in" else "Signed out"
    }

    private fun signOut() {
        secureStore.clear()
        CookieManager.getInstance().removeAllCookies(null)
        updateStatus()
    }
}
