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
import com.adsamcik.temperaturedashboard.core.ui.resources.Res
import com.adsamcik.temperaturedashboard.core.ui.resources.action_add
import com.adsamcik.temperaturedashboard.core.ui.resources.action_cancel
import com.adsamcik.temperaturedashboard.core.ui.resources.action_delete
import com.adsamcik.temperaturedashboard.core.ui.resources.action_plus_add
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_kind_battery_below
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_kind_humidity_above
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_kind_humidity_below
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_kind_temp_above
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_kind_temp_below
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_label_battery_below
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_label_humidity_above
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_label_humidity_below
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_label_temp_above
import com.adsamcik.temperaturedashboard.core.ui.resources.alert_label_temp_below
import com.adsamcik.temperaturedashboard.core.ui.resources.alerts_dialog_cooldown
import com.adsamcik.temperaturedashboard.core.ui.resources.alerts_dialog_cooldown_help
import com.adsamcik.temperaturedashboard.core.ui.resources.alerts_dialog_threshold
import com.adsamcik.temperaturedashboard.core.ui.resources.alerts_dialog_title
import com.adsamcik.temperaturedashboard.core.ui.resources.alerts_dialog_type
import com.adsamcik.temperaturedashboard.core.ui.resources.alerts_empty
import com.adsamcik.temperaturedashboard.core.ui.resources.alerts_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlin.time.Duration.Companion.minutes

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
    onAdd: (AlertKind, kotlin.time.Duration) -> Unit,
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
                    text = stringResource(Res.string.alerts_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                var dialogOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { dialogOpen = true }) { Text(stringResource(Res.string.action_plus_add)) }
                if (dialogOpen) {
                    AddAlertDialog(
                        onConfirm = { kind, cooldown ->
                            onAdd(kind, cooldown)
                            dialogOpen = false
                        },
                        onDismiss = { dialogOpen = false },
                    )
                }
            }

            if (alerts.isEmpty()) {
                Text(
                    text = stringResource(Res.string.alerts_empty),
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
        TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlertDialog(
    onConfirm: (AlertKind, kotlin.time.Duration) -> Unit,
    onDismiss: () -> Unit,
) {
    var kindIndex by remember { mutableStateOf(0) }
    val kindOptions = listOf(
        stringResource(Res.string.alert_kind_temp_above) to { v: Double -> AlertKind.TempAbove(v) },
        stringResource(Res.string.alert_kind_temp_below) to { v: Double -> AlertKind.TempBelow(v) },
        stringResource(Res.string.alert_kind_humidity_above) to { v: Double -> AlertKind.HumidityAbove(v) },
        stringResource(Res.string.alert_kind_humidity_below) to { v: Double -> AlertKind.HumidityBelow(v) },
        stringResource(Res.string.alert_kind_battery_below) to { v: Double -> AlertKind.BatteryBelow(v.toInt()) },
    )
    val defaultThreshold = when (kindIndex) {
        0 -> 25.0; 1 -> 5.0; 2 -> 70.0; 3 -> 30.0; 4 -> 20.0; else -> 0.0
    }
    var threshold by remember(kindIndex) { mutableStateOf(defaultThreshold) }
    var cooldownMinutes by remember { mutableStateOf(30f) }
    val sliderRange = when (kindIndex) {
        in 0..1 -> -20f..50f
        in 2..3 -> 0f..100f
        4 -> 0f..100f
        else -> 0f..100f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        kindOptions[kindIndex].second(threshold),
                        cooldownMinutes.toLong().minutes,
                    )
                },
            ) { Text(stringResource(Res.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
        title = { Text(stringResource(Res.string.alerts_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TdashSpacing.s)) {
                Text(stringResource(Res.string.alerts_dialog_type), style = MaterialTheme.typography.labelMedium)
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
                    text = stringResource(Res.string.alerts_dialog_threshold, formatDecimal(threshold, decimals = 1)),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { threshold = it.toDouble() },
                    valueRange = sliderRange,
                )
                Text(
                    text = stringResource(Res.string.alerts_dialog_cooldown, cooldownMinutes.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(Res.string.alerts_dialog_cooldown_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = cooldownMinutes,
                    onValueChange = { cooldownMinutes = it },
                    valueRange = 5f..240f,
                    steps = 46,
                )
            }
        },
    )
}

@Composable
private fun AlertKind.label(): String = when (this) {
    is AlertKind.TempAbove -> stringResource(Res.string.alert_label_temp_above, formatDecimal(celsius, 1))
    is AlertKind.TempBelow -> stringResource(Res.string.alert_label_temp_below, formatDecimal(celsius, 1))
    is AlertKind.HumidityAbove -> stringResource(Res.string.alert_label_humidity_above, percent.toInt())
    is AlertKind.HumidityBelow -> stringResource(Res.string.alert_label_humidity_below, percent.toInt())
    is AlertKind.BatteryBelow -> stringResource(Res.string.alert_label_battery_below, percent)
}
