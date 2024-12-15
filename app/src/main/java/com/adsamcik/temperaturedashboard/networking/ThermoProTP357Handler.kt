package com.example.tp357

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.min

/**
 * Represents a discovered TP357 advertising packet with relevant data.
 */
data class Tp357ScanResult(
    val address: String,
    val time: Long,
    val rssi: Int,
    val humidityRh: Int,
    val temperatureC: Double,
    val batteryLevelPercentage: String
)

/**
 * Represents a data point retrieved from querying the TP357 device.
 */
data class Tp357DataPoint(
    val timestampIso: String,
    val humidityRh: Int,
    val temperatureC: Double
)

enum class QueryMode {
    DAY, WEEK, YEAR
}

/**
 * A high-level BLE client that:
 * 1. Scans for TP357 devices advertising under the given criteria.
 * 2. Connects to a specific TP357 device and queries historical data.
 *
 * This class uses coroutines for asynchronous operations and Flow for scanning.
 */
class Tp357BleClient(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "Tp357BleClient"

        private const val DEVICE_NAME_PREFIX = "TP357 (7216)"

        // Service/Characteristic UUIDs (from the provided Python code)
        private val UUID_READ: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b10")
        private val UUID_WRITE: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b11")

        private const val MAX_RETRIES = 3
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val scanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    /**
     * Scan for TP357 devices. The flow emits discovered advertising packets that match the TP357 pattern.
     * Use a timeout or external mechanism to stop collecting.
     */
    @ExperimentalCoroutinesApi
    fun scanForTp357Devices(): Flow<Tp357ScanResult> = callbackFlow {
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    val deviceName = scanResult.scanRecord?.deviceName
                    if (deviceName != null && deviceName.contains(DEVICE_NAME_PREFIX)) {
                        // Parse manufacturer data
                        val manufacturerData = scanResult.scanRecord?.manufacturerSpecificData
                        if (manufacturerData != null && manufacturerData.size() > 0) {
                            for (i in 0 until manufacturerData.size()) {
                                val key = manufacturerData.keyAt(i)
                                val value = manufacturerData.valueAt(i)
                                // According to Python code:
                                // raw = struct.pack("<H4s", k, v)
                                // The original parsing: "temp_100mdC, hum_rh, batt_level = struct.unpack('=hBB', raw[1:5])"
                                // We must reconstruct the same structure:
                                // Key: 2 bytes (unsigned short)
                                // Value: 4 bytes
                                // raw[0:2]: key, raw[2:6]: v
                                val raw = ByteBuffer.allocate(2 + value.size)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .putShort(key.toShort())
                                    .put(value)
                                    .array()

                                if (raw.size >= 5) {
                                    val tempRaw = ByteBuffer.wrap(raw.copyOfRange(1, 3))
                                        .order(ByteOrder.LITTLE_ENDIAN).short
                                    val humRh = raw[3].toInt() and 0xFF
                                    val battLevel = raw[4].toInt() and 0xFF

                                    if (tempRaw <= 1024 && humRh <= 100) {
                                        val now = System.currentTimeMillis()
                                        val batteryPercentage = "${((battLevel / 2.0) * 100).toInt()}%"
                                        val tempC = tempRaw / 10.0
                                        trySend(
                                            Tp357ScanResult(
                                                address = scanResult.device.address,
                                                time = now,
                                                rssi = scanResult.rssi,
                                                humidityRh = humRh,
                                                temperatureC = tempC,
                                                batteryLevelPercentage = batteryPercentage
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf<ScanFilter>() // or you can create filters if needed

        scanner?.startScan(filters, settings, scanCallback)

        awaitClose {
            Log.d(TAG, "Stopping BLE scan")
            scanner?.stopScan(scanCallback)
        }
    }

    /**
     * Query data from a connected TP357 device.
     * The mode influences the command and timeframe from which data is pulled.
     */
    suspend fun queryData(device: BluetoothDevice, mode: QueryMode): List<Tp357DataPoint> {
        return withContext(ioDispatcher) {
            retryBleOperation(MAX_RETRIES) {
                performQuery(device, mode)
            }
        }
    }

    /**
     * Perform the actual query logic:
     * - Connect GATT
     * - Enable notifications on READ characteristic
     * - Write command to WRITE characteristic
     * - Collect data until 'fin_evt' is triggered (data[0] == 194)
     * - Close connection
     */
    private suspend fun performQuery(device: BluetoothDevice, mode: QueryMode): List<Tp357DataPoint> {
        val gattResult = CompletableDeferred<List<Tp357DataPoint>>()

        val (command, timeDeltaMinutes, startOffset) = when (mode) {
            QueryMode.DAY -> {
                Triple(byteArrayOf(0xa7.toByte(), 0x00, 0x00, 0x00, 0x00, 0x7a.toByte()),
                    1, 1) // 1 minute resolution, 1 day offset
            }

            QueryMode.WEEK -> {
                Triple(byteArrayOf(0xa6.toByte(), 0x00, 0x00, 0x00, 0x00, 0x6a.toByte()),
                    60, 7 * 24) // 1 hour resolution, 7 days offset
            }

            QueryMode.YEAR -> {
                Triple(byteArrayOf(0xa8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x8a.toByte()),
                    60, 365 * 24) // 1 hour resolution, 365 days offset
            }
        }

        val collectedData = mutableListOf<Tp357DataPoint>()

        val gattCallback = object : BluetoothGattCallback() {
            private var done = false

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState != BluetoothGatt.STATE_CONNECTED) {
                    Log.e(TAG, "Connection failed or disconnected. Status: $status")
                    if (!done) {
                        done = true
                        gattResult.completeExceptionally(RuntimeException("Failed to connect or lost connection."))
                        gatt.close()
                    }
                } else {
                    // Connected, discover services
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val readChar = gatt.getService(UUID_READ)?.getCharacteristic(UUID_READ)
                        ?: gatt.services.flatMap { it.characteristics }
                            .find { it.uuid == UUID_READ }
                    val writeChar = gatt.getService(UUID_WRITE)?.getCharacteristic(UUID_WRITE)
                        ?: gatt.services.flatMap { it.characteristics }
                            .find { it.uuid == UUID_WRITE }

                    if (readChar == null || writeChar == null) {
                        Log.e(TAG, "Required characteristics not found")
                        gattResult.completeExceptionally(RuntimeException("Characteristics not found"))
                        gatt.close()
                        return
                    }

                    // Enable notifications
                    gatt.setCharacteristicNotification(readChar, true)
                    val desc = readChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                } else {
                    Log.e(TAG, "Service discovery failed: $status")
                    gattResult.completeExceptionally(RuntimeException("Service discovery failed"))
                    gatt.close()
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && descriptor.characteristic.uuid == UUID_READ) {
                    // Now write command to the WRITE characteristic
                    val writeChar = gatt.getService(UUID_WRITE)?.getCharacteristic(UUID_WRITE)
                        ?: run {
                            Log.e(TAG, "Write characteristic not found after descriptor write.")
                            gattResult.completeExceptionally(RuntimeException("Write characteristic not found"))
                            gatt.close()
                            return
                        }
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    writeChar.value = command
                    gatt.writeCharacteristic(writeChar)
                } else {
                    Log.e(TAG, "Failed to write descriptor or unknown descriptor write.")
                    gattResult.completeExceptionally(RuntimeException("Descriptor write failed"))
                    gatt.close()
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Failed to write command to device.")
                    if (!done) {
                        done = true
                        gattResult.completeExceptionally(RuntimeException("Command write failed"))
                        gatt.close()
                    }
                }
                // Command was sent, now we wait for notifications (onCharacteristicChanged).
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val data = characteristic.value
                if (data.isEmpty()) return

                // According to the Python code:
                // if data[0] == 194 (0xC2), it signals end of data
                // else if data[0] == command[0], parse data points
                if (data[0].toInt() == 0xC2) {
                    // End of data
                    if (!done) {
                        done = true
                        gattResult.complete(collectedData.toList())
                        gatt.disconnect()
                        gatt.close()
                    }
                    return
                }

                // Parse data if it matches the initial command byte
                if (data[0] == command[0]) {
                    // raw = struct.unpack("h", data[1:3])[0]
                    val rawIndex = ByteBuffer.wrap(data, 1, 2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .short.toInt()

                    // We have 5 sets of measurements, each 3 bytes: (temp_100mdC(2 bytes), hum_rh(1 byte))
                    // start offset 4, length 3 * 5 = 15 bytes
                    for (i in 0 until 5) {
                        val start = 4 + i * 3
                        if (start + 3 > data.size) break
                        val tempRaw = ByteBuffer.wrap(data, start, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short.toInt()

                        val humRh = data[start + 2].toInt() and 0xFF

                        // Validate
                        if (tempRaw <= 1024 && humRh <= 100) {
                            // The Python code computes timestamps by offsetting a t0 by raw indexes and increments.
                            // For simplicity, we just use a rough approximation here or replicate logic:
                            // mode 'day': t0 = now - 1 day, each step 1 minute
                            // mode 'week': t0 = now - 7 days, each step 1 hour
                            // mode 'year': t0 = now - 365 days, each step 1 hour
                            val now = System.currentTimeMillis()
                            val stepsFromStart = (5 * (rawIndex - 1) + i)
                            val offsetMillis = stepsFromStart * timeDeltaMinutes * 60_000L
                            // Start offset means how far back we go from now.
                            val baseTime = now - (startOffset.toLong() * 60 * 60 * 1000L)
                            val dataTime = baseTime + offsetMillis

                            val tempC = tempRaw / 10.0
                            val isoTime = java.time.Instant.ofEpochMilli(dataTime)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDateTime()
                                .toString()

                            collectedData.add(
                                Tp357DataPoint(
                                    timestampIso = isoTime,
                                    humidityRh = humRh,
                                    temperatureC = tempC
                                )
                            )
                        }
                    }
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                // Not used, we rely on notifications
            }
        }

        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        return gattResult.await()
    }

    /**
     * Retry a BLE operation up to n times if it fails.
     */
    private suspend fun <T> retryBleOperation(retries: Int, block: suspend () -> T): T {
        var currentAttempt = 0
        var lastException: Throwable? = null

        while (currentAttempt < retries) {
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                Log.w(TAG, "BLE operation failed, retrying... (${retries - currentAttempt - 1} retries left)", e)
                currentAttempt++
            }
        }

        throw lastException ?: RuntimeException("Unknown BLE operation failure")
    }
}
