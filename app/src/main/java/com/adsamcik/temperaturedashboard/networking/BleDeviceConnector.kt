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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.coroutines.resume

class BleDeviceConnector(
    private val context: Context,
    private val device: ViewDevice,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "BleDeviceConnector"
    private var connectedDevice: ConnectedBleDevice? = null
    private val genericClient = GenericBleClient(context, ioDispatcher)

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    suspend fun connect(): Boolean {
        return withContext(ioDispatcher) {
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    ?: return@withContext false
                val bluetoothAdapter = bluetoothManager.adapter ?: return@withContext false

                val btDevice = try {
                    bluetoothAdapter.getRemoteDevice(device.device.macAddress)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid MAC address: ${e.message}")
                    return@withContext false
                }

                val gatt = suspendCancellableCoroutine<BluetoothGatt?> { continuation ->
                    val gattCallback = object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                                gatt.discoverServices()
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                if (continuation.isActive) {
                                    continuation.resume(gatt)
                                }
                            } else {
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    }

                    val gattObj = btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    continuation.invokeOnCancellation {
                        gattObj.close()
                    }
                }

                if (gatt != null) {
                    connectedDevice = ConnectedBleDevice(gatt)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device", e)
                false
            }
        }
    }

    suspend fun readData(mode: ApiMode = ApiMode.LATEST): List<TemperatureHumidityData> {
        val connected = connectedDevice ?: throw IllegalStateException("Device not connected")
        
        return if (device.decoder != null) {
            // Use specific decoder for known device types
            device.decoder.retrieveData(connected, mode)
        } else {
            // For unknown devices, try generic analysis
            val analysis = genericClient.analyzeDevice(connected.gatt.device)
            
            // If we found a likely protocol, try to use it
            analysis.protocols.firstOrNull()?.let { protocol ->
                when (protocol.dataType) {
                    ProtocolType.TEMPERATURE, ProtocolType.TEMPERATURE_AND_HUMIDITY -> {
                        // Try to read using the discovered protocol
                        val characteristic = protocol.serviceUuid?.let { serviceUuid ->
                            connected.getService(serviceUuid)?.getCharacteristic(protocol.characteristicUuid)
                        } ?: connected.gatt.services?.flatMap { it.characteristics }
                            ?.find { it.uuid == protocol.characteristicUuid }
                        
                        characteristic?.let {
                            val data = connected.readCharacteristic(it) ?: return@let null
                            parseGenericData(data, protocol.pattern)
                        }
                    }
                    ProtocolType.UNKNOWN -> null
                }
            } ?: emptyList()
        }
    }

    private fun parseGenericData(data: ByteArray, pattern: String): List<TemperatureHumidityData> {
        // Parse data according to discovered pattern
        var index = 0
        var temperature: Double? = null
        var humidity: Double? = null
        
        pattern.forEach { type ->
            when (type) {
                'T' -> {
                    if (index + 1 < data.size) {
                        val tempRaw = ByteBuffer.wrap(data, index, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short.toInt()
                        temperature = tempRaw / 10.0
                        index += 2
                    }
                }
                'H' -> {
                    if (index < data.size) {
                        humidity = (data[index].toInt() and 0xFF).toDouble()
                        index++
                    }
                }
                'x' -> index++
            }
        }
        
        return if (temperature != null) {
            listOf(TemperatureHumidityData(
                temperature = temperature,
                humidity = humidity ?: 0.0
            ))
        } else {
            emptyList()
        }
    }

    fun disconnect() {
        connectedDevice?.close()
        connectedDevice = null
    }
}
