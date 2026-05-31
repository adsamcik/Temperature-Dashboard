package com.adsamcik.temperaturedashboard.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

actual class DatabaseFactory(
    /** Defaults to `~/.temperature-dashboard/`. */
    private val baseDir: File = defaultBaseDir(),
) {
    actual fun create(): TemperatureDatabase {
        if (!baseDir.exists()) baseDir.mkdirs()
        val dbFile = File(baseDir, DATABASE_NAME)
        return Room.databaseBuilder<TemperatureDatabase>(
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    private companion object {
        const val DATABASE_NAME = "temperature.db"
        const val DATA_DIR = ".temperature-dashboard"

        fun defaultBaseDir(): File {
            val home = System.getProperty("user.home")
                ?: System.getenv("HOME")
                ?: System.getenv("USERPROFILE")
                ?: "."
            return File(home, DATA_DIR)
        }
    }
}
