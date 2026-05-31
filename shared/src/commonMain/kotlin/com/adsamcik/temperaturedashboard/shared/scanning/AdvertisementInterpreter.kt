package com.adsamcik.temperaturedashboard.shared.scanning

import com.adsamcik.temperaturedashboard.ble.api.BleAdvertisement
import com.adsamcik.temperaturedashboard.decoder.api.AdvertisementSnapshot
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfile
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfileRegistry
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.ReadingSource

/**
 * Pure-function bridge between the decoder layer and the readings domain.
 *
 * Takes a raw [BleAdvertisement], finds a matching [DeviceProfile], runs the
 * profile's advertisement decoder, extracts temperature / humidity / battery
 * into a [Reading], and tells the caller which profile matched (used by the
 * scan UI to surface "add this device" candidates).
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
        )
        val profile = profileRegistry.firstMatch(snapshot) ?: return null
        val fields = profile.decodeAdvertisement(snapshot)
        val reading = fields.toReading(timestampMillis = advert.timestamp, rssi = advert.rssi)
        return InterpretedAdvertisement(
            advertisement = advert,
            profile = profile,
            fields = fields,
            reading = reading,
        )
    }

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
}

/** Output of [AdvertisementInterpreter.interpret] for a matched advertisement. */
data class InterpretedAdvertisement(
    val advertisement: BleAdvertisement,
    val profile: DeviceProfile,
    val fields: List<DecodedField>,
    /** Null if the matched profile didn't surface any persistable temp/humidity/battery. */
    val reading: Reading?,
)
