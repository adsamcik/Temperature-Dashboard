package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile

/**
 * Govee single-probe thermo-hygrometer family.
 *
 * Govee broadcasts at least four different on-the-wire layouts depending on
 * model. We mirror the dispatch table from
 * [govee-ble](https://github.com/Bluetooth-Devices/govee-ble/blob/main/src/govee_ble/parser.py):
 *
 * | Variant            | Models                              | Length | Layout                                     |
 * |--------------------|-------------------------------------|--------|--------------------------------------------|
 * | [Variant.A_PACKED] | H5100, H5101, H5102, H5103, H5104, H5105, H5108, H5110, H5174, H5177, GV5179 | 6 or 8 | `data[2..4]` = packed temp+humid, `data[5]` = battery + error |
 * | [Variant.B_PACKED] | H5072, H5075                        | 6      | `data[1..3]` = packed temp+humid, `data[4]` = battery + error |
 * | [Variant.C_LE]     | H5074, H5051, H5052, H5071          | 7 or 9 | `data[1..2]` int16 LE / 100 = temp, `data[3..4]` uint16 LE / 100 = humid, `data[5]` = battery |
 *
 * Identification cascade per advertisement:
 *  1. **By local name** — preferred; the model code is in the name
 *     (`GVH5104_ABCD`, `H5074 ABCD`, etc.). Picks the right variant directly.
 *  2. **By manufacturer ID + length** as a fallback when the device has been
 *     renamed and no token is left. `0xEC88` is the historical Govee company
 *     ID covering both the H5072/74/75 and H5100-series payloads, so we try
 *     all variants until one passes range checks.
 *
 * Apple manufacturer ID `0x004C` is always skipped — Apple iBeacons frequently
 * piggyback on Govee adverts and would otherwise collide.
 *
 * Drop guards (mirrors govee-ble):
 *  - Temperature outside `[-40, 100]` °C
 *  - Humidity outside `[0, 100]` %
 *  - Sensor-error bit (where applicable) — battery is still kept so the user
 *    knows the device is alive.
 */
internal object GoveeProfile : DeviceProfile {
    override val id = "govee.hygrometer"
    override val displayName = "Govee Thermo-Hygrometer"

    private const val APPLE_MANUFACTURER_ID = 0x004C
    private const val GOVEE_MANUFACTURER_ID_EC88 = 0xEC88
    private const val GOVEE_MANUFACTURER_ID_0001 = 0x0001

    private const val NEGATIVE_SIGN_BIT = 0x800000
    private const val VALUE_MASK = 0x7FFFFF
    private const val ERROR_BIT = 0x80
    private const val SEVEN_BIT_MASK = 0x7F

    private const val MIN_TEMP_C = -40.0
    private const val MAX_TEMP_C = 100.0

    /** Local-name token → which variant decoder to use. Substring match. */
    private val NAME_VARIANT_MAP: List<Pair<String, Variant>> = listOf(
        // Order matters when one token is a substring of another; longer first.
        "H5108" to Variant.A_PACKED,
        "H5174" to Variant.A_PACKED,
        "H5177" to Variant.A_PACKED,
        "GV5179" to Variant.A_PACKED,
        "H5100" to Variant.A_PACKED,
        "H5101" to Variant.A_PACKED,
        "H5102" to Variant.A_PACKED,
        "H5103" to Variant.A_PACKED,
        "H5104" to Variant.A_PACKED,
        "H5105" to Variant.A_PACKED,
        "H5110" to Variant.A_PACKED,
        "H5072" to Variant.B_PACKED,
        "H5075" to Variant.B_PACKED,
        "H5074" to Variant.C_LE,
        "H5051" to Variant.C_LE,
        "H5052" to Variant.C_LE,
        "H5071" to Variant.C_LE,
    )

    private enum class Variant { A_PACKED, B_PACKED, C_LE }

    override fun matches(snapshot: AdvertisementSnapshot): Boolean {
        if (matchByName(snapshot.name) != null) return true
        // Manufacturer-id fallback for renamed devices.
        return snapshot.manufacturerData.any { (id, bytes) ->
            id != APPLE_MANUFACTURER_ID && looksLikeGoveePayload(id, bytes.size)
        }
    }

    override fun decodeAdvertisement(snapshot: AdvertisementSnapshot): List<DecodedField> {
        val nameVariant = matchByName(snapshot.name)
        for ((companyId, bytes) in snapshot.manufacturerData) {
            if (companyId == APPLE_MANUFACTURER_ID) continue
            val variants = nameVariant?.let { listOf(it) }
                ?: variantsForFallback(companyId, bytes.size)
            for (variant in variants) {
                val fields = decode(variant, bytes) ?: continue
                if (fields.isNotEmpty()) return fields
            }
        }
        return emptyList()
    }

