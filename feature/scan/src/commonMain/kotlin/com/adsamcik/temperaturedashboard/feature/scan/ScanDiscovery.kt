package com.adsamcik.temperaturedashboard.feature.scan

import com.adsamcik.temperaturedashboard.core.model.SensorAddress

/**
 * Display row for the scan list, pre-shaped from an
 * `InterpretedAdvertisement` in the shell layer so the feature module
 * doesn't depend on the BLE / decoder pipeline.
 */
data class ScanDiscovery(
    val address: SensorAddress,
    val displayName: String,
    val profileId: String,
    val profileLabel: String,
    val temperatureC: Double?,
    val humidityPct: Double?,
    val rssi: Int?,
)
