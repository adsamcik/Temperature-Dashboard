package com.adsamcik.temperaturedashboard.ble.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.adsamcik.temperaturedashboard.ble.api.BleConnection
import com.adsamcik.temperaturedashboard.ble.api.BleConnector
import com.adsamcik.temperaturedashboard.ble.api.ConnectionFailure
import com.adsamcik.temperaturedashboard.ble.api.ConnectionResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map as flowMap
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * `android.bluetooth.BluetoothGatt`-backed [BleConnection]. Designed for
 * short-lived backfill sessions — open, write a few opcodes, drain
 * notifications for a few seconds, close.
 *
 * Thread model: the BLE callbacks fire on a binder thread; we marshal each
 * event through CompletableDeferred / SharedFlow so the coroutines side stays
 * suspending and serial.
 */
class AndroidBleConnector(private val context: Context) : BleConnector {
    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): ConnectionResult.Connected {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw failure(ConnectionFailure.AdapterUnavailable, "BluetoothManager unavailable")
        val adapter = manager.adapter
            ?: throw failure(ConnectionFailure.AdapterUnavailable, "Bluetooth adapter unavailable")
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (ex: IllegalArgumentException) {
            throw failure(ConnectionFailure.AdapterUnavailable, "Invalid address: ${ex.message}")
        }
        val connection = AndroidBleConnection(context, device)
        connection.openAndDiscover()
        return ConnectionResult.Connected(connection)
    }
}

internal class AndroidBleConnection(
    private val context: Context,
    private val device: BluetoothDevice,
) : BleConnection {

    override val address: String get() = device.address.uppercase()

    private val notifyBus = MutableSharedFlow<NotifyEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var connectAwaiter: CompletableDeferred<Unit>? = null
    @Volatile private var discoveryAwaiter: CompletableDeferred<Unit>? = null
    @Volatile private var writeAwaiter: CompletableDeferred<Int>? = null
    @Volatile private var descriptorAwaiter: CompletableDeferred<Int>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
            Napier.d("GATT state $newState status $status", tag = LOG_TAG)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> connectAwaiter?.complete(Unit)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectAwaiter?.takeIf { !it.isCompleted }
                        ?.completeExceptionally(
                            failure(ConnectionFailure.Disconnected, "disconnect status $status"),
                        )
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) discoveryAwaiter?.complete(Unit)
            else discoveryAwaiter?.completeExceptionally(
                failure(ConnectionFailure.PlatformError, "service discovery status $status"),
            )
        }

        override fun onCharacteristicWrite(g: BluetoothGatt?, c: BluetoothGattCharacteristic?, status: Int) {
            writeAwaiter?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt?, d: BluetoothGattDescriptor?, status: Int) {
            descriptorAwaiter?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            notifyBus.tryEmit(NotifyEvent(c.service.uuid.toString().uppercase(), c.uuid.toString().uppercase(), value))
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt?, c: BluetoothGattCharacteristic?) {
            // Pre-Android 13 callback path
            if (c == null) return
            val bytes = c.value ?: return
            notifyBus.tryEmit(NotifyEvent(c.service.uuid.toString().uppercase(), c.uuid.toString().uppercase(), bytes))
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun openAndDiscover() {
        val connectDeferred = CompletableDeferred<Unit>()
        connectAwaiter = connectDeferred
        val transport = BluetoothDevice.TRANSPORT_LE
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, /* autoConnect = */ false, callback, transport)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback)
        }
        withTimeout(CONNECT_TIMEOUT_MS) { connectDeferred.await() }

        val discoveryDeferred = CompletableDeferred<Unit>()
        discoveryAwaiter = discoveryDeferred
        @SuppressLint("MissingPermission")
        val started = gatt?.discoverServices() ?: false
        check(started) { "discoverServices() returned false" }
        withTimeout(DISCOVERY_TIMEOUT_MS) { discoveryDeferred.await() }
    }

    override fun observeNotifications(serviceUuid: String, characteristicUuid: String): Flow<ByteArray> {
        // Best-effort enable notifications on subscription. Caller is expected
        // to collect this flow BEFORE issuing the write that triggers data.
        return notifyBus
            .asSharedFlow()
            .filter {
                it.serviceUuid.equals(serviceUuid, ignoreCase = true) &&
                    it.characteristicUuid.equals(characteristicUuid, ignoreCase = true)
            }
            .onStart { enableNotificationsAsync(serviceUuid, characteristicUuid) }
            .flowMap { it.payload }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotificationsAsync(serviceUuid: String, characteristicUuid: String) {
        val ch = findCharacteristic(serviceUuid, characteristicUuid) ?: return
        val g = gatt ?: return
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        payload: ByteArray,
        withResponse: Boolean,
    ): ConnectionResult {
        val g = gatt ?: return ConnectionResult.Failed(ConnectionFailure.Disconnected)
        val ch = findCharacteristic(serviceUuid, characteristicUuid)
            ?: return ConnectionResult.Failed(ConnectionFailure.CharacteristicNotFound)
        val deferred = CompletableDeferred<Int>()
        writeAwaiter = deferred
        val writeType = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val sent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, payload, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = payload
            @Suppress("DEPRECATION")
            ch.writeType = writeType
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
        if (!sent) return ConnectionResult.Failed(ConnectionFailure.PlatformError, "writeCharacteristic returned false")

        val status = withTimeoutOrNull(WRITE_TIMEOUT_MS) { deferred.await() }
            ?: return ConnectionResult.Failed(ConnectionFailure.Timeout, "write ack not received")
        return if (status == BluetoothGatt.GATT_SUCCESS) ConnectionResult.Ok
        else ConnectionResult.Failed(ConnectionFailure.PlatformError, "write status $status")
    }

    @SuppressLint("MissingPermission")
    override suspend fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private fun findCharacteristic(serviceUuid: String, characteristicUuid: String): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        val service = g.getService(UUID.fromString(serviceUuid)) ?: return null
        return service.getCharacteristic(UUID.fromString(characteristicUuid))
    }

    private data class NotifyEvent(val serviceUuid: String, val characteristicUuid: String, val payload: ByteArray)

    private companion object {
        const val LOG_TAG = "AndroidBleConnection"
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val WRITE_TIMEOUT_MS = 5_000L
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

private fun failure(reason: ConnectionFailure, message: String): IllegalStateException =
    IllegalStateException("BLE $reason: $message")

