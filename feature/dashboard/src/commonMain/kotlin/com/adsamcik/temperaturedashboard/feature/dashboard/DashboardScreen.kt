package com.adsamcik.temperaturedashboard.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.SensorId
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.core.ui.component.EmptyState
import com.adsamcik.temperaturedashboard.core.ui.component.SensorCard

/**
 * Dashboard — responsive grid of [SensorCard]s, one per added sensor.
 *
 * `:feature:dashboard` is deliberately side-effect-free at the API boundary:
 * it consumes pre-built [DashboardSensorRow]s the shared layer assembles
 * from SensorRepository + ReadingRepository + SettingsRepository. This
 * keeps the feature module easy to preview and test in isolation.
 */
@Composable
fun DashboardScreen(
    rows: List<DashboardSensorRow>,
    unit: TemperatureUnit,
    onSensorClick: (SensorId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        EmptyState(
            title = "No sensors yet",
            message = "Open the Add sensor tab to discover nearby ThermoPro, BTHome v2, " +
                "or ESS-standard thermometers.",
            modifier = modifier,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TdashSpacing.m),
        verticalArrangement = Arrangement.spacedBy(TdashSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(TdashSpacing.m),
    ) {
        items(rows, key = { it.sensor.id.raw }) { row ->
            SensorCard(
                title = row.sensor.displayName,
                subtitle = row.sensor.modelHint,
                temperatureC = row.currentTemperatureC,
                humidityPct = row.currentHumidityPct,
                batteryPct = row.batteryPct,
                rssi = row.rssi,
                sparklineValues = row.sparklineValues,
                unit = unit,
                onClick = { onSensorClick(row.sensor.id) },
            )
        }
    }
}

/** Pre-shaped row data that the shell builds from the repositories. */
data class DashboardSensorRow(
    val sensor: Sensor,
    val currentTemperatureC: Double?,
    val currentHumidityPct: Double?,
    val batteryPct: Int?,
    val rssi: Int?,
    val sparklineValues: List<Double>,
)
