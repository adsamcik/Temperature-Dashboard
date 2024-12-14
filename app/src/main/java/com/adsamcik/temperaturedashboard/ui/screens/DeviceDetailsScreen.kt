package com.adsamcik.temperaturedashboard.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.networking.BleDeviceConnector
import kotlinx.coroutines.launch

@Composable
fun DeviceDetailsScreen(device: ViewDevice?) {
    if (device == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Device not found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = device.device.name ?: "Unknown Device",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "MAC Address: ${device.device.macAddress}",
            style = MaterialTheme.typography.bodyMedium
        )
        device.decoder?.name?.let {
            Text(
                text = "Device Type: $it",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val context = LocalContext.current

        Button(
            onClick = {
                // Use BleDeviceConnector to connect and retrieve data for this device
                val bleDeviceConnector = BleDeviceConnector(context, device)
                kotlinx.coroutines.GlobalScope.launch {
                    Log.d("DeviceDetails", "Connecting to device...")
                    val connected = bleDeviceConnector.connect()
                    if (connected) {
                        val data = bleDeviceConnector.readData()
                        Log.d("DeviceDetails", "Data: $data")
                        bleDeviceConnector.disconnect()
                    } else {
                        Log.e("DeviceDetails", "Failed to connect.")
                    }
                }
            }
        ) {
            Text("Connect to Device")
        }
    }
}