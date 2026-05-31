package com.adsamcik.temperaturedashboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingIntervalDao {
    @Insert
    suspend fun insert(interval: ReadingIntervalEntity): Long

    /** Most recent interval for a sensor — the one a coalescer may want to extend. */
    @Query(
        """
        SELECT * FROM reading_interval
        WHERE sensor_id = :sensorId
        ORDER BY valid_from DESC
        LIMIT 1
        """,
    )
    suspend fun findLatest(sensorId: Long): ReadingIntervalEntity?

    /**
     * Extend an existing interval in-place by pushing its `valid_until`
     * forward and folding the new sample's contribution into the running
     * counters.
     */
    @Query(
        """
        UPDATE reading_interval
           SET valid_until  = :newValidUntil,
               sample_count = :newSampleCount,
               rssi_avg     = :newRssiAvg,
               battery_pct  = :newBatteryPct
         WHERE id = :id
        """,
    )
    suspend fun extend(
        id: Long,
        newValidUntil: Long,
        newSampleCount: Int,
        newRssiAvg: Int?,
        newBatteryPct: Int?,
    )

    /** All intervals that *overlap* the half-open `[startMillis, endMillis)` window. */
    @Query(
        """
        SELECT * FROM reading_interval
         WHERE sensor_id = :sensorId
           AND valid_until >= :startMillis
           AND valid_from  <= :endMillis
         ORDER BY valid_from ASC
        """,
    )
    fun observeInRange(sensorId: Long, startMillis: Long, endMillis: Long): Flow<List<ReadingIntervalEntity>>

    @Query(
        """
        SELECT * FROM reading_interval
         WHERE sensor_id = :sensorId
           AND valid_until >= :startMillis
           AND valid_from  <= :endMillis
         ORDER BY valid_from ASC
        """,
    )
    suspend fun findInRange(sensorId: Long, startMillis: Long, endMillis: Long): List<ReadingIntervalEntity>

    /** Most recent interval, observed reactively for the dashboard. */
    @Query(
        """
        SELECT * FROM reading_interval
         WHERE sensor_id = :sensorId
         ORDER BY valid_from DESC
         LIMIT 1
        """,
    )
    fun observeLatest(sensorId: Long): Flow<ReadingIntervalEntity?>

    @Query("SELECT COUNT(*) FROM reading_interval WHERE sensor_id = :sensorId")
    suspend fun countForSensor(sensorId: Long): Long

    @Query("DELETE FROM reading_interval WHERE sensor_id = :sensorId")
    suspend fun deleteAllForSensor(sensorId: Long)
}
