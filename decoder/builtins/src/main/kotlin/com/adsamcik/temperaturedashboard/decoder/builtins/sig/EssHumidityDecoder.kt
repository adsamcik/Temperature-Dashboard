package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU16At

internal object EssHumidityDecoder : Decoder {
    override val id = "sig.ess_humidity"
    override val displayName = "ESS Humidity"
    private const val Uuid = "2A6F"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 2) return null
        val humidity = payload.leU16At(0) * 0.01
        return DecodeResult(
            id,
            listOf(DecodedField("Humidity", DecodedValue.FloatValue(humidity), "%")),
        )
    }
}
