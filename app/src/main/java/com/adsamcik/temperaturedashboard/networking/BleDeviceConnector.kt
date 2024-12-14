package com.adsamcik.temperaturedashboard.networking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.data.ViewDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Handles common BLE operations such as:
 * - Connecting to a device
 * - Discovering services
 * - Finding and initializing characteristics
 * - Reading/writing characteristics
 * - Enabling and handling notifications
 *
 * Device-specific logic is delegated to a provided [BleDeviceHandler].
 */
class BleDeviceConnector(
    private val context: Context,
    private val viewDevice: ViewDevice
) {
    private val TAG = "BleDeviceConnector"

    // Timeouts for BLE operations (in milliseconds)
    private val SERVICE_DISCOVERY_TIMEOUT_MS = 25_000L
    private val NOTIFICATION_DATA_TIMEOUT_MS = 25_000L

    private var bluetoothGatt: BluetoothGatt? = null

    // Characteristics for read and write
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // Deferred used to track service discovery completion
    private var servicesDiscoveredDeferred = CompletableDeferred<Boolean>()

    // The device-specific handler (decoder/logic)
    private val handler = viewDevice.decoder
        ?: throw IllegalArgumentException("ViewDevice.decoder (BleDeviceHandler) must not be null")

    // Continuations for async writes
    private var characteristicWriteContinuation: CancellableContinuation<Boolean>? = null
    private var descriptorWriteContinuation: CancellableContinuation<Boolean>? = null

    // Deferred for notification data
    private var notificationData = CompletableDeferred<ByteArray?>()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            when {
                newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    Log.d(TAG, "Connected to device. Discovering services...")
                    gatt.discoverServices()
                }

                newState == BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.e(TAG, "Disconnected from device.")
                    cleanupOnDisconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (initializeCharacteristics(gatt)) {
                    bluetoothGatt = gatt
                    Log.d(TAG, "Services discovered and characteristics initialized.")
                    servicesDiscoveredDeferred.complete(true)
                } else {
                    Log.e(TAG, "Required characteristics not found. Closing GATT.")
                    gatt.close()
                    servicesDiscoveredDeferred.complete(false)
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                gatt.close()
                servicesDiscoveredDeferred.complete(false)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            characteristicWriteContinuation?.resume(success)
            characteristicWriteContinuation = null
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            descriptorWriteContinuation?.resume(success)
            descriptorWriteContinuation = null
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Notification data received
            if (!notificationData.isCompleted) {
                notificationData.complete(characteristic.value)
            }
        }
    }

    /**
     * Connects to the specified device using BLE. Attempts service discovery,
     * and ensures that required characteristics are available.
     *
     * @return true if connection and initialization were successful, false otherwise.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    suspend fun connect(): Boolean {
        val device = viewDevice.device
        if (!handler.isCompatible(device)) {
            Log.e(TAG, "Device not compatible with the provided handler.")
            return false
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: run {
                Log.e(TAG, "BluetoothManager not available.")
                return false
            }

        val bluetoothAdapter = bluetoothManager.adapter ?: run {
            Log.e(TAG, "BluetoothAdapter not available.")
            return false
        }

        val btDevice = try {
            bluetoothAdapter.getRemoteDevice(device.macAddress)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address: ${e.message}")
            return false
        } ?: run {
            Log.e(TAG, "BluetoothDevice not found for the given MAC address.")
            return false
        }

        // Reset the deferred for each new connection attempt
        servicesDiscoveredDeferred = CompletableDeferred()

        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val gatt = btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

                // Handle coroutine cancellation
                continuation.invokeOnCancellation {
                    gatt.close()
                }

                // Once connecting, we wait for services to be discovered or time out
                launch(Dispatchers.IO) {
                    val result = withTimeoutOrNull(SERVICE_DISCOVERY_TIMEOUT_MS) {
                        servicesDiscoveredDeferred.await()
                    }

                    if (result == true) {
                        continuation.resume(true)
                    } else {
                        Log.e(TAG, "Service discovery timed out or failed.")
                        gatt.close()
                        continuation.resume(false)
                    }
                }
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
        Log.d(TAG, "Disconnected and cleaned up resources.")
    }

    /**
     * Requests raw data from the device by:
     * 1. Enabling notifications on the read characteristic
     * 2. Sending a request command via the write characteristic
     * 3. Awaiting notification data
     *
     * @return The raw data as a ByteArray, or null if not available.
     */
    suspend fun requestRawData(): ByteArray? = withContext(Dispatchers.IO) {
        resetNotificationData()

        val gatt = bluetoothGatt ?: run {
            Log.e(TAG, "Not connected to a device.")
            return@withContext null
        }

        val readChar = readCharacteristic ?: run {
            Log.e(TAG, "Read characteristic not initialized.")
            return@withContext null
        }

        val writeChar = writeCharacteristic ?: run {
            Log.e(TAG, "Write characteristic not initialized.")
            return@withContext null
        }

        if (!enableNotifications(gatt, readChar)) {
            Log.e(TAG, "Failed to enable notifications on read characteristic.")
            return@withContext null
        }

        val requestCommand = handler.getRequestCommand()
        if (!writeToCharacteristic(gatt, writeChar, requestCommand)) {
            Log.e(TAG, "Failed to write request command.")
            disableNotifications(gatt, readChar)
            return@withContext null
        }

        val data = try {
            withTimeoutOrNull(NOTIFICATION_DATA_TIMEOUT_MS) { notificationData.await() }
        } catch (e: CancellationException) {
            Log.e(TAG, "Notification data wait was cancelled: ${e.message}")
            null
        }

        // Disable notifications after receiving data
        if (!disableNotifications(gatt, readChar)) {
            Log.w(TAG, "Failed to disable notifications. Continuing anyway.")
        }

        Log.d(TAG, "Received raw data: ${data?.contentToString()}")
        data
    }


    /**
     * Convenience method to:
     * 1. Request raw data from the device
     * 2. Decode it into [TemperatureHumidityData] using the handler
     *
     * @return Decoded [TemperatureHumidityData] or null if not successful.
     */
    suspend fun readData(): TemperatureHumidityData? {
        val rawData = requestRawData() ?: return null
        return handler.decodeData(rawData)
    }

    /**
     * Initializes characteristics by searching through the discovered services.
     */
    private fun initializeCharacteristics(gatt: BluetoothGatt): Boolean {
        val readUuid = handler.getReadCharacteristicUuid()
        val writeUuid = handler.getWriteCharacteristicUuid()

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

    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            return false
        }
        val descriptor = characteristic.descriptors.firstOrNull() ?: return false
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return writeDescriptor(gatt, descriptor)
    }

    @SuppressLint("MissingPermission")
    private suspend fun disableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        gatt.setCharacteristicNotification(characteristic, false)
        val descriptor = characteristic.descriptors.firstOrNull() ?: return false
        descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        return writeDescriptor(gatt, descriptor)
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeToCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean = suspendCancellableCoroutine { continuation ->
        if (characteristicWriteContinuation != null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        characteristic.value = data
        if (!gatt.writeCharacteristic(characteristic)) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        characteristicWriteContinuation = continuation
        continuation.invokeOnCancellation {
            Log.d(TAG, "Coroutine cancelled during characteristic write.")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor
    ): Boolean = suspendCancellableCoroutine { continuation ->
        if (descriptorWriteContinuation != null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        if (!gatt.writeDescriptor(descriptor)) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        descriptorWriteContinuation = continuation
        continuation.invokeOnCancellation {
            Log.d(TAG, "Coroutine cancelled during descriptor write.")
        }
    }

    /**
     * Cleans up resources when disconnected, ensuring any ongoing operations are resumed
     * with failure if necessary.
     */
    private fun cleanupOnDisconnect() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        readCharacteristic = null
        writeCharacteristic = null

        // Resume any pending operations with failure
        characteristicWriteContinuation?.resume(false)
        characteristicWriteContinuation = null
        descriptorWriteContinuation?.resume(false)
        descriptorWriteContinuation = null
    }

    private fun resetNotificationData() {
        notificationData = CompletableDeferred<ByteArray?>()
    }
}
