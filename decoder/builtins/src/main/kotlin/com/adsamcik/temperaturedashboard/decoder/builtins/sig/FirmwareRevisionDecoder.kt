package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher

internal object FirmwareRevisionDecoder : Decoder {
    override val id = "sig.firmware_revision"
    override val displayName = "Firmware Revision"
    private const val Uuid = "2A26"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext) =
        DecodeResult(
            id,
            listOf(DecodedField("Firmware Revision", DecodedValue.StringValue(payload.decodeToString()))),
        )
}
