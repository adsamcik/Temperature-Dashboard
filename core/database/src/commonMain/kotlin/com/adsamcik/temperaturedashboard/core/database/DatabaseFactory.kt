package com.adsamcik.temperaturedashboard.core.database

/**
 * Platform-specific factory for [TemperatureDatabase]. Each platform binds the
 * SQLite driver and database file location it needs:
 *
 * - **Android** binds to `Context.getDatabasePath("temperature.db")` and uses
 *   the bundled SQLite driver for KMP consistency.
 * - **Desktop** writes to `~/.temperature-dashboard/temperature.db` and uses
 *   the bundled SQLite driver (native binary shipped per OS by androidx-sqlite).
 *
 * `actual class DatabaseFactory(...)` may have a different constructor signature
 * per platform — Android takes a `Context`, Desktop takes nothing.
 */
expect class DatabaseFactory {
    fun create(): TemperatureDatabase
}
