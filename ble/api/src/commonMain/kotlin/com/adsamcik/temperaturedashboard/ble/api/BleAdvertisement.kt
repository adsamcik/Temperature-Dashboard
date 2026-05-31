package com.adsamcik.temperaturedashboard.ble.api

import kotlinx.datetime.Instant

/**
 * A single BLE advertisement event observed by the scanner.
 *
 * Platform-agnostic shape: every backend (android.bluetooth, btleplug-via-JNI)
 * maps its native event into this DTO before crossing the [BleScanner] boundary.
 *
 * Decoder integration lives upstream in `:shared` — `:ble:api` deliberately
 * doesn't depend on `:decoder:api` so the two can evolve independently.
 */
data class BleAdvertisement(
    /** Platform-stable identifier — MAC on Android/Linux, UUID on Apple, device-id on Windows. */
    val address: String,
    /** Local name from the advertising packet, if any. */
    val name: String?,
    /** Received Signal Strength Indicator in dBm. Typical range -100..-30. */
    val rssi: Int,
    /** When the advertisement was observed by the scanner (host clock). */
    val timestamp: Instant,
    /** 128-bit service UUIDs the device claims to expose. */
    val serviceUuids: List<String>,
    /** `companyId → bytes` manufacturer-specific data. */
    val manufacturerData: Map<Int, ByteArray>,
    /** `serviceUuid → bytes` service-specific data. */
    val serviceData: Map<String, ByteArray>,
)
