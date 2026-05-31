package com.adsamcik.temperaturedashboard.decoder.builtins.sig

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher

internal object BatteryLevelDecoder : Decoder {
    override val id = "sig.battery_level"
    override val displayName = "Battery Level"
    private const val Uuid = "2A19"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.isEmpty()) return null
        val level = payload[0].toInt() and 0xFF
        val warnings = if (level > 100) {
            listOf("Battery level $level% exceeds 100%, out-of-spec")
        } else {
            emptyList()
        }
        return DecodeResult(
            id,
            listOf(DecodedField("Battery Level", DecodedValue.IntValue(level.toLong()), "%")),
            warnings,
        )
    }
}
