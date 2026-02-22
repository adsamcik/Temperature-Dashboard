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

class ConnectedBleDevice(val gatt: BluetoothGatt) {
    private val TAG = "ConnectedBleDevice"

    private val notificationChannel = Channel<ByteArray>(capacity = 64)
    private val operationMutex = Mutex()

    private val readContinuations = mutableMapOf<UUID, CancellableContinuation<ByteArray?>>()
    private val writeContinuations = mutableMapOf<UUID, CancellableContinuation<Boolean>>()
    private val descriptorContinuations = mutableMapOf<UUID, CancellableContinuation<Boolean>>()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            notificationChannel.trySend(characteristic.value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val cont = readContinuations.remove(characteristic.uuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
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

    fun close() {
        notificationChannel.close()
        gatt.close()
    }

    fun getService(uuid: UUID): BluetoothGattService? = gatt.getService(uuid)

    fun getCharacteristic(serviceUuid: UUID, charUuid: UUID): BluetoothGattCharacteristic? {
        return getService(serviceUuid)?.getCharacteristic(charUuid)
    }

    suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray? =
        operationMutex.withLock {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val uuid = characteristic.uuid
                    readContinuations[uuid] = continuation
                    continuation.invokeOnCancellation {
                        readContinuations.remove(uuid)
                    }
                    if (!gatt.readCharacteristic(characteristic)) {
                        readContinuations.remove(uuid)?.resume(null)
                    }
                }
            }
        }

    @Suppress("DEPRECATION")
    suspend fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray): Boolean =
        operationMutex.withLock {
            withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val uuid = characteristic.uuid
                    writeContinuations[uuid] = continuation
                    continuation.invokeOnCancellation {
                        writeContinuations.remove(uuid)
                    }
                    characteristic.value = data
                    if (!gatt.writeCharacteristic(characteristic)) {
                        writeContinuations.remove(uuid)?.resume(false)
                    }
                }
            } ?: false
        }

    @Suppress("DEPRECATION")
    suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean =
        operationMutex.withLock {
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
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
                    if (!gatt.writeDescriptor(descriptor)) {
                        descriptorContinuations.remove(descriptor.uuid)?.resume(false)
                    }
                }
            } ?: false
        }

    @Suppress("DEPRECATION")
    suspend fun disableNotifications(characteristic: BluetoothGattCharacteristic): Boolean =
        operationMutex.withLock {
            if (!gatt.setCharacteristicNotification(characteristic, false)) {
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
                    if (!gatt.writeDescriptor(descriptor)) {
                        descriptorContinuations.remove(descriptor.uuid)?.resume(false)
                    }
                }
            } ?: false
        }

    suspend fun waitForNotification(timeoutMillis: Long = NOTIFICATION_TIMEOUT_MS): ByteArray? {
        return withTimeoutOrNull(timeoutMillis) {
            notificationChannel.receive()
        }
    }

    companion object {
        private const val OPERATION_TIMEOUT_MS = 5_000L
        private const val NOTIFICATION_TIMEOUT_MS = 5_000L
    }
}
