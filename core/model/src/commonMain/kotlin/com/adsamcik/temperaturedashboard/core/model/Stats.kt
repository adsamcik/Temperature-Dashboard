package com.adsamcik.temperaturedashboard.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

/**
 * Summary statistics derived from a sequence of [ReadingInterval]s over a time
 * range. All averages are *time-weighted* — an interval that covered 8 hours
 * counts 8× as much as one that covered 1 hour. Min/max are exact (per row).
 */
data class IntervalStats(
    val currentTemperatureC: Double?,
    val currentHumidityPct: Double?,
    val minTemperatureC: Double?,
    val maxTemperatureC: Double?,
    val avgTemperatureC: Double?,
    val minHumidityPct: Double?,
    val maxHumidityPct: Double?,
    val avgHumidityPct: Double?,
    /** Total time spanned by the query window. */
    val totalDuration: Duration,
    /** Sum of (validUntil - validFrom) across all observed intervals. */
    val coverageDuration: Duration,
) {
    /** Ratio of time the sensor actually reported, in `[0, 1]`. 0 = no data. */
    val coverageRatio: Double
        get() = if (totalDuration == ZERO) 0.0 else coverageDuration / totalDuration

    companion object {
        val Empty: IntervalStats = IntervalStats(
            currentTemperatureC = null,
            currentHumidityPct = null,
            minTemperatureC = null,
            maxTemperatureC = null,
            avgTemperatureC = null,
            minHumidityPct = null,
            maxHumidityPct = null,
            avgHumidityPct = null,
            totalDuration = ZERO,
            coverageDuration = ZERO,
        )
    }
}

/**
 * Pure-function aggregator: turns a list of [ReadingInterval]s into a single
 * [IntervalStats]. Tested in isolation — no DB, no clock.
 */
object IntervalAggregator {
    fun aggregate(intervals: List<ReadingInterval>, totalDuration: Duration): IntervalStats {
        if (intervals.isEmpty()) {
            return IntervalStats.Empty.copy(totalDuration = totalDuration)
        }

        var minT: Double? = null
        var maxT: Double? = null
        var minH: Double? = null
        var maxH: Double? = null

        var weightedTSum = 0.0
        var weightedTDur = 0.0
        var weightedHSum = 0.0
        var weightedHDur = 0.0
        var coverageMs = 0L

        for (interval in intervals) {
            val durMs = (interval.validUntil - interval.validFrom).inWholeMilliseconds
            coverageMs += durMs

            interval.temperatureC?.let { t ->
                if (minT == null || t < minT!!) minT = t
                if (maxT == null || t > maxT!!) maxT = t
                weightedTSum += t * durMs
                weightedTDur += durMs
            }
            interval.humidityPct?.let { h ->
                if (minH == null || h < minH!!) minH = h
                if (maxH == null || h > maxH!!) maxH = h
                weightedHSum += h * durMs
                weightedHDur += durMs
            }
        }

        val latest = intervals.maxBy { it.validUntil }

        return IntervalStats(
            currentTemperatureC = latest.temperatureC,
            currentHumidityPct = latest.humidityPct,
            minTemperatureC = minT,
            maxTemperatureC = maxT,
            avgTemperatureC = if (weightedTDur > 0) weightedTSum / weightedTDur else null,
            minHumidityPct = minH,
            maxHumidityPct = maxH,
            avgHumidityPct = if (weightedHDur > 0) weightedHSum / weightedHDur else null,
            totalDuration = totalDuration,
            coverageDuration = coverageMs.milliseconds,
        )
    }

    /**
     * Sum of time during which `temperatureC > thresholdC`. Intervals with
     * null temperature don't contribute. The window's gaps (intervals not
     * captured at all) also don't contribute — this is *measured* time
     * above threshold, not extrapolated.
     */
    fun durationAbove(intervals: List<ReadingInterval>, thresholdC: Double): Duration =
        durationWhere(intervals) { it > thresholdC }

    /** Sum of time during which `temperatureC < thresholdC`. */
    fun durationBelow(intervals: List<ReadingInterval>, thresholdC: Double): Duration =
        durationWhere(intervals) { it < thresholdC }

    /** Sum of time during which `lowC <= temperatureC <= highC` (inclusive). */
    fun durationInRange(intervals: List<ReadingInterval>, lowC: Double, highC: Double): Duration =
        durationWhere(intervals) { it in lowC..highC }

    private inline fun durationWhere(
        intervals: List<ReadingInterval>,
        predicate: (Double) -> Boolean,
    ): Duration {
        var totalMs = 0L
        for (interval in intervals) {
            val t = interval.temperatureC ?: continue
            if (!predicate(t)) continue
            totalMs += (interval.validUntil - interval.validFrom).inWholeMilliseconds
        }
        return totalMs.milliseconds
    }
}

