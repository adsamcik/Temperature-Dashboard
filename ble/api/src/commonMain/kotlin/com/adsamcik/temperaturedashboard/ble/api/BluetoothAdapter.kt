package com.adsamcik.temperaturedashboard.ble.api

import kotlinx.coroutines.flow.StateFlow

/** State of the host's Bluetooth radio. */
enum class BluetoothAdapterState {
    Unknown,
    Off,
    On,
    TurningOn,
    TurningOff,
    Unavailable,
}

/** Reactive view over the host's BLE radio state. */
interface BluetoothAdapterMonitor {
    val state: StateFlow<BluetoothAdapterState>
}

/**
 * Runtime BLE permission tristate.
 *
 * On Desktop the answer is always [NotApplicable] — there's no per-app
 * Bluetooth permission grant model on Windows/Linux. macOS does have one
 * (per bundle id, via TCC) but it's surfaced through `btleplug` errors, not
 * a queryable Kotlin API.
 */
enum class BlePermissionStatus {
    Granted,
    ShouldShowRationale,
    Denied,
    NotApplicable,
}
