package com.adsamcik.temperaturedashboard.ble.desktop

import com.adsamcik.temperaturedashboard.ble.api.BleAdvertisement
import com.adsamcik.temperaturedashboard.ble.api.BleScanner
import com.adsamcik.temperaturedashboard.ble.api.ScanFailureReason
import com.adsamcik.temperaturedashboard.ble.api.ScanStartResult
import com.adsamcik.temperaturedashboard.ble.api.ScanState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Desktop [BleScanner] backed by a native `btleplug` shim loaded via JNA.
 *
 * Architecture:
 *  - `btleplug` (Rust, BSD-3) is the only library that cleanly covers Windows
 *    (WinRT), Linux (BlueZ), and macOS (CoreBluetooth) BLE from a single API.
 *  - The Rust crate at `ble/btleplug-jni/` exposes a C ABI that this class
 *    binds to via JNA.
 *  - If the native library is missing (e.g. running an unbundled JAR locally),
 *    the scanner reports [ScanFailureReason.NotImplemented] so the rest of
 *    the app stays usable.
 *
 * Phase 3 ships the JVM half + Rust source; Phase 11 wires CI to bundle the
 * compiled `.dll` / `.so` / `.dylib` per release artifact.
 */
class DesktopBleScanner : BleScanner {

    private val _state = MutableStateFlow(ScanState.Idle)
    override val state: StateFlow<ScanState> = _state.asStateFlow()

    private val native: BtleplugNative? = runCatching { BtleplugNative.load() }
        .onFailure { Napier.w("btleplug-jni native library not available: ${it.message}", tag = LOG_TAG) }
        .getOrNull()

    override fun advertisements(): Flow<BleAdvertisement> {
        // Real implementation polls the native side; until the .dll/.so ships,
        // we return an empty cold flow so consumers compose cleanly.
        return native?.advertisements() ?: emptyFlow()
    }

    override suspend fun start(): ScanStartResult {
        if (_state.value == ScanState.Scanning) {
            return ScanStartResult.Failed(ScanFailureReason.AlreadyScanning)
        }
        val n = native ?: return ScanStartResult.Failed(
            reason = ScanFailureReason.NotImplemented,
            message = "btleplug-jni native library not loaded. Build it via `cargo build --release` " +
                "in ble/btleplug-jni and place the resulting .dll/.so/.dylib on java.library.path.",
        )
        _state.value = ScanState.Starting
        return try {
            n.startScan()
            _state.value = ScanState.Scanning
            ScanStartResult.Started
        } catch (ex: Throwable) {
            _state.value = ScanState.Error
            ScanStartResult.Failed(ScanFailureReason.PlatformError, ex.message)
        }
    }

    override suspend fun stop() {
        if (_state.value == ScanState.Idle) return
        _state.value = ScanState.Stopping
        runCatching { native?.stopScan() }
        _state.value = ScanState.Idle
    }

    private companion object {
        const val LOG_TAG = "DesktopBleScanner"
    }
}
