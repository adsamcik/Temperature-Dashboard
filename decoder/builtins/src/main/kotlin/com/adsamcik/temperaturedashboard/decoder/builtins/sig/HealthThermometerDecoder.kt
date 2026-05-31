package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.decodeDateTime
import com.adsamcik.temperaturedashboard.decoder.builtins.util.decodeFloat11073

internal object HealthThermometerDecoder : Decoder {
    override val id = "sig.health_thermometer"
    override val displayName = "Health Thermometer"
    private const val Uuid = "2A1C"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 5) return null
        val flags = payload[0].toInt() and 0xFF
        val isFahrenheit = (flags and 0x01) != 0
        val hasTimestamp = (flags and 0x02) != 0
        val hasType = (flags and 0x04) != 0

        val temperatureValue = decodeFloat11073(payload[1], payload[2], payload[3], payload[4])
        val unit = if (isFahrenheit) "°F" else "°C"
        val fields = mutableListOf(
            DecodedField("Temperature", DecodedValue.FloatValue(temperatureValue), unit),
        )

        var offset = 5
        if (hasTimestamp && payload.size >= offset + 7) {
            val dateTime = decodeDateTime(payload, offset)
            if (dateTime != null) {
                fields += DecodedField("Timestamp", DecodedValue.StringValue(dateTime.toString()))
            }
            offset += 7
        }
        if (hasType && payload.size > offset) {
            fields += DecodedField(
                "Temperature Type",
                DecodedValue.IntValue((payload[offset].toInt() and 0xFF).toLong()),
            )
        }
        return DecodeResult(id, fields)
    }
}
