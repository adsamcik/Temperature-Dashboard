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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.core.ui.component.formatDecimal
import com.adsamcik.temperaturedashboard.core.ui.resources.Res
import com.adsamcik.temperaturedashboard.core.ui.resources.action_show
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_about
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_about_tagline
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_about_version
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_appearance
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_coalescing_help
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_coalescing_humidity_label
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_coalescing_temp_label
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_coalescing_thresholds
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_hidden_sensors
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_material_you_help
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_material_you_title
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_oss_title
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_stale_window
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_stale_window_help
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_stale_window_label
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_stale_window_value
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_start_at_login
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_start_at_login_help
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_start_at_login_title
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_temperature_unit
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_theme
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_theme_dark
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_theme_light
import com.adsamcik.temperaturedashboard.core.ui.resources.settings_theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/** Light/dark/system override surfaced in Settings; mirrors core.datastore.ThemeMode. */
enum class ThemeOption(val labelRes: StringResource) {
    SYSTEM(Res.string.settings_theme_system),
    LIGHT(Res.string.settings_theme_light),
    DARK(Res.string.settings_theme_dark),
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
        SettingsSection(title = stringResource(Res.string.settings_appearance)) {
            Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s)) {
                ThemeOption.entries.forEach { opt ->
                    FilterChip(
                        selected = opt == themeMode,
                        onClick = { onThemeChange(opt) },
                        label = { Text(stringResource(opt.labelRes)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.settings_material_you_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(Res.string.settings_material_you_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
            }
        }

        SettingsSection(title = stringResource(Res.string.settings_temperature_unit)) {
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

        SettingsSection(title = stringResource(Res.string.settings_coalescing_thresholds)) {
            Text(
                text = stringResource(Res.string.settings_coalescing_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SliderRow(
                label = stringResource(Res.string.settings_coalescing_temp_label),
                value = policy.temperatureThresholdC.toFloat(),
                valueRange = 0.05f..1.0f,
                steps = 18,
                display = { formatDecimal(it.toDouble(), 2) },
                onChange = { onPolicyChange(policy.copy(temperatureThresholdC = it.toDouble())) },
            )
            SliderRow(
                label = stringResource(Res.string.settings_coalescing_humidity_label),
                value = policy.humidityThresholdPct.toFloat(),
                valueRange = 0.5f..5.0f,
                steps = 8,
                display = { formatDecimal(it.toDouble(), 1) },
                onChange = { onPolicyChange(policy.copy(humidityThresholdPct = it.toDouble())) },
            )
        }

        SettingsSection(title = stringResource(Res.string.settings_stale_window)) {
            Text(
                text = stringResource(Res.string.settings_stale_window_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val minFmt = stringResource(Res.string.settings_stale_window_value, 0).replace("0", "%d")
            SliderRow(
                label = stringResource(Res.string.settings_stale_window_label),
                value = (policy.staleWindow.inWholeMilliseconds / 60_000L).toFloat(),
                valueRange = 1f..60f,
                steps = 58,
                display = { minFmt.replace("%d", it.toInt().toString()) },
                onChange = {
                    onPolicyChange(
                        policy.copy(staleWindow = (it.toLong() * 60_000L).milliseconds),
                    )
                },
            )
        }

        if (hiddenSensors.isNotEmpty()) {
            SettingsSection(title = stringResource(Res.string.settings_hidden_sensors)) {
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
                        TextButton(onClick = { onUnhideSensor(sensor) }) { Text(stringResource(Res.string.action_show)) }
                    }
                }
            }
        }

        if (autostartSupported) {
            SettingsSection(title = stringResource(Res.string.settings_start_at_login)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_start_at_login_title),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(Res.string.settings_start_at_login_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autostartEnabled, onCheckedChange = onAutostartChange)
                }
            }
        }

        SettingsSection(title = stringResource(Res.string.settings_about)) {
            Text(stringResource(Res.string.settings_about_version), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(Res.string.settings_about_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TdashSpacing.xs),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = TdashSpacing.s))
            Text(stringResource(Res.string.settings_oss_title), style = MaterialTheme.typography.labelMedium)
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
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
