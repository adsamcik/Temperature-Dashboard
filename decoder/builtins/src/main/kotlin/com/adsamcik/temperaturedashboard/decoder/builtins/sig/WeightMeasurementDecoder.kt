package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.decodeDateTime
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU16At

internal object WeightMeasurementDecoder : Decoder {
    override val id = "sig.weight_measurement"
    override val displayName = "Weight Measurement"
    private const val Uuid = "2A9D"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 3) return null
        val flags = payload[0].toInt() and 0xFF
        val isImperial = (flags and 0x01) != 0
        val hasTimestamp = (flags and 0x02) != 0
        val hasUserId = (flags and 0x04) != 0
        val hasBodyMassIndex = (flags and 0x08) != 0

        val rawWeight = payload.leU16At(1)
        val (weight, weightUnit) = if (isImperial) {
            rawWeight * 0.01 to "lb"
        } else {
            rawWeight * 0.005 to "kg"
        }
        val fields = mutableListOf(
            DecodedField("Weight", DecodedValue.FloatValue(weight), weightUnit),
        )

        var offset = 3
        if (hasTimestamp && payload.size >= offset + 7) {
            val dateTime = decodeDateTime(payload, offset)
            if (dateTime != null) {
                fields += DecodedField("Timestamp", DecodedValue.StringValue(dateTime.toString()))
            }
            offset += 7
        }
        if (hasUserId && payload.size > offset) {
            fields += DecodedField(
                "User ID",
                DecodedValue.IntValue((payload[offset].toInt() and 0xFF).toLong()),
            )
            offset += 1
        }
        if (hasBodyMassIndex && payload.size >= offset + 4) {
            val bodyMassIndex = payload.leU16At(offset)
            val rawHeight = payload.leU16At(offset + 2)
            fields += DecodedField("BMI", DecodedValue.FloatValue(bodyMassIndex * 0.1))
            val (height, heightUnit) = if (isImperial) {
                rawHeight * 0.1 to "in"
            } else {
                rawHeight * 0.001 to "m"
            }
            fields += DecodedField("Height", DecodedValue.FloatValue(height), heightUnit)
        }
        return DecodeResult(id, fields)
    }
}
