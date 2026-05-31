package com.adsamcik.temperaturedashboard.decoder.builtins.profile

import com.adsamcik.temperaturedashboard.decoder.api.DecodeContext
import com.adsamcik.temperaturedashboard.decoder.api.DecodeResult
import com.adsamcik.temperaturedashboard.decoder.api.Decoder
import com.adsamcik.temperaturedashboard.decoder.api.UuidMatcher

/**
 * GATT-level decoder for the ThermoPro TP35x custom notify characteristic.
 * Delegates the actual byte-level decoding to [ThermoProTP35xProfile].
 */
internal object ThermoProTP35xNotifyDecoder : Decoder {
    override val id = "thermopro.tp35x.notify"
    override val displayName = "ThermoPro TP35x notification"

    private val notifyChar = ThermoProTP35xProfile.notifyCharacteristicUuid()

    override fun matches(serviceUuid: String?, characteristicUuid: String?): Boolean =
        UuidMatcher.matches(notifyChar, characteristicUuid)

    override fun decode(payload: ByteArray, context: DecodeContext): DecodeResult? {
        val fields = ThermoProTP35xProfile.decodeNotification(payload) ?: return null
        return DecodeResult(decoderId = id, fields = fields)
    }
}
