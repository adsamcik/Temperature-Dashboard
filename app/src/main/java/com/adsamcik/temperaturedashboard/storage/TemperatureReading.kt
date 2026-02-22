package com.adsamcik.temperaturedashboard.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "temperature_readings",
    foreignKeys = [ForeignKey(
        entity = Device::class,
        parentColumns = ["macAddress"],
        childColumns = ["deviceMac"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceMac"), Index("timestamp")]
)
data class TemperatureReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val temperature: Double,
    val humidity: Double,
    val timestamp: Long = System.currentTimeMillis()
)
