package com.adsamcik.temperaturedashboard.networking

import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.storage.Device
import java.util.UUID

/**
 * A public API interface for handling communication with BLE devices.
 * Implementations provide device-specific logic and data format.
 */
interface BleDeviceHandler {
    val name: String

    val iconRes: Int?


    /**
     * Checks if the provided [Device] is compatible.
     */
    fun isCompatible(device: Device): Boolean

    /**
     * Returns the UUID of the read characteristic for this device type.
     */
    fun getReadCharacteristicUuid(): UUID

    /**
     * Returns the UUID of the write characteristic for this device type.
     */
    fun getWriteCharacteristicUuid(): UUID

    /**
     * Provides the command (as ByteArray) that should be sent to the device
     * to request data.
     */
    fun getRequestCommand(): ByteArray

    /**
     * Decodes the provided raw data into a [TemperatureHumidityData].
     * Returns null if decoding fails.
     */
    fun decodeData(rawData: ByteArray): TemperatureHumidityData?
}