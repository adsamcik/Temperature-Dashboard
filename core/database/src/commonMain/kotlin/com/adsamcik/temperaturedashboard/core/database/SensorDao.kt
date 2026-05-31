package com.adsamcik.temperaturedashboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sensor: SensorEntity): Long

    @Query("SELECT * FROM sensor WHERE address = :address LIMIT 1")
    suspend fun findByAddress(address: String): SensorEntity?

    @Query("SELECT * FROM sensor WHERE id = :id")
    suspend fun findById(id: Long): SensorEntity?

    @Query("SELECT * FROM sensor ORDER BY last_seen_at DESC, display_name ASC")
    fun observeAll(): Flow<List<SensorEntity>>

    @Query("SELECT * FROM sensor WHERE id = :id")
    fun observeById(id: Long): Flow<SensorEntity?>

    @Update
    suspend fun update(sensor: SensorEntity)

    @Query("UPDATE sensor SET last_seen_at = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    @Query("UPDATE sensor SET display_name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM sensor WHERE id = :id")
    suspend fun delete(id: Long)
}
