package com.adsamcik.temperaturedashboard.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.adsamcik.temperaturedashboard.core.datastore.SettingsRepository
import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.SensorId
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.feature.dashboard.DashboardScreen
import com.adsamcik.temperaturedashboard.feature.dashboard.DashboardSensorRow
import com.adsamcik.temperaturedashboard.feature.detail.HistoryRange
import com.adsamcik.temperaturedashboard.feature.detail.SensorDetailScreen
import com.adsamcik.temperaturedashboard.feature.scan.ScanDiscovery
import com.adsamcik.temperaturedashboard.feature.scan.ScanScreen
import com.adsamcik.temperaturedashboard.feature.settings.SettingsScreen
import com.adsamcik.temperaturedashboard.shared.navigation.ShellDestination
import com.adsamcik.temperaturedashboard.shared.repository.ReadingRepository
import com.adsamcik.temperaturedashboard.shared.repository.SensorRepository
import com.adsamcik.temperaturedashboard.shared.scanning.ScanningCoordinator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Top-level integration layer. Wires repositories into the feature screens
 * and hands them off to [TdashAppShell].
 *
 * Both the Android Activity and the Desktop `application { Window { ... } }`
 * just call this composable inside their Compose set-content.
 */
@Composable
fun TdashApp(useCompactLayout: Boolean) {
    val sensorRepository = koinInject<SensorRepository>()
    val readingRepository = koinInject<ReadingRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val coordinator = koinInject<ScanningCoordinator>()

    val unit by settingsRepository.observeTemperatureUnit()
        .collectAsState(initial = TemperatureUnit.CELSIUS)
    val policy by settingsRepository.observeCoalescingPolicy()
        .collectAsState(initial = CoalescingPolicy.Default)

    val sensors by sensorRepository.observeAll().collectAsState(initial = emptyList())

    // Auto-start scanning on first composition; coordinator survives the composable.
    LaunchedEffect(Unit) {
        when (val result = coordinator.start()) {
            is com.adsamcik.temperaturedashboard.ble.api.ScanStartResult.Failed -> {
                Napier.w("Scan start failed: ${result.reason} ${result.message ?: ""}")
            }
            else -> Unit
        }
    }

    // Scan discoveries flow into a stable list keyed by address (latest wins).
    val discoveries = remember { MutableStateFlow<Map<String, ScanDiscovery>>(emptyMap()) }
    LaunchedEffect(Unit) {
        coordinator.discoveries.collect { interpreted ->
            val advert = interpreted.advertisement
            discoveries.update {
                it + (advert.address to ScanDiscovery(
                    address = com.adsamcik.temperaturedashboard.core.model.SensorAddress.normalize(advert.address),
                    displayName = advert.name ?: interpreted.profile.displayName,
                    profileId = interpreted.profile.id,
                    profileLabel = interpreted.profile.displayName,
                    temperatureC = interpreted.reading?.temperatureC,
                    humidityPct = interpreted.reading?.humidityPct,
                    rssi = advert.rssi,
                ))
            }
        }
    }
    val discoveryList by discoveries.collectAsState()
    val scope = rememberCoroutineScope()
    val scanState by coordinator.state.collectAsState()

    val shellScreens: Map<ShellDestination, @Composable () -> Unit> = mapOf(
        ShellDestination.Dashboard to {
            DashboardRoute(
                sensors = sensors,
                unit = unit,
                readingRepository = readingRepository,
                onSensorClick = { /* navigated via the shell */ },
            )
        },
        ShellDestination.Scan to {
            ScanScreen(
                discoveries = discoveryList.values.toList(),
                isScanning = scanState == com.adsamcik.temperaturedashboard.ble.api.ScanState.Scanning,
                errorMessage = null,
                onAdd = { discovery ->
                    scope.launch {
                        sensorRepository.registerIfAbsent(
                            address = discovery.address,
                            profileId = discovery.profileId,
                            displayName = discovery.displayName,
                            modelHint = discovery.profileLabel,
                        )
                        discoveries.update { it - discovery.address.raw }
                    }
                },
                onStart = { scope.launch { coordinator.start() } },
                onStop = { scope.launch { coordinator.stop() } },
            )
        },
        ShellDestination.Settings to {
            SettingsScreen(
                unit = unit,
                policy = policy,
                onUnitChange = settingsRepository::setTemperatureUnit,
                onPolicyChange = settingsRepository::setCoalescingPolicy,
            )
        },
    )

    TdashAppShell(
        shellScreens = shellScreens,
        sensorDetailScreen = { sensorId ->
            SensorDetailRoute(
                sensorId = sensorId,
                unit = unit,
                sensorRepository = sensorRepository,
                readingRepository = readingRepository,
            )
        },
        useCompactLayout = useCompactLayout,
    )
}

@Composable
private fun DashboardRoute(
    sensors: List<Sensor>,
    unit: TemperatureUnit,
    readingRepository: ReadingRepository,
    onSensorClick: (SensorId) -> Unit,
) {
    var rows by remember { mutableStateOf<List<DashboardSensorRow>>(emptyList()) }
    LaunchedEffect(sensors) {
        // For each sensor: latest reading + a small sample of recent values for the sparkline.
        rows = sensors.map { sensor ->
            val latest = readingRepository.observeLatest(sensor.id)
            val recent = readingRepository.findInRange(
                sensorId = sensor.id,
                from = Clock.System.now() - 1.days,
                until = Clock.System.now(),
            )
            DashboardSensorRow(
                sensor = sensor,
                currentTemperatureC = recent.maxByOrNull { it.validUntil }?.temperatureC,
                currentHumidityPct = recent.maxByOrNull { it.validUntil }?.humidityPct,
                batteryPct = recent.maxByOrNull { it.validUntil }?.batteryPct,
                rssi = recent.maxByOrNull { it.validUntil }?.rssiAvg,
                sparklineValues = recent.mapNotNull { it.temperatureC }.takeLast(60),
            )
        }
    }
    DashboardScreen(rows = rows, unit = unit, onSensorClick = onSensorClick)
}

@Composable
private fun SensorDetailRoute(
    sensorId: SensorId,
    unit: TemperatureUnit,
    sensorRepository: SensorRepository,
    readingRepository: ReadingRepository,
) {
    var range by remember { mutableStateOf(HistoryRange.Day) }
    val sensor by sensorRepository.observe(sensorId).collectAsState(initial = null)
    val window = remember(range) { range.toDuration() }
    val now = remember(window) { Clock.System.now() }
    val intervals by readingRepository.observeInRange(
        sensorId = sensorId,
        from = now - window,
        until = now,
    ).collectAsState(initial = emptyList())

    val stats = remember(intervals, window) {
        com.adsamcik.temperaturedashboard.core.model.IntervalAggregator
            .aggregate(intervals, window)
    }

    SensorDetailScreen(
        sensor = sensor,
        intervals = intervals,
        stats = stats,
        range = range,
        unit = unit,
        onRangeChange = { range = it },
    )
}

private fun HistoryRange.toDuration() = when (this) {
    HistoryRange.Hour -> 1.hours
    HistoryRange.Day -> 1.days
    HistoryRange.Week -> 7.days
    HistoryRange.Month -> 30.days
    HistoryRange.Year -> 365.days
}
