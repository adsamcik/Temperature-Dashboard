package com.adsamcik.temperaturedashboard.ui.models

import android.annotation.SuppressLint
import android.bluetooth.le.ScanSettings
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _connectionState = MutableStateFlow<DeviceDetailsState>(DeviceDetailsState.Idle)
    private val _historicalReadings = MutableStateFlow<List<TemperatureReading>>(emptyList())
    val uiState: StateFlow<DeviceDetailsState> = combine(_connectionState, _historicalReadings) { connectionState, readings ->
        when (connectionState) {
            is DeviceDetailsState.Connecting -> DeviceDetailsState.Connecting
            is DeviceDetailsState.Error -> connectionState
            is DeviceDetailsState.PassiveMonitoring -> connectionState
            is DeviceDetailsState.Connected, is DeviceDetailsState.Idle -> {
                if (connectionState is DeviceDetailsState.Connected || readings.isNotEmpty()) {
                    val latest = readings.firstOrNull()
                    DeviceDetailsState.Connected(
                        readings = readings,
                        latestTemperature = latest?.temperature,
                        latestHumidity = latest?.humidity
                    )
                } else {
                    DeviceDetailsState.Idle
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceDetailsState.Idle)

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
                _historicalReadings.value = readings
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        val dev = device ?: return
        viewModelScope.launch {
            _connectionState.value = DeviceDetailsState.Connecting
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
                        _connectionState.value = DeviceDetailsState.Connected(readings = emptyList())
                    } else {
                        _connectionState.value = DeviceDetailsState.Connected(readings = emptyList())
                    }
                } else {
                    _connectionState.value = DeviceDetailsState.Error("Failed to connect to device")
                }
            } catch (e: Exception) {
                _connectionState.value = DeviceDetailsState.Error(e.message ?: "Connection error")
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
                    _connectionState.value = DeviceDetailsState.Error(message)
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

                    val currentState = uiState.value
                    val existingReadings = when (currentState) {
                        is DeviceDetailsState.PassiveMonitoring -> currentState.readings
                        is DeviceDetailsState.Connected -> currentState.readings
                        else -> emptyList()
                    }
                    _connectionState.value = DeviceDetailsState.PassiveMonitoring(
                        readings = (listOf(temperatureReading) + existingReadings).take(MAX_PASSIVE_MONITORING_READINGS),
                        latestTemperature = reading.temperature,
                        latestHumidity = reading.humidity,
                        batteryPercent = reading.batteryPercent
                    )
                }
            }
        })

        discoveryManager = manager
        manager.startScan(ScanSettings.SCAN_MODE_LOW_POWER)
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

    companion object {
        private const val MAX_PASSIVE_MONITORING_READINGS = 500
    }
}
