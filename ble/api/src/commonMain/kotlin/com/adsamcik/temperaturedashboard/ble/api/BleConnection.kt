package com.adsamcik.temperaturedashboard.ble.api

import kotlinx.coroutines.flow.Flow

/**
 * Active (connected) GATT session to one BLE device, for protocols that
 * can't be served from passive advertisements alone.
 *
 * The current consumer is ThermoPro history backfill — `TP35xProfile.actions`
 * defines opcodes for day / week / year history; the runtime fires those
 * via [writeCharacteristic] and consumes the resulting notifications via
 * [observeNotifications].
 *
 * Lifecycle: open via [BleConnector.connect] → use → [close]. Disconnect
 * is automatic on close or on the consumer cancelling the [Flow].
 */
interface BleConnection {
    /** Address of the device this connection is talking to. */
    val address: String

    /** Bytes the device sends over the given notify characteristic. */
    fun observeNotifications(serviceUuid: String, characteristicUuid: String): Flow<ByteArray>

    /**
     * Write [payload] to the given characteristic. [withResponse] picks
     * WRITE vs WRITE_WITHOUT_RESPONSE.
     */
    suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        payload: ByteArray,
        withResponse: Boolean = true,
    ): ConnectionResult

    suspend fun close()
}

/** Connects to a known device. Implementations live next to [BleScanner]. */
interface BleConnector {
    suspend fun connect(address: String): ConnectionResult.Connected
}

sealed interface ConnectionResult {
    /** A live [BleConnection] — caller owns its lifecycle. */
    data class Connected(val connection: BleConnection) : ConnectionResult
    data object Ok : ConnectionResult
    data class Failed(val reason: ConnectionFailure, val message: String? = null) : ConnectionResult
}

enum class ConnectionFailure {
    NotSupported,        // Desktop where the JNI shim isn't wired up
    AdapterUnavailable,
    PermissionDenied,
    Timeout,
    Disconnected,
    ServiceNotFound,
    CharacteristicNotFound,
    PlatformError,
}
