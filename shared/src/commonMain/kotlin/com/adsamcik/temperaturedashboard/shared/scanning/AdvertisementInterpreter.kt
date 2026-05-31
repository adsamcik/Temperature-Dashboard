package com.adsamcik.temperaturedashboard.shared.scanning

import com.adsamcik.temperaturedashboard.ble.api.BleAdvertisement
import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfileRegistry
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.ReadingSource
import io.github.aakira.napier.Napier

/**
 * Pure-function bridge between the decoder layer and the readings domain.
 *
 * Takes a raw [BleAdvertisement], finds a matching [DeviceProfile], runs the
 * profile's advertisement decoder, extracts temperature / humidity / battery
 * into a [Reading], and tells the caller which profile matched (used by the
 * scan UI to surface "add this device" candidates).
 *
 * Emits diagnostic warnings on Napier (filter `AdvertisementInterpreter`) for
 * adverts that *look* like a known family by name token but fail to decode
 * — invaluable when chasing "this sensor showed up in scan but never produces
 * readings" reports.
 */
class AdvertisementInterpreter(private val profileRegistry: DeviceProfileRegistry) {

    /**
     * @return null when no profile matched OR when the matched profile yielded
     *   no temperature/humidity/battery field (e.g. an unrelated payload from
     *   the same device).
     */
    fun interpret(advert: BleAdvertisement): InterpretedAdvertisement? {
        val snapshot = AdvertisementSnapshot(
            name = advert.name,
            advertisedServiceUuids = advert.serviceUuids,
            manufacturerData = advert.manufacturerData,
            serviceData = advert.serviceData,
        )
        val profile = profileRegistry.firstMatch(snapshot)
        if (profile == null) {
            logSuspiciousMiss(advert)
            return null
        }
        val fields = profile.decodeAdvertisement(snapshot)
        val reading = fields.toReading(timestampMillis = advert.timestamp, rssi = advert.rssi)
        if (reading == null && fields.isEmpty()) {
            logMatchedButEmpty(profile, advert)
        }
        return InterpretedAdvertisement(
            advertisement = advert,
            profile = profile,
            fields = fields,
            reading = reading,
        )
    }

    private fun logSuspiciousMiss(advert: BleAdvertisement) {
        val name = advert.name ?: return
        if (!name.containsLikelyKnownToken()) return
        Napier.w(
            "Advert looked like a known device by name but no profile matched. " +
                "name='${name}' addr=${advert.address} " +
                "manufacturer=${advert.manufacturerData.summary()} " +
                "serviceUuids=${advert.serviceUuids} " +
                "serviceData=${advert.serviceData.keys}",
            tag = LOG_TAG,
        )
    }

    private fun logMatchedButEmpty(profile: DeviceProfile, advert: BleAdvertisement) {
        Napier.w(
            "Profile ${profile.id} matched but decoded zero fields. " +
                "name='${advert.name}' addr=${advert.address} " +
                "manufacturer=${advert.manufacturerData.summary()} " +
                "serviceData=${advert.serviceData.summaryServiceData()}",
            tag = LOG_TAG,
        )
    }

    private fun String.containsLikelyKnownToken(): Boolean {
        // Only flag tokens that genuinely indicate a thermometer-family device.
        // Bare "Govee" / "GV" without an H5xxx/GV5179 suffix is almost always
        // a Govee appliance (light strip, ice maker, etc.) and not in scope.
        val tokens = listOf(
            "GVH5", "ihoment",
            "H5072", "H5074", "H5075", "H5100", "H5101", "H5102", "H5103",
            "H5104", "H5105", "H5108", "H5110", "H5174", "H5177",
            "GV5179",
            "WoSensorTH", "WoIOSensorTH", "WoMeter",
            "TP35",
        )
        return tokens.any { it in this }
    }

    private fun Map<Int, ByteArray>.summary(): String =
        entries.joinToString(prefix = "{", postfix = "}") { (id, bytes) ->
            "0x${id.toString(16).uppercase()}:${bytes.size}=${bytes.toHex()}"
        }

    private fun Map<String, ByteArray>.summaryServiceData(): String =
        entries.joinToString(prefix = "{", postfix = "}") { (uuid, bytes) ->
            "$uuid:${bytes.size}=${bytes.toHex()}"
        }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private fun List<DecodedField>.toReading(
        timestampMillis: kotlinx.datetime.Instant,
        rssi: Int,
    ): Reading? {
        if (isEmpty()) return null
        val temp = doubleFor("Temperature")
        val hum = doubleFor("Humidity")?.let { it } ?: longFor("Humidity")?.toDouble()
        val batt = longFor("Battery")?.toInt()
        if (temp == null && hum == null && batt == null) return null
        return Reading(
            temperatureC = temp,
            humidityPct = hum,
            batteryPct = batt,
            rssi = rssi,
            timestamp = timestampMillis,
            source = ReadingSource.ADVERTISEMENT,
        )
    }

    private fun List<DecodedField>.doubleFor(name: String): Double? =
        firstOrNull { it.name == name }?.value
            ?.let { (it as? DecodedValue.FloatValue)?.v ?: (it as? DecodedValue.IntValue)?.v?.toDouble() }

    private fun List<DecodedField>.longFor(name: String): Long? =
        firstOrNull { it.name == name }?.value?.let { (it as? DecodedValue.IntValue)?.v }

    private companion object { const val LOG_TAG = "AdvertisementInterpreter" }
}

/** Output of [AdvertisementInterpreter.interpret] for a matched advertisement. */
data class InterpretedAdvertisement(
    val advertisement: BleAdvertisement,
    val profile: DeviceProfile,
    val fields: List<DecodedField>,
    /** Null if the matched profile didn't surface any persistable temp/humidity/battery. */
    val reading: Reading?,
)
