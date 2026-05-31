package com.adsamcik.temperaturedashboard.ble.desktop

import com.adsamcik.temperaturedashboard.ble.api.BleConnection
import com.adsamcik.temperaturedashboard.ble.api.ConnectionFailure
import com.adsamcik.temperaturedashboard.ble.api.ConnectionResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Desktop-side [BleConnection] backed by the `btleplug_jni` shim. The
 * connection owns a polling coroutine that drains the per-connection
 * notification queue from the Rust side and republishes events into a
 * [SharedFlow] that consumers filter by `(serviceUuid, characteristicUuid)`.
 *
 * Subscriptions are sticky: calling [observeNotifications] on a new flow
 * implicitly issues a `btleplug_subscribe` for the targeted characteristic
 * if not yet subscribed. The subscribe set is tracked locally so reopening
 * a flow doesn't subscribe twice.
 */
internal class DesktopBleConnection(
    private val native: BtleplugNative,
    private val connectionId: Int,
    override val address: String,
) : BleConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = MutableSharedFlow<Notification>(extraBufferCapacity = 256)
    private val subscribed = mutableSetOf<Pair<String, String>>()
    private val pumpJob = scope.launch { pump() }

    override fun observeNotifications(
        serviceUuid: String,
        characteristicUuid: String,
    ): Flow<ByteArray> {
        val svc = serviceUuid.lowercase()
        val chr = characteristicUuid.lowercase()
        val key = svc to chr
        synchronized(subscribed) {
            if (key !in subscribed) {
                val rc = native.subscribe(connectionId, svc, chr)
                if (rc != 0) {
                    Napier.w("btleplug_subscribe rc=$rc for $svc/$chr")
                }
                subscribed.add(key)
            }
        }
        return incoming
            .filter { it.serviceUuid == svc && it.characteristicUuid == chr }
            .map { it.bytes }
    }

    override suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        payload: ByteArray,
        withResponse: Boolean,
    ): ConnectionResult {
        val rc = native.writeCharacteristic(
            connId = connectionId,
            serviceUuid = serviceUuid.lowercase(),
            characteristicUuid = characteristicUuid.lowercase(),
            payload = payload,
            withResponse = withResponse,
        )
        return when (rc) {
            0 -> ConnectionResult.Ok
            -5 -> ConnectionResult.Failed(ConnectionFailure.Disconnected, "Connection $connectionId is gone")
            -6 -> ConnectionResult.Failed(ConnectionFailure.ServiceNotFound, serviceUuid)
            -7 -> ConnectionResult.Failed(ConnectionFailure.CharacteristicNotFound, characteristicUuid)
            else -> ConnectionResult.Failed(ConnectionFailure.PlatformError, "rc=$rc")
        }
    }

    override suspend fun close() {
        runCatching { native.disconnect(connectionId) }
            .onFailure { Napier.w("btleplug_disconnect failed: ${it.message}") }
        pumpJob.cancel()
        scope.cancel()
    }

    private suspend fun pump() {
        val buf = ByteArray(BtleplugNative.NOTIFICATION_BUFFER_BYTES)
        while (true) {
            val n = native.nextNotification(connectionId, buf)
            when {
                n > 0 -> {
                    val raw = String(buf, 0, n, Charsets.UTF_8)
                    val parsed = runCatching {
                        Json { ignoreUnknownKeys = true }.decodeFromString<NotificationJson>(raw)
                    }.getOrNull()
                    if (parsed != null) {
                        incoming.tryEmit(
                            Notification(
                                serviceUuid = parsed.serviceUuid.lowercase(),
                                characteristicUuid = parsed.characteristicUuid.lowercase(),
                                bytes = parsed.bytes.hexToBytes(),
                            ),
                        )
                    } else {
                        Napier.w("Failed to parse notification JSON: $raw")
                    }
                }
                n == -1 -> delay(BtleplugNative.POLL_INTERVAL_MS)
                n == -5 -> {
                    Napier.d("Connection $connectionId no longer present; stopping pump")
                    return
                }
                else -> {
                    Napier.w("btleplug_next_notification rc=$n")
                    delay(BtleplugNative.POLL_INTERVAL_MS_ON_ERROR)
                }
            }
        }
    }

    private data class Notification(
        val serviceUuid: String,
        val characteristicUuid: String,
        val bytes: ByteArray,
    )

    @Serializable
    private data class NotificationJson(
        val serviceUuid: String,
        val characteristicUuid: String,
        val bytes: String,
    )
}

private fun String.hexToBytes(): ByteArray {
    if (isEmpty()) return ByteArray(0)
    val clean = replace(" ", "")
    require(clean.length % 2 == 0) { "hex string must have even length" }
    val out = ByteArray(clean.length / 2)
    for (i in out.indices) {
        out[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
            Character.digit(clean[i * 2 + 1], 16)).toByte()
    }
    return out
}
