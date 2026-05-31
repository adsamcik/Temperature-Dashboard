package com.adsamcik.temperaturedashboard.shared.export

import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.Sensor

/**
 * Saves a sensor's history to a user-visible location.
 *
 * - **Android**: writes via `MediaStore.Downloads` so the file appears in
 *   Files / Gallery without needing WRITE_EXTERNAL_STORAGE on Android 10+.
 * - **Desktop**: writes to `~/Documents/TemperatureDashboard/<filename>`.
 */
interface HistorySharer {
    suspend fun exportCsv(sensor: Sensor, intervals: List<ReadingInterval>): ExportResult
    suspend fun exportJson(sensor: Sensor, intervals: List<ReadingInterval>): ExportResult
}

sealed interface ExportResult {
    data class Saved(val location: String, val rowCount: Int) : ExportResult
    data class Failed(val message: String) : ExportResult
}
