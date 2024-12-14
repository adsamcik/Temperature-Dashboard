package com.adsamcik.temperaturedashboard.ui.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.data.toViewDevice
import com.adsamcik.temperaturedashboard.decoders.DecoderProvider
import com.adsamcik.temperaturedashboard.networking.DeviceDiscoveryManager
import com.adsamcik.temperaturedashboard.storage.Device
import com.adsamcik.temperaturedashboard.storage.DeviceDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for adding new devices discovered via BLE scanning.
 */
open class AddDeviceViewModel(context: Context) : ViewModel(), DeviceDiscoveryManager.DeviceDiscoveryCallback {

    private val scanner = DeviceDiscoveryManager(context, this)

    private val _discoveredDevices = MutableStateFlow<List<ViewDevice>>(emptyList())
    open val discoveredDevices = _discoveredDevices.asStateFlow()

    // Example known recognized devices. In a real app, this could come from a database or config.
    private val recognizedAddresses = setOf("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF")

    private var scanning = false

    /**
     * Starts scanning for devices, resetting the current discovered list.
     */
    open fun startScanning() {
        if (!scanning) {
            _discoveredDevices.value = emptyList()
            scanner.startScan()
            scanning = true
        }
    }

    /**
     * Stops scanning for devices.
     */
    open fun stopScanning() {
        if (scanning) {
            scanner.stopScan()
            scanning = false
        }
    }

    override fun onDeviceDiscovered(deviceInfo: Device) {
        viewModelScope.launch {
            val currentList = _discoveredDevices.value.toMutableList()
            if (currentList.none { it.device.macAddress == deviceInfo.macAddress }) {
                currentList.add(deviceInfo.toViewDevice())
                // Recognized devices go at the top
                val (recognized, others) = currentList.partition { recognizedAddresses.contains(it.device.macAddress) }
                _discoveredDevices.value = recognized + others
            }
        }
    }

    override fun onScanStateChanged(isScanning: Boolean) {
        // Could update UI states if needed
    }

    override fun onScanError(message: String) {
        // Handle error gracefully (e.g. show a toast or a dialog)
        stopScanning()
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}