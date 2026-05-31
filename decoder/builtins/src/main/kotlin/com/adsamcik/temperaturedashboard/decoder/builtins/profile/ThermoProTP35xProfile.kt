package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile
import com.adsamcik.temperaturedashboard.decoder.api.ProfileAction

/**
 * ThermoPro TP35x family (TP357, TP357S, TP358, TP358S etc.) — Bluetooth
 * indoor thermometer/hygrometer.
 *
 * Passive advertisements expose temperature, humidity, and coarse battery
 * state in manufacturer data. The first two bytes of the "data" array used
 * by open decoders are the manufacturer-data company-id in little-endian;
 * the remainder is the manufacturer-data payload. Byte 0 is typically
 * `0xC2`. Byte layout (after reconstruction):
 *
 * | Offset | Field         | Decode |
 * |-------:|---------------|--------|
 * |    `0` | Header        | usually `0xC2` |
 * |  `1-2` | Temperature   | signed int16 LE, tenths of °C |
 * |    `3` | Humidity      | uint8 percent |
 * |    `4` | Battery       | `byte & 0x03` → coarse 1%/50%/100% |
 *
 * On GATT, the device also exposes a custom Telink-style service:
 *
 * - Service `00010203-0405-0607-0809-0a0b0c0d1910`
 *   - Notify `..2b10` — current reading (`0xC2`, temp at offset 3..4 LE/10,
 *     humidity at 5, status at 6) and history responses
 *   - Write `..2b11` — history request opcodes (Day=A7..., Week=A6..., Year=A8...)
 *
 * References: github.com/Bluetooth-Devices/thermopro-ble, github.com/pasky/tp357
 */
object ThermoProTP35xProfile : DeviceProfile {
    override val id = "thermopro.tp35x"
    override val displayName = "ThermoPro TP35x"

    private const val NAME_PREFIX = "TP35"
    private const val HEADER_BYTE = 0xC2

    private const val SERVICE = "00010203-0405-0607-0809-0a0b0c0d1910"
    private const val NOTIFY_CHAR = "00010203-0405-0607-0809-0a0b0c0d2b10"
    private const val WRITE_CHAR = "00010203-0405-0607-0809-0a0b0c0d2b11"

    override fun matches(snapshot: AdvertisementSnapshot): Boolean {
        val name = snapshot.name ?: return false
        // Match by local-name prefix. Service UUID list is empty in TP35x
        // advertisements, so name is the only reliable advertisement-level
        // signal.
        return name.uppercase().startsWith(NAME_PREFIX)
    }

    override fun decodeAdvertisement(snapshot: AdvertisementSnapshot): List<DecodedField> {
        for ((companyId, payload) in snapshot.manufacturerData) {
            val data = reconstruct(companyId, payload)
            val decoded = decodeAdvertisementSensorBlock(data) ?: continue
            return decoded
        }
        return emptyList()
    }

    /** Used by [ThermoProTP35xNotifyDecoder] for GATT notifications. */
    fun decodeNotification(payload: ByteArray): List<DecodedField>? {
        val markerOffset = payload.indexOfHeader()
        if (markerOffset < 0) return null
        return decodeCurrentNotification(payload, markerOffset)
    }

    private fun reconstruct(companyId: Int, payload: ByteArray): ByteArray = byteArrayOf(
        (companyId and 0xFF).toByte(),
        ((companyId shr 8) and 0xFF).toByte(),
    ) + payload

    private fun ByteArray.indexOfHeader(): Int =
        indexOfFirst { (it.toInt() and 0xFF) == HEADER_BYTE }

    /**
     * Passive advertisements use the open-source documented compact layout:
     * `C2 temp_lo temp_hi humidity battery [trailing...]`, with battery in
     * the low two bits of the battery/status byte.
     */
    private fun decodeAdvertisementSensorBlock(data: ByteArray): List<DecodedField>? =
        decodeReading(
            data = data,
            tempOffset = 1,
            humidityOffset = 3,
            batteryStatusOffset = 4,
            batteryDecoder = ::advertisementBatteryPercent,
        )

