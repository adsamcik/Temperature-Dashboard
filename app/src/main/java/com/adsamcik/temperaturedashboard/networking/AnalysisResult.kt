package com.adsamcik.temperaturedashboard.networking

import java.util.UUID

/**
 * Represents a single analysis result from observing BLE characteristic data.
 */
data class AnalysisResult(
    val serviceUuid: UUID?,
    val characteristicUuid: UUID,
    val pattern: String,
    val potentialTemperature: Double?,
    val potentialHumidity: Double?,
    val confidence: Double
) {
    /**
     * Indicates if this result seems to contain temperature data.
     */
    val hasTemperature: Boolean
        get() = potentialTemperature != null && pattern.contains("T")

    /**
     * Indicates if this result seems to contain humidity data.
     */
    val hasHumidity: Boolean
        get() = potentialHumidity != null && pattern.contains("H")

    /**
     * Returns true if this result seems to match a common sensor pattern.
     */
    fun matchesCommonPattern(): Boolean {
        // Common patterns for temperature/humidity sensors
        val commonPatterns = listOf(
            "TH",    // Temperature followed by humidity
            "HT",    // Humidity followed by temperature
            "xTHx",  // Command byte, temp, humidity, checksum
            "TxHx"   // Temp with scale, humidity with scale
        )
        return commonPatterns.any { pattern.contains(it) }
    }
}