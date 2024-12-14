package com.adsamcik.temperaturedashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.adsamcik.temperaturedashboard.AddDeviceDialog
import com.adsamcik.temperaturedashboard.DeviceListItem
import com.adsamcik.temperaturedashboard.ui.models.AddDeviceViewModel
import com.adsamcik.temperaturedashboard.ui.models.MainViewModel

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    addDeviceViewModel: AddDeviceViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val addedDevices by mainViewModel.addedDevices
    val showAddDeviceDialog by mainViewModel.showAddDeviceDialog

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(addedDevices) { device ->
                DeviceListItem(
                    device = device,
                    onDeviceSelected = {
                        navController.navigate("device_details/${device.device.macAddress}")
                    }
                )
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
