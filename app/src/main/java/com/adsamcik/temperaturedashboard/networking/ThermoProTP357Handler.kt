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

        // Characteristic UUIDs
        private val UUID_READ: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b10")
        private val UUID_WRITE: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b11")

        // Commands derived from Python code (similar to day/week/year)
        private val DAY_COMMAND = byteArrayOf(0xA7.toByte(), 0x01, 0x00, 0x7A.toByte())
        private val WEEK_COMMAND = byteArrayOf(0xA6.toByte(), 0x01, 0x00, 0x6A.toByte())
        private val YEAR_COMMAND = byteArrayOf(0xA8.toByte(), 0x01, 0x00, 0x8A.toByte())

        // Immediate (LATEST) reading final packet: 0xC2 (194)
        // Historical packets start with the command byte (e.g., 0xA6 for week).
    }

    override val readCharacteristicUuid: UUID = UUID_READ
    override val writeCharacteristicUuid: UUID = UUID_WRITE

    override fun isCompatible(device: Device): Boolean {
        return device.name?.matches(deviceFilter) == true
    }

    override fun getRequestCommand(mode: ApiMode): ByteArray? {
        return when (mode) {
            ApiMode.LATEST -> null     // No command, just wait for immediate reading
            ApiMode.DELTA -> WEEK_COMMAND
            ApiMode.HISTORY -> YEAR_COMMAND
        }
    }

    override suspend fun retrieveData(
        connectedDevice: ConnectedBleDevice,
        mode: ApiMode
    ): List<TemperatureHumidityData> {
        val readChar = connectedDevice.getCharacteristic(readCharacteristicUuid)
            ?: return emptyList()

        val writeChar = connectedDevice.getCharacteristic(writeCharacteristicUuid)
        val command = getRequestCommand(mode)

        // Enable notifications
        if (!connectedDevice.enableNotifications(readChar)) {
            Log.e(name, "Failed to enable notifications.")
            return emptyList()
        }

        // Send request command if any
        if (command != null && writeChar != null) {
            if (!connectedDevice.writeCharacteristic(writeChar, command)) {
                Log.e(name, "Failed to write command.")
                connectedDevice.disableNotifications(readChar)
                return emptyList()
            }
        }

        // Collect packets until we see the final packet (0xC2) or timeout.
        val packets = mutableListOf<ByteArray>()
        val startTime = System.currentTimeMillis()
        val timeout = 10_000L // 10 seconds

        // Determine the expected start byte if we're expecting historical packets
        // For immediate (LATEST) readings, no start byte check needed; we just wait for 0xC2.
        val expectedCmdByte = command?.get(0)?.toInt()?.and(0xFF)

        loop@ while (System.currentTimeMillis() - startTime < timeout) {
            val packet = connectedDevice.waitForNotification(2000) ?: break

            val firstByte = packet[0].toInt() and 0xFF
            // If we get the final packet indicating end
            if (firstByte == 194) { // 0xC2 final packet
                // Add final packet if it's LATEST mode (we decode immediate from this)
                if (mode == ApiMode.LATEST) {
                    packets.add(packet)
                }
                break@loop
            }

            // Otherwise, if we have a command-based mode (DELTA/HISTORY), check if packet starts with that command byte
            if (command != null) {
                if (firstByte == expectedCmdByte) {
                    packets.add(packet)
                } else {
                    // Non-matching packet received, stop reading (this might be extra logic depending on device)
                    break@loop
                }
            } else {
                // For LATEST, if we didn't get 0xC2 yet and got some random packet, let's just store it
                // or ignore it. According to the Python snippet, immediate reading is signaled by C2 only.
                // This device might not send anything else if no command is given.
                // We'll ignore non-194 packets in LATEST mode.
            }
        }

        connectedDevice.disableNotifications(readChar)

        return decodeData(mode, packets)
    }

    /**
     * Decode data based on mode and packets.
     * - LATEST: Expect exactly one packet with first byte = 0xC2. Decode immediate reading.
     * - DELTA/HISTORY: Expect multiple packets starting with command's byte, ended by a 0xC2 packet.
     *   The final 0xC2 packet is not used for historical data decoding. Only the command-starting packets are decoded.
     */
    private fun decodeData(mode: ApiMode, packets: List<ByteArray>): List<TemperatureHumidityData> {
        return when (mode) {
            ApiMode.LATEST -> {
                // For immediate reading, look for the c2 packet
                // According to device logic, the final packet with 0xC2 contains the immediate reading data
                val c2Packet = packets.find { (it[0].toInt() and 0xFF) == 194 }
                if (c2Packet != null) {
                    val single = decodeImmediateData(c2Packet)
                    if (single != null) listOf(single) else emptyList()
                } else {
                    emptyList()
                }
            }
            ApiMode.DELTA, ApiMode.HISTORY -> {
                // Historical modes return multiple packets starting with command byte, ended by c2 packet.
                // The c2 packet isn't added to historical packets (we ended loop at that point),
                // so 'packets' should contain only historical data packets.
                decodeHistoricalData(packets)
            }
        }
    }

    /**
     * Decode immediate reading data:
     * When final packet with first byte = 194 (0xC2) is received,
     * temp = (raw[3] + raw[4]*256)/10
     * hum = raw[5]
     */
    private fun decodeImmediateData(rawData: ByteArray): TemperatureHumidityData? {
        if (rawData.size < 6) {
            return null
        }
        if (rawData[0].toInt() and 0xFF != 194) {
            return null
        }

        val tempRaw = ((rawData[3].toInt() and 0xFF) or ((rawData[4].toInt() and 0xFF) shl 8)).toShort()
        val temperature = tempRaw / 10.0
        val humidity = rawData[5].toInt() and 0xFF
        if (temperature !in -40.0..60.0 || humidity !in 0..100) {
            return null
        }
        return TemperatureHumidityData(temperature, humidity.toDouble())
    }

    /**
     * Decode a batch of historical data packets.
     * Each packet:
     *  - byte[0]: command (e.g., 0xA7)
     *  - byte[1..2]: time index (not currently used here)
     *  - byte[3]: flag (not currently used)
     *  - For i in [0..4]: byte[4 + i*3 .. 4 + i*3 + 2] is a reading (2 bytes temp, 1 byte hum)
     * Non-valid readings are recorded as NaN.
     */
    private fun decodeHistoricalData(rawPackets: List<ByteArray>): List<TemperatureHumidityData> {
        val result = mutableListOf<TemperatureHumidityData>()
        for (packet in rawPackets) {
            if (packet.size < 19) {
                // At least one full set of 5 readings (3*5=15 plus 4 header =19)
                continue
            }

            // We don't use packet[1..3], we just decode readings
            for (i in 0 until 5) {
                val ofs = 4 + i * 3
                if (ofs + 2 >= packet.size) continue
                val tLow = packet[ofs].toInt() and 0xFF
                val tHigh = packet[ofs+1].toInt() and 0xFF
                val hum = packet[ofs+2].toInt() and 0xFF

                if (tLow == 0xFF && tHigh == 0xFF) {
                    // Invalid reading
                    result.add(TemperatureHumidityData(Double.NaN, Double.NaN))
                } else {
                    val tempRaw = ((tLow) or (tHigh shl 8)).toShort()
                    val temperature = tempRaw / 10.0
                    if (temperature in -40.0..60.0 && hum in 0..100) {
                        result.add(TemperatureHumidityData(temperature, hum.toDouble()))
                    } else {
                        result.add(TemperatureHumidityData(Double.NaN, Double.NaN))
                    }
                }
            }
        }
        return result
    }
}