    /**
     * Current GATT notifications add two bytes between the `C2` marker and
     * the temperature pair: `C2 xx xx temp_lo temp_hi humidity status`.
     *
     * The final status byte is not the same field layout as passive
     * advertisements: the observed TP357 packet `... 2C` is medium battery in
     * the official app, so current-notify battery is decoded from bits 5..6.
     */
    private fun decodeCurrentNotification(data: ByteArray, markerOffset: Int): List<DecodedField>? =
        decodeReading(
            data = data,
            tempOffset = markerOffset + 3,
            humidityOffset = markerOffset + 5,
            batteryStatusOffset = markerOffset + 6,
            batteryDecoder = ::currentNotificationBatteryPercent,
        )

    /**
     * Decodes temperature/humidity/battery fields once the byte positions are
     * known. Returns null if the data is too short, contains the FF FF FF
     * invalid marker, or has an unrecognised battery code.
     */
    private fun decodeReading(
        data: ByteArray,
        tempOffset: Int,
        humidityOffset: Int,
        batteryStatusOffset: Int,
        batteryDecoder: (Int) -> Int?,
    ): List<DecodedField>? {
        if (data.size <= batteryStatusOffset) return null

        val tempBytes = data.sliceArray(tempOffset..tempOffset + 1)
        val humidityByte = data[humidityOffset]
        val batteryStatusByte = data[batteryStatusOffset]
        if (tempBytes[0] == 0xFF.toByte() && tempBytes[1] == 0xFF.toByte() &&
            humidityByte == 0xFF.toByte()
        ) {
            return null
        }

        val tempRaw = (tempBytes[0].toInt() and 0xFF) or ((tempBytes[1].toInt() and 0xFF) shl 8)
        val tempSigned = if (tempRaw and 0x8000 != 0) tempRaw - 0x10000 else tempRaw
        val tempC = tempSigned / 10.0
        val humidity = humidityByte.toInt() and 0xFF
        val batteryPct = batteryDecoder(batteryStatusByte.toInt() and 0xFF) ?: return null

        return listOf(
            DecodedField("Temperature", DecodedValue.FloatValue(tempC), "°C"),
            DecodedField("Humidity", DecodedValue.IntValue(humidity.toLong()), "%"),
            DecodedField("Battery", DecodedValue.IntValue(batteryPct.toLong()), "%"),
        )
    }

    private fun advertisementBatteryPercent(status: Int): Int? =
        coarseBatteryPercent(status and BATTERY_CODE_MASK)

    private fun currentNotificationBatteryPercent(status: Int): Int? =
        coarseBatteryPercent((status ushr CURRENT_BATTERY_SHIFT) and BATTERY_CODE_MASK)

    private fun coarseBatteryPercent(code: Int): Int? = when (code) {
        0 -> 1
        1 -> 50
        2 -> 100
        else -> null
    }

    override val actions: List<ProfileAction> = listOf(
        ProfileAction(
            id = "tp35x.current",
            label = "Read current readings",
            description = "Subscribe and capture the next live temperature/humidity/battery packet.",
            serviceUuid = SERVICE,
            characteristicUuid = NOTIFY_CHAR,
            payload = ByteArray(0),
            withResponse = false,
            awaitResponseOn = NOTIFY_CHAR,
        ),
        ProfileAction(
            id = "tp35x.history.day",
            label = "Read day history",
            description = "Minute-by-minute readings from the last 24 hours.",
            serviceUuid = SERVICE,
            characteristicUuid = WRITE_CHAR,
            payload = byteArrayOf(0xA7.toByte(), 0x01, 0x00, 0x7A),
            withResponse = true,
            awaitResponseOn = NOTIFY_CHAR,
        ),
        ProfileAction(
            id = "tp35x.history.week",
            label = "Read week history",
            description = "Hour-by-hour readings from the last 7 days.",
            serviceUuid = SERVICE,
            characteristicUuid = WRITE_CHAR,
            payload = byteArrayOf(0xA6.toByte(), 0x01, 0x00, 0x6A),
            withResponse = true,
            awaitResponseOn = NOTIFY_CHAR,
        ),
        ProfileAction(
            id = "tp35x.history.year",
            label = "Read year history",
            description = "Aggregated readings from the last year.",
            serviceUuid = SERVICE,
            characteristicUuid = WRITE_CHAR,
            payload = byteArrayOf(0xA8.toByte(), 0x01, 0x00, 0x8A.toByte()),
            withResponse = true,
            awaitResponseOn = NOTIFY_CHAR,
        ),
    )

