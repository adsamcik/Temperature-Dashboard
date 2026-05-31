package com.adsamcik.temperaturedashboard.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.core.ui.component.formatDecimal
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SettingsScreen(
    unit: TemperatureUnit,
    policy: CoalescingPolicy,
    onUnitChange: (TemperatureUnit) -> Unit,
    onPolicyChange: (CoalescingPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TdashSpacing.m),
        verticalArrangement = Arrangement.spacedBy(TdashSpacing.l),
    ) {
        SettingsSection(title = "Temperature unit") {
            Row(horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s)) {
                TemperatureUnit.entries.forEach { u ->
                    FilterChip(
                        selected = u == unit,
                        onClick = { onUnitChange(u) },
                        label = { Text(u.symbol) },
                    )
                }
            }
        }

        SettingsSection(title = "Coalescing thresholds") {
            Text(
                text = "Smaller thresholds capture more detail but write more rows. " +
                    "Defaults work well for ThermoPro and BTHome devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SliderRow(
                label = "Temperature \u0394 (°C)",
                value = policy.temperatureThresholdC.toFloat(),
                valueRange = 0.05f..1.0f,
                steps = 18,
                display = { formatDecimal(it.toDouble(), 2) },
                onChange = { onPolicyChange(policy.copy(temperatureThresholdC = it.toDouble())) },
            )
            SliderRow(
                label = "Humidity \u0394 (%)",
                value = policy.humidityThresholdPct.toFloat(),
                valueRange = 0.5f..5.0f,
                steps = 8,
                display = { formatDecimal(it.toDouble(), 1) },
                onChange = { onPolicyChange(policy.copy(humidityThresholdPct = it.toDouble())) },
            )
        }

        SettingsSection(title = "Stale window") {
            Text(
                text = "If no advertisement arrives in this window, the current interval " +
                    "freezes and chart gaps show as gaps (not stale held values).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SliderRow(
                label = "Window (minutes)",
                value = (policy.staleWindow.inWholeMilliseconds / 60_000L).toFloat(),
                valueRange = 1f..60f,
                steps = 58,
                display = { "${it.toInt()} min" },
                onChange = {
                    onPolicyChange(
                        policy.copy(staleWindow = (it.toLong() * 60_000L).milliseconds),
                    )
                },
            )
        }

        SettingsSection(title = "About") {
            Text(
                text = "Temperature Dashboard \u00B7 v0.1.0",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Universal Bluetooth temperature & humidity sensor dashboard. " +
                    "ThermoPro TP35x, BTHome v2, BLE Environmental Sensing, " +
                    "and Health Thermometer out of the box.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TdashSpacing.xs),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(TdashSpacing.m),
            verticalArrangement = Arrangement.spacedBy(TdashSpacing.s),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(text = display(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
