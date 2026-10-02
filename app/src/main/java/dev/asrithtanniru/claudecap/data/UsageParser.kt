package dev.asrithtanniru.claudecap.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Parser for the unofficial usage endpoint. Confirmed shape (captured from a live
 * response): a top-level "limits" array of {kind, group, percent, resets_at, is_active, ...},
 * padded with large numbers of unrelated null fields (apparent anti-scraping noise).
 * Still tolerant about exact key casing/underscores since this is unverified and may change.
 */
object UsageParser {

    fun parse(raw: String): ParseResult {
        return try {
            val root = JSONObject(raw)
            val limits = root.optJSONArray("limits") ?: return ParseResult.Failure(raw)

            val sessionLimit = findLimit(limits) { it == "session" } ?: return ParseResult.Failure(raw)
            val weeklyLimit = findLimit(limits) { it.contains("weekly") }

            ParseResult.Success(
                UsageSnapshot(
                    sessionPct = percentOf(sessionLimit),
                    sessionResetsAt = parseInstant(sessionLimit.opt("resets_at")),
                    weeklyPct = weeklyLimit?.let { percentOf(it) },
                    weeklyResetsAt = weeklyLimit?.let { parseInstant(it.opt("resets_at")) },
                    fetchedAt = Instant.now()
                )
            )
        } catch (e: JSONException) {
            ParseResult.Failure(raw)
        }
    }

    private fun findLimit(limits: JSONArray, matches: (String) -> Boolean): JSONObject? {
        for (i in 0 until limits.length()) {
            val obj = limits.optJSONObject(i) ?: continue
            val kind = obj.optString("kind", "").lowercase()
            val group = obj.optString("group", "").lowercase()
            if (matches(kind) || matches(group)) return obj
        }
        return null
    }

    private fun percentOf(limit: JSONObject): Double {
        val value = limit.optDouble("percent", 0.0)
        return if (value <= 1.0) value * 100.0 else value
    }

    private fun parseInstant(value: Any?): Instant? = when (value) {
        is String -> {
            try {
                Instant.parse(value)
            } catch (e: DateTimeParseException) {
                try {
                    OffsetDateTime.parse(value).toInstant()
                } catch (e2: DateTimeParseException) {
                    null
                }
            }
        }
        is Number -> {
            val epoch = value.toLong()
            if (epoch > 10_000_000_000L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch)
        }
        else -> null
    }
}
