package com.adsamcik.temperaturedashboard.data

import com.adsamcik.temperaturedashboard.decoders.DecoderProvider
import com.adsamcik.temperaturedashboard.networking.BleDeviceHandler
import com.adsamcik.temperaturedashboard.storage.Device

data class ViewDevice(
    val device: Device,
    val decoder: BleDeviceHandler?,
    val liveTemperature: Double? = null
)

fun Device.toViewDevice(): ViewDevice {
    val decoder = DecoderProvider.getDecoderForDevice(this)
    return ViewDevice(
        device = this,
        decoder = decoder
    )
}

