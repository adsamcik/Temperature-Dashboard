package com.adsamcik.temperaturedashboard.core.database

import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.Reading
import com.adsamcik.temperaturedashboard.core.model.ReadingSource
import com.adsamcik.temperaturedashboard.core.model.SensorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Exhaustive tests for the run-length encoder. Every freezing rule has at
 * least one positive and one boundary test.
 */
class IntervalCoalescerTest {

    private val sensorId = SensorId(1L)
    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun reading(
        at: Instant = t0,
        tempC: Double? = 22.0,
        humPct: Double? = 45.0,
        battery: Int? = 100,
        rssi: Int? = -60,
        source: ReadingSource = ReadingSource.ADVERTISEMENT,
    ) = Reading(
        temperatureC = tempC,
        humidityPct = humPct,
        batteryPct = battery,
        rssi = rssi,
        timestamp = at,
        source = source,
    )

    // ------------------------------------------------------------- first interval

    @Test
    fun `first ingest into empty store inserts a fresh interval`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)

        val outcome = coalescer.ingest(sensorId, reading())

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        assertEquals(IntervalCoalescer.Reason.FIRST_INTERVAL, outcome.reason)
        assertEquals(1, dao.allInserts.size)
        val row = dao.allInserts.single()
        assertEquals(22.0, row.temperatureC)
        assertEquals(45.0, row.humidityPct)
        assertEquals(1, row.sampleCount)
        assertEquals(row.validFrom, row.validUntil)
    }

    // ------------------------------------------------------------- extension

    @Test
    fun `same-value reading within threshold extends the latest interval`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0))

        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 30.seconds, tempC = 22.05))

        assertIs<IntervalCoalescer.Outcome.Extended>(outcome)
        val current = dao.latestFor(sensorId.raw)!!
        assertEquals(2, current.sampleCount)
        assertEquals((t0 + 30.seconds).toEpochMilliseconds(), current.validUntil)
        // validFrom is unchanged
        assertEquals(t0.toEpochMilliseconds(), current.validFrom)
    }

    @Test
    fun `temperature change beyond threshold opens a new interval`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0, tempC = 22.0))

        // Threshold = 0.1; 22.0 → 22.2 is a 0.2 jump
        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 30.seconds, tempC = 22.2))

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        assertEquals(IntervalCoalescer.Reason.OUT_OF_THRESHOLD, outcome.reason)
        assertEquals(2, dao.allInserts.size)
    }

    @Test
    fun `temperature exactly at threshold still opens a new interval`() = runTest {
        // Threshold check is strict-less-than, so threshold-equal counts as drift.
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0, tempC = 22.0))

        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 30.seconds, tempC = 22.1))

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        assertEquals(IntervalCoalescer.Reason.OUT_OF_THRESHOLD, outcome.reason)
    }

    @Test
    fun `humidity drift beyond threshold opens a new interval`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0, humPct = 45.0))

        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 30.seconds, humPct = 47.0))

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        assertEquals(IntervalCoalescer.Reason.OUT_OF_THRESHOLD, outcome.reason)
    }

    // ------------------------------------------------------------- stale window

    @Test
    fun `gap longer than stale window freezes prior and starts new interval`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0))

        // 5-minute stale window; sensor was silent for 10 min then returned with the same value.
        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 10.minutes))

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        assertEquals(IntervalCoalescer.Reason.STALE_PRIOR, outcome.reason)
        assertEquals(2, dao.allInserts.size)
        // Prior row's validUntil remains at the LAST confirmed time, not "now".
        assertEquals(t0.toEpochMilliseconds(), dao.allInserts.first().validUntil)
    }

    @Test
    fun `gap exactly equal to stale window still extends`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao, CoalescingPolicy.Default)
        coalescer.ingest(sensorId, reading(at = t0))

        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 5.minutes))

        // The condition is `gap > staleWindow`, so equality extends.
        assertIs<IntervalCoalescer.Outcome.Extended>(outcome)
    }

    // ------------------------------------------------------------- max interval duration

    @Test
    fun `interval older than max duration force-closes even without drift`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0))

        // Default max is 6h; this is 6h + 1s after validFrom.
        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 6.hours + 1.seconds))

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        // Stale window will fire first because 6h > 5min, so we expect STALE_PRIOR.
        // That's fine — both rules want the same outcome (close + new).
        assertTrue(
            outcome.reason == IntervalCoalescer.Reason.STALE_PRIOR ||
                outcome.reason == IntervalCoalescer.Reason.MAX_DURATION_REACHED,
        )
    }

    @Test
    fun `max duration triggers when stale window is loose enough`() = runTest {
        val dao = FakeIntervalDao()
        val policy = CoalescingPolicy.Default.copy(staleWindow = 24.hours)
        val coalescer = IntervalCoalescer(dao, policy)
        coalescer.ingest(sensorId, reading(at = t0))

        // 6h after validFrom; stale gate disabled, so MAX_DURATION_REACHED must fire.
        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 6.hours + 1.seconds))

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        assertEquals(IntervalCoalescer.Reason.MAX_DURATION_REACHED, outcome.reason)
    }

    // ------------------------------------------------------------- bookkeeping

    @Test
    fun `rssi averaging is a running mean over coalesced sample count`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0, rssi = -60))
        coalescer.ingest(sensorId, reading(at = t0 + 10.seconds, rssi = -40))
        coalescer.ingest(sensorId, reading(at = t0 + 20.seconds, rssi = -50))

        val current = dao.latestFor(sensorId.raw)!!
        // (-60 + -40 + -50) / 3 = -50
        assertEquals(-50, current.rssiAvg)
        assertEquals(3, current.sampleCount)
    }

    @Test
    fun `battery percent is updated to the latest non-null sample`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0, battery = 100))
        coalescer.ingest(sensorId, reading(at = t0 + 10.seconds, battery = null))
        coalescer.ingest(sensorId, reading(at = t0 + 20.seconds, battery = 50))

        val current = dao.latestFor(sensorId.raw)!!
        assertEquals(50, current.batteryPct)
    }

    // ------------------------------------------------------------- guard-rails

    @Test
    fun `empty reading is rejected`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)

        val outcome = coalescer.ingest(
            sensorId,
            Reading(null, null, null, -60, t0, ReadingSource.ADVERTISEMENT),
        )

        assertIs<IntervalCoalescer.Outcome.Rejected>(outcome)
        assertEquals(0, dao.allInserts.size)
    }

    @Test
    fun `out-of-order reading older than latest interval is rejected`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0 + 60.seconds))

        val outcome = coalescer.ingest(sensorId, reading(at = t0))

        assertIs<IntervalCoalescer.Outcome.Rejected>(outcome)
    }

    @Test
    fun `null fields on prior and new are treated as same`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        // First sensor only reports humidity (no temp)
        coalescer.ingest(sensorId, reading(at = t0, tempC = null))

        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 30.seconds, tempC = null))

        assertIs<IntervalCoalescer.Outcome.Extended>(outcome)
    }

    @Test
    fun `field appearing after being null opens new interval`() = runTest {
        val dao = FakeIntervalDao()
        val coalescer = IntervalCoalescer(dao)
        coalescer.ingest(sensorId, reading(at = t0, tempC = null))

        val outcome = coalescer.ingest(sensorId, reading(at = t0 + 30.seconds, tempC = 22.0))

        assertIs<IntervalCoalescer.Outcome.Inserted>(outcome)
        assertEquals(IntervalCoalescer.Reason.OUT_OF_THRESHOLD, outcome.reason)
    }
}

