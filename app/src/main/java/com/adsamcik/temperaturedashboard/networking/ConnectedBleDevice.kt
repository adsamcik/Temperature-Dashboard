package com.adsamcik.temperaturedashboard.networking

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class ConnectedBleDevice {
    private val notificationChannel = Channel<ByteArray>(capacity = 64)
    private val operationMutex = Mutex()

    private val readContinuations = mutableMapOf<UUID, CancellableContinuation<ByteArray?>>()
    private val writeContinuations = mutableMapOf<UUID, CancellableContinuation<Boolean>>()
    private val descriptorContinuations = mutableMapOf<UUID, CancellableContinuation<Boolean>>()

    // Connection state managed via continuation
    private var connectionContinuation: CancellableContinuation<BluetoothGatt?>? = null

    var gatt: BluetoothGatt? = null
        private set

    /**
     * The GATT callback that MUST be used when calling connectGatt().
     * This ensures all read/write/notify operations receive their callbacks.
     */
    val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                connectionContinuation?.let {
                    if (it.isActive) it.resume(null)
                }
                connectionContinuation = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            connectionContinuation?.let {
                if (it.isActive) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        this@ConnectedBleDevice.gatt = gatt
                        it.resume(gatt)
                    } else {
                        it.resume(null)
                    }
                }
            }
            connectionContinuation = null
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            notificationChannel.trySend(characteristic.value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val cont = readContinuations.remove(characteristic.uuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                cont?.resume(characteristic.value)
            } else {
                cont?.resume(null)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val cont = writeContinuations.remove(characteristic.uuid)
            cont?.resume(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val cont = descriptorContinuations.remove(descriptor.uuid)
            cont?.resume(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    /**
     * Suspends until GATT connection + service discovery completes.
     * Call connectGatt() with [callback] BEFORE calling this.
     */
    suspend fun awaitConnection(gattInstance: BluetoothGatt): Boolean {
        return withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            suspendCancellableCoroutine<BluetoothGatt?> { continuation ->
                connectionContinuation = continuation
                continuation.invokeOnCancellation {
                    connectionContinuation = null
                    gattInstance.close()
                }
            }
        } != null
    }

    fun close() {
        notificationChannel.close()
        gatt?.close()
        gatt = null
    }

    fun getService(uuid: UUID): BluetoothGattService? = gatt?.getService(uuid)

    fun getCharacteristic(serviceUuid: UUID, charUuid: UUID): BluetoothGattCharacteristic? {
        return getService(serviceUuid)?.getCharacteristic(charUuid)
    }

    suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray? {
        val g = gatt ?: return null
        return operationMutex.withLock {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val uuid = characteristic.uuid
                    readContinuations[uuid] = continuation
                    continuation.invokeOnCancellation {
                        readContinuations.remove(uuid)
                    }
                    if (!g.readCharacteristic(characteristic)) {
                        readContinuations.remove(uuid)?.resume(null)
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        val g = gatt ?: return false
        return operationMutex.withLock {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val uuid = characteristic.uuid
                    writeContinuations[uuid] = continuation
                    continuation.invokeOnCancellation {
                        writeContinuations.remove(uuid)
                    }
                    characteristic.value = data
                    if (!g.writeCharacteristic(characteristic)) {
                        writeContinuations.remove(uuid)?.resume(false)
                    }
                }
            } ?: false
        }
    }

    @Suppress("DEPRECATION")
    suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean {
        val g = gatt ?: return false
        return operationMutex.withLock {
            if (!g.setCharacteristicNotification(characteristic, true)) {
                return@withLock false
            }

            val descriptor = characteristic.descriptors.firstOrNull() ?: return@withLock false

            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    descriptorContinuations[descriptor.uuid] = continuation
                    continuation.invokeOnCancellation {
                        descriptorContinuations.remove(descriptor.uuid)
                    }
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (!g.writeDescriptor(descriptor)) {
                        descriptorContinuations.remove(descriptor.uuid)?.resume(false)
                    }
                }
            } ?: false
        }
    }

    @Suppress("DEPRECATION")
    suspend fun disableNotifications(characteristic: BluetoothGattCharacteristic): Boolean {
        val g = gatt ?: return false
        return operationMutex.withLock {
            if (!g.setCharacteristicNotification(characteristic, false)) {
                return@withLock false
            }

            val descriptor = characteristic.descriptors.firstOrNull() ?: return@withLock false

            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    descriptorContinuations[descriptor.uuid] = continuation
                    continuation.invokeOnCancellation {
                        descriptorContinuations.remove(descriptor.uuid)
                    }
                    descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    if (!g.writeDescriptor(descriptor)) {
                        descriptorContinuations.remove(descriptor.uuid)?.resume(false)
                    }
                }
            } ?: false
        }
    }

    suspend fun waitForNotification(timeoutMillis: Long = NOTIFICATION_TIMEOUT_MS): ByteArray? {
        return withTimeoutOrNull(timeoutMillis) {
            notificationChannel.receive()
        }
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 5_000L
        private const val NOTIFICATION_TIMEOUT_MS = 5_000L
        private const val CONNECTION_TIMEOUT_MS = 15_000L
    }
}
