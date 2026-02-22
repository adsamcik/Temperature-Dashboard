package com.adsamcik.temperaturedashboard.networking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.data.ViewDevice
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BleDeviceConnector(
    private val context: Context,
    private val device: ViewDevice,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private val ESS_SERVICE_UUID = UUID.fromString("0000181A-0000-1000-8000-00805f9b34fb")
        private val TEMP_CHAR_UUID = UUID.fromString("00002A6E-0000-1000-8000-00805f9b34fb")
        private val HUMIDITY_CHAR_UUID = UUID.fromString("00002A6F-0000-1000-8000-00805f9b34fb")
        private val HTP_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
        private val HTP_TEMP_CHAR_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")
    }

    private val TAG = "BleDeviceConnector"
    private var connectedDevice: ConnectedBleDevice? = null
    private val genericClient = GenericBleClient(context, ioDispatcher)
    var isConnected: Boolean = false
        private set

    @SuppressLint("MissingPermission")
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

                // Create ConnectedBleDevice FIRST so its callback handles ALL GATT events
                val bleDevice = ConnectedBleDevice()
                val gattObj = btDevice.connectGatt(context, false, bleDevice.callback, BluetoothDevice.TRANSPORT_LE)

                val success = bleDevice.awaitConnection(gattObj)
                if (success) {
                    connectedDevice = bleDevice
                    isConnected = true
                    true
                } else {
                    gattObj.close()
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
            // Try standard BLE profiles first (fast, reliable)
            tryStandardProfiles(connected)?.let { return it }

            // Fall back to generic analysis for non-standard devices
            val gatt = connected.gatt ?: return emptyList()
            val analysis = genericClient.analyzeDevice(gatt.device)
            
            // If we found a likely protocol, try to use it
            analysis.protocols.firstOrNull()?.let { protocol ->
                when (protocol.dataType) {
                    ProtocolType.TEMPERATURE, ProtocolType.TEMPERATURE_AND_HUMIDITY -> {
                        // Try to read using the discovered protocol
                        val characteristic = protocol.serviceUuid?.let { serviceUuid ->
                            connected.getService(serviceUuid)?.getCharacteristic(protocol.characteristicUuid)
                        } ?: gatt.services?.flatMap { it.characteristics }
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

    private suspend fun tryStandardProfiles(connected: ConnectedBleDevice): List<TemperatureHumidityData>? {
        // Try ESS (Environmental Sensing Service)
        val essService = connected.getService(ESS_SERVICE_UUID)
        if (essService != null) {
            var temperature: Double? = null
            var humidity: Double? = null

            essService.getCharacteristic(TEMP_CHAR_UUID)?.let { char ->
                connected.readCharacteristic(char)?.let { data ->
                    if (data.size >= 2) {
                        val raw = ByteBuffer.wrap(data, 0, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short.toInt()
                        temperature = raw / 100.0
                    }
                }
            }

            essService.getCharacteristic(HUMIDITY_CHAR_UUID)?.let { char ->
                connected.readCharacteristic(char)?.let { data ->
                    if (data.size >= 2) {
                        val raw = ByteBuffer.wrap(data, 0, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short.toInt() and 0xFFFF
                        humidity = raw / 100.0
                    }
                }
            }

            if (temperature != null) {
                return listOf(
                    TemperatureHumidityData(
                        temperature = temperature!!,
                        humidity = humidity ?: 0.0
                    )
                )
            }
        }

        // Try HTP (Health Thermometer Profile)
        val htpService = connected.getService(HTP_SERVICE_UUID)
        if (htpService != null) {
            htpService.getCharacteristic(HTP_TEMP_CHAR_UUID)?.let { char ->
                connected.readCharacteristic(char)?.let { data ->
                    if (data.size >= 5) {
                        // IEEE-11073 FLOAT: bytes 1-4 (byte 0 is flags)
                        val mantissa = (data[1].toInt() and 0xFF) or
                                ((data[2].toInt() and 0xFF) shl 8) or
                                ((data[3].toInt() and 0xFF) shl 16)
                        val exponent = data[4].toInt() // signed int8
                        val temperature = mantissa * Math.pow(10.0, exponent.toDouble())

                        if (temperature in -40.0..85.0) {
                            return listOf(
                                TemperatureHumidityData(
                                    temperature = temperature,
                                    humidity = 0.0
                                )
                            )
                        }
                    }
                }
            }
        }

        return null
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
        genericClient.cancel()
        connectedDevice?.close()
        connectedDevice = null
        isConnected = false
    }
}
