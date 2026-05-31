package com.adsamcik.temperaturedashboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent representation of a [com.adsamcik.temperaturedashboard.core.model.Sensor].
 */
@Entity(
    tableName = "sensor",
    indices = [Index(value = ["address"], unique = true)],
)
data class SensorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Normalized BLE address — see [com.adsamcik.temperaturedashboard.core.model.SensorAddress]. */
    val address: String,
    /** Decoder profile id (`thermopro.tp35x`, `bthome.v2`, ...). */
    @ColumnInfo(name = "profile_id")
    val profileId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "model_hint")
    val modelHint: String? = null,
    /** Epoch millis. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** Epoch millis; null until the first successful reading lands. */
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long? = null,
    @ColumnInfo(name = "color_seed")
    val colorSeed: Int,
)
