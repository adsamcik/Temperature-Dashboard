package com.adsamcik.temperaturedashboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One user-defined alert rule for a sensor — see
 * [com.adsamcik.temperaturedashboard.core.model.SensorAlert].
 */
@Entity(
    tableName = "sensor_alert",
    foreignKeys = [
        ForeignKey(
            entity = SensorEntity::class,
            parentColumns = ["id"],
            childColumns = ["sensor_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sensor_id"])],
)
data class SensorAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sensor_id")
    val sensorId: Long,
    /** `AlertKind.storageKey` — TEMP_ABOVE, TEMP_BELOW, HUM_ABOVE, HUM_BELOW, BATT_BELOW. */
    val kind: String,
    val threshold: Double,
    val enabled: Boolean = true,
    @ColumnInfo(name = "cooldown_minutes")
    val cooldownMinutes: Int = 30,
    /** Epoch millis at which this alert most recently fired; null if never. */
    @ColumnInfo(name = "last_fired_at")
    val lastFiredAt: Long? = null,
)
