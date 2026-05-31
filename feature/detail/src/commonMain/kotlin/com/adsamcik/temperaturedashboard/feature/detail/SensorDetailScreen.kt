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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.adsamcik.temperaturedashboard.core.ui.resources.Res
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_compare_to
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_coverage
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_export_csv
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_export_json
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_humidity
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_overlay_none
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_range_stats
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_sensor_not_found_message
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_sensor_not_found_title
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_stat_avg
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_stat_max
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_stat_min
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_stat_unknown
import com.adsamcik.temperaturedashboard.core.ui.resources.detail_sync_history_default
import com.adsamcik.temperaturedashboard.core.ui.resources.range_1d
import com.adsamcik.temperaturedashboard.core.ui.resources.range_1h
import com.adsamcik.temperaturedashboard.core.ui.resources.range_1m
import com.adsamcik.temperaturedashboard.core.ui.resources.range_1w
import com.adsamcik.temperaturedashboard.core.ui.resources.range_1y
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

enum class HistoryRange(val labelRes: StringResource) {
    Hour(Res.string.range_1h),
    Day(Res.string.range_1d),
    Week(Res.string.range_1w),
    Month(Res.string.range_1m),
    Year(Res.string.range_1y),
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
    syncHistoryLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    if (sensor == null) {
        EmptyState(
            title = stringResource(Res.string.detail_sensor_not_found_title),
            message = stringResource(Res.string.detail_sensor_not_found_message),
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
                        text = stringResource(Res.string.detail_humidity),
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
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.detail_export_csv)) }
            OutlinedButton(onClick = onExportJson, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.detail_export_json)) }
        }

        if (onSyncHistory != null) {
            OutlinedButton(
                onClick = onSyncHistory,
                modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.s),
            ) {
                Text(syncHistoryLabel ?: stringResource(Res.string.detail_sync_history_default))
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
                label = { Text(stringResource(r.labelRes)) },
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
        Text(stringResource(Res.string.detail_compare_to), style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.padding(top = TdashSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(TdashSpacing.xs),
        ) {
            AssistChip(
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.detail_overlay_none)) },
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
            Text(
                stringResource(Res.string.detail_range_stats),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(modifier = Modifier.padding(top = TdashSpacing.s).fillMaxWidth()) {
                val unknown = stringResource(Res.string.detail_stat_unknown)
                StatColumn(stringResource(Res.string.detail_stat_min), stats.minTemperatureC?.let { formatTemperature(it) + " °C" } ?: unknown)
                StatColumn(stringResource(Res.string.detail_stat_avg), stats.avgTemperatureC?.let { formatTemperature(it) + " °C" } ?: unknown)
                StatColumn(stringResource(Res.string.detail_stat_max), stats.maxTemperatureC?.let { formatTemperature(it) + " °C" } ?: unknown)
            }
            Text(
                text = stringResource(Res.string.detail_coverage, (stats.coverageRatio * 100).toInt()),
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
