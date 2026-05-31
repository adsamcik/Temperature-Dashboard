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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
    windowStart: Instant,
    windowEnd: Instant,
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
                windowStart = windowStart,
                windowEnd = windowEnd,
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
 *
 * Axes:
 *  - **X**: fixed to the user-selected window `[windowStart, windowEnd]` so
 *    the chart's width represents "the last hour/day/week", not "the data
 *    bounding box". Without this, two minutes of data in a 1H view would
 *    stretch across the whole canvas.
 *  - **Y**: auto-scaled to the union of all series' min/max, with at least
 *    1 °C of vertical headroom so a flat-line interval still draws across
 *    the middle instead of pinning to the bottom edge.
 *
 * Each interval draws as a true step: a horizontal segment at its temperature
 * from `validFrom` to `validUntil`. Adjacent intervals with different temps
 * get a vertical connector at the transition.
 */
@Composable
private fun TemperatureChart(
    primary: ChartSeries,
    secondary: ChartSeries?,
    windowStart: Instant,
    windowEnd: Instant,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = axisLabelColor)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Measure axis-label height up front and use it to size the gutters.
            val sampleLabelHeight = textMeasurer.measure("0", labelStyle).size.height.toFloat()
            val labelGutterPx = 44f
            val bottomGutterPx = sampleLabelHeight + 8f
            val plotLeft = labelGutterPx
            val plotTop = 4f
            val plotRight = size.width
            val plotBottom = (size.height - bottomGutterPx).coerceAtLeast(plotTop + 1f)
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

            val allSeries = listOfNotNull(primary, secondary)
                .filter { s -> s.intervals.any { it.temperatureC != null } }

            // X axis uses the user-selected window, not the data bbox.
            val tMin = windowStart.toEpochMilliseconds()
            val tMax = windowEnd.toEpochMilliseconds()
            val tRange = (tMax - tMin).coerceAtLeast(1L).toDouble()

            // Y axis: union of all series' temperatures, with vertical headroom.
            val tempValues = allSeries.flatMap { it.intervals.mapNotNull { i -> i.temperatureC } }
            val (valMin, valMax) = if (tempValues.isEmpty()) {
                0.0 to 1.0
            } else {
                val rawMin = tempValues.min()
                val rawMax = tempValues.max()
                val span = rawMax - rawMin
                val pad = if (span < 1.0) (1.0 - span) / 2.0 else span * 0.05
                (rawMin - pad) to (rawMax + pad)
            }
            val valRange = (valMax - valMin).coerceAtLeast(0.01)

            fun xAt(millis: Long): Float {
                val clamped = millis.coerceIn(tMin, tMax)
                return (plotLeft + (clamped - tMin).toDouble() / tRange * plotWidth).toFloat()
            }

            fun yAt(value: Double): Float =
                (plotBottom - (value - valMin) / valRange * plotHeight).toFloat()

            // Horizontal grid lines at min / mid / max.
            val gridLevels = listOf(valMax, (valMax + valMin) / 2.0, valMin)
            for (level in gridLevels) {
                val y = yAt(level)
                drawLine(
                    color = gridColor.copy(alpha = 0.35f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f,
                )
                val labelText = formatTemperature(level) + "°"
                val measured = textMeasurer.measure(labelText, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    style = labelStyle,
                    topLeft = Offset(0f, (y - measured.size.height / 2f).coerceAtLeast(0f)),
                )
            }

            // Vertical axis line at plot left.
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(plotLeft, plotTop),
                end = Offset(plotLeft, plotBottom),
                strokeWidth = 1f,
            )

            // X-axis labels: now and the window start.
            val tz = TimeZone.currentSystemDefault()
            val startLabel = formatAxisTime(windowStart, windowEnd - windowStart, tz)
            val endLabel = formatAxisTime(windowEnd, windowEnd - windowStart, tz)
            val startMeasured = textMeasurer.measure(startLabel, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = startLabel,
                style = labelStyle,
                topLeft = Offset(plotLeft, plotBottom + 2f),
            )
            val endMeasured = textMeasurer.measure(endLabel, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = endLabel,
                style = labelStyle,
                topLeft = Offset(plotRight - endMeasured.size.width, plotBottom + 2f),
            )

            if (allSeries.isEmpty()) return@Canvas

            for (series in allSeries) {
                drawSteppedSeries(
                    series = series,
                    xAt = ::xAt,
                    yAt = ::yAt,
                    plotLeft = plotLeft,
                    plotRight = plotRight,
                )
            }
        }
    }
}

/**
 * Draws one series as a proper stepped line: horizontal segment per interval
 * at its temperature, vertical connector when the temperature changes between
 * adjacent intervals, and a gap (no connector) when the sensor went silent.
 * Intervals fully outside the visible window are skipped; partial intervals
 * are clipped at the window edges by the [xAt] helper's clamp.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSteppedSeries(
    series: ChartSeries,
    xAt: (Long) -> Float,
    yAt: (Double) -> Float,
    plotLeft: Float,
    plotRight: Float,
) {
    val path = Path()
    var penDown = false
    var prevX = 0f
    var prevY = 0f
    val intervals = series.intervals.asSequence()
        .filter { it.temperatureC != null }
        .sortedBy { it.validFrom }
        .toList()
    if (intervals.isEmpty()) return

    // Treat any horizontal gap larger than ~0.4% of plot width as a real
    // gap in coverage; smaller gaps are just floating-point noise between
    // adjacent intervals.
    val gapPx = (plotRight - plotLeft) * 0.004f

    for (interval in intervals) {
        val v = interval.temperatureC!!
        val x0 = xAt(interval.validFrom.toEpochMilliseconds())
        val x1 = xAt(interval.validUntil.toEpochMilliseconds())
        val y = yAt(v)
        if (x1 < plotLeft || x0 > plotRight) continue

        if (!penDown || x0 - prevX > gapPx) {
            // Gap (or first interval): lift the pen, move to (x0, y).
            path.moveTo(x0, y)
        } else {
            // Adjacent interval: draw the connector. Stepped means horizontal
            // at the prior y until x0, then vertical at x0 to the new y.
            path.lineTo(x0, prevY)
            if (y != prevY) path.lineTo(x0, y)
        }
        path.lineTo(x1, y)
        penDown = true
        prevX = x1
        prevY = y
    }

    drawPath(
        path = path,
        color = series.color,
        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Picks a sensible label format for the X axis based on the window length:
 *  - ≤ 12 h → HH:mm
 *  - ≤ 7 days → "Mon dd HH:mm"
 *  - longer → "Mon dd"
 *
 * For the longer windows we include the date so the two endpoints don't
 * collapse to the same value (e.g. "22:30" left and "22:30" right when the
 * window is exactly 24h).
 */
private fun formatAxisTime(instant: Instant, windowSpan: Duration, tz: TimeZone): String {
    val ldt = instant.toLocalDateTime(tz)
    val twoDigit: (Int) -> String = { if (it < 10) "0$it" else it.toString() }
    val hhmm = "${twoDigit(ldt.hour)}:${twoDigit(ldt.minute)}"
    val monShort = ldt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return when {
        windowSpan <= 12.hours -> hhmm
        windowSpan <= 7.days -> "$monShort ${twoDigit(ldt.dayOfMonth)} $hhmm"
        else -> "$monShort ${twoDigit(ldt.dayOfMonth)}"
    }
}
