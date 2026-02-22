package com.adsamcik.temperaturedashboard.ui.state

import com.adsamcik.temperaturedashboard.storage.TemperatureReading

sealed interface DeviceDetailsState {
    data object Idle : DeviceDetailsState
    data object Connecting : DeviceDetailsState
    data class Connected(
        val readings: List<TemperatureReading>,
        val latestTemperature: Double? = null,
        val latestHumidity: Double? = null
    ) : DeviceDetailsState
    data class Error(val message: String) : DeviceDetailsState
}
