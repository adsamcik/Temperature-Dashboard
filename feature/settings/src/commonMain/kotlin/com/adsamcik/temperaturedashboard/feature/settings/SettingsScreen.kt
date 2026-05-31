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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.core.ui.component.formatDecimal
import kotlin.time.Duration.Companion.milliseconds

/** Light/dark/system override surfaced in Settings; mirrors core.datastore.ThemeMode. */
enum class ThemeOption(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

@Composable
fun SettingsScreen(
    unit: TemperatureUnit,
    policy: CoalescingPolicy,
    themeMode: ThemeOption,
    dynamicColor: Boolean,
    hiddenSensors: List<Sensor>,
    autostartSupported: Boolean,
    autostartEnabled: Boolean,
    onUnitChange: (TemperatureUnit) -> Unit,
    onPolicyChange: (CoalescingPolicy) -> Unit,
    onThemeChange: (ThemeOption) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onUnhideSensor: (Sensor) -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TdashSpacing.m),
        verticalArrangement = Arrangement.spacedBy(TdashSpacing.l),
    ) {
        SettingsSection(title = "Appearance") {
            Text("Theme", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s)) {
                ThemeOption.entries.forEach { opt ->
                    FilterChip(
                        selected = opt == themeMode,
                        onClick = { onThemeChange(opt) },
                        label = { Text(opt.label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Material You colour", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "On Android 12+ the colour scheme follows your wallpaper. " +
                            "Turn off for the warm-coral default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
            }
        }

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
                    "Defaults work well for ThermoPro, SwitchBot, and Govee devices.",
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

        if (hiddenSensors.isNotEmpty()) {
            SettingsSection(title = "Hidden sensors") {
                hiddenSensors.forEach { sensor ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sensor.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = sensor.address.raw,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onUnhideSensor(sensor) }) { Text("Show") }
                    }
                }
            }
        }

        if (autostartSupported) {
            SettingsSection(title = "Start at login") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Launch automatically when you sign in",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Keeps your sensors recording even after a reboot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autostartEnabled, onCheckedChange = onAutostartChange)
                }
            }
        }

        SettingsSection(title = "About") {
            Text("Temperature Dashboard \u00B7 v0.3.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Universal Bluetooth temperature & humidity sensor dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TdashSpacing.xs),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = TdashSpacing.s))
            Text("Open source libraries", style = MaterialTheme.typography.labelMedium)
            Text(
                text = OSS_ATTRIBUTIONS,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private val OSS_ATTRIBUTIONS = listOf(
    "Kotlin (Apache 2.0)",
    "Compose Multiplatform (Apache 2.0)",
    "AndroidX Compose / Room / Activity / Lifecycle / Navigation (Apache 2.0)",
    "Koin (Apache 2.0)",
    "Multiplatform Settings (Apache 2.0)",
    "Napier (Apache 2.0)",
    "KoalaPlot (MIT)",
    "JNA (Apache 2.0 / LGPL)",
    "btleplug (BSD-3) — Rust BLE shim for Desktop",
    "SwitchBot BLE protocol — OpenWonderLabs / pySwitchbot (MIT)",
    "govee-ble parser — Bluetooth-Devices/govee-ble (MIT)",
).joinToString("\n") { "\u2022 $it" }


