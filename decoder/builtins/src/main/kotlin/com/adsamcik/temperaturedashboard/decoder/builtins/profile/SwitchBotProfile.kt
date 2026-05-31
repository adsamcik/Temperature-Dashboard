package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher

/**
 * Unified profile for the **entire SwitchBot Bluetooth thermo-hygrometer family**.
 *
 * Identified by service-data UUID `0xFD3D` and a recognised model byte:
 *
 * | Byte | ASCII   | Device                                         | Temp/humidity source |
 * |-----:|---------|------------------------------------------------|----------------------|
 * | 0x54 | `'T'`   | Meter (WoSensorTH)                             | service data `[3..5]` |
 * | 0x74 | `'t'`   | Meter (unencrypted variant)                    | service data `[3..5]` |
 * | 0x69 | `'i'`   | Meter Plus                                     | service data `[3..5]` |
 * | 0x49 | `'I'`   | Meter Plus (encrypted variant)                 | service data `[3..5]` |
 * | 0x77 | `'w'`   | **Indoor/Outdoor Thermo-Hygrometer (W3400010)** | manufacturer data `[8..10]` |
 * | 0x57 | `'W'`   | Indoor/Outdoor Thermo-Hygrometer (encrypted)   | manufacturer data `[8..10]` |
 * | 0x34 | `'4'`   | Meter Pro                                      | either                |
 * | 0x35 | `'5'`   | Meter Pro CO2 (CO2 reading not yet surfaced)   | either                |
 *
 * Wire format for the **3-byte temperature/humidity block** (identical wherever
 * it lives — see `decode_temp_humidity` in pySwitchbot):
 *
 * ```
 * byte[0]: bits[3:0] = temperature decimal (× 0.1 °C)
 * byte[1]: bit[7]    = sign (1 = positive); bits[6:0] = integer °C
 * byte[2]: bit[7]    = Fahrenheit display flag; bits[6:0] = humidity %
 * ```
 *
 * The Indoor/Outdoor Thermo-Hygrometer (`'w'`) puts that block at offsets
 * `8..10` of manufacturer data (company id `0x0969`, after the 6-byte MAC).
 * The older indoor Meter family puts it at offsets `3..5` of service data
 * after the `model / status / battery` header.
 *
 * Battery is always at service-data byte `2`, masked with `0x7F`, when
 * service data is at least 3 bytes long.
 *
 * ## References
 * - github.com/OpenWonderLabs/SwitchBotAPI-BLE — official protocol docs
 * - github.com/sblibs/pySwitchbot — reference implementation used by
 *   Home Assistant's `switchbot-ble` integration
 */
internal object SwitchBotProfile : DeviceProfile {
    override val id = "switchbot.meter"
    override val displayName = "SwitchBot Thermo-Hygrometer"

    private const val SERVICE_DATA_UUID = "0000FD3D-0000-1000-8000-00805F9B34FB"
    private const val MANUFACTURER_ID = 0x0969 // Woan Technology Shenzhen Co., Ltd

    /** Model bytes whose temp/humidity layout this profile handles. */
    private val KNOWN_MODELS: Set<Int> = setOf(
        'T'.code, 't'.code, // Meter
        'i'.code, 'I'.code, // Meter Plus
        'w'.code, 'W'.code, // Indoor/Outdoor Thermo-Hygrometer (W3400010)
        '4'.code,           // Meter Pro
        '5'.code,           // Meter Pro CO2 (CO2 itself is not surfaced)
    )

    private const val SEVEN_BIT_MASK = 0x7F
    private const val NIBBLE_MASK = 0x0F
    private const val SIGN_POSITIVE = 0x80

    private const val MIN_SERVICE_DATA_LEN_FOR_MATCH = 1
    private const val MIN_SERVICE_DATA_LEN_FOR_BATTERY = 3
    private const val MIN_SERVICE_DATA_LEN_FOR_INDOOR_TEMP = 6
    private const val MIN_MANUFACTURER_DATA_LEN_FOR_OUTDOOR_TEMP = 11

