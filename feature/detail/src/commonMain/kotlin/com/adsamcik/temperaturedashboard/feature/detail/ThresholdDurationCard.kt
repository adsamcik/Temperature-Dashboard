package com.adsamcik.temperaturedashboard.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.IntervalAggregator
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.ui.component.formatDecimal
import kotlin.time.Duration

/**
 * Compact summary card for "time the temperature was above / below a
 * user-set threshold". Three modes: Above / Below / In range. The threshold
 * is per-card state — doesn't persist; the user picks it in the moment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdDurationCard(
    intervals: List<ReadingInterval>,
    modifier: Modifier = Modifier,
) {
    var modeIndex by remember { mutableStateOf(0) }
    val modes = listOf("Above", "Below")
    var threshold by remember { mutableStateOf(20.0) }

    val duration: Duration = remember(intervals, modeIndex, threshold) {
        when (modeIndex) {
            0 -> IntervalAggregator.durationAbove(intervals, threshold)
            else -> IntervalAggregator.durationBelow(intervals, threshold)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(TdashSpacing.m),
            verticalArrangement = Arrangement.spacedBy(TdashSpacing.s),
        ) {
            Text("Time at threshold", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s)) {
                modes.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = idx == modeIndex,
                        onClick = { modeIndex = idx },
                        label = { Text(label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Threshold ${formatDecimal(threshold, 1)} °C",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = duration.humanReadable(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = threshold.toFloat(),
                onValueChange = { threshold = it.toDouble() },
                valueRange = -20f..50f,
            )
            Text(
                text = "Of the times this sensor was reporting in the current range, " +
                    "it was ${modes[modeIndex].lowercase()} ${formatDecimal(threshold, 1)} °C " +
                    "for ${duration.humanReadable()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Duration.humanReadable(): String {
    val totalMin = inWholeMinutes
    if (totalMin <= 0L) return "0 min"
    val days = totalMin / (60 * 24)
    val hours = (totalMin / 60) % 24
    val minutes = totalMin % 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        append("${minutes}m")
    }.trim()
}
