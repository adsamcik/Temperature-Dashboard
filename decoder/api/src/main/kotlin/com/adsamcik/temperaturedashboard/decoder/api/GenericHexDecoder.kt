package com.adsamcik.temperaturedashboard.decoder.api

object GenericHexDecoder : Decoder {
    override val id = "generic.hex"
    override val displayName = "Generic Hex"

    override fun matches(serviceUuid: String?, characteristicUuid: String?) = true

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult =
        DecodeResult(id, listOf(DecodedField("hex", DecodedValue.HexValue(payload.toUpperHex()))))

    private fun ByteArray.toUpperHex(): String {
        if (isEmpty()) return ""
        val chars = CharArray(size * 2)
        var index = 0
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            chars[index++] = HEX_DIGITS[value ushr 4]
            chars[index++] = HEX_DIGITS[value and 0x0F]
        }
        return chars.concatToString()
    }

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
}
