package com.adsamcik.temperaturedashboard.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.data.toViewDevice
import com.adsamcik.temperaturedashboard.storage.DeviceRepository
import com.adsamcik.temperaturedashboard.ui.state.MainScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenState>(MainScreenState.Loading)
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()

    private val _showAddDeviceDialog = MutableStateFlow(false)
    val showAddDeviceDialog: StateFlow<Boolean> = _showAddDeviceDialog.asStateFlow()

    private val _devices = mutableListOf<ViewDevice>()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = MainScreenState.Loading
            try {
                val devices = deviceRepository.getAllDevices().map { it.toViewDevice() }
                _devices.clear()
                _devices.addAll(devices)
                _uiState.value = if (devices.isEmpty()) MainScreenState.Empty else MainScreenState.Success(devices)
            } catch (e: Exception) {
                _uiState.value = MainScreenState.Error(e.message ?: "Failed to load devices")
            }
        }
    }

    fun onAddDeviceClicked() {
        _showAddDeviceDialog.value = true
    }

    fun dismissAddDeviceDialog() {
        _showAddDeviceDialog.value = false
    }

    fun addDevice(device: ViewDevice) {
        viewModelScope.launch {
            if (_devices.none { it.device.macAddress == device.device.macAddress }) {
                deviceRepository.addDevice(device.device)
                _devices.add(device)
                _uiState.value = MainScreenState.Success(_devices.toList())
            }
        }
    }

    fun removeDevice(device: ViewDevice) {
        viewModelScope.launch {
            deviceRepository.deleteDevice(device.device)
            _devices.removeAll { it.device.macAddress == device.device.macAddress }
            _uiState.value = if (_devices.isEmpty()) MainScreenState.Empty else MainScreenState.Success(_devices.toList())
        }
    }

    fun getDeviceByMac(macAddress: String): ViewDevice? {
        return _devices.find { it.device.macAddress == macAddress }
    }
}