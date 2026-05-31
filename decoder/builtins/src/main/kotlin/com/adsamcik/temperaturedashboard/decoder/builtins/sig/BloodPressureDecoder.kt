package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.decodeDateTime
import com.adsamcik.temperaturedashboard.decoder.builtins.util.decodeSfloat
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU16At

internal object BloodPressureDecoder : Decoder {
    override val id = "sig.blood_pressure"
    override val displayName = "Blood Pressure"
    private const val Uuid = "2A35"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 7) return null
        val flags = payload[0].toInt() and 0xFF
        val isKilopascal = (flags and 0x01) != 0
        val hasTimestamp = (flags and 0x02) != 0
        val hasPulseRate = (flags and 0x04) != 0
        val hasUserId = (flags and 0x08) != 0
        val hasMeasurementStatus = (flags and 0x10) != 0
        val unit = if (isKilopascal) "kPa" else "mmHg"

        val systolic = decodeSfloat(payload.leU16At(1).toShort())
        val diastolic = decodeSfloat(payload.leU16At(3).toShort())
        val meanArterialPressure = decodeSfloat(payload.leU16At(5).toShort())
        val fields = mutableListOf(
            DecodedField("Systolic", DecodedValue.FloatValue(systolic), unit),
            DecodedField("Diastolic", DecodedValue.FloatValue(diastolic), unit),
            DecodedField(
                "Mean Arterial Pressure",
                DecodedValue.FloatValue(meanArterialPressure),
                unit,
            ),
        )

        var offset = 7
        if (hasTimestamp && payload.size >= offset + 7) {
            val dateTime = decodeDateTime(payload, offset)
            if (dateTime != null) {
                fields += DecodedField("Timestamp", DecodedValue.StringValue(dateTime.toString()))
            }
            offset += 7
        }
        if (hasPulseRate && payload.size >= offset + 2) {
            val pulseRate = decodeSfloat(payload.leU16At(offset).toShort())
            fields += DecodedField("Pulse Rate", DecodedValue.FloatValue(pulseRate), "BPM")
            offset += 2
        }
        if (hasUserId && payload.size > offset) {
            fields += DecodedField(
                "User ID",
                DecodedValue.IntValue((payload[offset].toInt() and 0xFF).toLong()),
            )
            offset += 1
        }
        if (hasMeasurementStatus && payload.size >= offset + 2) {
            fields += DecodedField(
                "Measurement Status",
                DecodedValue.IntValue(payload.leU16At(offset).toLong()),
            )
        }
        return DecodeResult(id, fields)
    }
}
