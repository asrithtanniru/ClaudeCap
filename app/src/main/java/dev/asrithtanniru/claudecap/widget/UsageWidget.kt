package dev.asrithtanniru.claudecap.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.asrithtanniru.claudecap.MainActivity
import dev.asrithtanniru.claudecap.R
import dev.asrithtanniru.claudecap.data.OkHttpFetcher
import dev.asrithtanniru.claudecap.data.SecureStore
import dev.asrithtanniru.claudecap.data.UsageRepository
import dev.asrithtanniru.claudecap.data.UsageSnapshot
import dev.asrithtanniru.claudecap.data.WebViewFetcher
import java.time.Instant
import java.time.ZoneId

val REFRESHING_KEY = booleanPreferencesKey("refreshing")

private val SIZE_COMPACT = DpSize(250.dp, 110.dp)
private val SIZE_REGULAR = DpSize(250.dp, 180.dp)

fun buildRepository(context: Context): UsageRepository {
    val secureStore = SecureStore(context)
    return UsageRepository(context, OkHttpFetcher(secureStore), WebViewFetcher(context, secureStore))
}

suspend fun setRefreshing(context: Context, id: GlanceId, refreshing: Boolean) {
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
        prefs.toMutablePreferences().apply { set(REFRESHING_KEY, refreshing) }
    }
}

class UsageWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SIZE_COMPACT, SIZE_REGULAR))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val signedIn = SecureStore(context).getString(SecureStore.KEY_COOKIE) != null
        val repository = buildRepository(context)
        val snapshot = repository.lastSnapshot()
        val resultKind = repository.lastResultKind()

        provideContent {
            val prefs = currentState<Preferences>()
            val refreshing = prefs[REFRESHING_KEY] ?: false
            val uiState = computeUiState(signedIn, snapshot, resultKind, refreshing, Instant.now())
            WidgetContent(uiState)
        }
    }
}

@Composable
private fun WidgetContent(uiState: WidgetUiState) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Palette.CardBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(16.dp)
    ) {
        when (uiState) {
            WidgetUiState.SignedOut -> TapMessage("Tap to sign in")
            WidgetUiState.Loading -> TapMessage("Loading…")
            WidgetUiState.Error -> TapMessage("Couldn't read usage")
            is WidgetUiState.Content -> NormalContent(uiState)
        }
    }
}

@Composable
private fun TapMessage(message: String) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = TextStyle(color = ColorProvider(Palette.PrimaryText), fontSize = 16.sp))
    }
}

@Composable
private fun NormalContent(content: WidgetUiState.Content) {
    val size = LocalSize.current
    val isCompact = size.height <= 130.dp
    val textColor = if (content.stale) Palette.MutedText else Palette.PrimaryText
    val zone = ZoneId.systemDefault()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(content.snapshot, content.offline, content.refreshing, zone, is24Hour)
        Spacer(modifier = GlanceModifier.height(8.dp))
        UsageBlock(
            label = "Session",
            resetText = "resets in ${TimeFormat.relativeToNow(content.snapshot.sessionResetsAt, Instant.now())}",
            pct = content.snapshot.sessionPct,
            textColor = textColor,
            isCompact = isCompact
        )
        if (content.snapshot.weeklyPct != null) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            UsageBlock(
                label = "Weekly",
                resetText = "resets ${TimeFormat.weekdayAndTime(content.snapshot.weeklyResetsAt, zone, is24Hour)}",
                pct = content.snapshot.weeklyPct,
                textColor = textColor,
                isCompact = isCompact
            )
        }
    }
}

@Composable
private fun HeaderRow(snapshot: UsageSnapshot, offline: Boolean, refreshing: Boolean, zone: ZoneId, is24Hour: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                "Claude",
                style = TextStyle(color = ColorProvider(Palette.MutedText), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            val updatedLabel = if (offline) {
                "Offline · ${TimeFormat.clockTime(snapshot.fetchedAt, zone, is24Hour)}"
            } else {
                "Updated ${TimeFormat.clockTime(snapshot.fetchedAt, zone, is24Hour)}"
            }
            Text(updatedLabel, style = TextStyle(color = ColorProvider(Palette.MutedText), fontSize = 11.sp))
            Spacer(modifier = GlanceModifier.width(4.dp))
            if (refreshing) {
                Box(modifier = GlanceModifier.size(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = GlanceModifier.size(16.dp),
                        color = ColorProvider(Palette.MutedText)
                    )
                }
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(24.dp)
                        .cornerRadius(12.dp)
                        .clickable(actionRunCallback<RefreshAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageBlock(label: String, resetText: String, pct: Double, textColor: Color, isCompact: Boolean) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "$label · $resetText",
                style = TextStyle(color = ColorProvider(Palette.MutedText), fontSize = 12.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                "${pct.toInt()}%",
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = if (isCompact) 16.sp else 28.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        ProgressBar(pct)
    }
}

@Composable
private fun ProgressBar(pct: Double) {
    val barHeight = 8.dp
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(barHeight)
            .background(Palette.BarTrack)
            .cornerRadius(barHeight)
    ) {
        val containerWidth: Dp = LocalSize.current.width - 32.dp
        val fillWidth: Dp = if (pct <= 0) 0.dp else maxOf(barHeight, containerWidth * (pct / 100.0).toFloat())
        Box(
            modifier = GlanceModifier
                .width(fillWidth)
                .height(barHeight)
                .background(Palette.fillColorFor(pct))
                .cornerRadius(barHeight)
        ) {}
    }
}
