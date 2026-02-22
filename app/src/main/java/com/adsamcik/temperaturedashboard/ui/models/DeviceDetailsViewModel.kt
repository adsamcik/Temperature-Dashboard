package com.adsamcik.temperaturedashboard.ui.models

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.data.toViewDevice
import com.adsamcik.temperaturedashboard.networking.ApiMode
import com.adsamcik.temperaturedashboard.networking.BleDeviceConnector
import com.adsamcik.temperaturedashboard.networking.DeviceDiscoveryManager
import com.adsamcik.temperaturedashboard.networking.Tp357AdvertisementParser
import com.adsamcik.temperaturedashboard.storage.Device
import com.adsamcik.temperaturedashboard.storage.DeviceRepository
import com.adsamcik.temperaturedashboard.storage.ReadingRepository
import com.adsamcik.temperaturedashboard.storage.TemperatureReading
import com.adsamcik.temperaturedashboard.ui.state.DeviceDetailsState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceRepository: DeviceRepository,
    private val readingRepository: ReadingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val deviceMac: String = savedStateHandle.get<String>("deviceId") ?: ""

    private val _uiState = MutableStateFlow<DeviceDetailsState>(DeviceDetailsState.Idle)
    val uiState: StateFlow<DeviceDetailsState> = _uiState.asStateFlow()

    private var device: ViewDevice? = null
    private var connector: BleDeviceConnector? = null
    private var discoveryManager: DeviceDiscoveryManager? = null

    init {
        loadDevice()
        loadHistoricalReadings()
    }

    private fun loadDevice() {
        viewModelScope.launch {
            val storedDevice = deviceRepository.getDeviceByMac(deviceMac)
            if (storedDevice != null) {
                device = storedDevice.toViewDevice()
            }
        }
    }

    private fun loadHistoricalReadings() {
        viewModelScope.launch {
            readingRepository.getAllReadings(deviceMac).collect { readings ->
                val currentState = _uiState.value
                if (currentState is DeviceDetailsState.Connected || readings.isNotEmpty()) {
                    val latest = readings.firstOrNull()
                    _uiState.value = DeviceDetailsState.Connected(
                        readings = readings,
                        latestTemperature = latest?.temperature,
                        latestHumidity = latest?.humidity
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        val dev = device ?: return
        viewModelScope.launch {
            _uiState.value = DeviceDetailsState.Connecting
            try {
                val bleConnector = BleDeviceConnector(context, dev)
                connector = bleConnector
                val connected = bleConnector.connect()
                if (connected) {
                    val data = bleConnector.readData(ApiMode.LATEST)
                    bleConnector.disconnect()
                    connector = null

                    if (data.isNotEmpty()) {
                        val readings = data.map { reading ->
                            TemperatureReading(
                                deviceMac = deviceMac,
                                temperature = reading.temperature,
                                humidity = reading.humidity,
                                timestamp = reading.timestamp
                            )
                        }
                        readingRepository.saveReadings(readings)
                    } else {
                        val latest = readingRepository.getLatestReading(deviceMac)
                        _uiState.value = DeviceDetailsState.Connected(
                            readings = emptyList(),
                            latestTemperature = latest?.temperature,
                            latestHumidity = latest?.humidity
                        )
                    }
                } else {
                    _uiState.value = DeviceDetailsState.Error("Failed to connect to device")
                }
            } catch (e: Exception) {
                _uiState.value = DeviceDetailsState.Error(e.message ?: "Connection error")
                connector?.disconnect()
                connector = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startPassiveMonitoring() {
        if (deviceMac.isBlank()) return
        stopPassiveMonitoring()

        val manager = DeviceDiscoveryManager(context, object : DeviceDiscoveryManager.DeviceDiscoveryCallback {
            override fun onDeviceDiscovered(deviceInfo: Device) {
                // Not used during passive monitoring
            }

            override fun onScanStateChanged(isScanning: Boolean) {}

            override fun onScanError(message: String) {
                viewModelScope.launch {
                    _uiState.value = DeviceDetailsState.Error(message)
                }
            }

            override fun onAdvertisementReading(reading: Tp357AdvertisementParser.AdvertisementReading) {
                if (reading.address != deviceMac) return
                viewModelScope.launch {
                    val temperatureReading = TemperatureReading(
                        deviceMac = deviceMac,
                        temperature = reading.temperature,
                        humidity = reading.humidity,
                        timestamp = reading.timestamp
                    )
                    readingRepository.saveReadings(listOf(temperatureReading))

                    val currentState = _uiState.value
                    val existingReadings = when (currentState) {
                        is DeviceDetailsState.PassiveMonitoring -> currentState.readings
                        is DeviceDetailsState.Connected -> currentState.readings
                        else -> emptyList()
                    }
                    _uiState.value = DeviceDetailsState.PassiveMonitoring(
                        readings = listOf(temperatureReading) + existingReadings,
                        latestTemperature = reading.temperature,
                        latestHumidity = reading.humidity,
                        batteryPercent = reading.batteryPercent
                    )
                }
            }
        })

        discoveryManager = manager
        manager.startScan()
    }

    fun stopPassiveMonitoring() {
        discoveryManager?.stopScan()
        discoveryManager = null
    }

    override fun onCleared() {
        super.onCleared()
        connector?.disconnect()
        stopPassiveMonitoring()
    }
}
