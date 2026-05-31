package com.adsamcik.temperaturedashboard.decoder.api

/**
 * A high-level recogniser for a *device* (rather than a single characteristic).
 *
 * A profile matches a device by some combination of advertised name,
 * advertised service UUIDs, and manufacturer-data company IDs. When matched,
 * it can:
 *
 *  1. Decode passive BLE advertisements into a list of [DecodedField]s
 *     (e.g. TP357 broadcasts temperature/humidity/battery directly in its
 *     manufacturer data).
 *  2. Expose a list of [ProfileAction]s — one-tap interactions the app
 *     can fire against a connected device. These let users exercise the
 *     vendor-specific protocol without having to remember opcode hex.
 *
 * Profiles are intentionally additive — multiple profiles can match the
 * same device. The first match wins for display purposes, but every match's
 * actions surface in the Smart Actions UI.
 */
interface DeviceProfile {
    val id: String
    val displayName: String

    /** True if this profile recognises the advertised device. */
    fun matches(snapshot: AdvertisementSnapshot): Boolean

    /** Optional: decode passive advertisement payload into human-readable fields. */
    fun decodeAdvertisement(snapshot: AdvertisementSnapshot): List<DecodedField> = emptyList()

    /** Optional: predefined GATT interactions a user can fire from the UI. */
    val actions: List<ProfileAction> get() = emptyList()

    /**
     * Optional: decode one raw notification packet captured as a response
     * to [actionId] into human-readable fields. Returning `null` means
     * "no parsed structure" — the UI then falls back to rendering the raw
     * hex. Profiles use this for vendor-specific bulk-response formats
     * (e.g. TP35x history dumps where each notification carries N
     * temperature + humidity samples).
     */
    fun parseActionResponse(actionId: String, packet: ByteArray): List<DecodedField> = emptyList()
}

/**
 * A minimal snapshot of what we know about an advertising device, suitable
 * for matching and decoding. Decoder profiles only need a few fields, so we
 * keep this DTO small and decoupled from the larger app-side scan model.
 *
 * [serviceData] keys are full 128-bit UUIDs (uppercase). Use
 * [UuidMatcher.matches] to compare against 16-bit short forms.
 */
data class AdvertisementSnapshot(
    val name: String?,
    val advertisedServiceUuids: List<String>,
    val manufacturerData: Map<Int, ByteArray>,
    val serviceData: Map<String, ByteArray> = emptyMap(),
)

/**
 * A one-tap interaction the user can fire from the Smart Actions UI when the
 * profile matches. The action writes [payload] to the given GATT
 * characteristic; if the device's response arrives via notification on a
 * different characteristic (a common pattern for vendor history protocols),
 * declare it in [awaitResponseOn] so the BLE layer auto-subscribes before
 * writing and the resulting notify payload is rendered by whatever
 * [Decoder] matches.
 */
data class ProfileAction(
    val id: String,
    val label: String,
    val description: String,
    val serviceUuid: String,
    val characteristicUuid: String,
    val payload: ByteArray,
    val withResponse: Boolean = true,
    val awaitResponseOn: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is ProfileAction &&
        id == other.id && label == other.label && description == other.description &&
        serviceUuid == other.serviceUuid && characteristicUuid == other.characteristicUuid &&
        payload.contentEquals(other.payload) && withResponse == other.withResponse &&
        awaitResponseOn == other.awaitResponseOn

    override fun hashCode(): Int {
        var h = id.hashCode()
        h = 31 * h + label.hashCode()
        h = 31 * h + description.hashCode()
        h = 31 * h + serviceUuid.hashCode()
        h = 31 * h + characteristicUuid.hashCode()
        h = 31 * h + payload.contentHashCode()
        h = 31 * h + withResponse.hashCode()
        h = 31 * h + (awaitResponseOn?.hashCode() ?: 0)
        return h
    }
}
