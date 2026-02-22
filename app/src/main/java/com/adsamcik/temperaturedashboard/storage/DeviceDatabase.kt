package com.adsamcik.temperaturedashboard.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Device::class, TemperatureReading::class],
    version = 2,
    exportSchema = false
)
abstract class DeviceDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun readingDao(): ReadingDao

    companion object {
        @Volatile
        private var INSTANCE: DeviceDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `temperature_readings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `deviceMac` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `humidity` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`deviceMac`) REFERENCES `devices`(`macAddress`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_temperature_readings_deviceMac` ON `temperature_readings` (`deviceMac`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_temperature_readings_timestamp` ON `temperature_readings` (`timestamp`)")
            }
        }

        fun getDatabase(context: Context): DeviceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeviceDatabase::class.java,
                    "device_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
