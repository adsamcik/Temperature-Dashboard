package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU16At

internal object HeartRateMeasurementDecoder : Decoder {
    override val id = "sig.heart_rate"
    override val displayName = "Heart Rate Measurement"
    private const val Uuid = "2A37"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 2) return null
        val flags = payload[0].toInt() and 0xFF
        val isUint16 = (flags and 0x01) != 0
        val hasEnergy = (flags and 0x08) != 0
        val hasRr = (flags and 0x10) != 0

        var offset = 1
        val heartRate = if (isUint16) {
            if (payload.size < offset + 2) return null
            payload.leU16At(offset).toLong().also { offset += 2 }
        } else {
            (payload[offset].toInt() and 0xFF).toLong().also { offset += 1 }
        }

        val fields = mutableListOf(
            DecodedField("Heart Rate", DecodedValue.IntValue(heartRate), "BPM"),
        )

        if (hasEnergy && payload.size >= offset + 2) {
            val energy = payload.leU16At(offset)
            fields += DecodedField("Energy Expended", DecodedValue.IntValue(energy.toLong()), "kJ")
            offset += 2
        }
        if (hasRr) {
            var rrIndex = 1
            while (payload.size >= offset + 2) {
                val rr = payload.leU16At(offset)
                fields += DecodedField(
                    "RR Interval $rrIndex",
                    DecodedValue.FloatValue(rr / 1024.0),
                    "s",
                )
                offset += 2
                rrIndex += 1
            }
        }
        return DecodeResult(id, fields)
    }
}
