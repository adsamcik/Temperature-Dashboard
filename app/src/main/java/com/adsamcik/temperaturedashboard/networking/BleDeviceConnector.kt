package com.adsamcik.temperaturedashboard.networking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.data.ViewDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * This class handles the common BLE operations:
 * - Connecting to a device
 * - Discovering services
 * - Finding characteristics
 * - Reading/writing characteristics
 * - Handling notifications
 *
 * It delegates device-specific logic to the given [BleDeviceHandler].
 */
class BleDeviceConnector(
    private val context: Context,
    private val viewDevice: ViewDevice
) {
    private val TAG = "BleDeviceConnector"

    private var bluetoothGatt: BluetoothGatt? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val handler = viewDevice.decoder
        ?: throw IllegalArgumentException("ViewDevice.decoder (BleDeviceHandler) must not be null")

    private val notificationData = CompletableDeferred<ByteArray?>()

    /**
     * Connects to the specified device using the handler's logic.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    suspend fun connect(): Boolean {
        val device = viewDevice.device
        if (!handler.isCompatible(device)) {
            Log.e(TAG, "Device not compatible with handler.")
            return false
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter ?: return false
        val btDevice = bluetoothAdapter.getRemoteDevice(device.macAddress) ?: return false

        return suspendCancellableCoroutine { continuation ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Connected. Discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        Log.e(TAG, "Disconnected from device.")
                        continuation.resume(false, null) // Pass null if no cancellation logic is needed
                        gatt.close()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (initializeCharacteristics(gatt)) {
                            bluetoothGatt = gatt
                            Log.d(TAG, "Services discovered and characteristics found.")
                            continuation.resume(true, null)
                        } else {
                            Log.e(TAG, "Required characteristics not found.")
                            gatt.close()
                            continuation.resume(false, null)
                        }
                    } else {
                        Log.e(TAG, "Service discovery failed: $status")
                        gatt.close()
                        continuation.resume(false, null)
                    }
                }
            }

            btDevice.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

            continuation.invokeOnCancellation {
                // Handle resource cleanup on coroutine cancellation
                bluetoothGatt?.close()
            }
        }
    }

    /**
     * Disconnects from the device and releases resources.
     */
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        readCharacteristic = null
        writeCharacteristic = null
        Log.d(TAG, "Disconnected and resources cleaned up.")
    }

    /**
     * Requests raw data from the device by:
     * - Enabling notifications on the read characteristic
     * - Writing the request command to the write characteristic
     * - Waiting for the notification data
     */
    suspend fun requestRawData(): ByteArray? = withContext(Dispatchers.IO) {
        val gatt = bluetoothGatt ?: run {
            Log.e(TAG, "Not connected to any device.")
            return@withContext null
        }

        val readChar = readCharacteristic ?: return@withContext null
        val writeChar = writeCharacteristic ?: return@withContext null

        notificationData.complete(null) // reset for a new request

        if (!enableNotifications(gatt, readChar)) {
            Log.e(TAG, "Failed to enable notifications.")
            return@withContext null
        }

        // Write the command to request data
        val command = handler.getRequestCommand()
        if (!writeToCharacteristic(gatt, writeChar, command)) {
            Log.e(TAG, "Failed to write command to request data.")
            disableNotifications(gatt, readChar)
            return@withContext null
        }

        // Await notification data
        val data = try {
            notificationData.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for notification data: ${e.message}")
            null
        }

        disableNotifications(gatt, readChar)
        data
    }

    /**
     * A convenience method that requests raw data from the device and then uses the handler to decode it.
     */
    suspend fun readData(): TemperatureHumidityData? {
        val rawData = requestRawData() ?: return null
        return handler.decodeData(rawData)
    }

    private fun initializeCharacteristics(gatt: BluetoothGatt): Boolean {
        val readUuid = handler.getReadCharacteristicUuid()
        val writeUuid = handler.getWriteCharacteristicUuid()

        // Try to find characteristics in any of the discovered services.
        for (service in gatt.services) {
            val rChar = service.getCharacteristic(readUuid)
            val wChar = service.getCharacteristic(writeUuid)
            if (rChar != null && wChar != null) {
                readCharacteristic = rChar
                writeCharacteristic = wChar
                return true
            }
        }
        return false
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            return false
        }

        val descriptor = characteristic.descriptors.firstOrNull()
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt.writeDescriptor(descriptor)
    }

    @SuppressLint("MissingPermission")
    private fun disableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        gatt.setCharacteristicNotification(characteristic, false)
        val descriptor = characteristic.descriptors.firstOrNull()
        descriptor?.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        return gatt.writeDescriptor(descriptor)
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeToCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean = suspendCancellableCoroutine { continuation ->
        characteristic.value = data
        val success = gatt.writeCharacteristic(characteristic)
        if (!success) {
            continuation.resume(false, null)
        } else {
            continuation.invokeOnCancellation {
                Log.d(TAG, "Coroutine cancelled during characteristic write.")
                // Additional cleanup logic if needed
            }
            continuation.resume(true, null)
        }
    }

}