package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU32At

internal object EssPressureDecoder : Decoder {
    override val id = "sig.ess_pressure"
    override val displayName = "ESS Pressure"
    private const val Uuid = "2A6D"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 4) return null
        val pressure = payload.leU32At(0) * 0.1
        return DecodeResult(
            id,
            listOf(DecodedField("Pressure", DecodedValue.FloatValue(pressure), "Pa")),
        )
    }
}
