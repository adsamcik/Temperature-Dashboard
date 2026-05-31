package com.adsamcik.temperaturedashboard.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        SensorEntity::class,
        ReadingIntervalEntity::class,
        SensorAlertEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 1, to = 2),
        androidx.room.AutoMigration(from = 2, to = 3),
    ],
)
@ConstructedBy(TemperatureDatabaseConstructor::class)
abstract class TemperatureDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao
    abstract fun readingIntervalDao(): ReadingIntervalDao
    abstract fun sensorAlertDao(): SensorAlertDao
}

/**
 * Room 2.7+ KMP requires an `expect` constructor object; Room KSP generates
 * the `actual` implementation for each platform target.
 */
expect object TemperatureDatabaseConstructor : RoomDatabaseConstructor<TemperatureDatabase> {
    override fun initialize(): TemperatureDatabase
}
