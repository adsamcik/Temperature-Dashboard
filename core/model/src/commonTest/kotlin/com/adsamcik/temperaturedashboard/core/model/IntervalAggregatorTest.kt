package com.adsamcik.temperaturedashboard.core.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class IntervalAggregatorTest {

    private val t0 = Instant.fromEpochMilliseconds(0L)

    private fun interval(
        from: Long,
        until: Long,
        tempC: Double? = null,
        humPct: Double? = null,
    ) = ReadingInterval(
        id = 0,
        sensorId = SensorId(1),
        temperatureC = tempC,
        humidityPct = humPct,
        batteryPct = null,
        rssiAvg = null,
        validFrom = Instant.fromEpochMilliseconds(from),
        validUntil = Instant.fromEpochMilliseconds(until),
        sampleCount = 1,
        source = ReadingSource.ADVERTISEMENT,
    )

    @Test
    fun `empty list returns Empty stats with declared totalDuration`() {
        val stats = IntervalAggregator.aggregate(emptyList(), 1.hours)
        assertEquals(1.hours, stats.totalDuration)
        assertEquals(0.milliseconds, stats.coverageDuration)
        assertNull(stats.currentTemperatureC)
        assertNull(stats.avgTemperatureC)
    }

    @Test
    fun `min max are exact per row`() {
        val stats = IntervalAggregator.aggregate(
            listOf(
                interval(0, 100, tempC = 20.0),
                interval(100, 200, tempC = 25.0),
                interval(200, 300, tempC = 22.0),
            ),
            totalDuration = 300.milliseconds,
        )
        assertEquals(20.0, stats.minTemperatureC)
        assertEquals(25.0, stats.maxTemperatureC)
    }

    @Test
    fun `time-weighted average dominates by duration, not sample count`() {
        // An interval that covered 9× longer should pull the mean toward its value.
        val stats = IntervalAggregator.aggregate(
            listOf(
                interval(0, 9_000, tempC = 20.0),    // 9 seconds @ 20
                interval(9_000, 10_000, tempC = 30.0), // 1 second @ 30
            ),
            totalDuration = 10.milliseconds * 1000, // 10 s
        )
        assertNotNull(stats.avgTemperatureC)
        // Weighted: (20*9000 + 30*1000) / 10000 = 21.0
        assertEquals(21.0, stats.avgTemperatureC!!, absoluteTolerance = 1e-9)
    }

    @Test
    fun `current is the value from the most recently ended interval`() {
        val stats = IntervalAggregator.aggregate(
            listOf(
                interval(0, 100, tempC = 20.0),
                interval(100, 500, tempC = 25.0),      // most recent validUntil
                interval(200, 300, tempC = 22.0),
            ),
            totalDuration = 500.milliseconds,
        )
        assertEquals(25.0, stats.currentTemperatureC)
    }

    @Test
    fun `coverage ratio is fraction of totalDuration covered by intervals`() {
        // Two intervals covering 100ms each inside a 500ms window = 40 % coverage
        val stats = IntervalAggregator.aggregate(
            listOf(
                interval(0, 100, tempC = 20.0),
                interval(300, 400, tempC = 22.0),
            ),
            totalDuration = 500.milliseconds,
        )
        assertEquals(0.4, stats.coverageRatio, absoluteTolerance = 1e-9)
    }

    private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
        val diff = kotlin.math.abs(expected - actual)
        if (diff > absoluteTolerance) {
            throw AssertionError("Expected $expected within $absoluteTolerance of $actual (diff=$diff)")
        }
    }
}
