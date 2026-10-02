package dev.asrithtanniru.claudecap

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.setPadding
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import dev.asrithtanniru.claudecap.data.AccountFetcher
import dev.asrithtanniru.claudecap.data.OkHttpFetcher
import dev.asrithtanniru.claudecap.data.SecureStore
import dev.asrithtanniru.claudecap.data.UsageRepository
import dev.asrithtanniru.claudecap.data.WebViewFetcher
import dev.asrithtanniru.claudecap.widget.RefreshScheduler
import dev.asrithtanniru.claudecap.widget.TimeFormat
import dev.asrithtanniru.claudecap.widget.UsageWidget
import dev.asrithtanniru.claudecap.widget.WidgetUiState
import dev.asrithtanniru.claudecap.widget.computeUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private const val WINDOW_BG = "#1F1E1D"
private const val CARD_BG = "#262624"
private const val PRIMARY_TEXT = "#FAF9F5"
private const val MUTED_TEXT = "#9C9A92"
private const val BAR_TRACK = "#3A3935"
private const val ACCENT = "#D97757"
private const val WARNING = "#E5A24A"
private const val CRITICAL = "#D4574E"
private const val KEY_ACCOUNT_EMAIL = "email"

class MainActivity : Activity() {

    private lateinit var secureStore: SecureStore
    private lateinit var repository: UsageRepository
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var accountAction: TextView
    private lateinit var accountEmail: TextView
    private lateinit var cardBody: LinearLayout
    private lateinit var rawJsonRow: TextView
    private lateinit var intervalRow: LinearLayout
    private val intervalChips = mutableMapOf<Long, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(this)
        repository = UsageRepository(this, OkHttpFetcher(secureStore), WebViewFetcher(this, secureStore))

