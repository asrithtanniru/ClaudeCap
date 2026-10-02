package dev.asrithtanniru.claudecap.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimeFormatTest {

    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Test
    fun `relativeToNow formats hours and minutes`() {
        assertEquals("2h 14m", TimeFormat.relativeToNow(now.plusSeconds(2 * 3600 + 14 * 60), now))
    }

    @Test
    fun `relativeToNow formats minutes only under an hour`() {
        assertEquals("45m", TimeFormat.relativeToNow(now.plusSeconds(45 * 60), now))
    }

    @Test
    fun `relativeToNow returns now when already passed`() {
        assertEquals("now", TimeFormat.relativeToNow(now.minusSeconds(60), now))
        assertEquals("now", TimeFormat.relativeToNow(now, now))
    }

    @Test
    fun `relativeToNow returns placeholder for null`() {
        assertEquals("—", TimeFormat.relativeToNow(null, now))
    }

    @Test
    fun `weekdayAndTime formats 24-hour`() {
        val resetsAt = Instant.parse("2026-10-07T15:30:00Z")
        assertEquals("Wed 15:30", TimeFormat.weekdayAndTime(resetsAt, ZoneOffset.UTC, is24Hour = true))
    }

    @Test
    fun `weekdayAndTime formats 12-hour`() {
        val resetsAt = Instant.parse("2026-10-07T15:30:00Z")
        assertEquals("Wed 3:30 PM", TimeFormat.weekdayAndTime(resetsAt, ZoneOffset.UTC, is24Hour = false))
    }

    @Test
    fun `clockTime respects zone and format`() {
        val instant = Instant.parse("2026-10-02T08:05:00Z")
        assertEquals("08:05", TimeFormat.clockTime(instant, ZoneOffset.UTC, is24Hour = true))
        assertEquals("8:05 AM", TimeFormat.clockTime(instant, ZoneOffset.UTC, is24Hour = false))
    }
}
