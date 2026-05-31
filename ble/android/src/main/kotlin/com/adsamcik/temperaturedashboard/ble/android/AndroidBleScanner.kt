package com.adsamcik.temperaturedashboard.ble.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.adsamcik.temperaturedashboard.ble.api.BleAdvertisement
import com.adsamcik.temperaturedashboard.ble.api.BleScanner
import com.adsamcik.temperaturedashboard.ble.api.ScanFailureReason
import com.adsamcik.temperaturedashboard.ble.api.ScanStartResult
import com.adsamcik.temperaturedashboard.ble.api.ScanState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * `android.bluetooth` implementation of [BleScanner].
 *
 * Wraps a single `BluetoothLeScanner` instance behind a SharedFlow so multiple
 * consumers can observe the same underlying scan. The radio is shared across
 * the process — explicit [start]/[stop] manage that single live scan.
 */
class AndroidBleScanner(private val context: Context) : BleScanner {

    private val _state = MutableStateFlow(ScanState.Idle)
    override val state: StateFlow<ScanState> = _state.asStateFlow()

    private val advertisementBus = MutableSharedFlow<BleAdvertisement>(
        replay = 0,
        extraBufferCapacity = ADVERT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val callback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            advertisementBus.tryEmit(result.toAdvertisement())
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { advertisementBus.tryEmit(it.toAdvertisement()) }
        }

        override fun onScanFailed(errorCode: Int) {
            Napier.w("BLE scan failed: $errorCode", tag = LOG_TAG)
            _state.value = ScanState.Error
        }
    }

    override fun advertisements(): Flow<BleAdvertisement> = advertisementBus.asSharedFlow()

    override suspend fun start(): ScanStartResult {
        if (_state.value == ScanState.Scanning || _state.value == ScanState.Starting) {
            return ScanStartResult.Failed(ScanFailureReason.AlreadyScanning)
        }
        val permission = checkPermission()
        if (permission != null) return ScanStartResult.Failed(permission)

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return ScanStartResult.Failed(ScanFailureReason.AdapterUnavailable)
        val adapter: BluetoothAdapter = manager.adapter
            ?: return ScanStartResult.Failed(ScanFailureReason.AdapterUnavailable)
        if (!adapter.isEnabled) {
            return ScanStartResult.Failed(ScanFailureReason.BluetoothDisabled)
        }
        val leScanner = adapter.bluetoothLeScanner
            ?: return ScanStartResult.Failed(ScanFailureReason.AdapterUnavailable)

        _state.value = ScanState.Starting
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            @SuppressLint("MissingPermission")
            leScanner.startScan(null, settings, callback)
            _state.value = ScanState.Scanning
            ScanStartResult.Started
        } catch (se: SecurityException) {
            _state.value = ScanState.Error
            ScanStartResult.Failed(ScanFailureReason.PermissionDenied, se.message)
        } catch (ex: Exception) {
            Napier.e("BLE scan start exploded", ex, LOG_TAG)
            _state.value = ScanState.Error
            ScanStartResult.Failed(ScanFailureReason.PlatformError, ex.message)
        }
    }

    override suspend fun stop() {
        if (_state.value == ScanState.Idle || _state.value == ScanState.Stopping) return
        _state.value = ScanState.Stopping
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val leScanner = manager?.adapter?.bluetoothLeScanner
            @SuppressLint("MissingPermission")
            leScanner?.stopScan(callback)
        } catch (ex: SecurityException) {
            Napier.w("BLE stopScan denied: ${ex.message}", tag = LOG_TAG)
        } catch (ex: Exception) {
            Napier.w("BLE stopScan failed: ${ex.message}", tag = LOG_TAG)
        } finally {
            _state.value = ScanState.Idle
        }
    }

    private fun checkPermission(): ScanFailureReason? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) null else ScanFailureReason.PermissionDenied
    }

    private companion object {
        const val LOG_TAG = "AndroidBleScanner"
        const val ADVERT_BUFFER = 256
    }
}

@SuppressLint("MissingPermission")
internal fun ScanResult.toAdvertisement(): BleAdvertisement {
    val record = scanRecord
    val serviceUuids = record?.serviceUuids?.map { it.uuid.toString().uppercase() }.orEmpty()
    val manufacturerData = buildMap {
        record?.manufacturerSpecificData?.let { sparse ->
            for (i in 0 until sparse.size()) {
                put(sparse.keyAt(i), sparse.valueAt(i))
            }
        }
    }
    val serviceData = record?.serviceData
        ?.mapKeys { it.key.uuid.toString().uppercase() }
        ?.mapValues { it.value }
        .orEmpty()
    return BleAdvertisement(
        address = device.address.uppercase(),
        name = record?.deviceName ?: device.name,
        rssi = rssi,
        timestamp = Clock.System.now(),
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerData,
        serviceData = serviceData,
    )
}
