package com.adsamcik.temperaturedashboard.networking

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class ConnectedBleDevice(val gatt: BluetoothGatt) {
    private val TAG = "ConnectedBleDevice"
    private val ioDispatcher = Dispatchers.IO

    private var notificationData = CompletableDeferred<ByteArray?>()

    private val readContinuations = mutableMapOf<UUID, CancellableContinuation<ByteArray?>>()
    private val writeContinuations = mutableMapOf<UUID, CancellableContinuation<Boolean>>()
    private val descriptorContinuations = mutableMapOf<UUID, CancellableContinuation<Boolean>>()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (!notificationData.isCompleted) {
                notificationData.complete(characteristic.value)
            }
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
        gatt.close()
    }

    fun getService(uuid: UUID): BluetoothGattService? = gatt.getService(uuid)

    fun getCharacteristic(serviceUuid: UUID, charUuid: UUID): BluetoothGattCharacteristic? {
        return getService(serviceUuid)?.getCharacteristic(charUuid)
    }

    suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray? = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val uuid = characteristic.uuid
            readContinuations[uuid] = continuation
            continuation.invokeOnCancellation {
                readContinuations.remove(uuid)?.resume(null)
            }
            if (!gatt.readCharacteristic(characteristic)) {
                readContinuations.remove(uuid)?.resume(null)
            }
        }
    }

    suspend fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray): Boolean = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val uuid = characteristic.uuid
            writeContinuations[uuid] = continuation
            continuation.invokeOnCancellation {
                writeContinuations.remove(uuid)?.resume(false)
            }
            characteristic.value = data
            if (!gatt.writeCharacteristic(characteristic)) {
                writeContinuations.remove(uuid)?.resume(false)
            }
        }
    }

    suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean = withContext(ioDispatcher) {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            return@withContext false
        }

        val descriptor = characteristic.descriptors.firstOrNull() ?: return@withContext false
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        suspendCancellableCoroutine { continuation ->
            descriptorContinuations[descriptor.uuid] = continuation
            continuation.invokeOnCancellation {
                descriptorContinuations.remove(descriptor.uuid)?.resume(false)
            }
            if (!gatt.writeDescriptor(descriptor)) {
                descriptorContinuations.remove(descriptor.uuid)?.resume(false)
            }
        }
    }

    suspend fun disableNotifications(characteristic: BluetoothGattCharacteristic): Boolean = withContext(ioDispatcher) {
        if (!gatt.setCharacteristicNotification(characteristic, false)) {
            return@withContext false
        }

        val descriptor = characteristic.descriptors.firstOrNull() ?: return@withContext false
        descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

        suspendCancellableCoroutine { continuation ->
            descriptorContinuations[descriptor.uuid] = continuation
            continuation.invokeOnCancellation {
                descriptorContinuations.remove(descriptor.uuid)?.resume(false)
            }
            if (!gatt.writeDescriptor(descriptor)) {
                descriptorContinuations.remove(descriptor.uuid)?.resume(false)
            }
        }
    }

    fun resetNotificationData() {
        notificationData = CompletableDeferred()
    }

    suspend fun waitForNotification(timeoutMillis: Long = 5000): ByteArray? {
        return withTimeoutOrNull(timeoutMillis) {
            val data = notificationData.await()
            resetNotificationData()
            data
        }
    }
}
