package com.adsamcik.temperaturedashboard.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRepository(private val deviceDao: DeviceDao) {

    suspend fun getAllDevices(): List<Device> = withContext(Dispatchers.IO) {
        deviceDao.getAllDevices()
    }

    suspend fun addDevice(device: Device) = withContext(Dispatchers.IO) {
        deviceDao.insertDevice(device)
    }

    suspend fun deleteDevice(device: Device) = withContext(Dispatchers.IO) {
        deviceDao.deleteDevice(device)
    }

    suspend fun deleteDeviceByMac(macAddress: String) = withContext(Dispatchers.IO) {
        deviceDao.deleteDeviceByMac(macAddress)
    }
}
