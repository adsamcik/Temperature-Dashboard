package com.adsamcik.temperaturedashboard.ui.models

import android.bluetooth.le.ScanRecord
import android.content.Context
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.networking.DeviceDiscoveryManager
import com.adsamcik.temperaturedashboard.storage.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockAddDeviceViewModel(context: Context) : AddDeviceViewModel(context), DeviceDiscoveryManager.DeviceDiscoveryCallback {

    // Mock list of discovered devices
    private val _discoveredDevices = MutableStateFlow<List<ViewDevice>>(
        listOf(
            ViewDevice(
                Device(
                    name = "TempSensor",
                    macAddress = "11:22:33:44:55:66",
                    manufacturerId = 0x1234,
                    serviceUuid = "0000-0000",
                    lastSeen = System.currentTimeMillis()
                ),
                null
            ),
            ViewDevice(
                Device(
                    name = "TempSensor2",
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    manufacturerId = 0x5678,
                    serviceUuid = "0000-0001",
                    lastSeen = System.currentTimeMillis()
                ),
                null
            ),
        )
    )
    override val discoveredDevices: StateFlow<List<ViewDevice>> = _discoveredDevices

    // Mock recognized addresses
    private val recognizedAddresses = setOf("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF")

    override fun startScanning() {
        // No-op for preview
    }

    override fun stopScanning() {
        // No-op for preview
    }

    override fun onDeviceDiscovered(deviceInfo: Device) {
        // No-op for preview
    }

    override fun onScanStateChanged(isScanning: Boolean) {
        // No-op for preview
    }

    override fun onScanError(message: String) {
        // No-op for preview
    }
}