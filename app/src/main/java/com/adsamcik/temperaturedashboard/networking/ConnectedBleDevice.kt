package com.adsamcik.temperaturedashboard.networking

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ConnectedBleDevice(private val gatt: BluetoothGatt) {
    private val TAG = "ConnectedBleDevice"
    private val ioDispatcher = Dispatchers.IO

    // A single CompletableDeferred for demonstration of receiving one notification.
    // For continuous notifications, consider a Channel or SharedFlow.
    private var notificationData = CompletableDeferred<ByteArray?>()

    // Maps for pending operations
    private val readContinuations = mutableMapOf<UUID, Continuation<ByteArray?>>()
    private val writeContinuations = mutableMapOf<UUID, Continuation<Boolean>>()
    private val descriptorContinuations = mutableMapOf<UUID, Continuation<Boolean>>()

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

    init {
        // In a real scenario, you should supply gattCallback when connecting:
        // device.connectGatt(context, false, gattCallback)
        // Here we assume gatt already has this callback. If not, you'd need reflection or
        // to ensure the callback is set at connection time.
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
            // Store the continuation, so we can resume it in onCharacteristicRead
            readContinuations[uuid] = continuation
            if (!gatt.readCharacteristic(characteristic)) {
                readContinuations.remove(uuid)?.resume(null)
            }
        }
    }

    suspend fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray): Boolean = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val uuid = characteristic.uuid
            writeContinuations[uuid] = continuation
            characteristic.value = data
            if (!gatt.writeCharacteristic(characteristic)) {
                // If writeCharacteristic returned false immediately, remove and resume false
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
