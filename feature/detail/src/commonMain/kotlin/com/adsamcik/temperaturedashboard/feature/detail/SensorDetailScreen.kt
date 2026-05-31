package com.adsamcik.temperaturedashboard.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.IntervalStats
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.core.ui.component.EmptyState
import com.adsamcik.temperaturedashboard.core.ui.component.TemperatureBigDisplay
import com.adsamcik.temperaturedashboard.core.ui.component.formatTemperature

/** Time-range presets the detail screen exposes. */
enum class HistoryRange(val label: String) {
    Hour("1H"),
    Day("1D"),
    Week("1W"),
    Month("1M"),
    Year("1Y"),
}

@Composable
fun SensorDetailScreen(
    sensor: Sensor?,
    intervals: List<ReadingInterval>,
    stats: IntervalStats,
    range: HistoryRange,
    unit: TemperatureUnit,
    onRangeChange: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sensor == null) {
        EmptyState(
            title = "Sensor not found",
            message = "This sensor may have been deleted.",
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TdashSpacing.m)) {
        Text(text = sensor.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "${sensor.profileId} \u00B7 ${sensor.address.raw}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.l),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom,
        ) {
            TemperatureBigDisplay(valueC = stats.currentTemperatureC, unit = unit)
            stats.currentHumidityPct?.let { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${h.toInt()} %",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = "Humidity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        RangeChips(
            range = range,
            onRangeChange = onRangeChange,
            modifier = Modifier.padding(top = TdashSpacing.l),
        )

        TemperatureChart(
            intervals = intervals,
            unit = unit,
            modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = TdashSpacing.m),
        )

        StatsPanel(
            stats = stats,
            unit = unit,
            modifier = Modifier.padding(top = TdashSpacing.l),
        )
    }
}

@Composable
private fun RangeChips(
    range: HistoryRange,
    onRangeChange: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s),
    ) {
        HistoryRange.entries.forEach { r ->
            FilterChip(
                selected = r == range,
                onClick = { onRangeChange(r) },
                label = { Text(r.label) },
            )
        }
    }
}

@Composable
private fun StatsPanel(stats: IntervalStats, unit: TemperatureUnit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(TdashSpacing.m)) {
            Text("Range stats", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = TdashSpacing.s).fillMaxWidth()) {
                StatColumn("Min", stats.minTemperatureC?.let { formatTemperature(it) + " °C" } ?: "—")
                StatColumn("Avg", stats.avgTemperatureC?.let { formatTemperature(it) + " °C" } ?: "—")
                StatColumn("Max", stats.maxTemperatureC?.let { formatTemperature(it) + " °C" } ?: "—")
            }
            Text(
                text = "Coverage: ${(stats.coverageRatio * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TdashSpacing.s),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatColumn(label: String, value: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Stepped-line temperature chart drawn directly on a Canvas — one segment per
 * interval. Honest gaps where the sensor went silent (i.e. consecutive
 * intervals separated by a gap > stale window) are drawn as visual gaps.
 */
@Composable
private fun TemperatureChart(
    intervals: List<ReadingInterval>,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background grid
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = size.height * i / gridCount
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            val series = intervals.filter { it.temperatureC != null }
            if (series.isEmpty()) return@Canvas

            val tMin = series.minOf { it.validFrom.toEpochMilliseconds() }
            val tMax = series.maxOf { it.validUntil.toEpochMilliseconds() }
            val tRange = (tMax - tMin).coerceAtLeast(1L).toDouble()
            val valMin = series.minOf { it.temperatureC!! }
            val valMax = series.maxOf { it.temperatureC!! }
            val valRange = (valMax - valMin).takeIf { it > 0 } ?: 1.0

            fun xAt(millis: Long): Float =
                ((millis - tMin).toDouble() / tRange * size.width).toFloat()
            fun yAt(value: Double): Float =
                (size.height - (value - valMin) / valRange * size.height).toFloat()

            val path = Path()
            var penDown = false
            var prevX = 0f
            var prevY = 0f
            for (interval in series) {
                val v = interval.temperatureC!!
                val x0 = xAt(interval.validFrom.toEpochMilliseconds())
                val x1 = xAt(interval.validUntil.toEpochMilliseconds())
                val y = yAt(v)
                if (penDown && x0 - prevX > size.width * 0.01f) {
                    penDown = false
                }
                if (!penDown) {
                    path.moveTo(x0, y)
                    penDown = true
                } else {
                    path.lineTo(x0, y)
                }
                path.lineTo(x1, y)
                prevX = x1; prevY = y
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
        }
    }
}
