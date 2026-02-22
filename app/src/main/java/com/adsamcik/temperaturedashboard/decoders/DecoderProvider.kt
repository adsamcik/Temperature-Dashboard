package com.adsamcik.temperaturedashboard.decoders

import com.adsamcik.temperaturedashboard.networking.BleDeviceHandler
import com.adsamcik.temperaturedashboard.networking.Tp357BleClient
import com.adsamcik.temperaturedashboard.storage.Device

/**
 * Provides decoders for known BLE devices.
 */
object DecoderProvider {
    private val decoders = mutableListOf<BleDeviceHandler>(
        Tp357BleClient()
    )

    fun getDecoderForDevice(device: Device): BleDeviceHandler? {
        // Try to find a decoder that's compatible with this device
        return decoders.firstOrNull { decoder -> 
            decoder.isCompatible(device)
        }
    }
}