    override fun matches(snapshot: AdvertisementSnapshot): Boolean {
        val service = snapshot.serviceDataAt(SERVICE_DATA_UUID) ?: return false
        if (service.size < MIN_SERVICE_DATA_LEN_FOR_MATCH) return false
        return (service[0].toInt() and 0xFF) in KNOWN_MODELS
    }

    override fun decodeAdvertisement(snapshot: AdvertisementSnapshot): List<DecodedField> {
        val service = snapshot.serviceDataAt(SERVICE_DATA_UUID) ?: return emptyList()
        if (service.isEmpty()) return emptyList()
        val model = service[0].toInt() and 0xFF
        if (model !in KNOWN_MODELS) return emptyList()

        // Battery — from service data byte 2 when available.
        val battery: Int? = if (service.size >= MIN_SERVICE_DATA_LEN_FOR_BATTERY) {
            service[2].toInt() and SEVEN_BIT_MASK
        } else null

        // Temperature + humidity — same 3-byte block, two possible locations.
        // Outdoor Meter sends it in manufacturer data; indoor Meter in service data.
        val tempBlock: ByteArray? = pickTempBlock(snapshot, service)
        val tempHum = tempBlock?.let(::decodeTempHumidity)

        if (tempHum == null && battery == null) return emptyList()
        if (battery != null && (battery !in 0..100)) return emptyList()

        return buildList {
            if (tempHum != null) {
                add(DecodedField("Temperature", DecodedValue.FloatValue(tempHum.temperatureC), "°C"))
                add(DecodedField("Humidity", DecodedValue.IntValue(tempHum.humidityPct.toLong()), "%"))
            }
            if (battery != null) {
                add(DecodedField("Battery", DecodedValue.IntValue(battery.toLong()), "%"))
            }
        }
    }

    /**
     * Returns the 3-byte temperature/humidity block, preferring manufacturer
     * data (Outdoor Meter layout) when present.
     */
    private fun pickTempBlock(snapshot: AdvertisementSnapshot, service: ByteArray): ByteArray? {
        val mfr = snapshot.manufacturerData[MANUFACTURER_ID]
        if (mfr != null && mfr.size >= MIN_MANUFACTURER_DATA_LEN_FOR_OUTDOOR_TEMP) {
            return mfr.copyOfRange(8, 11)
        }
        if (service.size >= MIN_SERVICE_DATA_LEN_FOR_INDOOR_TEMP) {
            return service.copyOfRange(3, 6)
        }
        return null
    }

    private data class TempHumidity(val temperatureC: Double, val humidityPct: Int)

    private fun decodeTempHumidity(block: ByteArray): TempHumidity? {
        val decimalByte = block[0].toInt() and 0xFF
        val integerSignByte = block[1].toInt() and 0xFF
        val humidityByte = block[2].toInt() and 0xFF

        val sign = if (integerSignByte and SIGN_POSITIVE != 0) 1 else -1
        val integer = integerSignByte and SEVEN_BIT_MASK
        val decimal = decimalByte and NIBBLE_MASK
        val temperatureC = sign * (integer + decimal / 10.0)
        val humidity = humidityByte and SEVEN_BIT_MASK

        // Sanity check: humidity must be plausible; -127..+127 °C accepts any sign.
        if (humidity !in 0..100) return null
        // Reject the "all zeros" no-data marker pySwitchbot also drops.
        if (temperatureC == 0.0 && humidity == 0) return null
        return TempHumidity(temperatureC, humidity)
    }

    private fun AdvertisementSnapshot.serviceDataAt(targetUuid: String): ByteArray? {
        for ((uuid, bytes) in serviceData) {
            if (UuidMatcher.matches(targetUuid, uuid)) return bytes
        }
        return null
    }
}
