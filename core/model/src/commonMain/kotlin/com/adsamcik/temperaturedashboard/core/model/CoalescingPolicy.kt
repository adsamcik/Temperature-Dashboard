package com.adsamcik.temperaturedashboard.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Rules driving the run-length encoder.
 *
 * - [temperatureThresholdC] / [humidityThresholdPct] — a new reading within
 *   this much of the prior interval's value just *extends* that interval.
 *   Outside the threshold, the interval freezes and a new one starts.
 * - [staleWindow] — if no sample for this long, the current interval freezes
 *   at its last known `validUntil`. When the sensor returns, a *new* interval
 *   starts, leaving an honest gap on the chart instead of a stale held value.
 * - [maxIntervalDuration] — hard cap: even if values stay perfectly steady,
 *   close the interval and open a new one after this duration. Caps blast
 *   radius of bad readings, keeps queries chunked, simplifies aggregation.
 */
data class CoalescingPolicy(
    val temperatureThresholdC: Double = DEFAULT_TEMPERATURE_THRESHOLD_C,
    val humidityThresholdPct: Double = DEFAULT_HUMIDITY_THRESHOLD_PCT,
    val staleWindow: Duration = DEFAULT_STALE_WINDOW,
    val maxIntervalDuration: Duration = DEFAULT_MAX_INTERVAL_DURATION,
) {
    companion object {
        const val DEFAULT_TEMPERATURE_THRESHOLD_C: Double = 0.1
        const val DEFAULT_HUMIDITY_THRESHOLD_PCT: Double = 1.0
        val DEFAULT_STALE_WINDOW: Duration = 5.minutes
        val DEFAULT_MAX_INTERVAL_DURATION: Duration = 6.hours

        /** Sensible app default; can be overridden via settings. */
        val Default: CoalescingPolicy = CoalescingPolicy()
    }
}
