package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.decodeExactTime256

internal object CurrentTimeDecoder : Decoder {
    override val id = "sig.current_time"
    override val displayName = "Current Time"
    private const val Uuid = "2A2B"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 10) return null
        val exactTime = decodeExactTime256(payload) ?: return null
        val fields = mutableListOf(
            DecodedField("Date Time", DecodedValue.StringValue(exactTime.dateTime.toString())),
            DecodedField("Fractions256", DecodedValue.IntValue(exactTime.fractions256.toLong())),
            DecodedField("Adjust Reason", DecodedValue.IntValue(exactTime.adjustReason.toLong())),
        )
        if (exactTime.dayOfWeek != null) {
            fields += DecodedField("Day of Week", DecodedValue.StringValue(exactTime.dayOfWeek.name))
        }
        return DecodeResult(id, fields)
    }
}
