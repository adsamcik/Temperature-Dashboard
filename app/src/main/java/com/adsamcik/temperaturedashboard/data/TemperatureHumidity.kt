package com.adsamcik.temperaturedashboard.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * General data structure for temperature and humidity readings.
 *
 * @param temperature The temperature value in Celsius.
 * @param humidity The relative humidity in percentage.
 * @param timestamp The timestamp of the reading.
 */
data class TemperatureHumidityData(
    val temperature: Double,
    val humidity: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val formattedTimestamp = formatter.format(Instant.ofEpochMilli(timestamp))
        return "Temperature: $temperature°C, Humidity: $humidity%, Timestamp: $formattedTimestamp"
    }
}