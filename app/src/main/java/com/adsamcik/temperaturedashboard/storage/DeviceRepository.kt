package com.adsamcik.temperaturedashboard.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRepository(
    private val deviceDao: DeviceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getAllDevices(): List<Device> = withContext(ioDispatcher) {
        deviceDao.getAllDevices()
    }

    suspend fun addDevice(device: Device) = withContext(ioDispatcher) {
        deviceDao.insertDevice(device)
    }

    suspend fun deleteDevice(device: Device) = withContext(ioDispatcher) {
        deviceDao.deleteDevice(device)
    }

    suspend fun deleteDeviceByMac(macAddress: String) = withContext(ioDispatcher) {
        deviceDao.deleteDeviceByMac(macAddress)
    }

    suspend fun getDeviceByMac(macAddress: String): Device? = withContext(ioDispatcher) {
        deviceDao.getDeviceByMac(macAddress)
    }

    suspend fun deleteDeviceAndReadings(macAddress: String) = withContext(ioDispatcher) {
        deviceDao.deleteDeviceByMac(macAddress)
    }
}
