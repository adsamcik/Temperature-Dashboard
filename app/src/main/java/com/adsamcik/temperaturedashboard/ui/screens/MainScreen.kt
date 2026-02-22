package com.adsamcik.temperaturedashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.adsamcik.temperaturedashboard.AddDeviceDialog
import com.adsamcik.temperaturedashboard.DeviceListItem
import com.adsamcik.temperaturedashboard.ui.models.AddDeviceViewModel
import com.adsamcik.temperaturedashboard.ui.models.MainViewModel
import com.adsamcik.temperaturedashboard.ui.state.MainScreenState

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    addDeviceViewModel: AddDeviceViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val showAddDeviceDialog by mainViewModel.showAddDeviceDialog.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is MainScreenState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is MainScreenState.Empty -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No devices added yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Add a Bluetooth temperature sensor to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { mainViewModel.onAddDeviceClicked() }) {
                        Text("Scan for Devices")
                    }
                }
            }
            is MainScreenState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.devices) { device ->
                        DeviceListItem(
                            device = device,
                            onDeviceSelected = {
                                navController.navigate("device_details/${device.device.macAddress}")
                            }
                        )
                    }
                }
            }
            is MainScreenState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Error: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { mainViewModel.retryLoadDevices() }) {
                        Text("Retry")
                    }
                }
            }
        }

        if (showAddDeviceDialog) {
            AddDeviceDialog(
                viewModel = addDeviceViewModel,
                onDeviceSelected = { device ->
                    mainViewModel.addDevice(device.copy())
                    mainViewModel.dismissAddDeviceDialog()
                    addDeviceViewModel.stopScanning()
                },
                onDismiss = {
                    mainViewModel.dismissAddDeviceDialog()
                    addDeviceViewModel.stopScanning()
                }
            )
        }
    }
}
