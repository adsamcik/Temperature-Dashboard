package com.adsamcik.temperaturedashboard.storage

import androidx.room.*

@Dao
interface DeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Query("SELECT * FROM devices WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getDeviceByMac(macAddress: String): Device?

    @Query("SELECT * FROM devices")
    suspend fun getAllDevices(): List<Device>

    @Delete
    suspend fun deleteDevice(device: Device)

    @Query("DELETE FROM devices WHERE macAddress = :macAddress")
    suspend fun deleteDeviceByMac(macAddress: String)
}
