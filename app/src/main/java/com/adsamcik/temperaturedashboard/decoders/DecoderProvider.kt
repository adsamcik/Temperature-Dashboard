package com.adsamcik.temperaturedashboard.decoders

import com.adsamcik.temperaturedashboard.networking.BleDeviceHandler
import com.adsamcik.temperaturedashboard.networking.ThermoProTP357Handler
import com.adsamcik.temperaturedashboard.storage.Device

/**
 * Provides decoders for known BLE devices.
 */
object DecoderProvider {
    private val decoders = mutableListOf<BleDeviceHandler>(
        ThermoProTP357Handler()
    )

    fun getDecoderForDevice(device: Device): BleDeviceHandler? {
        return decoders.firstOrNull { it.isCompatible(device) }
    }
}