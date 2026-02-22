package com.adsamcik.temperaturedashboard.networking

import com.adsamcik.temperaturedashboard.data.TemperatureHumidityData
import com.adsamcik.temperaturedashboard.storage.Device
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class Tp357BleClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BleDeviceHandler {

    override val name: String = "ThermoPro TP357"
    override val iconRes: Int? = null  // Add an icon resource if you have one
    override val readCharacteristicUuid: UUID = UUID_READ
    override val writeCharacteristicUuid: UUID = UUID_WRITE

    override fun isCompatible(device: Device): Boolean {
        return device.name?.contains("TP357") == true ||
               device.serviceUuid == readCharacteristicUuid.toString()
    }

    override fun getRequestCommand(mode: ApiMode): ByteArray? {
        return when (mode) {
            ApiMode.LATEST -> byteArrayOf(0xa7.toByte(), 0x00, 0x00, 0x00, 0x00, 0x7a.toByte())
            ApiMode.DELTA -> byteArrayOf(0xa6.toByte(), 0x00, 0x00, 0x00, 0x00, 0x6a.toByte())
            ApiMode.HISTORY -> byteArrayOf(0xa8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x8a.toByte())
        }
    }

    override suspend fun retrieveData(connectedDevice: ConnectedBleDevice, mode: ApiMode): List<TemperatureHumidityData> {
        val command = getRequestCommand(mode) ?: return emptyList()
        
        // Enable notifications on read characteristic
        val readChar = connectedDevice.getCharacteristic(UUID_READ, UUID_READ)
            ?: throw IllegalStateException("Read characteristic not found")
        
        if (!connectedDevice.enableNotifications(readChar)) {
            throw IllegalStateException("Failed to enable notifications")
        }
        
        // Write command
        val writeChar = connectedDevice.getCharacteristic(UUID_WRITE, UUID_WRITE)
            ?: throw IllegalStateException("Write characteristic not found")
        
        if (!connectedDevice.writeCharacteristic(writeChar, command)) {
            throw IllegalStateException("Failed to write command")
        }
        
        // Collect data until we receive the end marker (0xC2)
        val collectedData = mutableListOf<TemperatureHumidityData>()
        var done = false
        
        while (!done) {
            val data = connectedDevice.waitForNotification() ?: break
            
            if (data.isEmpty()) continue
            
            if (data[0].toInt() == 0xC2) {
                done = true
                continue
            }
            
            // Parse data if it matches command byte
            if (data[0] == command[0]) {
                parseDataPacket(data)?.let { dataPoints ->
                    collectedData.addAll(dataPoints)
                }
            }
        }
        
        return collectedData
    }

    private fun parseDataPacket(data: ByteArray): List<TemperatureHumidityData>? {
        if (data.size < 4) return null
        
        val rawIndex = ByteBuffer.wrap(data, 1, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short.toInt()
        
        val results = mutableListOf<TemperatureHumidityData>()
        
        // Parse 5 data points
        for (i in 0 until 5) {
            val start = 4 + i * 3
            if (start + 3 > data.size) break
            
            val tempRaw = ByteBuffer.wrap(data, start, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short.toInt()
            
            val humRh = data[start + 2].toInt() and 0xFF
            
            if (tempRaw <= 1024 && humRh <= 100) {
                val tempC = tempRaw / 10.0
                results.add(
                    TemperatureHumidityData(
                        temperature = tempC,
                        humidity = humRh.toDouble()
                    )
                )
            }
        }
        
        return results
    }

    companion object {
        private const val TAG = "Tp357BleClient"
        private val UUID_READ: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b10")
        private val UUID_WRITE: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b11")
    }
}