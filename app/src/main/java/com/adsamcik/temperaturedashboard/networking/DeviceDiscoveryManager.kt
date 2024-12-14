package com.adsamcik.temperaturedashboard.networking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.adsamcik.temperaturedashboard.decoders.DecoderProvider
import com.adsamcik.temperaturedashboard.storage.Device

/**
 * A helper class to discover BLE devices that provide temperature and humidity data.
 *
 * **Usage Guidelines:**
 * - Check and request runtime permissions before calling [startScan].
 * - Ensure that Bluetooth is enabled before starting the scan.
 * - Stop scanning when your Activity/Fragment is paused or stopped to prevent leaks.
 */
class DeviceDiscoveryManager(
    private val context: Context,
    private val callback: DeviceDiscoveryCallback
) {

    /**
     * Callback interface for device discovery events and scanning state changes.
     */
    interface DeviceDiscoveryCallback {
        /**
         * Called when a new BLE device is discovered.
         * @param deviceInfo A data class holding device information.
         */
        fun onDeviceDiscovered(deviceInfo: Device)

        /**
         * Called when the scanning state changes.
         * @param isScanning True if scanning started, false if it stopped.
         */
        fun onScanStateChanged(isScanning: Boolean)

        /**
         * Called when an error occurs during scanning.
         * @param message A descriptive error message.
         */
        fun onScanError(message: String)
    }

    /**
     * Represents a discovered BLE device.
     *
     * @param name The device's name.
     * @param address The device's MAC address.
     * @param isCompatible Whether the device is compatible with one of the known decoders.
     * @param decodeName A human-readable name for the device type if available.
     * @param decodeIcon An optional icon resource representing the device type.
     */
    data class DiscoveredDevice(
        val name: String?,
        val address: String,
        val manufacturerId: Int?,
        val serviceUuid: String?,
        val isCompatible: Boolean,
        val decodeName: String?,
        val decodeIcon: Int?,
        val scanRecord: ScanRecord?
    )

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private var isScanning = false

    /**
     * Callback for BLE scan results.
     */
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMessage = "Scan failed with error code: $errorCode"
            Log.e(TAG, errorMessage)
            callback.onScanError(errorMessage)
            stopScan()
        }
    }

    /**
     * Processes a single scan result, determining if it's compatible and saving it to the database if so.
     */
    private fun handleScanResult(result: ScanResult) {
        val scanDevice = result.device ?: return
        val scanRecord = result.scanRecord ?: return

        val macAddress = scanDevice.address
        val deviceName = scanDevice.name ?: scanRecord.deviceName ?: "Unknown Device"

        // Extract Manufacturer ID safely
        val manufacturerId = scanRecord.manufacturerSpecificData?.let {
            if (it.size() > 0) it.keyAt(0) else null
        }

        // Extract first Service UUID safely
        val serviceUuid = scanRecord.serviceUuids?.firstOrNull()?.uuid?.toString()

        val device = Device(
            macAddress = macAddress,
            name = deviceName,
            manufacturerId = manufacturerId,
            serviceUuid = serviceUuid,
            lastSeen = System.currentTimeMillis()
        )

        // Notify the callback about the discovered device
        callback.onDeviceDiscovered(device)
    }

    /**
     * Starts scanning for compatible environmental sensor devices.
     * Make sure permissions and Bluetooth are properly enabled before calling this.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) {
            Log.w(TAG, "Already scanning.")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            callback.onScanError("Bluetooth is not enabled or not available.")
            return
        }

        if (!hasRequiredPermissions()) {
            callback.onScanError("Missing required Bluetooth scanning permissions.")
            return
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            callback.onScanError("Unable to obtain BluetoothLeScanner.")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Start scanning without filters to capture all devices.
        bluetoothLeScanner?.startScan(null, scanSettings, leScanCallback)
        isScanning = true
        callback.onScanStateChanged(true)
        Log.d(TAG, "Scanning started.")
    }

    /**
     * Stops scanning for devices.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(leScanCallback)
        isScanning = false
        callback.onScanStateChanged(false)
        Log.d(TAG, "Scanning stopped.")
    }

    /**
     * Checks if the required permissions for Bluetooth scanning are granted.
     */
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val TAG = "EnvironmentalSensorScanner"
    }
}