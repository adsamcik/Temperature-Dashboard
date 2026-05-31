package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leS16At
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU16At

internal object EssTemperatureDecoder : Decoder {
    override val id = "sig.ess_temperature"
    override val displayName = "ESS Temperature"
    private const val Uuid = "2A6E"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.size < 2) return null
        val raw = payload.leU16At(0)
        if (raw == 0x8000) {
            return DecodeResult(
                id,
                listOf(DecodedField("Temperature", DecodedValue.StringValue("unknown"), "°C")),
                listOf("Sentinel 0x8000 indicates unknown value"),
            )
        }
        val signed = payload.leS16At(0)
        val temperature = signed * 0.01
        return DecodeResult(
            id,
            listOf(DecodedField("Temperature", DecodedValue.FloatValue(temperature), "°C")),
        )
    }
}
