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

class BleDeviceConnector(
    private val context: Context
) {
    private val TAG = "BleDeviceConnector"

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    suspend fun connectDevice(macAddress: String): ConnectedBleDevice? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return null
        val bluetoothAdapter = bluetoothManager.adapter ?: return null

        val btDevice = try {
            bluetoothAdapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address: ${e.message}")
            return null
        }

        val gatt = suspendCancellableCoroutine<BluetoothGatt?> { continuation ->
            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                        Log.d(TAG, "Connected to device. Discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        Log.e(TAG, "Disconnected from device.")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Once services are discovered, we can return the gatt
                        if (continuation.isActive) {
                            continuation.resume(gatt)
                        }
                    } else {
                        Log.e(TAG, "Service discovery failed")
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

        return if (gatt != null) {
            ConnectedBleDevice(gatt)
        } else {
            null
        }
    }
}
