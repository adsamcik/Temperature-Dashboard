package com.adsamcik.temperaturedashboard.core.model

import kotlinx.datetime.Instant

/** Where a [Reading] originated. */
enum class ReadingSource {
    /** Passive BLE advertisement (no connection). */
    ADVERTISEMENT,

    /** Active GATT notification or characteristic read. */
    GATT,

    /** Historical readings fetched in bulk from the device's on-board buffer. */
    BACKFILL,
}

/**
 * A single instantaneous sensor reading captured by the scanning pipeline.
 *
 * This is the transient input to [com.adsamcik.temperaturedashboard.core.model.IntervalCoalescer]
 * — the persisted form is [ReadingInterval] (one row per run of equal-ish values).
 */
data class Reading(
    val temperatureC: Double?,
    val humidityPct: Double?,
    val batteryPct: Int?,
    val rssi: Int?,
    val timestamp: Instant,
    val source: ReadingSource,
) {
    /** A reading carries information iff at least one observable field is non-null. */
    val isEmpty: Boolean
        get() = temperatureC == null && humidityPct == null && batteryPct == null
}
