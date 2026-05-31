package com.adsamcik.temperaturedashboard.core.model

import kotlin.jvm.JvmInline

/** Unique row identifier for a [Sensor] in the local database. */
@JvmInline
value class SensorId(val raw: Long) {
    companion object {
        /** Sentinel for sensors not yet inserted (pre-insert state). */
        val Unassigned: SensorId = SensorId(0L)
    }
}

/**
 * Platform-stable identifier for a Bluetooth sensor. On Android and Linux this
 * is the MAC address (`AA:BB:CC:DD:EE:FF`); on macOS / iOS it is a CoreBluetooth
 * peripheral UUID; on Windows it is a `BLUETOOTHLE_DEVICE_ID`.
 *
 * Stored as raw text — uppercased and trimmed at construction. Two addresses
 * coming from the same device on the same platform compare equal.
 */
@JvmInline
value class SensorAddress(val raw: String) {
    companion object {
        fun normalize(raw: String): SensorAddress =
            SensorAddress(raw.trim().uppercase())
    }
}
