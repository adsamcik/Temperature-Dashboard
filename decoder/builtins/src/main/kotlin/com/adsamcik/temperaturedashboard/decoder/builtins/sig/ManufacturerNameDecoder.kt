package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher

internal object ManufacturerNameDecoder : Decoder {
    override val id = "sig.manufacturer_name"
    override val displayName = "Manufacturer Name"
    private const val Uuid = "2A29"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext) =
        DecodeResult(
            id,
            listOf(DecodedField("Manufacturer Name", DecodedValue.StringValue(payload.decodeToString()))),
        )
}
