package com.adsamcik.temperaturedashboard.shared.backfill

import com.adsamcik.temperaturedashboard.ble.api.BleConnector
import com.adsamcik.temperaturedashboard.ble.api.ConnectionResult
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.ReadingSource
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.decoder.api.DecodedField
import com.adsamcik.temperaturedashboard.decoder.api.DecodedValue
import com.adsamcik.temperaturedashboard.decoder.builtins.profile.ThermoProTP35xProfile
import com.adsamcik.temperaturedashboard.shared.repository.ReadingRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ThermoProBackfillService(
    private val connector: BleConnector,
    private val readingRepository: ReadingRepository,
) {
    suspend fun syncDayHistory(sensor: Sensor): BackfillResult {
        if (!sensor.profileId.startsWith("thermopro.tp")) {
            return BackfillResult.Skipped("Unsupported profile ${sensor.profileId}")
        }
        return runCatching {
            val connection = (connector.connect(sensor.address.raw) as ConnectionResult.Connected).connection
            try {
                val notifyService = "00010203-0405-0607-0809-0a0b0c0d1910"
                val notifyChar = "00010203-0405-0607-0809-0a0b0c0d2b10"
                val writeChar = "00010203-0405-0607-0809-0a0b0c0d2b11"
                val dayOpcode = byteArrayOf(0xA7.toByte(), 0x01, 0x00, 0x7A)

                val notifications = connection.observeNotifications(notifyService, notifyChar)

                val writeResult = connection.writeCharacteristic(
                    serviceUuid = notifyService,
                    characteristicUuid = writeChar,
                    payload = dayOpcode,
                    withResponse = true,
                )
                if (writeResult is ConnectionResult.Failed) {
                    return BackfillResult.Failed("Write failed: ${writeResult.reason}")
                }

                val pages = withTimeoutOrNull(6.seconds) {
                    notifications.take(60).toList()
                }.orEmpty()

                if (pages.isEmpty()) {
                    return BackfillResult.Failed("No history pages received")
                }

                val samples: List<Reading> = pages
                    .flatMap { ThermoProTP35xProfile.parseActionResponse("tp35x.history.day", it) }
                    .let(::readingsFromHistoryFields)

                if (samples.isEmpty()) return BackfillResult.Failed("No decodable samples")

                samples.forEach { readingRepository.ingest(sensor.id, it) }
                BackfillResult.Ok(samples.size)
            } finally {
                runCatching { connection.close() }
                    .onFailure { Napier.w("backfill close failed: ${it.message}", tag = LOG_TAG) }
            }
        }.getOrElse {
            Napier.w("ThermoPro backfill failed: ${it.message}", tag = LOG_TAG)
            BackfillResult.Failed(it.message ?: "Unknown failure")
        }
    }

    private fun readingsFromHistoryFields(fields: List<DecodedField>): List<Reading> {
        val pairs = mutableListOf<Pair<Double, Double>>()
        var pendingTemp: Double? = null
        for (f in fields.filter { it.name.startsWith("T") || it.name.startsWith("H") }) {
            when {
                f.name.startsWith("T") -> pendingTemp = (f.value as? DecodedValue.FloatValue)?.v
                f.name.startsWith("H") -> {
                    val t = pendingTemp
                    val h = (f.value as? DecodedValue.IntValue)?.v?.toDouble()
                    if (t != null && h != null) pairs.add(t to h)
                    pendingTemp = null
                }
            }
        }
        if (pairs.isEmpty()) return emptyList()

        val now = Clock.System.now()
        return pairs.mapIndexed { idx, (t, h) ->
            val tsOffset = (pairs.size - 1 - idx).minutes
            Reading(
                temperatureC = t,
                humidityPct = h,
                batteryPct = null,
                rssi = null,
                timestamp = now - tsOffset,
                source = ReadingSource.BACKFILL,
            )
        }
    }

    private companion object { const val LOG_TAG = "ThermoProBackfill" }
}

sealed interface BackfillResult {
    data class Ok(val readingsIngested: Int) : BackfillResult
    data class Skipped(val reason: String) : BackfillResult
    data class Failed(val message: String) : BackfillResult
}
