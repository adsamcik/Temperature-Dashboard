package com.adsamcik.temperaturedashboard.networking

import android.bluetooth.le.ScanResult
import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses ThermoPro TP357 sensor data from BLE advertisement packets.
 * This provides real-time readings without needing a GATT connection.
 */
object Tp357AdvertisementParser {

    private const val DEVICE_NAME_PREFIX = "TP357"
    private const val MIN_RAW_SIZE = 5
    private const val MIN_TEMP_C = -30.0
    private const val MAX_TEMP_C = 70.0

    data class AdvertisementReading(
        val address: String,
        val temperature: Double,
        val humidity: Double,
        val batteryPercent: Int,
        val rssi: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Attempts to parse a TP357 reading from a BLE scan result.
     * Returns null if the scan result is not from a TP357 device.
     */
    fun parse(scanResult: ScanResult): AdvertisementReading? {
        val deviceName = scanResult.scanRecord?.deviceName ?: return null
        if (!deviceName.contains(DEVICE_NAME_PREFIX, ignoreCase = true)) return null

        val manufacturerData = scanResult.scanRecord?.manufacturerSpecificData ?: return null
        if (manufacturerData.size() == 0) return null

        for (i in 0 until manufacturerData.size()) {
            val key = manufacturerData.keyAt(i)
            val value = manufacturerData.valueAt(i) ?: continue

            val raw = ByteBuffer.allocate(2 + value.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(key.toShort())
                .put(value)
                .array()

            val reading = parseRawAdvertisement(raw, scanResult.device.address, scanResult.rssi)
            if (reading != null) return reading
        }

        return null
    }

    /**
     * Parses the raw manufacturer data bytes.
     * Format: [mfr_id(2)] [temp_signed_le(2)] [humidity(1)] [battery(1)] [optional checksum(1)]
     * Starting at offset 1 for sensor data.
     */
    internal fun parseRawAdvertisement(raw: ByteArray, address: String, rssi: Int): AdvertisementReading? {
        if (raw.size < MIN_RAW_SIZE) return null
        if (raw.size > MIN_RAW_SIZE && !validateChecksum(raw)) return null

        val tempRaw = ByteBuffer.wrap(raw, 1, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt()

        val humidity = raw[3].toInt() and 0xFF
        val batteryRaw = raw[4].toInt() and 0xFF
        val batteryPercent = ((batteryRaw / 2.0) * 100).toInt().coerceIn(0, 100)

        val tempCByHundredths = tempRaw / 100.0
        val tempCByTenths = tempRaw / 10.0
        val tempC = when {
            tempCByHundredths in MIN_TEMP_C..MAX_TEMP_C -> tempCByHundredths
            tempCByTenths in MIN_TEMP_C..MAX_TEMP_C -> tempCByTenths
            else -> return null
        }

        if (humidity > 100) return null

        return AdvertisementReading(
            address = address,
            temperature = tempC,
            humidity = humidity.toDouble(),
            batteryPercent = batteryPercent,
            rssi = rssi
        )
    }

    private fun validateChecksum(raw: ByteArray): Boolean {
        if (raw.size < 2) return false
        val expected = raw.last().toInt() and 0xFF
        val sum = raw.dropLast(1).fold(0) { acc, byte -> (acc + (byte.toInt() and 0xFF)) and 0xFF }
        return sum == expected
    }

    /**
     * Converts an AdvertisementReading to TemperatureHumidityData for consistency with GATT-based readings.
     */
    fun AdvertisementReading.toTemperatureHumidityData(): TemperatureHumidityData {
        return TemperatureHumidityData(
            temperature = temperature,
            humidity = humidity,
            timestamp = timestamp
        )
    }
}
