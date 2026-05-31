package com.adsamcik.temperaturedashboard.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

actual class DatabaseFactory(private val context: Context) {
    actual fun create(): TemperatureDatabase {
        val dbPath = context.getDatabasePath(DATABASE_NAME).absolutePath
        return Room.databaseBuilder<TemperatureDatabase>(
            context = context.applicationContext,
            name = dbPath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    private companion object {
        const val DATABASE_NAME = "temperature.db"
    }
}
