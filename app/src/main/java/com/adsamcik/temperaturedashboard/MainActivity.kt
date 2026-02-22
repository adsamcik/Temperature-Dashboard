package com.adsamcik.temperaturedashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adsamcik.temperaturedashboard.data.ViewDevice
import com.adsamcik.temperaturedashboard.ui.models.AddDeviceViewModel
import com.adsamcik.temperaturedashboard.ui.models.MainViewModel
import com.adsamcik.temperaturedashboard.ui.screens.DeviceDetailsScreen
import com.adsamcik.temperaturedashboard.ui.screens.MainScreen
import com.adsamcik.temperaturedashboard.ui.permissions.RequireBluetoothPermissions
import com.adsamcik.temperaturedashboard.ui.SnackbarManager
import com.adsamcik.temperaturedashboard.ui.theme.TemperatureDashboardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()
            val mainViewModel: MainViewModel = hiltViewModel()

            TemperatureDashboardTheme {
                RequireBluetoothPermissions {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val snackbarHostState = remember { SnackbarHostState() }

                    LaunchedEffect(Unit) {
                        SnackbarManager.messages.collect { message ->
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        floatingActionButton = {
                             if (currentRoute == "main_screen") {
                                 FloatingActionButton(onClick = { mainViewModel.onAddDeviceClicked() }) {
                                     Icon(
                                         imageVector = Icons.Default.Add,
                                         contentDescription = stringResource(R.string.cd_add_device)
                                     )
                                 }
                             }
                         }
                    ) { innerPadding ->
                        NavigationGraph(
                            navController = navController,
                            innerPadding = innerPadding,
                            mainViewModel = mainViewModel,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun NavigationGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    snackbarHostState: SnackbarHostState
) {
    NavHost(
        navController = navController,
        startDestination = "main_screen",
        modifier = Modifier.padding(innerPadding)
    ) {
        composable("main_screen") {
            val addDeviceViewModel: AddDeviceViewModel = hiltViewModel()
            MainScreen(
                mainViewModel = mainViewModel,
                addDeviceViewModel = addDeviceViewModel,
                snackbarHostState = snackbarHostState,
                onDeviceSelected = { macAddress ->
                    navController.navigate("device_details/$macAddress")
                }
            )
        }

        composable("device_details/{deviceId}") { backStackEntry ->
            DeviceDetailsScreen(
                onNavigateBack = { navController.popBackStack() }
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

    val devices by viewModel.discoveredDevices.collectAsStateWithLifecycle()

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
                val closeDialogDescription = stringResource(R.string.cd_close_dialog_button)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.title_add_device),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.semantics {
                            contentDescription = closeDialogDescription
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = closeDialogDescription
                        )
                    }
                }

                HorizontalDivider()

                Text(text = stringResource(R.string.state_scanning))

                Spacer(modifier = Modifier.height(8.dp))

                val sortedDevices = remember(devices) {
                    devices.sortedByDescending { it.decoder != null }
                }

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
                    Text(stringResource(R.string.btn_cancel))
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
    val deviceName = device.device.name ?: stringResource(R.string.unknown_device)
    val deviceNameDescription = stringResource(R.string.cd_device_name, deviceName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) }
            .padding(8.dp)
            .semantics {
                contentDescription = deviceNameDescription
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        device.decoder?.iconRes?.let { iconRes ->
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = stringResource(R.string.cd_device_icon),
                modifier = Modifier.size(24.dp)
            )
        } ?: Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = stringResource(R.string.cd_warning_icon),
            modifier = Modifier.size(24.dp),
            tint = if (device.decoder != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = deviceNameDescription
                }
            )
            Text(
                text = device.device.macAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            device.decoder?.name?.let {
                Text(
                    text = stringResource(R.string.label_type, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        if (device.decoder != null) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.cd_compatible_device),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
