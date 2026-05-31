package com.adsamcik.temperaturedashboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alert: SensorAlertEntity): Long

    @Update
    suspend fun update(alert: SensorAlertEntity)

    @Query("SELECT * FROM sensor_alert WHERE sensor_id = :sensorId ORDER BY id ASC")
    fun observeForSensor(sensorId: Long): Flow<List<SensorAlertEntity>>

    @Query("SELECT * FROM sensor_alert WHERE sensor_id = :sensorId AND enabled = 1")
    suspend fun enabledForSensor(sensorId: Long): List<SensorAlertEntity>

    @Query("UPDATE sensor_alert SET last_fired_at = :timestamp WHERE id = :id")
    suspend fun markFired(id: Long, timestamp: Long)

    @Query("DELETE FROM sensor_alert WHERE id = :id")
    suspend fun delete(id: Long)
}
