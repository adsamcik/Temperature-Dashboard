package com.adsamcik.temperaturedashboard.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val macAddress: String,
    val name: String?,
    val manufacturerId: Int?,
    val serviceUuid: String?,
    val lastSeen: Long
) {
    companion object {
        /**
         * Creates a Device instance from a BLE scan record.
         * @param macAddress The MAC address of the device.
         * @param scanRecord The raw scan record as a ByteArray.
         * @param currentTime The current timestamp for the `lastSeen` field.
         * @return A Device instance created from the scan record.
         */
        fun fromScanRecord(macAddress: String, scanRecord: ByteArray?, currentTime: Long): Device {
            // Example parsing logic
            val name = parseDeviceName(scanRecord)
            val manufacturerId = parseManufacturerId(scanRecord)
            val serviceUuid = parseServiceUuid(scanRecord)

            return Device(
                macAddress = macAddress,
                name = name,
                manufacturerId = manufacturerId,
                serviceUuid = serviceUuid,
                lastSeen = currentTime
            )
        }

        /**
         * Extracts the device name from the scan record.
         */
        private fun parseDeviceName(scanRecord: ByteArray?): String? {
            // Simulate parsing logic for the device name from the scan record
            // This depends on the BLE advertisement format
            return scanRecord?.let {
                // Example parsing logic for name
                String(it) // Replace with actual parsing logic
            }
        }

        /**
         * Extracts the manufacturer ID from the scan record.
         */
        private fun parseManufacturerId(scanRecord: ByteArray?): Int? {
            // Simulate parsing logic for manufacturer ID
            return scanRecord?.let {
                // Example: Assuming manufacturer data is in specific bytes
                if (it.size >= 2) (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) else null
            }
        }

        /**
         * Extracts the service UUID from the scan record.
         */
        private fun parseServiceUuid(scanRecord: ByteArray?): String? {
            // Simulate parsing logic for service UUID
            return scanRecord?.let {
                // Example: Extracting UUID from specific offset
                if (it.size > 16) it.sliceArray(9..16).joinToString("") { byte -> "%02x".format(byte) } else null
            }
        }
    }
}
