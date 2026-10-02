package dev.me.claudeusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsageParserTest {

    @Test
    fun `fraction values with ISO resets_at`() {
        val raw = """
            {"five_hour":{"utilization":0.37,"resets_at":"2026-10-02T18:00:00Z"},
             "seven_day":{"utilization":0.52,"resets_at":"2026-10-07T03:30:00Z"}}
        """.trimIndent()
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(37.0, result.snapshot.sessionPct, 0.001)
        assertEquals(52.0, result.snapshot.weeklyPct!!, 0.001)
        assertEquals(Instant.parse("2026-10-02T18:00:00Z"), result.snapshot.sessionResetsAt)
        assertEquals(Instant.parse("2026-10-07T03:30:00Z"), result.snapshot.weeklyResetsAt)
    }

    @Test
    fun `percent values with epoch seconds resets_at`() {
        val raw = """
            {"five_hour":{"utilization":37,"resets_at":1759424400},
             "seven_day":{"utilization":52,"resets_at":1759856400}}
        """.trimIndent()
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(37.0, result.snapshot.sessionPct, 0.001)
        assertEquals(52.0, result.snapshot.weeklyPct!!, 0.001)
        assertEquals(Instant.ofEpochSecond(1759424400), result.snapshot.sessionResetsAt)
    }

    @Test
    fun `epoch millis resets_at and extra unknown keys are ignored`() {
        val raw = """
            {"five_hour":{"utilization":0.1,"resets_at":1759424400000,"model_breakdown":{"opus":0.4}},
             "seven_day":{"utilization":0.2,"resets_at":1759856400000},
             "some_future_field":{"nested":true}}
        """.trimIndent()
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(10.0, result.snapshot.sessionPct, 0.001)
        assertEquals(Instant.ofEpochMilli(1759424400000), result.snapshot.sessionResetsAt)
    }

    @Test
    fun `missing weekly window leaves weekly fields null`() {
        val raw = """{"five_hour":{"utilization":0.6,"resets_at":"2026-10-02T18:00:00Z"}}"""
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(60.0, result.snapshot.sessionPct, 0.001)
        assertNull(result.snapshot.weeklyPct)
        assertNull(result.snapshot.weeklyResetsAt)
    }

    @Test
    fun `case and underscore key variants are tolerated`() {
        val raw = """{"FiveHour":{"Utilization":0.8,"ResetsAt":"2026-10-02T18:00:00Z"}}"""
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(80.0, result.snapshot.sessionPct, 0.001)
    }

    @Test
    fun `malformed payload returns failure with raw json preserved`() {
        val raw = "not json at all"
        val result = UsageParser.parse(raw) as ParseResult.Failure
        assertEquals(raw, result.rawJson)
    }

    @Test
    fun `missing five_hour window returns failure`() {
        val raw = """{"seven_day":{"utilization":0.5,"resets_at":"2026-10-07T03:30:00Z"}}"""
        val result = UsageParser.parse(raw)
        assertTrue(result is ParseResult.Failure)
    }
}
