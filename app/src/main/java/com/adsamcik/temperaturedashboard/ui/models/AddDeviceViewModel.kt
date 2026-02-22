package com.adsamcik.temperaturedashboard.ui.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.data.toViewDevice
import com.adsamcik.temperaturedashboard.networking.DeviceDiscoveryManager
import com.adsamcik.temperaturedashboard.networking.Tp357AdvertisementParser
import com.adsamcik.temperaturedashboard.storage.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel(), DeviceDiscoveryManager.DeviceDiscoveryCallback {

    private val scanner = DeviceDiscoveryManager(context, this)

    private val _discoveredDevices = MutableStateFlow<List<ViewDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ViewDevice>> = _discoveredDevices.asStateFlow()

    private var scanning = false

    fun startScanning() {
        if (!scanning) {
            _discoveredDevices.value = emptyList()
            scanner.startScan()
            scanning = true
        }
    }

    fun stopScanning() {
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
                _discoveredDevices.value = currentList
            }
        }
    }

    override fun onScanStateChanged(isScanning: Boolean) {}

    override fun onScanError(message: String) {
        stopScanning()
    }

    override fun onAdvertisementReading(reading: Tp357AdvertisementParser.AdvertisementReading) {
        viewModelScope.launch {
            val currentList = _discoveredDevices.value.toMutableList()
            val index = currentList.indexOfFirst { it.device.macAddress == reading.address }
            if (index >= 0) {
                currentList[index] = currentList[index].copy(liveTemperature = reading.temperature)
                _discoveredDevices.value = currentList
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}