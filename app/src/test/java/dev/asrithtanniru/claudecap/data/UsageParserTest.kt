package dev.asrithtanniru.claudecap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsageParserTest {

    @Test
    fun `real captured shape with offset resets_at and weekly_all kind`() {
        val raw = """
            {"random_decoy_field":null,"another_decoy":null,
             "limits":[
               {"kind":"session","group":"session","percent":26,"severity":"normal","resets_at":"2026-10-02T12:09:59.891058+00:00","scope":null,"is_active":true},
               {"kind":"weekly_all","group":"weekly","percent":12,"severity":"normal","resets_at":"2026-10-02T13:59:59.891083+00:00","scope":null,"is_active":false}
             ],
             "spend":{"used":{"amount_minor":0}},
             "seven_day_breakdown":{"rows":[{"key":"chat","percent":48}]}}
        """.trimIndent()
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(26.0, result.snapshot.sessionPct, 0.001)
        assertEquals(12.0, result.snapshot.weeklyPct!!, 0.001)
        assertEquals(Instant.parse("2026-10-02T12:09:59.891058Z"), result.snapshot.sessionResetsAt)
        assertEquals(Instant.parse("2026-10-02T13:59:59.891083Z"), result.snapshot.weeklyResetsAt)
    }

    @Test
    fun `fraction percent values are normalized to 0-100`() {
        val raw = """
            {"limits":[
               {"kind":"session","percent":0.37,"resets_at":"2026-10-02T18:00:00Z"},
               {"kind":"weekly","percent":0.52,"resets_at":"2026-10-07T03:30:00Z"}
             ]}
        """.trimIndent()
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(37.0, result.snapshot.sessionPct, 0.001)
        assertEquals(52.0, result.snapshot.weeklyPct!!, 0.001)
    }

    @Test
    fun `epoch seconds and epoch millis resets_at`() {
        val raw = """
            {"limits":[
               {"kind":"session","percent":10,"resets_at":1759424400},
               {"kind":"weekly_all","percent":20,"resets_at":1759424400000}
             ]}
        """.trimIndent()
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(Instant.ofEpochSecond(1759424400), result.snapshot.sessionResetsAt)
        assertEquals(Instant.ofEpochMilli(1759424400000), result.snapshot.weeklyResetsAt)
    }

    @Test
    fun `missing weekly entry leaves weekly fields null`() {
        val raw = """{"limits":[{"kind":"session","percent":60,"resets_at":"2026-10-02T18:00:00Z"}]}"""
        val result = UsageParser.parse(raw) as ParseResult.Success
        assertEquals(60.0, result.snapshot.sessionPct, 0.001)
        assertNull(result.snapshot.weeklyPct)
        assertNull(result.snapshot.weeklyResetsAt)
    }

    @Test
    fun `group field matches when kind is absent`() {
        val raw = """{"limits":[{"group":"session","percent":80,"resets_at":"2026-10-02T18:00:00Z"}]}"""
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
    fun `missing limits array returns failure`() {
        val raw = """{"spend":{"used":0}}"""
        assertTrue(UsageParser.parse(raw) is ParseResult.Failure)
    }

    @Test
    fun `missing session entry returns failure`() {
        val raw = """{"limits":[{"kind":"weekly_all","percent":50,"resets_at":"2026-10-02T18:00:00Z"}]}"""
        assertTrue(UsageParser.parse(raw) is ParseResult.Failure)
    }
}