    fun notifyCharacteristicUuid(): String = NOTIFY_CHAR

    /**
     * Parse one notification packet captured as a history response.
     *
     * Observed packet layout (20 bytes typical):
     *
     * | Offset | Field                | Notes                          |
     * |-------:|----------------------|--------------------------------|
     * |    `0` | Opcode               | 0xA7 day, 0xA6 week, 0xA8 year |
     * |    `1` | Page index           | 0x1F → first, increases        |
     * |  `2-3` | Reserved             | typically 0x00 0x00            |
     * | `4..N` | Samples × M          | each 3 bytes: temp_lo, temp_hi, humidity |
     * |  last  | Checksum             | not verified                   |
     *
     * Each sample decodes to:
     *  - temperature °C = (temp_hi * 256 + temp_lo) / 10
     *  - humidity % = humidity byte
     *
     * The same opcode prefix that was written is the one that comes back,
     * which is how we recognise the packet without an explicit response
     * tag from the device.
     */
    override fun parseActionResponse(actionId: String, packet: ByteArray): List<DecodedField> {
        if (actionId == "tp35x.current") {
            return decodeNotification(packet).orEmpty()
        }
        if (!actionId.startsWith("tp35x.history.")) return emptyList()
        if (packet.size < HISTORY_HEADER_BYTES + HISTORY_SAMPLE_BYTES + HISTORY_FOOTER_BYTES) {
            return emptyList()
        }
        val opcode = packet[0].toInt() and 0xFF
        if (opcode !in HISTORY_OPCODES) return emptyList()
        val sampleBytes = packet.size - HISTORY_HEADER_BYTES - HISTORY_FOOTER_BYTES
        if (sampleBytes % HISTORY_SAMPLE_BYTES != 0) return emptyList()
        val sampleCount = sampleBytes / HISTORY_SAMPLE_BYTES
        val pageIndex = packet[1].toInt() and 0xFF
        return buildList {
            add(
                DecodedField(
                    name = "Page",
                    value = DecodedValue.IntValue(pageIndex.toLong()),
                    unit = "",
                ),
            )
            for (i in 0 until sampleCount) {
                val base = HISTORY_HEADER_BYTES + i * HISTORY_SAMPLE_BYTES
                val tempRaw = (packet[base].toInt() and 0xFF) or
                    ((packet[base + 1].toInt() and 0xFF) shl 8)
                val tempC = tempRaw / 10.0
                val humidity = packet[base + 2].toInt() and 0xFF
                add(
                    DecodedField(
                        name = "T${i + 1}",
                        value = DecodedValue.FloatValue(tempC),
                        unit = "°C",
                    ),
                )
                add(
                    DecodedField(
                        name = "H${i + 1}",
                        value = DecodedValue.IntValue(humidity.toLong()),
                        unit = "%",
                    ),
                )
            }
        }
    }

    private const val HISTORY_HEADER_BYTES = 4
    private const val HISTORY_SAMPLE_BYTES = 3
    private const val HISTORY_FOOTER_BYTES = 1
    private const val CURRENT_BATTERY_SHIFT = 5
    private const val BATTERY_CODE_MASK = 0x03
    private val HISTORY_OPCODES = setOf(0xA7, 0xA6, 0xA8)
}
