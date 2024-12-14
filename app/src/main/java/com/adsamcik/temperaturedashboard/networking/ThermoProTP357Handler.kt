package com.adsamcik.temperaturedashboard.networking

import android.util.Log
import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.storage.Device
import java.util.UUID

class ThermoProTP357Handler : BleDeviceHandler {
    override val name: String
        get() = "ThermoPro TP357"
    override val iconRes: Int?
        get() = null

    companion object {
        private const val DEVICE_NAME_FILTER = "TP357 \\(\\w{4}\\)"
        private val deviceFilter = DEVICE_NAME_FILTER.toRegex()
        private val UUID_READ: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b10")
        private val UUID_WRITE: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b11")

        // Commands corresponding to Python implementation
        private val DAY_COMMAND = byteArrayOf(0xA7.toByte(), 0x00, 0x00, 0x00, 0x00, 0x7A.toByte())
        private val WEEK_COMMAND = byteArrayOf(0xA6.toByte(), 0x00, 0x00, 0x00, 0x00, 0x6A.toByte())
        private val YEAR_COMMAND = byteArrayOf(0xA8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x8A.toByte())
    }

    override fun isCompatible(device: Device): Boolean {
        return device.name?.matches(deviceFilter) == true
    }

    override fun getReadCharacteristicUuid(): UUID = UUID_READ

    override fun getWriteCharacteristicUuid(): UUID = UUID_WRITE

    override fun getRequestCommand(): ByteArray {
        return when ("day") {
            "day" -> DAY_COMMAND
            "week" -> WEEK_COMMAND
            "year" -> YEAR_COMMAND
            else -> throw IllegalArgumentException("Unsupported mode: ")
        }
    }

    override fun decodeData(rawData: ByteArray): TemperatureHumidityData? {
        if (rawData.size < 19) {
            Log.d("ThermoProTP357Handler", "Data too short")
            return null
        }

// Ensure data[0] matches the command you sent (e.g., day = 0xA7)
        val cmd = rawData[0].toInt() and 0xFF
        if (cmd != 0xA7 && cmd != 0xA6 && cmd != 0xA8) {
            Log.d("ThermoProTP357Handler", "Unexpected command byte: 0x${cmd.toString(16)}")
            return null
        }

// data[1:3] is the index (raw)
        val rawIndex =
            ((rawData[1].toInt() and 0xFF) or ((rawData[2].toInt() and 0xFF) shl 8)).toShort()

// data[3] is a flag (not currently used)
        val flag = rawData[3]

        Log.d("ThermoProTP357Handler", "Decoded data - Index: $rawIndex, Flag: $flag")

// Now the readings start at data[4]
        val tempRaw =
            ((rawData[4].toInt() and 0xFF) or ((rawData[5].toInt() and 0xFF) shl 8)).toShort()
        val temperature = tempRaw / 10.0
        val humidity = rawData[6].toInt() and 0xFF

// Validate and return
        if (temperature !in -40.0..60.0 || humidity !in 0..100) {
            Log.d("ThermoProTP357Handler", "Invalid temperature or humidity values.")
            return null
        }

        Log.d(
            "ThermoProTP357Handler",
            "Decoded data - Temperature: $temperature°C, Humidity: $humidity%"
        )
        return TemperatureHumidityData(temperature, humidity.toDouble())

    }

    // Decode History Data
    fun decodeHistory(rawData: ByteArray): List<TemperatureHumidityData>? {
        val history = mutableListOf<TemperatureHumidityData>()
        val entrySize = 3 // 2 bytes for temp, 1 byte for humidity

        if (rawData.size % entrySize != 0) {
            Log.d("ThermoProTP357Handler", "Invalid history data length: ${rawData.size}")
            return null
        }

        for (i in rawData.indices step entrySize) {
            try {
                // Extract temperature (2 bytes)
                val tempRaw =
                    ((rawData[i].toInt() and 0xFF) or ((rawData[i + 1].toInt() and 0xFF) shl 8)).toShort()
                val temperature = tempRaw / 10.0

                // Extract humidity (1 byte)
                val humidity = (rawData[i + 2].toInt() and 0xFF).toDouble()

                // Validate ranges
                if (temperature in -40.0..60.0 && humidity in 0.0..100.0) {
                    history.add(TemperatureHumidityData(temperature, humidity))
                } else {
                    Log.d(
                        "ThermoProTP357Handler",
                        "Invalid history entry at index $i: Temperature=$temperature, Humidity=$humidity"
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "ThermoProTP357Handler",
                    "Error parsing history entry at index $i: ${e.message}"
                )
                return null
            }
        }
        return history
    }
}
