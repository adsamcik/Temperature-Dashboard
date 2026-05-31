package com.adsamcik.temperaturedashboard.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.AlertKind
import com.adsamcik.temperaturedashboard.core.model.SensorAlert
import com.adsamcik.temperaturedashboard.core.ui.component.formatDecimal

/**
 * In-detail-screen panel for managing a sensor's threshold alerts.
 *
 * - Existing alerts: switch (enabled), label, delete button
 * - Add: opens a dialog to pick kind + threshold + confirm
 */
@Composable
fun AlertsPanel(
    alerts: List<SensorAlert>,
    onToggle: (SensorAlert, Boolean) -> Unit,
    onAdd: (AlertKind) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(TdashSpacing.m),
            verticalArrangement = Arrangement.spacedBy(TdashSpacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Alerts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                var dialogOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { dialogOpen = true }) { Text("+ Add") }
                if (dialogOpen) {
                    AddAlertDialog(
                        onConfirm = {
                            onAdd(it)
                            dialogOpen = false
                        },
                        onDismiss = { dialogOpen = false },
                    )
                }
            }

            if (alerts.isEmpty()) {
                Text(
                    text = "No alerts configured. Tap + Add to be notified when " +
                        "this sensor crosses a threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                alerts.forEach { alert ->
                    AlertRow(
                        alert = alert,
                        onToggle = { onToggle(alert, it) },
                        onDelete = { onDelete(alert.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: SensorAlert,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = alert.enabled, onCheckedChange = onToggle)
        Text(
            text = alert.kind.label(),
            modifier = Modifier.padding(start = TdashSpacing.s).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlertDialog(
    onConfirm: (AlertKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var kindIndex by remember { mutableStateOf(0) }
    val kindOptions = listOf(
        "Temperature above" to { v: Double -> AlertKind.TempAbove(v) },
        "Temperature below" to { v: Double -> AlertKind.TempBelow(v) },
        "Humidity above" to { v: Double -> AlertKind.HumidityAbove(v) },
        "Humidity below" to { v: Double -> AlertKind.HumidityBelow(v) },
        "Battery below" to { v: Double -> AlertKind.BatteryBelow(v.toInt()) },
    )
    val defaultThreshold = when (kindIndex) {
        0 -> 25.0; 1 -> 5.0; 2 -> 70.0; 3 -> 30.0; 4 -> 20.0; else -> 0.0
    }
    var threshold by remember(kindIndex) { mutableStateOf(defaultThreshold) }
    val sliderRange = when (kindIndex) {
        in 0..1 -> -20f..50f
        in 2..3 -> 0f..100f
        4 -> 0f..100f
        else -> 0f..100f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(kindOptions[kindIndex].second(threshold)) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TdashSpacing.s)) {
                Text("Type", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TdashSpacing.xs),
                ) {
                    kindOptions.forEachIndexed { i, (label, _) ->
                        FilterChip(
                            selected = i == kindIndex,
                            onClick = { kindIndex = i },
                            label = { Text(label) },
                        )
                    }
                }
                Text(
                    text = "Threshold: ${formatDecimal(threshold, decimals = 1)}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { threshold = it.toDouble() },
                    valueRange = sliderRange,
                )
            }
        },
    )
}

private fun AlertKind.label(): String = when (this) {
    is AlertKind.TempAbove -> "Temperature > ${formatDecimal(celsius, 1)} °C"
    is AlertKind.TempBelow -> "Temperature < ${formatDecimal(celsius, 1)} °C"
    is AlertKind.HumidityAbove -> "Humidity > ${percent.toInt()} %"
    is AlertKind.HumidityBelow -> "Humidity < ${percent.toInt()} %"
    is AlertKind.BatteryBelow -> "Battery < $percent %"
}
