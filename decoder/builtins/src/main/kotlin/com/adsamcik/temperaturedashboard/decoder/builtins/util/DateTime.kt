package com.adsamcik.temperaturedashboard.decoder.builtins.util

import java.time.DateTimeException
import java.time.LocalDateTime

fun decodeDateTime(payload: ByteArray, offset: Int = 0): LocalDateTime? {
    if (payload.size < offset + 7) return null
    val year = payload.leU16At(offset)
    val month = payload[offset + 2].toInt() and 0xFF
    val day = payload[offset + 3].toInt() and 0xFF
    val hour = payload[offset + 4].toInt() and 0xFF
    val minute = payload[offset + 5].toInt() and 0xFF
    val second = payload[offset + 6].toInt() and 0xFF
    if (!isPlausibleDateTime(year, month, day, hour, minute, second)) return null

    return try {
        LocalDateTime.of(year, month, day, hour, minute, second)
    } catch (_: DateTimeException) {
        null
    }
}

private fun isPlausibleDateTime(
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