/** Minimal in-memory DAO double — only the methods [IntervalCoalescer] touches. */
private class FakeIntervalDao : ReadingIntervalDao {
    val allInserts = mutableListOf<ReadingIntervalEntity>()
    private var nextId = 1L

    override suspend fun insert(interval: ReadingIntervalEntity): Long {
        val withId = interval.copy(id = nextId++)
        allInserts.add(withId)
        return withId.id
    }

    override suspend fun findLatest(sensorId: Long): ReadingIntervalEntity? =
        allInserts.filter { it.sensorId == sensorId }.maxByOrNull { it.validFrom }

    fun latestFor(sensorId: Long): ReadingIntervalEntity? =
        allInserts.filter { it.sensorId == sensorId }.maxByOrNull { it.validFrom }

    override suspend fun extend(
        id: Long,
        newValidUntil: Long,
        newSampleCount: Int,
        newRssiAvg: Int?,
        newBatteryPct: Int?,
    ) {
        val idx = allInserts.indexOfFirst { it.id == id }
        if (idx >= 0) {
            allInserts[idx] = allInserts[idx].copy(
                validUntil = newValidUntil,
                sampleCount = newSampleCount,
                rssiAvg = newRssiAvg,
                batteryPct = newBatteryPct,
            )
        }
    }

    override fun observeInRange(sensorId: Long, startMillis: Long, endMillis: Long): Flow<List<ReadingIntervalEntity>> = flowOf(emptyList())
    override suspend fun findInRange(sensorId: Long, startMillis: Long, endMillis: Long): List<ReadingIntervalEntity> = emptyList()
    override fun observeLatest(sensorId: Long): Flow<ReadingIntervalEntity?> = flowOf(null)
    override suspend fun countForSensor(sensorId: Long): Long = allInserts.count { it.sensorId == sensorId }.toLong()
    override suspend fun deleteAllForSensor(sensorId: Long) { allInserts.removeAll { it.sensorId == sensorId } }
}
