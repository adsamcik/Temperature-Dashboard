package com.adsamcik.temperaturedashboard.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        SensorEntity::class,
        ReadingIntervalEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(TemperatureDatabaseConstructor::class)
abstract class TemperatureDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao
    abstract fun readingIntervalDao(): ReadingIntervalDao
}

/**
 * Room 2.7+ KMP requires an `expect` constructor object; Room KSP generates
 * the `actual` implementation for each platform target.
 */
expect object TemperatureDatabaseConstructor : RoomDatabaseConstructor<TemperatureDatabase> {
    override fun initialize(): TemperatureDatabase
}
