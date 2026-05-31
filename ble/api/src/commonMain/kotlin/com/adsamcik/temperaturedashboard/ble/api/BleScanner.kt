package com.adsamcik.temperaturedashboard.ble.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic BLE scanner.
 *
 * Lifecycle:
 *  1. Check [state] and adapter availability via [BluetoothAdapterMonitor].
 *  2. Call [start]; observe the returned [ScanStartResult] for failure modes.
 *  3. Collect [advertisements] in a coroutine — the hot stream of events.
 *  4. Call [stop] when done. Idempotent.
 *
 * Threading: implementations must be safe to call from any dispatcher. The
 * returned [Flow] is cold per collector but the underlying scan is a single
 * shared platform resource — multiple collectors share the live scan.
 */
interface BleScanner {
    val state: StateFlow<ScanState>

    /** Cold flow of advertisements observed while the scanner is running. */
    fun advertisements(): Flow<BleAdvertisement>

    suspend fun start(): ScanStartResult
    suspend fun stop()
}

enum class ScanState {
    Idle,
    Starting,
    Scanning,
    Stopping,
    Error,
}

sealed interface ScanStartResult {
    data object Started : ScanStartResult
    data class Failed(val reason: ScanFailureReason, val message: String? = null) : ScanStartResult
}

enum class ScanFailureReason {
    BluetoothDisabled,
    PermissionDenied,
    AdapterUnavailable,
    AlreadyScanning,
    PlatformError,
    NotImplemented,
}
