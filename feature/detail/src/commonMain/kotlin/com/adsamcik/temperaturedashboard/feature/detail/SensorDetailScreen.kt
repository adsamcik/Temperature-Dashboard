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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.adsamcik.temperaturedashboard.core.model.AlertKind
import com.adsamcik.temperaturedashboard.core.model.IntervalStats
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.SensorAlert
import com.adsamcik.temperaturedashboard.core.model.SensorId
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.core.ui.component.EmptyState
import com.adsamcik.temperaturedashboard.core.ui.component.TemperatureBigDisplay
import com.adsamcik.temperaturedashboard.core.ui.component.formatTemperature
import kotlin.time.Duration

enum class HistoryRange(val label: String) {
    Hour("1H"),
    Day("1D"),
    Week("1W"),
    Month("1M"),
    Year("1Y"),
}

/** Compare-with selection: the other sensor whose line should overlay the chart, or null for none. */
data class OverlayChoice(val sensor: Sensor?, val intervals: List<ReadingInterval>)

@Composable
fun SensorDetailScreen(
    sensor: Sensor?,
    intervals: List<ReadingInterval>,
    stats: IntervalStats,
    range: HistoryRange,
    unit: TemperatureUnit,
    alerts: List<SensorAlert>,
    candidateOverlays: List<Sensor>,
    overlay: OverlayChoice,
    onRangeChange: (HistoryRange) -> Unit,
    onOverlayChange: (SensorId?) -> Unit,
    onToggleAlert: (SensorAlert, Boolean) -> Unit,
    onAddAlert: (AlertKind, Duration) -> Unit,
    onDeleteAlert: (Long) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onSyncHistory: (() -> Unit)? = null,
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
    val accent = Color(sensor.colorSeed.toLong() or 0xFF000000L)

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
                    Text(text = "${h.toInt()} %", style = MaterialTheme.typography.displaySmall)
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

        if (candidateOverlays.isNotEmpty()) {
            OverlayPicker(
                candidates = candidateOverlays,
                current = overlay.sensor,
                onSelect = onOverlayChange,
                modifier = Modifier.padding(top = TdashSpacing.s),
            )
        }

        if (range == HistoryRange.Year) {
            YearHeatmap(intervals = intervals, modifier = Modifier.padding(top = TdashSpacing.m))
        } else {
            TemperatureChart(
                primary = ChartSeries(intervals, accent),
                secondary = overlay.sensor?.let {
                    val secondaryColor = Color(it.colorSeed.toLong() or 0xFF000000L)
                    ChartSeries(overlay.intervals, secondaryColor)
                },
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = TdashSpacing.m),
            )
        }

        StatsPanel(stats = stats, modifier = Modifier.padding(top = TdashSpacing.l))

        ThresholdDurationCard(
            intervals = intervals,
            modifier = Modifier.padding(top = TdashSpacing.l),
        )

        AlertsPanel(
            alerts = alerts,
            onToggle = onToggleAlert,
            onAdd = onAddAlert,
            onDelete = onDeleteAlert,
            modifier = Modifier.padding(top = TdashSpacing.l),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s),
        ) {
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) { Text("Export CSV") }
            OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) { Text("Export JSON") }
        }

        if (onSyncHistory != null) {
            OutlinedButton(
                onClick = onSyncHistory,
                modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.s),
            ) {
                Text("Sync 24h history from device (BETA)")
            }
        }
    }
}

@Composable
private fun RangeChips(
    range: HistoryRange,
    onRangeChange: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s)) {
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
private fun OverlayPicker(
    candidates: List<Sensor>,
    current: Sensor?,
    onSelect: (SensorId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Compare to", style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.padding(top = TdashSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(TdashSpacing.xs),
        ) {
            AssistChip(
                onClick = { onSelect(null) },
                label = { Text("None") },
                colors = if (current == null) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else AssistChipDefaults.assistChipColors(),
            )
            candidates.forEach { s ->
                AssistChip(
                    onClick = { onSelect(s.id) },
                    label = { Text(s.displayName) },
                    colors = if (current?.id == s.id) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    } else AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}

@Composable
private fun StatsPanel(stats: IntervalStats, modifier: Modifier = Modifier) {
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

/** Renderable series for [TemperatureChart] — colour plus intervals. */
data class ChartSeries(val intervals: List<ReadingInterval>, val color: Color)

/**
 * Stepped-line temperature chart with optional secondary overlay. Both
 * series share the same auto-scaled axes so they're directly comparable.
 * Gaps between consecutive intervals where the device went silent draw as
 * breaks in the line.
 */
@Composable
private fun TemperatureChart(
    primary: ChartSeries,
    secondary: ChartSeries?,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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

            val allSeries = listOfNotNull(primary, secondary)
                .filter { s -> s.intervals.any { it.temperatureC != null } }
            if (allSeries.isEmpty()) return@Canvas

            val tMin = allSeries.minOf { s -> s.intervals.minOf { it.validFrom.toEpochMilliseconds() } }
            val tMax = allSeries.maxOf { s -> s.intervals.maxOf { it.validUntil.toEpochMilliseconds() } }
            val tRange = (tMax - tMin).coerceAtLeast(1L).toDouble()
            val valMin = allSeries.minOf { s -> s.intervals.mapNotNull { it.temperatureC }.min() }
            val valMax = allSeries.maxOf { s -> s.intervals.mapNotNull { it.temperatureC }.max() }
            val valRange = (valMax - valMin).takeIf { it > 0 } ?: 1.0

            fun xAt(millis: Long) = ((millis - tMin).toDouble() / tRange * size.width).toFloat()
            fun yAt(value: Double) =
                (size.height - (value - valMin) / valRange * size.height).toFloat()

            for (series in allSeries) {
                val path = Path()
                var penDown = false
                var prevX = 0f
                for (interval in series.intervals.filter { it.temperatureC != null }) {
                    val v = interval.temperatureC!!
                    val x0 = xAt(interval.validFrom.toEpochMilliseconds())
                    val x1 = xAt(interval.validUntil.toEpochMilliseconds())
                    val y = yAt(v)
                    if (penDown && x0 - prevX > size.width * 0.01f) penDown = false
                    if (!penDown) {
                        path.moveTo(x0, y); penDown = true
                    } else {
                        path.lineTo(x0, y)
                    }
                    path.lineTo(x1, y)
                    prevX = x1
                }
                drawPath(
                    path = path,
                    color = series.color,
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
            }
        }
    }
}
