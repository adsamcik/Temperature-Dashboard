package com.adsamcik.temperaturedashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.temperaturedashboard.R
import com.adsamcik.temperaturedashboard.data.TemperatureUnit
import com.adsamcik.temperaturedashboard.storage.TemperatureReading
import com.adsamcik.temperaturedashboard.ui.models.DeviceDetailsViewModel
import com.adsamcik.temperaturedashboard.ui.state.DeviceDetailsState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DeviceDetailsViewModel = hiltViewModel()
) {
    val device by viewModel.device.collectAsStateWithLifecycle()

    if (device == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val currentDevice = device!!
    var showDeleteDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val deviceName = currentDevice.device.name ?: stringResource(R.string.unknown_device)
    val deviceNameDescription = stringResource(R.string.cd_device_name, deviceName)
    val deleteDeviceDescription = stringResource(R.string.cd_delete_device)

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_device_title)) },
            text = { Text(stringResource(R.string.delete_device_message, deviceName)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteDevice()
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = deviceNameDescription
                    }
            )
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = deleteDeviceDescription,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        Text(
            text = stringResource(R.string.label_mac, currentDevice.device.macAddress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        currentDevice.decoder?.name?.let {
            Text(
                text = stringResource(R.string.label_type, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilterChip(
                selected = temperatureUnit == TemperatureUnit.FAHRENHEIT,
                onClick = { viewModel.toggleTemperatureUnit() },
                label = { Text(temperatureUnit.symbol()) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (val state = uiState) {
            is DeviceDetailsState.Idle -> {
                Button(
                    onClick = { viewModel.connect() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_connect_read))
                }
            }

            is DeviceDetailsState.Connecting -> {
                ConnectingContent(onCancel = { viewModel.cancelConnection() })
            }

            is DeviceDetailsState.Connected -> {
                ConnectedContent(
                    state = state,
                    isRefreshing = isRefreshing,
                    temperatureUnit = temperatureUnit,
                    onRefresh = { viewModel.refreshData() },
                    onRefreshButton = { viewModel.connect() }
                )
            }

            is DeviceDetailsState.PassiveMonitoring -> {
                if (state.latestTemperature != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.section_live_reading_passive),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val displayTemp = temperatureUnit.convert(state.latestTemperature)
                                    val formattedTemp = "%.1f%s".format(displayTemp, temperatureUnit.symbol())
                                    val currentTemperatureDescription = stringResource(
                                        R.string.cd_current_temperature_value,
                                        state.latestTemperature
                                    )
                                    Text(
                                        text = formattedTemp,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.semantics {
                                            contentDescription = currentTemperatureDescription
                                        }
                                    )
                                    Text(
                                        text = stringResource(R.string.label_temperature),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (state.latestHumidity != null) {
                                    val currentHumidityDescription = stringResource(
                                        R.string.cd_current_humidity_value,
                                        state.latestHumidity
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stringResource(
                                                R.string.format_humidity_percent,
                                                state.latestHumidity
                                            ),
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.semantics {
                                                contentDescription = currentHumidityDescription
                                            }
                                        )
                                        Text(
                                            text = stringResource(R.string.label_humidity),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                if (state.batteryPercent != null) {
                                    val currentBatteryDescription = stringResource(
                                        R.string.cd_current_battery_value,
                                        state.batteryPercent
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stringResource(
                                                R.string.format_battery_percent,
                                                state.batteryPercent
                                            ),
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.semantics {
                                                contentDescription = currentBatteryDescription
                                            }
                                        )
                                        Text(
                                            text = stringResource(R.string.label_battery),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.stopPassiveMonitoring() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_stop_monitoring))
                }

                if (state.readings.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.section_history, state.readings.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.readings.take(50)) { reading ->
                            ReadingRow(reading, temperatureUnit)
                        }
                    }
                }
            }

            is DeviceDetailsState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.connect() },
                    onTryPassive = { viewModel.startPassiveMonitoring() }
                )
            }
        }
    }
}

@Composable
private fun ConnectingContent(onCancel: () -> Unit) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.state_connecting))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.state_elapsed_seconds, elapsedSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.btn_cancel))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedContent(
    state: DeviceDetailsState.Connected,
    isRefreshing: Boolean,
    temperatureUnit: TemperatureUnit,
    onRefresh: () -> Unit,
    onRefreshButton: () -> Unit
) {
    if (state.latestTemperature != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.section_current_reading),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displayTemp = temperatureUnit.convert(state.latestTemperature)
                        val formattedTemp = "%.1f%s".format(displayTemp, temperatureUnit.symbol())
                        val currentTemperatureDescription = stringResource(
                            R.string.cd_current_temperature_value,
                            state.latestTemperature
                        )
                        Text(
                            text = formattedTemp,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics {
                                contentDescription = currentTemperatureDescription
                            }
                        )
                        Text(
                            text = stringResource(R.string.label_temperature),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (state.latestHumidity != null) {
                        val currentHumidityDescription = stringResource(
                            R.string.cd_current_humidity_value,
                            state.latestHumidity
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(
                                    R.string.format_humidity_percent,
                                    state.latestHumidity
                                ),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.semantics {
                                    contentDescription = currentHumidityDescription
                                }
                            )
                            Text(
                                text = stringResource(R.string.label_humidity),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    LastUpdatedText(state.lastUpdatedAt)

    OutlinedButton(
        onClick = onRefreshButton,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.btn_refresh))
    }

    if (state.readings.isNotEmpty()) {
        Text(
            text = stringResource(R.string.section_history, state.readings.size),
            style = MaterialTheme.typography.titleMedium
        )
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.readings.take(50)) { reading ->
                    ReadingRow(reading, temperatureUnit)
                }
            }
        }
    }
}

@Composable
private fun LastUpdatedText(lastUpdatedAt: Long) {
    var displayText by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastUpdatedAt) {
        while (true) {
            val elapsedMinutes = (System.currentTimeMillis() - lastUpdatedAt) / 60_000
            displayText = elapsedMinutes
            delay(30_000L)
        }
    }

    val text = when {
        displayText < 1L -> stringResource(R.string.last_updated_just_now)
        displayText == 1L -> stringResource(R.string.last_updated_one_minute)
        else -> stringResource(R.string.last_updated_minutes, displayText)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onTryPassive: () -> Unit
) {
    val errorIconDescription = stringResource(R.string.cd_error_icon)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = errorIconDescription,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.btn_retry))
    }
    TextButton(
        onClick = onTryPassive,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.btn_try_passive_mode))
    }
}

@Composable
private fun ReadingRow(reading: TemperatureReading, temperatureUnit: TemperatureUnit) {
    val pattern = stringResource(R.string.format_history_timestamp)
    val dateFormat = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    val temperatureReadingDescription = stringResource(
        R.string.cd_temperature_reading,
        reading.temperature
    )
    val humidityReadingDescription = stringResource(
        R.string.cd_humidity_reading,
        reading.humidity
    )
    val formattedTemp = "%.1f%s".format(temperatureUnit.convert(reading.temperature), temperatureUnit.symbol())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = dateFormat.format(Date(reading.timestamp)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formattedTemp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics {
                contentDescription = temperatureReadingDescription
            }
        )
        Text(
            text = stringResource(R.string.format_humidity_percent, reading.humidity),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics {
                contentDescription = humidityReadingDescription
            }
        )
    }
}