    private fun matchByName(name: String?): Variant? {
        if (name == null) return null
        return NAME_VARIANT_MAP.firstOrNull { (token, _) -> token in name }?.second
    }

    private fun looksLikeGoveePayload(companyId: Int, size: Int): Boolean = when (companyId) {
        GOVEE_MANUFACTURER_ID_EC88 -> size in setOf(6, 7, 8, 9)
        GOVEE_MANUFACTURER_ID_0001 -> size == 8
        else -> false
    }

    private fun variantsForFallback(companyId: Int, size: Int): List<Variant> {
        if (companyId == GOVEE_MANUFACTURER_ID_0001 && size == 8) return listOf(Variant.A_PACKED)
        if (companyId != GOVEE_MANUFACTURER_ID_EC88) return emptyList()
        // Try in the order most-likely-to-be-correct based on length.
        return when (size) {
            6 -> listOf(Variant.B_PACKED, Variant.A_PACKED)
            7, 9 -> listOf(Variant.C_LE)
            8 -> listOf(Variant.A_PACKED)
            else -> emptyList()
        }
    }

    private fun decode(variant: Variant, data: ByteArray): List<DecodedField>? = when (variant) {
        Variant.A_PACKED -> if (data.size == 6 || data.size == 8) decodePacked(data, base = 2) else null
        Variant.B_PACKED -> if (data.size == 6) decodePacked(data, base = 1) else null
        Variant.C_LE -> if (data.size == 7 || data.size == 9) decodeLittleEndian(data) else null
    }

    /**
     * Big-endian packed temp+humid at `data[base..base+2]`, status byte at
     * `data[base+3]`. Used by H5100-series (base=2) and H5072/H5075 (base=1).
     */
    private fun decodePacked(data: ByteArray, base: Int): List<DecodedField> {
        val packed = ((data[base].toInt() and 0xFF) shl 16) or
            ((data[base + 1].toInt() and 0xFF) shl 8) or
            (data[base + 2].toInt() and 0xFF)

        val isNegative = (packed and NEGATIVE_SIGN_BIT) != 0
        val value = packed and VALUE_MASK
        var temperatureC = ((value / 1000) / 10.0)
        if (isNegative) temperatureC = -temperatureC
        val humidity = (value % 1000) / 10.0

        val statusByte = data[base + 3].toInt() and 0xFF
        val battery = statusByte and SEVEN_BIT_MASK
        val sensorError = (statusByte and ERROR_BIT) != 0

        return buildFields(temperatureC, humidity, battery, sensorError)
    }

    /**
     * Little-endian layout used by H5074 and the older H5051/52/71 series:
     * `data[1..2]` = int16 LE / 100 (temperature), `data[3..4]` = uint16 LE / 100
     * (humidity), `data[5]` = battery percentage. Length 7 (H5074) or 9 (H5051).
     */
    private fun decodeLittleEndian(data: ByteArray): List<DecodedField> {
        val tLo = data[1].toInt() and 0xFF
        val tHi = data[2].toInt() // sign-extend
        val tempRaw = (tHi shl 8) or tLo
        val temperatureC = tempRaw / 100.0

        val hLo = data[3].toInt() and 0xFF
        val hHi = data[4].toInt() and 0xFF
        val humidityRaw = (hHi shl 8) or hLo
        val humidity = humidityRaw / 100.0

        val battery = data[5].toInt() and 0xFF
        return buildFields(temperatureC, humidity, battery, sensorError = false)
    }

    private fun buildFields(
        temperatureC: Double,
        humidity: Double,
        battery: Int,
        sensorError: Boolean,
    ): List<DecodedField> {
        val tempOk = !sensorError && temperatureC in MIN_TEMP_C..MAX_TEMP_C
        val humOk = humidity in 0.0..100.0
        if (!tempOk) {
            // Surface battery so the dashboard shows the sensor is alive.
            return listOf(DecodedField("Battery", DecodedValue.IntValue(battery.toLong()), "%"))
        }
        if (!humOk) return emptyList()
        return listOf(
            DecodedField("Temperature", DecodedValue.FloatValue(temperatureC), "°C"),
            DecodedField("Humidity", DecodedValue.FloatValue(humidity), "%"),
            DecodedField("Battery", DecodedValue.IntValue(battery.toLong()), "%"),
        )
    }
}
