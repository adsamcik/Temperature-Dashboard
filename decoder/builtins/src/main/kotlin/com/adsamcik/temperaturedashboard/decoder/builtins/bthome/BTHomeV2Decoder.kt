package com.adsamcik.temperaturedashboard.decoder.builtins.bthome

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leS16At
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU16At
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU24At
import com.adsamcik.temperaturedashboard.decoder.builtins.util.leU32At

internal object BTHomeV2Decoder : Decoder {
    override val id = "bthome.v2"
    override val displayName = "BTHome v2"
    private const val Uuid = "FCD2"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) =
        UuidMatcher.matches(Uuid, serviceUuid)

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        if (payload.isEmpty()) return null
        val deviceInfo = payload[0].toInt() and 0xFF
        val version = (deviceInfo ushr 5) and 0x07
        val encrypted = (deviceInfo and 0x01) != 0

        val warnings = mutableListOf<String>()
        if (version != 2) {
            warnings += "BTHome version $version expected 2"
        }
        if (encrypted) {
            warnings += "Payload is encrypted; values may not be meaningful"
        }

        val fields = mutableListOf<DecodedField>()
        var index = 1
        while (index < payload.size) {
            val objectId = payload[index].toInt() and 0xFF
            index += 1
            val entry = objectTable[objectId]
            if (entry == null) {
                val hex = objectId.toString(16).uppercase().padStart(2, '0')
                warnings += "Unknown object ID 0x$hex, skipping remaining payload"
                break
            }
            if (index + entry.width > payload.size) {
                val hex = objectId.toString(16).uppercase().padStart(2, '0')
                warnings += "Truncated payload for object 0x$hex"
                break
            }

            val rawValue = when (entry.width) {
                1 -> if (entry.signed) payload[index].toLong() else (payload[index].toInt() and 0xFF).toLong()
                2 -> if (entry.signed) payload.leS16At(index).toLong() else payload.leU16At(index).toLong()
                3 -> payload.leU24At(index).toLong()
                4 -> payload.leU32At(index)
                else -> 0L
            }
            index += entry.width

            val value = if (entry.factor == 1.0) {
                DecodedValue.IntValue(rawValue)
            } else {
                DecodedValue.FloatValue(rawValue * entry.factor)
            }
            fields += DecodedField(entry.name, value, entry.unit)
        }
        return DecodeResult(id, fields, warnings)
    }

    private data class ObjectEntry(
        val name: String,
        val width: Int,
        val factor: Double,
        val unit: String?,
        val signed: Boolean = false,
    )

    private val objectTable = mapOf(
        0x01 to ObjectEntry("Battery", 1, 1.0, "%"),
        0x02 to ObjectEntry("Temperature", 2, 0.01, "°C", signed = true),
        0x03 to ObjectEntry("Humidity", 2, 0.01, "%"),
        0x04 to ObjectEntry("Pressure", 3, 0.01, "hPa"),
        0x05 to ObjectEntry("Illuminance", 3, 0.01, "lx"),
        0x09 to ObjectEntry("Count", 1, 1.0, null),
        0x0C to ObjectEntry("Voltage", 2, 0.001, "V"),
        0x12 to ObjectEntry("CO2", 2, 1.0, "ppm"),
        0x14 to ObjectEntry("Moisture", 2, 0.01, "%"),
        0x45 to ObjectEntry("Temperature", 2, 0.1, "°C", signed = true),
        0x50 to ObjectEntry("Timestamp", 4, 1.0, "s"),
        0x57 to ObjectEntry("Temperature", 1, 1.0, "°C", signed = true),
    )
}
