package dev.me.claudeusage.widget

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {

    fun relativeToNow(resetsAt: Instant?, now: Instant): String {
        if (resetsAt == null) return "—"
        val seconds = Duration.between(now, resetsAt).seconds
        if (seconds <= 0) return "now"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun weekdayAndTime(resetsAt: Instant?, zone: ZoneId, is24Hour: Boolean): String {
        if (resetsAt == null) return "—"
        val zoned = resetsAt.atZone(zone)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        return zoned.format(DateTimeFormatter.ofPattern("EEE $timePattern", Locale.US))
    }

    fun clockTime(instant: Instant?, zone: ZoneId, is24Hour: Boolean): String {
        if (instant == null) return "—"
        val zoned = instant.atZone(zone)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        return zoned.format(DateTimeFormatter.ofPattern(timePattern, Locale.US))
    }
}
