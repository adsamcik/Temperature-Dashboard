package com.adsamcik.temperaturedashboard.core.model

import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * One run-length-encoded interval: the value(s) held steady between
 * [validFrom] and [validUntil].
 *
 * Multiple physical samples may have been coalesced into a single interval as
 * long as each was within the threshold of the previous (see [CoalescingPolicy]).
 * The interval freezes when the value drifts beyond threshold, when the sensor
 * goes silent for longer than the stale window, or when the interval exceeds
 * the maximum lifetime.
 */
data class ReadingInterval(
    val id: Long,
    val sensorId: SensorId,
    val temperatureC: Double?,
    val humidityPct: Double?,
    val batteryPct: Int?,
    val rssiAvg: Int?,
    val validFrom: Instant,
    val validUntil: Instant,
    val sampleCount: Int,
    val source: ReadingSource,
) {
    val duration: Duration get() = validUntil - validFrom
}
