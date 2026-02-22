package com.adsamcik.temperaturedashboard.networking

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * A BLE client that can analyze and interact with unknown BLE devices by observing their behavior
 * and attempting to deduce their protocol.
 */
class GenericBleClient(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val protocolCollector = BleProtocolCollector()
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(ioDispatcher + supervisorJob)

    fun cancel() {
        supervisorJob.cancel()
    }

    companion object {
        private const val TAG = "GenericBleClient"
        private const val MAX_RETRIES = 3
        private const val ANALYSIS_DURATION_MS = 10_000L // 10 seconds of analysis
    }

    /**
     * Analyzes an unknown BLE device by:
     * 1. Connecting to it
     * 2. Discovering services and characteristics
     * 3. Attempting to read and observe notifications from characteristics
     * 4. Looking for patterns in the data that might indicate temperature/humidity
     */
    suspend fun analyzeDevice(device: BluetoothDevice): DeviceAnalysis {
        return withContext(ioDispatcher) {
            val analysis = DeviceAnalysis()
            
            try {
                val gatt = device.connectGatt(context, false, createAnalysisCallback(analysis))
                
                // Wait for initial connection and service discovery
                delay(2000)
                
                // Check standard BLE profiles first (fast, reliable)
                if (checkStandardProfiles(gatt, analysis)) {
                    gatt.disconnect()
                    gatt.close()
                    return@withContext analysis
                }
                
                // Fall back to heuristic analysis for non-standard devices
                gatt.services?.forEach { service ->
                    service.characteristics.forEach { characteristic ->
                        tryEnableNotifications(gatt, characteristic)
                        tryReadCharacteristic(gatt, characteristic)
                    }
                }
                
                // Collect data for analysis duration
                delay(ANALYSIS_DURATION_MS)
                
                // Get confirmed protocols
                analysis.protocols.addAll(protocolCollector.getConfirmedProtocols())
                
                gatt.disconnect()
                gatt.close()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing device", e)
                analysis.errors.add(e.message ?: "Unknown error")
            }
            
            analysis
        }
    }

    private fun createAnalysisCallback(analysis: DeviceAnalysis) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.services?.forEach { service ->
                    analysis.discoveredServices[service.uuid] = service.characteristics.map { it.uuid }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                analyzeCharacteristicData(characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            analyzeCharacteristicData(characteristic)
        }
    }

    private fun analyzeCharacteristicData(characteristic: BluetoothGattCharacteristic) {
        val data = characteristic.value ?: return
        
        // Look for patterns that might indicate temperature or humidity
        val result = AnalysisResult(
            serviceUuid = characteristic.service?.uuid,
            characteristicUuid = characteristic.uuid,
            pattern = identifyPattern(data),
            potentialTemperature = extractPotentialTemperature(data),
            potentialHumidity = extractPotentialHumidity(data),
            confidence = calculateConfidence(data)
        )
        
        protocolCollector.addResult(result)
    }

    private fun identifyPattern(data: ByteArray): String {
        // Create a pattern string that describes the data structure
        val sb = StringBuilder()
        
        var i = 0
        while (i < data.size) {
            when {
                isLikelyTemperature(data, i) -> {
                    sb.append("T") // Temperature pattern
                    i += 2 // Most temperature values use 2 bytes
                }
                isLikelyHumidity(data, i) -> {
                    sb.append("H") // Humidity pattern
                    i += 1 // Most humidity values use 1 byte
                }
                else -> {
                    sb.append("x") // Unknown byte
                    i += 1
                }
            }
        }
        
        return sb.toString()
    }

    private fun isLikelyTemperature(data: ByteArray, offset: Int): Boolean {
        if (offset + 1 >= data.size) return false
        
        // Try different byte orders and scalings
        val attempts = listOf(
            { ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short },
            { ByteBuffer.wrap(data, offset, 2).order(ByteOrder.BIG_ENDIAN).short }
        )
        
        return attempts.any { getter ->
            try {
                val value = getter().toInt()
                // Most temperature sensors report in range -40 to 85°C
                // Check both direct value and scaled values
                listOf(value, value / 10, value / 100).any { 
                    it in -40..85 
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun isLikelyHumidity(data: ByteArray, offset: Int): Boolean {
        if (offset >= data.size) return false
        val value = data[offset].toInt() and 0xFF
        // Humidity 0-100%, but exclude very low values that are likely flags/counters
        return value in 10..100
    }

    private fun extractPotentialTemperature(data: ByteArray): Double? {
        // Try to find a valid temperature value in the data
        for (i in 0..data.size - 2) {
            if (isLikelyTemperature(data, i)) {
                val raw = ByteBuffer.wrap(data, i, 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .short
                    .toInt()
                
                // Try different scaling factors
                listOf(1.0, 0.1, 0.01).forEach { scale ->
                    val temp = raw * scale
                    if (temp in -40.0..85.0) {
                        return temp
                    }
                }
            }
        }
        return null
    }

    private fun extractPotentialHumidity(data: ByteArray): Double? {
        // Try to find a valid humidity value in the data
        for (i in data.indices) {
            if (isLikelyHumidity(data, i)) {
                return (data[i].toInt() and 0xFF).toDouble()
            }
        }
        return null
    }

    private fun calculateConfidence(data: ByteArray): Double {
        var confidence = 0.0
        val hasTemp = extractPotentialTemperature(data) != null
        val hasHum = extractPotentialHumidity(data) != null

        if (hasTemp) confidence += 0.3
        if (hasHum) confidence += 0.2
        if (hasTemp && hasHum) confidence += 0.1  // Bonus for both
        if (data.size in 2..20) confidence += 0.1
        if (hasCommonPatterns(data)) confidence += 0.15
        // Require some minimum data structure
        if (data.size >= 3) confidence += 0.05

        return confidence.coerceIn(0.0, 1.0)
    }

    private fun hasCommonPatterns(data: ByteArray): Boolean {
        // Check for common BLE sensor patterns
        return when {
            // Common pattern: Last byte is checksum
            data.size > 1 && isLikelyChecksum(data) -> true
            // Common pattern: First byte is length or command
            data.size > 1 && data[0].toInt() == data.size - 1 -> true
            // Add more pattern checks as needed
            else -> false
        }
    }

    private fun isLikelyChecksum(data: ByteArray): Boolean {
        // Simple checksum verification
        val sum = data.dropLast(1).sum()
        return (sum and 0xFF) == (data.last().toInt() and 0xFF)
    }

    private fun checkStandardProfiles(gatt: BluetoothGatt, analysis: DeviceAnalysis): Boolean {
        // Check for Environmental Sensing Service (0x181A)
        val essService = gatt.getService(UUID.fromString("0000181A-0000-1000-8000-00805f9b34fb"))
        if (essService != null) {
            val tempChar = essService.getCharacteristic(UUID.fromString("00002A6E-0000-1000-8000-00805f9b34fb"))
            val humChar = essService.getCharacteristic(UUID.fromString("00002A6F-0000-1000-8000-00805f9b34fb"))

            if (tempChar != null || humChar != null) {
                val dataType = when {
                    tempChar != null && humChar != null -> ProtocolType.TEMPERATURE_AND_HUMIDITY
                    tempChar != null -> ProtocolType.TEMPERATURE
                    else -> ProtocolType.UNKNOWN
                }
                analysis.protocols.add(ConfirmedProtocol(
                    serviceUuid = essService.uuid,
                    characteristicUuid = tempChar?.uuid ?: humChar!!.uuid,
                    pattern = "STANDARD_ESS",
                    confidence = 1.0,
                    dataType = dataType
                ))
                return true
            }
        }

        // Check for Health Thermometer Service (0x1809)
        val htpService = gatt.getService(UUID.fromString("00001809-0000-1000-8000-00805f9b34fb"))
        if (htpService != null) {
            val tempMeasurement = htpService.getCharacteristic(UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb"))
            if (tempMeasurement != null) {
                analysis.protocols.add(ConfirmedProtocol(
                    serviceUuid = htpService.uuid,
                    characteristicUuid = tempMeasurement.uuid,
                    pattern = "STANDARD_HTP",
                    confidence = 1.0,
                    dataType = ProtocolType.TEMPERATURE
                ))
                return true
            }
        }

        return false
    }

    private suspend fun tryEnableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        try {
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                gatt.setCharacteristicNotification(characteristic, true)
                
                val descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable notifications for ${characteristic.uuid}")
        }
    }

    private suspend fun tryReadCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        try {
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                gatt.readCharacteristic(characteristic)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read characteristic ${characteristic.uuid}")
        }
    }
}

/**
 * Represents the analysis results of a BLE device.
 */
data class DeviceAnalysis(
    val discoveredServices: MutableMap<UUID, List<UUID>> = mutableMapOf(),
    val protocols: MutableList<ConfirmedProtocol> = mutableListOf(),
    val errors: MutableList<String> = mutableListOf()
) {
    fun findServiceForCharacteristic(characteristicUuid: UUID): UUID? {
        return discoveredServices.entries.firstOrNull { (_, characteristics) ->
            characteristics.contains(characteristicUuid)
        }?.key
    }
}