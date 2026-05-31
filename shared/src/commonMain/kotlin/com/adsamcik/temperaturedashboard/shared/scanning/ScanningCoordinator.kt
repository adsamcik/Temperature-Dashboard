package com.adsamcik.temperaturedashboard.shared.scanning

import com.adsamcik.temperaturedashboard.ble.api.BleAdvertisement
import com.adsamcik.temperaturedashboard.ble.api.BleScanner
import com.adsamcik.temperaturedashboard.ble.api.ScanStartResult
import com.adsamcik.temperaturedashboard.core.model.SensorAddress
import com.adsamcik.temperaturedashboard.shared.alerts.AlertEvaluator
import com.adsamcik.temperaturedashboard.shared.repository.ReadingRepository
import com.adsamcik.temperaturedashboard.shared.repository.SensorRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class ScanningCoordinator(
    private val scanner: BleScanner,
    private val interpreter: AdvertisementInterpreter,
    private val sensorRepository: SensorRepository,
    private val readingRepository: ReadingRepository,
    private val alertEvaluator: AlertEvaluator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {

    val state get() = scanner.state

    private val _discoveries = MutableSharedFlow<InterpretedAdvertisement>(
        replay = 0,
        extraBufferCapacity = DISCOVERY_BUFFER,
    )
    val discoveries: SharedFlow<InterpretedAdvertisement> = _discoveries.asSharedFlow()

    private var pumpJob: Job? = null

    suspend fun start(): ScanStartResult {
        val result = scanner.start()
        if (result is ScanStartResult.Started) {
            pumpJob?.cancel()
            pumpJob = scope.launch { collectAdvertisements(scanner.advertisements()) }
        }
        return result
    }

    suspend fun stop() {
        pumpJob?.cancel()
        pumpJob = null
        scanner.stop()
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun collectAdvertisements(stream: Flow<BleAdvertisement>) {
        stream.collect { advert ->
            try {
                process(advert)
            } catch (ex: Throwable) {
                Napier.w("ScanningCoordinator: dropped advertisement: ${ex.message}", tag = LOG_TAG)
            }
        }
    }

    private suspend fun process(advert: BleAdvertisement) {
        val interpreted = interpreter.interpret(advert) ?: return
        val sensorAddress = SensorAddress.normalize(advert.address)
        val existing = sensorRepository.findByAddress(sensorAddress)
        if (existing != null) {
            sensorRepository.touch(existing.id)
            val reading = interpreted.reading ?: return
            readingRepository.ingest(existing.id, reading)
            // Fan out to alert evaluator after persistence so notifications
            // only fire on genuine, persisted readings.
            alertEvaluator.evaluate(existing, reading)
        } else {
            _discoveries.tryEmit(interpreted)
        }
    }

    internal suspend fun processAdvertisementForTest(advert: BleAdvertisement) = process(advert)

    private companion object {
        const val LOG_TAG = "ScanningCoordinator"
        const val DISCOVERY_BUFFER = 64
    }
}
