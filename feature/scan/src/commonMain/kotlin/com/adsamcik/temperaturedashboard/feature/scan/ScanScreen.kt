package com.adsamcik.temperaturedashboard.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.SensorAddress
import com.adsamcik.temperaturedashboard.core.ui.component.EmptyState

/**
 * Add-sensor flow. Renders a live list of advertised devices that matched a
 * known [DeviceProfile] but aren't yet in the user's catalogue. Tapping
 * **Add** calls back to the shell which performs the registration via
 * SensorRepository.
 */
@Composable
fun ScanScreen(
    discoveries: List<ScanDiscovery>,
    isScanning: Boolean,
    errorMessage: String?,
    onAdd: (ScanDiscovery) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(TdashSpacing.m)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isScanning) "Scanning for sensors…" else "Scan paused",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = if (isScanning) onStop else onStart) {
                Text(if (isScanning) "Stop" else "Start")
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = TdashSpacing.s),
            )
        }

        if (discoveries.isEmpty()) {
            EmptyState(
                title = if (isScanning) "Looking for sensors…" else "Tap Start to scan",
                message = "Discovered sensors will show up here. Power-cycle the device " +
                    "to ensure it's actively broadcasting.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = TdashSpacing.m),
            verticalArrangement = Arrangement.spacedBy(TdashSpacing.s),
            contentPadding = PaddingValues(bottom = TdashSpacing.m),
        ) {
            items(discoveries, key = { it.address.raw }) { discovery ->
                DiscoveryCard(discovery = discovery, onAdd = { onAdd(discovery) })
            }
        }
    }
}

@Composable
private fun DiscoveryCard(discovery: ScanDiscovery, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(TdashSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = discovery.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${discovery.profileLabel} \u00B7 ${discovery.address.raw}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val temp = discovery.temperatureC
                val hum = discovery.humidityPct
                if (temp != null || hum != null) {
                    Text(
                        text = buildString {
                            if (temp != null) append("${formatOne(temp)} °C")
                            if (temp != null && hum != null) append(" \u00B7 ")
                            if (hum != null) append("${hum.toInt()} %")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(onClick = onAdd) { Text("Add") }
        }
    }
}

private fun formatOne(v: Double): String {
    val r = ((v * 10).toInt()).toDouble() / 10.0
    val whole = r.toLong()
    val tenths = (((r - whole) * 10).toInt()).let { if (it < 0) -it else it }
    return "$whole.$tenths"
}
