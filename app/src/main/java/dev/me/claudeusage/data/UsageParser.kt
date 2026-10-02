package dev.me.claudeusage.data

import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/** Tolerant parser for the unofficial usage endpoint. Key names and shapes aren't verified. */
object UsageParser {

    fun parse(raw: String): ParseResult {
        return try {
            val root = JSONObject(raw)
            val sessionObj = findWindow(root, "five_hour") ?: return ParseResult.Failure(raw)
            val weeklyObj = findWindow(root, "seven_day")

            val sessionUtil = findUtilization(sessionObj) ?: return ParseResult.Failure(raw)
            val weeklyUtil = weeklyObj?.let { findUtilization(it) }

            val isFraction = listOfNotNull(sessionUtil, weeklyUtil).all { it <= 1.0 }
            fun normalize(value: Double) = if (isFraction) value * 100.0 else value

            ParseResult.Success(
                UsageSnapshot(
                    sessionPct = normalize(sessionUtil),
                    sessionResetsAt = findResetsAt(sessionObj),
                    weeklyPct = weeklyUtil?.let { normalize(it) },
                    weeklyResetsAt = weeklyObj?.let { findResetsAt(it) },
                    fetchedAt = Instant.now()
                )
            )
        } catch (e: JSONException) {
            ParseResult.Failure(raw)
        }
    }

    private fun JSONObject.keyList(): List<String> {
        val result = mutableListOf<String>()
        val it = keys()
        while (it.hasNext()) result.add(it.next())
        return result
    }

    private fun normalizeKey(key: String) = key.lowercase().replace("_", "").replace("-", "")

    private fun findWindow(root: JSONObject, canonical: String): JSONObject? {
        val target = normalizeKey(canonical)
        return root.keyList()
            .firstOrNull { normalizeKey(it) == target }
            ?.let { root.opt(it) as? JSONObject }
    }

    private fun findUtilization(obj: JSONObject): Double? {
        for (key in obj.keyList()) {
            if (normalizeKey(key).contains("utilization")) {
                val value = obj.opt(key)
                when (value) {
                    is Number -> return value.toDouble()
                    is String -> value.toDoubleOrNull()?.let { return it }
                }
            }
        }
        return null
    }

    private fun findResetsAt(obj: JSONObject): Instant? {
        for (key in obj.keyList()) {
            val normalized = normalizeKey(key)
            if (normalized.contains("resetsat") || normalized.contains("resetat")) {
                return parseInstant(obj.opt(key))
            }
        }
        return null
    }

    private fun parseInstant(value: Any?): Instant? = when (value) {
        is String -> try {
            Instant.parse(value)
        } catch (e: Exception) {
            null
        }
        is Number -> {
            val epoch = value.toLong()
            if (epoch > 10_000_000_000L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch)
        }
        null -> null
        else -> null
    }
}
