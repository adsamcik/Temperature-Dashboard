package com.adsamcik.temperaturedashboard.networking

import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.storage.Device
import java.util.UUID

/**
 * Example of a handler implementation for the ThermoPro TP357 device.
 * Adjust logic as necessary.
 */
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

        // Example command from the Python logic (for "day" mode)
        private val DAY_COMMAND = byteArrayOf(0xA7.toByte(), 0x00, 0x00, 0x00, 0x00, 0x7A.toByte())
    }

    override fun isCompatible(device: Device): Boolean {
        return device.name?.matches(deviceFilter) == true
    }

    override fun getReadCharacteristicUuid(): UUID = UUID_READ

    override fun getWriteCharacteristicUuid(): UUID = UUID_WRITE

    override fun getRequestCommand(): ByteArray = DAY_COMMAND

    override fun decodeData(rawData: ByteArray): TemperatureHumidityData? {
        // Example decoding from previous code. Adjust as needed.
        if (rawData.size < 5) return null

        // temp_100mdC = short at rawData[1..2], hum_rh = rawData[3]
        val temp_100mdC = (rawData[1].toInt() and 0xFF) or (rawData[2].toInt() shl 8)
        val signedTemp = if (temp_100mdC > Short.MAX_VALUE) temp_100mdC - 0x10000 else temp_100mdC
        val hum_rh = rawData[3].toInt() and 0xFF

        if (signedTemp > 1024 || hum_rh > 100) return null

        val temperature = signedTemp.toDouble() / 10.0
        val humidity = hum_rh.toDouble()
        return TemperatureHumidityData(temperature, humidity)
    }
}