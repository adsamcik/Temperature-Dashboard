package com.adsamcik.temperaturedashboard.decoder.builtins.util

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDateTime

data class ExactTime256(
    val dateTime: LocalDateTime,
    val dayOfWeek: DayOfWeek?,
    val fractions256: Int,
    val adjustReason: Int,
)

fun decodeExactTime256(payload: ByteArray): ExactTime256? {
    if (payload.size < 10) return null
    val year = payload.leU16At(0)
    val month = payload[2].toInt() and 0xFF
    val day = payload[3].toInt() and 0xFF
    val hour = payload[4].toInt() and 0xFF
    val minute = payload[5].toInt() and 0xFF
    val second = payload[6].toInt() and 0xFF
    val dayOfWeekValue = payload[7].toInt() and 0xFF
    val fractions256 = payload[8].toInt() and 0xFF
    val adjustReason = payload[9].toInt() and 0xFF
    if (!isPlausibleExactTime(year, month, day, hour, minute, second)) return null

    return try {
        ExactTime256(
            dateTime = LocalDateTime.of(year, month, day, hour, minute, second),
            dayOfWeek = if (dayOfWeekValue in 1..7) DayOfWeek.of(dayOfWeekValue) else null,
            fractions256 = fractions256,
            adjustReason = adjustReason,
        )
    } catch (_: DateTimeException) {
        null
    }
}

private fun isPlausibleExactTime(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
): Boolean = year in 1582..9999 &&
    month in 1..12 &&
    day in 1..31 &&
    hour in 0..23 &&
    minute in 0..59 &&
    second in 0..59
