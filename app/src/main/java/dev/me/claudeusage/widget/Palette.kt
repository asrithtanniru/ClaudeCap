package dev.me.claudeusage.widget

import androidx.compose.ui.graphics.Color

object Palette {
    val CardBackground = Color(0xFF262624)
    val WindowBackground = Color(0xFF1F1E1D)
    val PrimaryText = Color(0xFFFAF9F5)
    val MutedText = Color(0xFF9C9A92)
    val BarTrack = Color(0xFF3A3935)
    val AccentNormal = Color(0xFFD97757)
    val Warning = Color(0xFFE5A24A)
    val Critical = Color(0xFFD4574E)

    fun fillColorFor(pct: Double): Color = when {
        pct >= 90.0 -> Critical
        pct >= 70.0 -> Warning
        else -> AccentNormal
    }
}
