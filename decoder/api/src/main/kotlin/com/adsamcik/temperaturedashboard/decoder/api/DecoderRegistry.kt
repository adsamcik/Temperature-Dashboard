package com.adsamcik.temperaturedashboard.decoder.api

class DecoderRegistry(private val decoders: List<Decoder>) {
    fun decoderFor(serviceUuid: String?, characteristicUuid: String?): Decoder =
        decoders.firstOrNull { it.matches(serviceUuid, characteristicUuid) } ?: GenericHexDecoder

    fun decode(payload: ByteArray, ctx: DecodeContext): DecodeResult =
        decoderFor(ctx.serviceUuid, ctx.characteristicUuid).decode(payload, ctx)
            ?: GenericHexDecoder.decode(payload, ctx)
}
