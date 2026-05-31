package com.adsamcik.temperaturedashboard.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit

/**
 * Single sensor card for the dashboard — temperature in big, humidity / battery
 * / signal as small badges, sparkline preview underneath. A small accent dot
 * + tinted sparkline help distinguish many cards at a glance.
 *
 * Long-press fires [onLongClick] — the dashboard binds this to its action
 * bottom-sheet for rename / colour / hide / delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorCard(
    title: String,
    subtitle: String?,
    temperatureC: Double?,
    humidityPct: Double?,
    batteryPct: Int?,
    rssi: Int?,
    sparklineValues: List<Double>,
    unit: TemperatureUnit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    accentColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val resolvedAccent = if (accentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else accentColor

    val a11yDescription = remember(title, temperatureC, humidityPct, batteryPct, unit) {
        buildString {
            append(title)
            if (temperatureC != null) {
                append(", temperature ")
                append(formatTemperature(temperatureC))
                append(' ')
                append(unit.symbol)
            }
            if (humidityPct != null) {
                append(", humidity ")
                append(humidityPct.toInt())
                append(" percent")
            }
            if (batteryPct != null) {
                append(", battery ")
                append(batteryPct)
                append(" percent")
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11yDescription }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier.padding(TdashSpacing.m),
            verticalArrangement = Arrangement.spacedBy(TdashSpacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(resolvedAccent),
                )
                Spacer(Modifier.width(TdashSpacing.s))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TemperatureBigDisplay(valueC = temperatureC, unit = unit)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(TdashSpacing.xs),
                ) {
                    if (humidityPct != null) Badge(
                        icon = Icons.Outlined.WaterDrop,
                        text = "${humidityPct.toInt()} %",
                        iconCd = "humidity",
                    )
                    if (batteryPct != null) Badge(
                        icon = Icons.Outlined.BatteryFull,
                        text = "$batteryPct %",
                        iconCd = "battery",
                    )
                    if (rssi != null) Badge(
                        icon = Icons.Outlined.SignalCellular4Bar,
                        text = "$rssi dBm",
                        iconCd = "signal",
                    )
                }
            }
            Sparkline(
                values = sparklineValues,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                color = resolvedAccent,
            )
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String, iconCd: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TdashSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconCd,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


