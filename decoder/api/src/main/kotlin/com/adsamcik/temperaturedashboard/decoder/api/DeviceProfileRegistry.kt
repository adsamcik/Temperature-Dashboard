package com.adsamcik.temperaturedashboard.decoder.api

/** Registry of all known device profiles. Order matters — the first match wins for display. */
class DeviceProfileRegistry(private val profiles: List<DeviceProfile>) {
    fun all(): List<DeviceProfile> = profiles

    fun matchingFor(snapshot: AdvertisementSnapshot): List<DeviceProfile> =
        profiles.filter { it.matches(snapshot) }

    fun firstMatch(snapshot: AdvertisementSnapshot): DeviceProfile? =
        profiles.firstOrNull { it.matches(snapshot) }
}
