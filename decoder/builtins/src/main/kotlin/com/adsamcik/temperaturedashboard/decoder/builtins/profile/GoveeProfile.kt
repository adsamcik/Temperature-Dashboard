package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile

/**
 * Govee single-probe thermo-hygrometer family.
 *
 * Covers (all share one decoding branch in the upstream `govee-ble` parser):
 *
 * | Model       | Notes                                                   |
 * |-------------|---------------------------------------------------------|
 * | H5100 / H5101 / H5102 / H5103 | Generic indoor thermo-hygrometers     |
 * | **H5104**   | GoveeLife Bluetooth Hygrometer Thermometer              |
 * | H5105 / H5108                | Variants with bigger displays           |
 * | H5174 / H5177                | Older smart hygrometer thermometers     |
 * | **H5110**   | Smart Thermo-Hygrometer 2s Lite (3-sensor kit)          |
 * | GV5179      | Pre-2020 variant                                        |
 *
 * Identification is by **BLE local name** — every Govee advertiser broadcasts
 * its model code as part of its name (e.g. `GVH5104_ABCD`). The manufacturer
 * ID is not safe to hardcode — different production runs and regions use
 * different ones, and Govee re-uses the same 4-byte payload structure across
 * many devices. Match by name and parse the payload structurally.
 *
 * ## Wire format
 *
 * Manufacturer-data value (excluding the 2-byte BLE company ID), **6 or 8 bytes**:
 *
 * | Offset | Field                                                                  |
 * |-------:|------------------------------------------------------------------------|
 * | `0..1` | Header / reserved (ignored)                                            |
 * | `2..4` | 24-bit packed temperature + humidity, big-endian                       |
 * |   `5`  | bit 7 = sensor-error flag; bits 6:0 = battery percentage               |
 * | `6..7` | Variant trailer (8-byte payloads only — not needed for our fields)     |
 *
 * The 24-bit field decodes as:
 *
 * ```
 * base       = (byte2 << 16) | (byte3 << 8) | byte4
 * is_negative = base & 0x80_00_00
 * value      = base & 0x7F_FF_FF
 * temperature_c =  (value / 1000) / 10.0    // integer div for /1000
 *                  × (if is_negative then -1 else +1)
 * humidity_%   = (value % 1000) / 10.0
 * ```
 *
 * ## Match guards
 *
 * - Skip Apple manufacturer ID `0x004C` (iBeacons / Find My signals) when
 *   iterating an advert's manufacturer data — Govee devices live in some
 *   other company ID, but Apple frequently shows up too.
 * - Drop readings outside `[-40 °C, +100 °C]` or with the error bit set —
 *   the same gate `govee-ble` uses.
 *
 * ## References
 * - github.com/Bluetooth-Devices/govee-ble — reference parser used by
 *   Home Assistant's `govee_ble` integration
 * - github.com/wcbonner/GoveeBTTempLogger — covers history download
 */
internal object GoveeProfile : DeviceProfile {
    override val id = "govee.hygrometer"
    override val displayName = "Govee Thermo-Hygrometer"

    /** Apple's company ID — frequently present alongside the real Govee payload. */
    private const val APPLE_MANUFACTURER_ID = 0x004C

    /**
     * Substrings the BLE local name must contain. The govee-ble project is the
     * source of truth for this list; we mirror their single-probe branch.
     */
    private val SUPPORTED_MODEL_TOKENS: List<String> = listOf(
        "H5100", "H5101", "H5102", "H5103",
        "H5104",
        "H5105", "H5108",
        "H5110",
        "H5174", "H5177",
        "GV5179",
    )

    private const val NEGATIVE_SIGN_BIT = 0x800000
    private const val VALUE_MASK = 0x7FFFFF
    private const val ERROR_BIT = 0x80
    private const val SEVEN_BIT_MASK = 0x7F

    private const val MIN_TEMP_C = -40.0
    private const val MAX_TEMP_C = 100.0

    override fun matches(snapshot: AdvertisementSnapshot): Boolean {
        val name = snapshot.name ?: return false
        return SUPPORTED_MODEL_TOKENS.any { it in name }
    }

    override fun decodeAdvertisement(snapshot: AdvertisementSnapshot): List<DecodedField> {
        val payload = snapshot.findGoveePayload() ?: return emptyList()

        val base = ((payload[2].toInt() and 0xFF) shl 16) or
            ((payload[3].toInt() and 0xFF) shl 8) or
            (payload[4].toInt() and 0xFF)

        val isNegative = (base and NEGATIVE_SIGN_BIT) != 0
        val value = base and VALUE_MASK
        var temperatureC = ((value / 1000) / 10.0)
        if (isNegative) temperatureC = -temperatureC
        val humidity = (value % 1000) / 10.0

        val statusByte = payload[5].toInt() and 0xFF
        val battery = statusByte and SEVEN_BIT_MASK
        val sensorError = (statusByte and ERROR_BIT) != 0

        if (sensorError || temperatureC < MIN_TEMP_C || temperatureC > MAX_TEMP_C) {
            // Per govee-ble: still surface battery so the user sees it's alive,
            // but drop the (likely garbage) temperature/humidity.
            return listOf(
                DecodedField("Battery", DecodedValue.IntValue(battery.toLong()), "%"),
            )
        }
        if (humidity !in 0.0..100.0) return emptyList()

        return listOf(
            DecodedField("Temperature", DecodedValue.FloatValue(temperatureC), "°C"),
            DecodedField("Humidity", DecodedValue.FloatValue(humidity), "%"),
            DecodedField("Battery", DecodedValue.IntValue(battery.toLong()), "%"),
        )
    }

    /**
     * Picks the right manufacturer-data payload for the Govee family — skips
     * Apple and accepts the first 6- or 8-byte entry left over.
     */
    private fun AdvertisementSnapshot.findGoveePayload(): ByteArray? {
        for ((companyId, bytes) in manufacturerData) {
            if (companyId == APPLE_MANUFACTURER_ID) continue
            if (bytes.size == 6 || bytes.size == 8) return bytes
        }
        return null
    }
}
