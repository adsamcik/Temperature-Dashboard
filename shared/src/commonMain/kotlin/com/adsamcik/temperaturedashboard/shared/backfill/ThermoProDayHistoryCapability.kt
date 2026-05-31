package com.adsamcik.temperaturedashboard.shared.backfill

import com.adsamcik.temperaturedashboard.ble.api.BleConnection
import com.adsamcik.temperaturedashboard.ble.api.ConnectionResult
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.ReadingSource
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.profile.ThermoProTP35xProfile
import com.adsamcik.temperaturedashboard.shared.repository.ReadingRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * ThermoPro TP35x day-history backfill (~24 hours, 1 sample per minute).
 *
 * ## How the protocol works
 * Opcode `A7 01 00 7A` requests the day buffer. The device responds with
 * a stream of notification packets, each carrying:
 *
 *   byte 0    = opcode (0xA7)
 *   byte 1    = page index (0..N-1)
 *   bytes 2-3 = reserved
 *   bytes 4.. = M samples × 3 bytes each (temp_lo, temp_hi, humidity)
 *   last byte = checksum
 *
 * The pages aren't guaranteed to arrive in order, so we collect every page
 * the device sends in a 6 second window, group by page index, and re-assemble
 * a flat sample list.
 *
 * ## Timestamps
 * The protocol doesn't carry absolute timestamps. We anchor the *last* sample
 * to "now" and walk backwards by exactly `24h / sampleCount` per sample. This
 * is true to the protocol's promise (the device buffers ~24h at uniform
 * spacing) and gives properly-spaced points that line up with the next live
 * advertisement.
 */
object ThermoProDayHistoryCapability : BackfillCapability {
    override val supportedProfilePrefix: String = "thermopro.tp"
    override val displayLabel: String = "Sync 24h history from device"

    private const val NOTIFY_SERVICE = "00010203-0405-0607-0809-0a0b0c0d1910"
    private const val NOTIFY_CHAR = "00010203-0405-0607-0809-0a0b0c0d2b10"
    private const val WRITE_CHAR = "00010203-0405-0607-0809-0a0b0c0d2b11"
    private val DAY_OPCODE = byteArrayOf(0xA7.toByte(), 0x01, 0x00, 0x7A)
    private const val COLLECTION_WINDOW_SECONDS = 6L
    private const val EXPECTED_TOTAL_PAGE_CAP = 300

    override suspend fun performBackfill(
        sensor: Sensor,
        connection: BleConnection,
        readingRepository: ReadingRepository,
    ): BackfillResult {
        val notifications = connection.observeNotifications(NOTIFY_SERVICE, NOTIFY_CHAR)

        val writeResult = connection.writeCharacteristic(
            serviceUuid = NOTIFY_SERVICE,
            characteristicUuid = WRITE_CHAR,
            payload = DAY_OPCODE,
            withResponse = true,
        )
        if (writeResult is ConnectionResult.Failed) {
            return BackfillResult.Failed("Opcode write failed: ${writeResult.reason}")
        }

        val pages = withTimeoutOrNull(COLLECTION_WINDOW_SECONDS.seconds) {
            notifications.take(EXPECTED_TOTAL_PAGE_CAP).toList()
        }.orEmpty()

        if (pages.isEmpty()) return BackfillResult.Failed("No history pages received")

        // Decode each page, capturing its index for ordering.
        val pageSamples: List<PageSamples> = pages.mapNotNull { decodePage(it) }
            .sortedBy { it.pageIndex }

        if (pageSamples.isEmpty()) {
            return BackfillResult.Failed("Pages received but none decodable")
        }

        // Flatten into one ordered sample list with proper timestamps.
        val samples: List<Reading> = synthesiseTimestamps(pageSamples)
        if (samples.isEmpty()) return BackfillResult.Failed("No samples after decoding")

        samples.forEach { readingRepository.ingest(sensor.id, it) }
        Napier.i(
            "ThermoPro backfill ingested ${samples.size} samples (${pageSamples.size} pages)",
            tag = LOG_TAG,
        )
        return BackfillResult.Ok(samples.size)
    }

    private fun decodePage(packet: ByteArray): PageSamples? {
        if (packet.size < 6) return null
        val pageIndex = packet[1].toInt() and 0xFF
        val fields = ThermoProTP35xProfile.parseActionResponse("tp35x.history.day", packet)
        val pairs = readingPairs(fields)
        if (pairs.isEmpty()) return null
        return PageSamples(pageIndex, pairs)
    }

    private fun readingPairs(fields: List<DecodedField>): List<TempHum> {
        val pairs = mutableListOf<TempHum>()
        var pendingTemp: Double? = null
        for (f in fields.filter { it.name.startsWith("T") || it.name.startsWith("H") }) {
            when {
                f.name.startsWith("T") -> pendingTemp = (f.value as? DecodedValue.FloatValue)?.v
                f.name.startsWith("H") -> {
                    val t = pendingTemp
                    val h = (f.value as? DecodedValue.IntValue)?.v?.toDouble()
                    if (t != null && h != null) pairs.add(TempHum(t, h))
                    pendingTemp = null
                }
            }
        }
        return pairs
    }

    /**
     * Anchor the most recent sample to `now` and distribute the rest evenly
     * across the device's 24h buffer.
     *
     * - If we got the full ~1440 samples, spacing is exactly 60 s.
     * - If we got partial data (some pages dropped), spacing is still
     *   `24h / sampleCount` so the visible coverage spans the whole day
     *   without being squashed.
     */
    private fun synthesiseTimestamps(pages: List<PageSamples>): List<Reading> {
        val flat = pages.flatMap { it.samples }
        if (flat.isEmpty()) return emptyList()

        val now = Clock.System.now()
        val totalSpan = 24.hours
        val spacing = totalSpan / flat.size

        return flat.mapIndexed { idx, pair ->
            val offset = spacing * (flat.size - 1 - idx)
            Reading(
                temperatureC = pair.temperatureC,
                humidityPct = pair.humidityPct,
                batteryPct = null,
                rssi = null,
                timestamp = now - offset,
                source = ReadingSource.BACKFILL,
            )
        }
    }

    private data class PageSamples(val pageIndex: Int, val samples: List<TempHum>)
    private data class TempHum(val temperatureC: Double, val humidityPct: Double)

    private const val LOG_TAG = "ThermoProBackfill"
}
