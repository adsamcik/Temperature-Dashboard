package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher

/**
 * SwitchBot Meter family — temperature & humidity sensors that broadcast in
 * BLE advertisements (no GATT connection required).
 *
 * Supported variants (identified by the first service-data byte):
 *
 * | Byte | ASCII | Model         |
 * |------|-------|---------------|
 * | 0x54 | 'T'   | Meter (WoSensorTH) |
 * | 0x69 | 'i'   | Meter Plus    |
 * | 0x6C | 'l'   | Hub 2 (built-in temp/humidity) |
 *
 * Outdoor Meter ('w' / 0x77), Meter Pro CO2, Plant Monitor, and other
 * SwitchBot devices use different layouts and are not yet handled.
 *
 * ## Wire format — service-data UUID `0xFD3D`
 *
 * Typical 6-byte payload, all integers unsigned-byte:
 *
 * | Offset | Field                                                                |
 * |-------:|----------------------------------------------------------------------|
 * |   `0`  | bit 7 = encryption flag (always 0 for unpaired broadcast); bit 6:0 = model |
 * |   `1`  | paired / status flags (ignored)                                      |
 * |   `2`  | bit 7 = is_charging; bit 6:0 = battery percentage (0..100)           |
 * |   `3`  | bit 3:0 = temperature decimal (0..9)                                 |
 * |   `4`  | bit 7 = temperature sign (1 = positive, 0 = negative); bit 6:0 = temperature integer (0..127) |
 * |   `5`  | bit 6:0 = humidity percentage (0..100)                               |
 *
 * Temperature is reconstructed as `sign × (integer + decimal/10)` °C.
 *
 * Manufacturer data (company id `0x0969` — Woan Technology) is present but
 * its leading 6 bytes are just the MAC; the temp/humidity bytes that follow
 * mirror the service data, so we prefer service data when both are present.
 *
 * ## References
 * - github.com/OpenWonderLabs/SwitchBotAPI-BLE — official protocol docs
 * - github.com/sblibs/pySwitchbot — reference Python decoder used by Home Assistant
 */
internal object SwitchBotMeterProfile : DeviceProfile {
    override val id = "switchbot.meter"
    override val displayName = "SwitchBot Meter"

    private const val SERVICE_DATA_UUID = "0000FD3D-0000-1000-8000-00805F9B34FB"
    private const val MANUFACTURER_ID = 0x0969 // Woan Technology Shenzhen Co., Ltd
    private const val MIN_SERVICE_DATA_LEN = 6

    private const val MODEL_METER: Int = 0x54        // 'T'
    private const val MODEL_METER_PLUS: Int = 0x69   // 'i'
    private const val MODEL_HUB2: Int = 0x6C         // 'l'
    private val KNOWN_MODELS = setOf(MODEL_METER, MODEL_METER_PLUS, MODEL_HUB2)

    private const val MODEL_MASK = 0x7F
    private const val SIGN_BIT_POSITIVE = 0x80
    private const val SEVEN_BIT_MASK = 0x7F
    private const val NIBBLE_MASK = 0x0F

    override fun matches(snapshot: AdvertisementSnapshot): Boolean {
        // Primary signal: service-data UUID 0xFD3D with a recognised model byte.
        val serviceBytes = snapshot.findServiceData() ?: return false
        if (serviceBytes.size < MIN_SERVICE_DATA_LEN) return false
        val model = serviceBytes[0].toInt() and MODEL_MASK
        return model in KNOWN_MODELS
    }

    override fun decodeAdvertisement(snapshot: AdvertisementSnapshot): List<DecodedField> {
        val data = snapshot.findServiceData() ?: return emptyList()
        if (data.size < MIN_SERVICE_DATA_LEN) return emptyList()

        val battery = data[2].toInt() and SEVEN_BIT_MASK
        val tempDec = data[3].toInt() and NIBBLE_MASK
        val tempIntByte = data[4].toInt() and 0xFF
        val tempInt = tempIntByte and SEVEN_BIT_MASK
        val sign = if (tempIntByte and SIGN_BIT_POSITIVE != 0) 1 else -1
        val temperatureC = sign * (tempInt + tempDec / 10.0)
        val humidity = data[5].toInt() and SEVEN_BIT_MASK

        if (humidity !in 0..100 || battery !in 0..100) return emptyList()

        return listOf(
            DecodedField("Temperature", DecodedValue.FloatValue(temperatureC), "°C"),
            DecodedField("Humidity", DecodedValue.IntValue(humidity.toLong()), "%"),
            DecodedField("Battery", DecodedValue.IntValue(battery.toLong()), "%"),
        )
    }

    private fun AdvertisementSnapshot.findServiceData(): ByteArray? {
        // serviceData keys come through as 128-bit UUIDs from the BLE layer;
        // UuidMatcher handles both forms so this works equally for short writers.
        for ((uuid, bytes) in serviceData) {
            if (UuidMatcher.matches(SERVICE_DATA_UUID, uuid)) return bytes
        }
        return null
    }
}
