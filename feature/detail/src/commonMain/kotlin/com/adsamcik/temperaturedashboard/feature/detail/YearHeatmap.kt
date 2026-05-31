package com.adsamcik.temperaturedashboard.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.ui.component.formatDecimal
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * 53-week × 7-day calendar heatmap (GitHub-contributions style). Each cell
 * is a day; colour mapped from cold (blue) → mid (amber) → hot (red) by
 * that day's time-weighted average temperature. Days with no readings
 * render as the surface colour so gaps are honestly visible.
 */
@Composable
fun YearHeatmap(
    intervals: List<ReadingInterval>,
    modifier: Modifier = Modifier,
) {
    val tz = TimeZone.currentSystemDefault()
    val dailyAvg = remember(intervals) { computeDailyAvg(intervals, tz) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(TdashSpacing.m)) {
            Text(
                text = "Daily average temperature, last 365 days",
                style = MaterialTheme.typography.titleMedium,
            )
            if (dailyAvg.isEmpty()) {
                Text(
                    text = "No data yet — heatmap fills in as readings accumulate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = TdashSpacing.s),
                )
                return@Column
            }

            val minT = dailyAvg.values.min()
            val maxT = dailyAvg.values.max()
            val empty = MaterialTheme.colorScheme.surface
            val cold = Color(0xFF1976D2)
            val mid = Color(0xFFFFB300)
            val hot = Color(0xFFE53935)

            val today = Clock.System.now().toLocalDateTime(tz).date
            val rangeStart = today.minus(364, DateTimeUnit.DAY)

            val a11ySummary = remember(dailyAvg, minT, maxT) {
                "Heatmap of ${dailyAvg.size} days of temperature data. " +
                    "Range from ${"%.1f".formatLike(minT)} to ${"%.1f".formatLike(maxT)} degrees Celsius."
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(53f / 7f)
                    .padding(top = TdashSpacing.s)
                    .semantics { contentDescription = a11ySummary },
            ) {
                val cellW = size.width / 53f
                val cellH = size.height / 7f
                val padPx = 1.5f
                for (i in 0 until 365) {
                    val date = rangeStart.plus(i, DateTimeUnit.DAY)
                    val col = i / 7
                    val row = i % 7
                    val temp = dailyAvg[date]
                    val cellColor = if (temp == null) empty
                    else colorForTemp(temp, minT, maxT, cold, mid, hot)
                    drawRect(
                        color = cellColor,
                        topLeft = Offset(col * cellW + padPx, row * cellH + padPx),
                        size = Size(cellW - 2 * padPx, cellH - 2 * padPx),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatDecimal(minT, 1)} °C",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatDecimal(maxT, 1)} °C",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun colorForTemp(
    t: Double,
    min: Double,
    max: Double,
    cold: Color,
    mid: Color,
    hot: Color,
): Color {
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    val frac = ((t - min) / range).coerceIn(0.0, 1.0).toFloat()
    return if (frac < 0.5f) lerp(cold, mid, frac * 2f) else lerp(mid, hot, (frac - 0.5f) * 2f)
}

/** KMP-safe one-decimal formatter — Locale-free so it works in commonMain. */
private fun String.formatLike(value: Double): String {
    // The receiver is just `"%.1f"` for readability at the call site; we don't
    // actually use Locale-based String.format here.
    val rounded = (value * 10).toLong()
    val whole = rounded / 10
    val tenths = kotlin.math.abs(rounded - whole * 10)
    return if (value < 0 && whole == 0L) "-0.$tenths" else "$whole.$tenths"
}

/**
 * Per-day time-weighted average. Each interval contributes to every day it
 * covers, clipped to day boundaries — so a 30-hour stretch is correctly
 * split across two days.
 */
private fun computeDailyAvg(
    intervals: List<ReadingInterval>,
    tz: TimeZone,
): Map<LocalDate, Double> {
    if (intervals.isEmpty()) return emptyMap()
    val sumByDay = mutableMapOf<LocalDate, Double>()
    val durByDay = mutableMapOf<LocalDate, Long>()
    for (interval in intervals) {
        val t = interval.temperatureC ?: continue
        var cursor: Instant = interval.validFrom
        while (cursor < interval.validUntil) {
            val date = cursor.toLocalDateTime(tz).date
            val dayEnd: Instant = date.atStartOfDayIn(tz) + 1.days
            val sliceEnd = if (interval.validUntil < dayEnd) interval.validUntil else dayEnd
            val durMs = (sliceEnd - cursor).inWholeMilliseconds
            sumByDay[date] = (sumByDay[date] ?: 0.0) + t * durMs
            durByDay[date] = (durByDay[date] ?: 0L) + durMs
            cursor = sliceEnd
        }
    }
    return durByDay.mapValues { (date, durMs) -> sumByDay.getValue(date) / durMs }
}

