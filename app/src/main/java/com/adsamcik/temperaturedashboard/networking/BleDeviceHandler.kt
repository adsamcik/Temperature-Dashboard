package com.adsamcik.temperaturedashboard.networking

import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.storage.Device
import java.util.UUID

interface BleDeviceHandler {
    val name: String
    val iconRes: Int?

    val readCharacteristicUuid: UUID
    val writeCharacteristicUuid: UUID

    /**
     * Returns true if this handler can decode data from the given device.
     */
    fun isCompatible(device: Device): Boolean

    /**
     * Returns the command (if any) to request data for the given mode.
     * If no command is needed, return null.
     */
    fun getRequestCommand(mode: ApiMode): ByteArray?

    /**
     * Given a connected device and a mode, this method:
     * 1. Writes the request command if available
     * 2. Enables notifications
     * 3. Collects all needed notifications from the device
     * 4. Decodes the packets into a list of TemperatureHumidityData
     */
    suspend fun retrieveData(connectedDevice: ConnectedBleDevice, mode: ApiMode): List<TemperatureHumidityData>
}
