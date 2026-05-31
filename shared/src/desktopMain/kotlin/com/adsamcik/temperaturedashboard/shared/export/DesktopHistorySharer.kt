package com.adsamcik.temperaturedashboard.shared.export

import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.Sensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File

class DesktopHistorySharer : HistorySharer {

    override suspend fun exportCsv(sensor: Sensor, intervals: List<ReadingInterval>): ExportResult =
        write(sensor, intervals, extension = "csv") { out ->
            HistoryExporter.writeCsv(intervals, out)
        }

    override suspend fun exportJson(sensor: Sensor, intervals: List<ReadingInterval>): ExportResult =
        write(sensor, intervals, extension = "json") { out ->
            HistoryExporter.writeJson(intervals, out)
        }

    private suspend fun write(
        sensor: Sensor,
        intervals: List<ReadingInterval>,
        extension: String,
        block: (Appendable) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val home = System.getProperty("user.home") ?: error("user.home unavailable")
            val dir = File(home, "Documents/TemperatureDashboard").apply { mkdirs() }

            val safeName = sensor.displayName.lowercase()
                .map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("")
                .trim('-')
                .ifBlank { "sensor" }
            val timestamp = Clock.System.now().toString().replace(':', '-').take(19)
            val file = File(dir, "tdash-$safeName-$timestamp.$extension")

            file.bufferedWriter(Charsets.UTF_8).use(block)
            ExportResult.Saved(location = file.absolutePath, rowCount = intervals.size)
        }.getOrElse { ExportResult.Failed(it.message ?: "Unknown export failure") }
    }
}
