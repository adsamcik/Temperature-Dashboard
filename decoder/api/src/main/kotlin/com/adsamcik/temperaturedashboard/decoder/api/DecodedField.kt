package com.adsamcik.temperaturedashboard.decoder.api

import kotlinx.serialization.Serializable

@Serializable
data class ByteRange(val first: Int, val last: Int)

@Serializable
data class DecodedField(
    val name: String,
    val value: DecodedValue,
    val unit: String? = null,
    val byteRange: ByteRange? = null,
)

@Serializable
sealed interface DecodedValue {
    @Serializable
    data class IntValue(val v: Long) : DecodedValue

    @Serializable
    data class FloatValue(val v: Double) : DecodedValue

    @Serializable
    data class BoolValue(val v: Boolean) : DecodedValue

    @Serializable
    data class StringValue(val v: String) : DecodedValue

    @Serializable
    data class HexValue(val v: String) : DecodedValue
}

@Serializable
data class DecodeResult(
    val decoderId: String,
    val fields: List<DecodedField>,
    val warnings: List<String> = emptyList(),
)
