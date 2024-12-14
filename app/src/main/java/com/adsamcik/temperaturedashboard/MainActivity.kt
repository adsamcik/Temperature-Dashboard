package com.adsamcik.temperaturedashboard

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.data.toDevice
import com.adsamcik.temperaturedashboard.data.toViewDevice
import com.adsamcik.temperaturedashboard.networking.BleDeviceConnector
import com.adsamcik.temperaturedashboard.networking.DeviceDiscoveryManager
import com.adsamcik.temperaturedashboard.storage.Device
import com.adsamcik.temperaturedashboard.storage.DeviceDatabase
import com.adsamcik.temperaturedashboard.storage.DeviceRepository
import com.adsamcik.temperaturedashboard.ui.models.AddDeviceViewModel
import com.adsamcik.temperaturedashboard.ui.models.MainViewModel
import com.adsamcik.temperaturedashboard.ui.models.MockAddDeviceViewModel
import com.adsamcik.temperaturedashboard.ui.screens.DeviceDetailsScreen
import com.adsamcik.temperaturedashboard.ui.theme.TemperatureDashboardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mainViewModel: MainViewModel = MainViewModel(DeviceRepository(DeviceDatabase.getDatabase(this).deviceDao()))
        val addDeviceViewModel: AddDeviceViewModel = AddDeviceViewModel(applicationContext)

        setContent {
            val navController = rememberNavController()
            TemperatureDashboardTheme {
                NavHost(
                    navController = navController,
                    startDestination = "main_screen"
                ) {
                    composable("main_screen") {
                        MainScreen(
                            mainViewModel = mainViewModel,
                            addDeviceViewModel = addDeviceViewModel,
                            navController = navController
                        )
                    }
                    composable("device_details/{deviceId}") { backStackEntry ->
                        val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                        val device = mainViewModel.addedDevices.value.find { it.device.macAddress == deviceId }
                        DeviceDetailsScreen(device = device)
                    }
                }
            }
        }
    }
}

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
                DeviceListItem(device = device, onDeviceSelected = {
                    navController.navigate("device_details/${device.device.macAddress}")
                })
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


@Composable
fun AddDeviceDialog(
    viewModel: AddDeviceViewModel,
    onDeviceSelected: (ViewDevice) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(key1 = Unit) {
        viewModel.startScanning()
    }

    val devices by viewModel.discoveredDevices.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Add Device",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDismiss() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                Text(text = "Scanning for devices...")

                Spacer(modifier = Modifier.height(8.dp))

                val sortedDevices = devices.sortedByDescending { it.decoder != null }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedDevices) { device ->
                        DeviceListItem(device = device, onDeviceSelected = onDeviceSelected)
                    }
                }

                Button(
                    onClick = { onDismiss() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}


@Composable
fun DeviceListItem(
    device: ViewDevice,
    onDeviceSelected: (ViewDevice) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        device.decoder?.iconRes?.let { iconRes ->
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        } ?: Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "No icon",
            modifier = Modifier.size(24.dp),
            tint = if (device.decoder != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.device.name ?: "Unknown Device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = device.device.macAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            device.decoder?.name?.let {
                Text(
                    text = "Type: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        if (device.decoder != null) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Compatible Device",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun MainScreenFullPreview() {
    val mainViewModel = MainViewModel(DeviceRepository(DeviceDatabase.getDatabase(LocalContext.current).deviceDao()))
    val addDeviceViewModel = MockAddDeviceViewModel(LocalContext.current)

    // Simulate the dialog being open
    mainViewModel.showAddDeviceDialog.value = true
    val navController = rememberNavController()
    MainScreen(mainViewModel = mainViewModel, addDeviceViewModel = addDeviceViewModel, navController = navController)
}
