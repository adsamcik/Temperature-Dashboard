package com.adsamcik.temperaturedashboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent representation of a [com.adsamcik.temperaturedashboard.core.model.ReadingInterval]
 * — one row per run of equal-ish values.
 *
 * The `(sensor_id, valid_from)` and `(sensor_id, valid_until)` composite indices
 * make every time-range chart query a clean index range scan.
 */
@Entity(
    tableName = "reading_interval",
    foreignKeys = [
        ForeignKey(
            entity = SensorEntity::class,
            parentColumns = ["id"],
            childColumns = ["sensor_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sensor_id", "valid_from"]),
        Index(value = ["sensor_id", "valid_until"]),
    ],
)
data class ReadingIntervalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sensor_id")
    val sensorId: Long,
    @ColumnInfo(name = "temperature_c")
    val temperatureC: Double?,
    @ColumnInfo(name = "humidity_pct")
    val humidityPct: Double?,
    @ColumnInfo(name = "battery_pct")
    val batteryPct: Int?,
    @ColumnInfo(name = "rssi_avg")
    val rssiAvg: Int?,
    /** Epoch millis at which the run starts (first sample). */
    @ColumnInfo(name = "valid_from")
    val validFrom: Long,
    /** Epoch millis at which the run ends (most recent confirming sample). */
    @ColumnInfo(name = "valid_until")
    val validUntil: Long,
    /** How many physical readings coalesced into this interval. */
    @ColumnInfo(name = "sample_count")
    val sampleCount: Int,
    /** `ReadingSource.name` — advertisement / gatt / backfill. */
    val source: String,
)
