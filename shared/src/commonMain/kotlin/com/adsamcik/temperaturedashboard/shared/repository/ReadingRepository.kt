package com.adsamcik.temperaturedashboard.shared.repository

import com.adsamcik.temperaturedashboard.core.database.IntervalCoalescer
import com.adsamcik.temperaturedashboard.core.database.ReadingIntervalDao
import com.adsamcik.temperaturedashboard.core.database.toDomain
import com.adsamcik.temperaturedashboard.core.model.IntervalAggregator
import com.adsamcik.temperaturedashboard.core.model.IntervalStats
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.SensorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

/**
 * History storage layer. All writes flow through the [IntervalCoalescer] so
 * that what hits SQLite is run-length-encoded.
 *
 * Per-sensor serialisation: concurrent advertisements for the same sensor
 * must not interleave inside the coalescer (it reads-then-writes the latest
 * row). A per-sensor [Mutex] map enforces that without blocking unrelated
 * sensors.
 */
class ReadingRepository(
    private val dao: ReadingIntervalDao,
    private val coalescer: IntervalCoalescer,
) {

    private val locks = mutableMapOf<Long, Mutex>()
    private val locksGuard = Mutex()

    suspend fun ingest(sensorId: SensorId, reading: Reading): IntervalCoalescer.Outcome {
        val lock = lockFor(sensorId)
        return lock.withLock { coalescer.ingest(sensorId, reading) }
    }

    fun observeInRange(
        sensorId: SensorId,
        from: Instant,
        until: Instant,
    ): Flow<List<ReadingInterval>> =
        dao.observeInRange(
            sensorId = sensorId.raw,
            startMillis = from.toEpochMilliseconds(),
            endMillis = until.toEpochMilliseconds(),
        ).map { rows -> rows.map { it.toDomain() } }

    suspend fun findInRange(
        sensorId: SensorId,
        from: Instant,
        until: Instant,
    ): List<ReadingInterval> = dao.findInRange(
        sensorId = sensorId.raw,
        startMillis = from.toEpochMilliseconds(),
        endMillis = until.toEpochMilliseconds(),
    ).map { it.toDomain() }

    fun observeLatest(sensorId: SensorId): Flow<ReadingInterval?> =
        dao.observeLatest(sensorId.raw).map { it?.toDomain() }

    suspend fun statsInRange(sensorId: SensorId, from: Instant, until: Instant): IntervalStats =
        IntervalAggregator.aggregate(findInRange(sensorId, from, until), until - from)

    suspend fun deleteAllForSensor(sensorId: SensorId) {
        dao.deleteAllForSensor(sensorId.raw)
    }

    suspend fun countForSensor(sensorId: SensorId): Long =
        dao.countForSensor(sensorId.raw)

    private suspend fun lockFor(sensorId: SensorId): Mutex = locksGuard.withLock {
        locks.getOrPut(sensorId.raw) { Mutex() }
    }
}
