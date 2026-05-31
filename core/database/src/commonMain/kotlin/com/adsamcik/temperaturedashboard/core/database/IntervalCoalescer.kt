package com.adsamcik.temperaturedashboard.core.database

import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.SensorId
import kotlin.math.abs

/**
 * Run-length encoder for sensor readings.
 *
 * Each call to [ingest] feeds one [Reading] from a sensor and either:
 * - **extends** the latest interval in-place by pushing its `valid_until`
 *   forward and folding the new sample's counters in; or
 * - **inserts** a brand-new interval when the latest is too stale, too long,
 *   has drifted out of threshold, or doesn't exist yet.
 *
 * The freezing rules are baked into [CoalescingPolicy] — see the policy doc
 * for the meaning of each threshold and why each one closes an interval.
 *
 * Threading: this class is *not* thread-safe — call it from a single coroutine
 * per sensor. The repository serialises calls per sensor with a `Mutex` map.
 */
class IntervalCoalescer(
    private val dao: ReadingIntervalDao,
    private val policy: CoalescingPolicy = CoalescingPolicy.Default,
) {

    /** Outcome of an [ingest] call. The id is the row that was touched. */
    sealed interface Outcome {
        val intervalId: Long

        /** A new interval row was inserted with this id. */
        data class Inserted(override val intervalId: Long, val reason: Reason) : Outcome

        /** The existing latest interval was extended in-place. */
        data class Extended(override val intervalId: Long) : Outcome

        /** The reading was rejected (empty, or out of order). */
        data class Rejected(val cause: String) : Outcome {
            override val intervalId: Long get() = -1L
        }
    }

    enum class Reason {
        FIRST_INTERVAL,
        STALE_PRIOR,
        MAX_DURATION_REACHED,
        OUT_OF_THRESHOLD,
    }

    suspend fun ingest(sensorId: SensorId, reading: Reading): Outcome {
        if (reading.isEmpty) return Outcome.Rejected("empty reading")

        val nowMillis = reading.timestamp.toEpochMilliseconds()
        val latest = dao.findLatest(sensorId.raw)

        if (latest == null) {
            return Outcome.Inserted(
                intervalId = dao.insert(reading.toNewInterval(sensorId, nowMillis)),
                reason = Reason.FIRST_INTERVAL,
            )
        }

        if (nowMillis < latest.validUntil) {
            // Out-of-order reading older than what we already know — drop it.
            // Real coverage of late-arriving samples comes via backfill paths.
            return Outcome.Rejected("reading older than latest interval")
        }

        val gapMs = nowMillis - latest.validUntil
        if (gapMs > policy.staleWindow.inWholeMilliseconds) {
            return Outcome.Inserted(
                intervalId = dao.insert(reading.toNewInterval(sensorId, nowMillis)),
                reason = Reason.STALE_PRIOR,
            )
        }

        val ageMs = nowMillis - latest.validFrom
        if (ageMs >= policy.maxIntervalDuration.inWholeMilliseconds) {
            return Outcome.Inserted(
                intervalId = dao.insert(reading.toNewInterval(sensorId, nowMillis)),
                reason = Reason.MAX_DURATION_REACHED,
            )
        }

        if (!withinThresholds(latest, reading)) {
            return Outcome.Inserted(
                intervalId = dao.insert(reading.toNewInterval(sensorId, nowMillis)),
                reason = Reason.OUT_OF_THRESHOLD,
            )
        }

        val newCount = latest.sampleCount + 1
        val newRssi = combineRssi(latest.rssiAvg, latest.sampleCount, reading.rssi)
        val newBattery = reading.batteryPct ?: latest.batteryPct
        dao.extend(
            id = latest.id,
            newValidUntil = nowMillis,
            newSampleCount = newCount,
            newRssiAvg = newRssi,
            newBatteryPct = newBattery,
        )
        return Outcome.Extended(latest.id)
    }

    private fun withinThresholds(latest: ReadingIntervalEntity, reading: Reading): Boolean {
        val tempOk = compareWithinThreshold(
            latest.temperatureC,
            reading.temperatureC,
            policy.temperatureThresholdC,
        )
        val humOk = compareWithinThreshold(
            latest.humidityPct,
            reading.humidityPct,
            policy.humidityThresholdPct,
        )
        return tempOk && humOk
    }

    /**
     * - both null → considered identical (nothing to compare)
     * - one null  → drifted (presence of a field changed)
     * - both set  → strict-less-than comparison vs the threshold
     */
    private fun compareWithinThreshold(prior: Double?, next: Double?, threshold: Double): Boolean =
        when {
            prior == null && next == null -> true
            prior == null || next == null -> false
            else -> abs(prior - next) < threshold
        }

    private fun combineRssi(priorAvg: Int?, priorCount: Int, sample: Int?): Int? {
        if (sample == null) return priorAvg
        if (priorAvg == null) return sample
        // Running mean. Using Long to avoid overflow at large priorCount.
        val total = priorAvg.toLong() * priorCount + sample
        return (total / (priorCount + 1L)).toInt()
    }

    private fun Reading.toNewInterval(sensorId: SensorId, nowMillis: Long): ReadingIntervalEntity =
        ReadingIntervalEntity(
            sensorId = sensorId.raw,
            temperatureC = temperatureC,
            humidityPct = humidityPct,
            batteryPct = batteryPct,
            rssiAvg = rssi,
            validFrom = nowMillis,
            validUntil = nowMillis,
            sampleCount = 1,
            source = source.name,
        )
}
