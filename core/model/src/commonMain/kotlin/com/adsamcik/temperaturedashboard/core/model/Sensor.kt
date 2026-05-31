package com.adsamcik.temperaturedashboard.core.model

import kotlinx.datetime.Instant

/**
 * A configured BLE temperature/humidity sensor the user has added to the app.
 *
 * Distinct from a [ReadingInterval] — a sensor is the device; intervals are
 * the run-length-encoded history of values it has reported.
 */
data class Sensor(
    val id: SensorId,
    val address: SensorAddress,
    /** Decoder profile id, e.g. `"thermopro.tp35x"`, `"bthome.v2"`. */
    val profileId: String,
    val displayName: String,
    /** Vendor/model hint surfaced during scanning, e.g. `"TP357 (6ECA)"`. */
    val modelHint: String?,
    val createdAt: Instant,
    val lastSeenAt: Instant?,
    /** Stable hash seed driving the chart line / accent colour for this sensor. */
    val colorSeed: Int,
)
