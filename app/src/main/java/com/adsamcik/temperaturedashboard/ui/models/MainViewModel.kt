package com.adsamcik.temperaturedashboard.ui.models

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.data.toViewDevice
import com.adsamcik.temperaturedashboard.storage.DeviceRepository
import kotlinx.coroutines.launch

/**
 * A ViewModel that manages devices stored in the local database.
 */
class MainViewModel(private val deviceRepository: DeviceRepository) : ViewModel() {
    val addedDevices = mutableStateOf<List<ViewDevice>>(emptyList())
    val showAddDeviceDialog = mutableStateOf(false)

    init {
        // Load devices from the database on initialization.
        viewModelScope.launch {
            val devices = deviceRepository.getAllDevices()
            addedDevices.value = devices.map { it.toViewDevice() }
        }
    }

    fun onAddDeviceClicked() {
        showAddDeviceDialog.value = true
    }

    fun dismissAddDeviceDialog() {
        showAddDeviceDialog.value = false
    }

    fun addDevice(device: ViewDevice) {
        viewModelScope.launch {
            val list = addedDevices.value.toMutableList()
            if (list.none { it.device.macAddress == device.device.macAddress }) {
                list.add(device)
                addedDevices.value = list

                // Save the device to the database
                deviceRepository.addDevice(device.device)
            }
        }
    }

    fun removeDevice(device: ViewDevice) {
        viewModelScope.launch {
            val list = addedDevices.value.toMutableList()
            list.removeAll { it.device == device.device }
            addedDevices.value = list

            // Remove the device from the database
            deviceRepository.deleteDevice(device.device)
        }
    }
}