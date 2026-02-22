package com.adsamcik.temperaturedashboard.data

import com.adsamcik.temperaturedashboard.networking.DeviceDiscoveryManager.DiscoveredDevice
import com.adsamcik.temperaturedashboard.storage.Device


fun DiscoveredDevice.toDevice(): Device {
    return Device(
        macAddress = address,
        name = name,
        manufacturerId = manufacturerId,
        serviceUuid = serviceUuid,
        lastSeen = System.currentTimeMillis()
    )
}
