package com.adsamcik.temperaturedashboard.decoder.api

interface Decoder {
    val id: String
    val displayName: String
    fun matches(serviceUuid: String?, characteristicUuid: String?): Boolean
    fun decode(payload: ByteArray, context: DecodeContext): DecodeResult?
}

data class DecodeContext(
    val serviceUuid: String? = null,
    val characteristicUuid: String? = null,
    val direction: PayloadDirection = PayloadDirection.Read,
)

enum class PayloadDirection { Read, Write, Notify }
