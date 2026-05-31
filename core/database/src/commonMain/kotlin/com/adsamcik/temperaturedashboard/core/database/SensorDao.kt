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

    /**
     * Insertion order (oldest first), filtered to non-hidden sensors —
     * deliberately stable so cards never swap positions when a different
     * sensor's advertisement lands.
     */
    @Query("SELECT * FROM sensor WHERE hidden = 0 ORDER BY id ASC")
    fun observeAll(): Flow<List<SensorEntity>>

    /** Includes hidden sensors — used by Settings → Hidden sensors. */
    @Query("SELECT * FROM sensor ORDER BY hidden ASC, id ASC")
    fun observeAllIncludingHidden(): Flow<List<SensorEntity>>

    @Query("UPDATE sensor SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE sensor SET color_seed = :colorArgb WHERE id = :id")
    suspend fun setColor(id: Long, colorArgb: Int)

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
