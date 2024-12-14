package com.adsamcik.temperaturedashboard.data

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
)