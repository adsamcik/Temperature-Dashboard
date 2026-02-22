package com.adsamcik.temperaturedashboard.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert
    suspend fun insertReading(reading: TemperatureReading)

    @Insert
    suspend fun insertReadings(readings: List<TemperatureReading>)

    @Query("SELECT * FROM temperature_readings WHERE deviceMac = :mac ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReading(mac: String): TemperatureReading?

    @Query("SELECT * FROM temperature_readings WHERE deviceMac = :mac AND timestamp > :since ORDER BY timestamp ASC")
    fun getReadingsSince(mac: String, since: Long): Flow<List<TemperatureReading>>

    @Query("SELECT * FROM temperature_readings WHERE deviceMac = :mac ORDER BY timestamp DESC")
    fun getAllReadings(mac: String): Flow<List<TemperatureReading>>

    @Query("DELETE FROM temperature_readings WHERE deviceMac = :mac")
    suspend fun deleteReadingsForDevice(mac: String)
}
