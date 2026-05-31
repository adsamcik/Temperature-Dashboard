package com.adsamcik.temperaturedashboard.shared.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.Sensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.OutputStreamWriter

/**
 * Android impl: drops the exported file into the system Downloads folder via
 * MediaStore. No runtime storage permission required on Android 10+.
 */
class AndroidHistorySharer(private val context: Context) : HistorySharer {

    override suspend fun exportCsv(sensor: Sensor, intervals: List<ReadingInterval>): ExportResult =
        write(sensor, intervals, extension = "csv", mime = "text/csv") { out ->
            HistoryExporter.writeCsv(intervals, out)
        }

    override suspend fun exportJson(sensor: Sensor, intervals: List<ReadingInterval>): ExportResult =
        write(sensor, intervals, extension = "json", mime = "application/json") { out ->
            HistoryExporter.writeJson(intervals, out)
        }

    private suspend fun write(
        sensor: Sensor,
        intervals: List<ReadingInterval>,
        extension: String,
        mime: String,
        block: (Appendable) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = safeFilename(sensor.displayName)
            val timestamp = Clock.System.now().toString().replace(':', '-').take(19)
            val filename = "tdash-$safeName-$timestamp.$extension"

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Files.getContentUri("external")
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val uri = resolver.insert(collection, values)
                ?: return@runCatching ExportResult.Failed("MediaStore rejected the insert.")

            resolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use(block)
            } ?: return@runCatching ExportResult.Failed("Couldn't open output stream.")

            ExportResult.Saved(
                location = "Downloads / $filename",
                rowCount = intervals.size,
            )
        }.getOrElse { ExportResult.Failed(it.message ?: "Unknown export failure") }
    }

    private fun safeFilename(name: String): String =
        name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "sensor" }
}
