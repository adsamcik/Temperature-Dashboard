package com.adsamcik.temperaturedashboard.shared.export

import com.adsamcik.temperaturedashboard.core.model.ReadingInterval

/**
 * Pure-function exporter for sensor history.
 *
 * Emits two formats:
 *  - **CSV** — RFC 4180-ish (LF line endings); five columns: valid_from_ms,
 *    valid_until_ms, temperature_c, humidity_pct, battery_pct
 *  - **JSON** — array of objects with the same five fields; ISO-8601 not
 *    used so importers don't have to parse timezones — just epoch millis
 *
 * Streamed write — callers feed bytes into whatever sink the platform gives
 * them (Android `ContentResolver` Uri, Desktop `File`).
 */
object HistoryExporter {

    fun writeCsv(intervals: List<ReadingInterval>, out: Appendable) {
        out.append("valid_from_ms,valid_until_ms,temperature_c,humidity_pct,battery_pct\n")
        for (i in intervals) {
            out.append(i.validFrom.toEpochMilliseconds().toString()).append(',')
            out.append(i.validUntil.toEpochMilliseconds().toString()).append(',')
            out.append(i.temperatureC?.toString().orEmpty()).append(',')
            out.append(i.humidityPct?.toString().orEmpty()).append(',')
            out.append(i.batteryPct?.toString().orEmpty())
            out.append('\n')
        }
    }

    fun writeJson(intervals: List<ReadingInterval>, out: Appendable) {
        out.append('[')
        intervals.forEachIndexed { idx, i ->
            if (idx > 0) out.append(',')
            out.append('\n')
            out.append("  {")
            out.append("\"valid_from_ms\":").append(i.validFrom.toEpochMilliseconds().toString())
            out.append(",\"valid_until_ms\":").append(i.validUntil.toEpochMilliseconds().toString())
            out.append(",\"temperature_c\":").append(i.temperatureC?.toString() ?: "null")
            out.append(",\"humidity_pct\":").append(i.humidityPct?.toString() ?: "null")
            out.append(",\"battery_pct\":").append(i.batteryPct?.toString() ?: "null")
            out.append('}')
        }
        out.append("\n]\n")
    }
}
