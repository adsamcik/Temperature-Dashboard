package com.adsamcik.temperaturedashboard.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val macAddress: String,
    val name: String?,
    val manufacturerId: Int?,
    val serviceUuid: String?,
    val lastSeen: Long
)