        setContentView(buildRoot())
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
        if (secureStore.getString(SecureStore.KEY_COOKIE) != null) refreshNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    // ---- layout ----

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(WINDOW_BG.toColorInt())
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }

        root.addView(buildTopBar())
        root.addView(spacer(16))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(CARD_BG, 20)
            setPadding(dp(16))
        }
        cardBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(cardBody)
        root.addView(card)

        root.addView(spacer(12))
        rawJsonRow = textButton("Show raw JSON") { showRawJson() }
        root.addView(rawJsonRow)

        root.addView(spacer(24))
        root.addView(label("Auto-refresh every"))
        root.addView(spacer(8))
        intervalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        RefreshScheduler.ALLOWED_INTERVAL_HOURS.forEachIndexed { index, hours ->
            val chip = intervalChip(hours)
            intervalChips[hours] = chip
            intervalRow.addView(chip)
            if (index != RefreshScheduler.ALLOWED_INTERVAL_HOURS.lastIndex) {
                intervalRow.addView(spacer(8, horizontal = true))
            }
        }
        root.addView(intervalRow)

        return ScrollView(this).apply { addView(root) }
    }

    private fun buildTopBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTextColor(PRIMARY_TEXT.toColorInt())
            bold()
        }
        row.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val accountColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        accountAction = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ACCENT.toColorInt())
            setOnClickListener { onAccountActionClick() }
            setPadding(dp(8), dp(8), dp(8), 0)
            bold()
        }
        accountEmail = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.END
            setTextColor(MUTED_TEXT.toColorInt())
            setPadding(dp(8), 0, dp(8), dp(4))
            visibility = View.GONE
        }
        accountColumn.addView(accountAction, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        accountColumn.addView(accountEmail, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(accountColumn)
        return row
    }

    private fun intervalChip(hours: Long): TextView {
        return TextView(this).apply {
            text = "${hours}h"
            textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { onIntervalSelected(hours) }
            bold()
        }
    }

    // ---- rendering ----

    private fun render() {
        val signedIn = secureStore.getString(SecureStore.KEY_COOKIE) != null
        accountAction.text = if (signedIn) "Sign out" else "Sign in"

        if (signedIn) {
            val email = accountPrefs().getString(KEY_ACCOUNT_EMAIL, null)
            if (email != null) {
                accountEmail.text = email
                accountEmail.visibility = View.VISIBLE
            } else {
                accountEmail.visibility = View.GONE
                fetchAccountEmail()
            }
        } else {
            accountEmail.visibility = View.GONE
        }

        val snapshot = repository.lastSnapshot()
        val resultKind = repository.lastResultKind()
        val uiState = computeUiState(signedIn, snapshot, resultKind, refreshing = false, now = Instant.now())
        renderCard(uiState)
        renderIntervalChips()
    }

    private fun fetchAccountEmail() {
        activityScope.launch {
            val email = AccountFetcher.fetchEmail(secureStore) ?: return@launch
            accountPrefs().edit { putString(KEY_ACCOUNT_EMAIL, email) }
            accountEmail.text = email
            accountEmail.visibility = View.VISIBLE
        }
    }

    private fun accountPrefs() = getSharedPreferences("account_cache", Context.MODE_PRIVATE)

    private fun renderCard(uiState: WidgetUiState) {
        cardBody.removeAllViews()
        when (uiState) {
            WidgetUiState.SignedOut -> cardBody.addView(cardMessage("Sign in to see usage"))
            WidgetUiState.Loading -> cardBody.addView(cardMessage("Loading…"))
            WidgetUiState.Error -> cardBody.addView(cardMessage("Couldn't read usage"))
            is WidgetUiState.Content -> renderUsage(uiState)
        }
    }

    private fun renderUsage(content: WidgetUiState.Content) {
        val zone = ZoneId.systemDefault()
        val is24Hour = DateFormat.is24HourFormat(this)
        val textColor = if (content.stale) MUTED_TEXT else PRIMARY_TEXT
        val snapshot = content.snapshot

        val statusLine = if (content.offline) {
            "Offline · updated ${TimeFormat.clockTime(snapshot.fetchedAt, zone, is24Hour)}"
        } else {
            "Updated ${TimeFormat.clockTime(snapshot.fetchedAt, zone, is24Hour)}"
        }
        cardBody.addView(mutedLine(statusLine))
        cardBody.addView(spacer(12))

        cardBody.addView(usageRow(
            "Session · resets in ${TimeFormat.relativeToNow(snapshot.sessionResetsAt, Instant.now())}",
            snapshot.sessionPct, textColor
        ))

        if (snapshot.weeklyPct != null) {
            cardBody.addView(spacer(12))
            cardBody.addView(usageRow(
                "Weekly · resets ${TimeFormat.weekdayAndTime(snapshot.weeklyResetsAt, zone, is24Hour)}",
                snapshot.weeklyPct, textColor
            ))
        }
    }

    private fun usageRow(label: String, pct: Double, textColor: String): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        headerRow.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(MUTED_TEXT.toColorInt())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(TextView(this).apply {
            text = "${pct.toInt()}%"
            textSize = 16f
            setTextColor(textColor.toColorInt())
            bold()
        })
        column.addView(headerRow)
        column.addView(spacer(6))
        column.addView(progressBar(pct))
        return column
    }

    private fun progressBar(pct: Double): View {
        val fillColor = when {
            pct >= 90.0 -> CRITICAL
            pct >= 70.0 -> WARNING
            else -> ACCENT
        }
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 100f
            background = roundedDrawable(BAR_TRACK, 4)
        }
        val weight = pct.toFloat().coerceIn(1f, 100f)
        track.addView(View(this).apply {
            background = roundedDrawable(fillColor, 4)
        }, LinearLayout.LayoutParams(0, dp(8), weight))
        if (weight < 100f) {
            track.addView(View(this), LinearLayout.LayoutParams(0, dp(8), 100f - weight))
        }
        return track
    }

    private fun renderIntervalChips() {
        val selected = RefreshScheduler.savedIntervalHours(this)
        intervalChips.forEach { (hours, chip) ->
            val isSelected = hours == selected
            chip.background = roundedDrawable(
                if (isSelected) ACCENT else CARD_BG,
                18,
                strokeColor = if (isSelected) null else MUTED_TEXT
            )
            chip.setTextColor((if (isSelected) WINDOW_BG else MUTED_TEXT).toColorInt())
        }
    }

    // ---- actions ----

    private fun onAccountActionClick() {
        if (secureStore.getString(SecureStore.KEY_COOKIE) != null) {
            signOut()
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun onIntervalSelected(hours: Long) {
        RefreshScheduler.saveIntervalHours(this, hours)
        RefreshScheduler.schedule(this, hours, ExistingPeriodicWorkPolicy.UPDATE)
        renderIntervalChips()
    }

    private fun refreshNow() {
        activityScope.launch {
            repository.refresh()
            render()
            UsageWidget().updateAll(this@MainActivity)
        }
    }

    private fun showRawJson() {
        val raw = repository.lastRawJson() ?: "No data yet"
        val text = TextView(this).apply {
            text = raw
            setPadding(dp(16))
            setTextColor(PRIMARY_TEXT.toColorInt())
        }
        AlertDialog.Builder(this)
            .setTitle("Raw response")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun signOut() {
        secureStore.clear()
        accountPrefs().edit { clear() }
        CookieManager.getInstance().removeAllCookies(null)
        render()
    }

    // ---- small view helpers ----

    private fun cardMessage(message: String) = mutedLine(message)

    private fun mutedLine(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(MUTED_TEXT.toColorInt())
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(MUTED_TEXT.toColorInt())
    }

    private fun textButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(ACCENT.toColorInt())
        setOnClickListener { onClick() }
        bold()
    }

    private fun TextView.bold() {
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun spacer(sizeDp: Int, horizontal: Boolean = false): View = View(this).apply {
        layoutParams = if (horizontal) {
            LinearLayout.LayoutParams(dp(sizeDp), 0)
        } else {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(sizeDp))
        }
    }

    private fun roundedDrawable(colorHex: String, radiusDp: Int, strokeColor: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(colorHex.toColorInt())
            strokeColor?.let { setStroke(dp(1), it.toColorInt()) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
