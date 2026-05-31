package com.adsamcik.temperaturedashboard.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.adsamcik.temperaturedashboard.core.datastore.SettingsRepository
import com.adsamcik.temperaturedashboard.core.datastore.ThemeMode
import com.adsamcik.temperaturedashboard.core.designsystem.TdashTheme
import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.ReadingInterval
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.model.SensorId
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.adsamcik.temperaturedashboard.core.ui.resources.Res
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_autostart_disabled
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_autostart_failed
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_autostart_unsupported
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_autostart_will_start
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_connecting_to
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_export_failed
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_export_ok
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_sync_failed
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_sync_ok
import com.adsamcik.temperaturedashboard.core.ui.resources.snack_sync_skipped
import org.jetbrains.compose.resources.getString
import com.adsamcik.temperaturedashboard.feature.dashboard.DashboardScreen
import com.adsamcik.temperaturedashboard.feature.dashboard.DashboardSensorRow
import com.adsamcik.temperaturedashboard.feature.dashboard.SensorActionSheet
import com.adsamcik.temperaturedashboard.feature.detail.HistoryRange
import com.adsamcik.temperaturedashboard.feature.detail.OverlayChoice
import com.adsamcik.temperaturedashboard.feature.detail.SensorDetailScreen
import com.adsamcik.temperaturedashboard.feature.scan.ScanDiscovery
import com.adsamcik.temperaturedashboard.feature.scan.ScanScreen
import com.adsamcik.temperaturedashboard.feature.settings.SettingsScreen
import com.adsamcik.temperaturedashboard.feature.settings.ThemeOption
import com.adsamcik.temperaturedashboard.shared.alerts.SensorAlertRepository
import com.adsamcik.temperaturedashboard.shared.export.ExportResult
import com.adsamcik.temperaturedashboard.shared.export.HistorySharer
import com.adsamcik.temperaturedashboard.shared.navigation.ShellDestination
import com.adsamcik.temperaturedashboard.shared.repository.ReadingRepository
import com.adsamcik.temperaturedashboard.shared.repository.SensorRepository
import com.adsamcik.temperaturedashboard.shared.scanning.ScanningCoordinator
import com.adsamcik.temperaturedashboard.shared.system.AutostartManager
import com.adsamcik.temperaturedashboard.shared.system.AutostartResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Top-level integration layer. Wires repositories into feature screens and
 * hands them off to [TdashAppShell]. Theming, autostart, export, alerts —
 * everything stateful flows through here.
 */
@Composable
fun TdashApp(useCompactLayout: Boolean) {
    val sensorRepository = koinInject<SensorRepository>()
    val readingRepository = koinInject<ReadingRepository>()
    val alertRepository = koinInject<SensorAlertRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val coordinator = koinInject<ScanningCoordinator>()
    val historySharer = koinInject<HistorySharer>()
    val autostartManager = koinInject<AutostartManager>()
    val backfillCoordinator = koinInject<com.adsamcik.temperaturedashboard.shared.backfill.BackfillCoordinator>()
    val snackbarState = remember { SnackbarHostState() }

    val unit by settingsRepository.observeTemperatureUnit()
        .collectAsState(initial = TemperatureUnit.CELSIUS)
    val policy by settingsRepository.observeCoalescingPolicy()
        .collectAsState(initial = CoalescingPolicy.Default)
    val themeMode by settingsRepository.observeThemeMode()
        .collectAsState(initial = ThemeMode.SYSTEM)
    val dynamicColor by settingsRepository.observeDynamicColor()
        .collectAsState(initial = true)

    val sensors by sensorRepository.observeAll().collectAsState(initial = emptyList())
    val allSensors by sensorRepository.observeAllIncludingHidden().collectAsState(initial = emptyList())
    val hiddenSensors = allSensors.filter { it.hidden }

    LaunchedEffect(Unit) {
        when (val result = coordinator.start()) {
            is com.adsamcik.temperaturedashboard.ble.api.ScanStartResult.Failed -> {
                Napier.w("Scan start failed: ${result.reason} ${result.message ?: ""}")
            }
            else -> Unit
        }
    }

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

    var autostartEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(autostartManager) {
        autostartEnabled = autostartManager.isEnabled()
    }

    val shellScreens: Map<ShellDestination, @Composable (onOpenSensorDetail: (SensorId) -> Unit) -> Unit> = mapOf(
        ShellDestination.Dashboard to { openDetail ->
            DashboardRoute(
                sensors = sensors,
                unit = unit,
                readingRepository = readingRepository,
                sensorRepository = sensorRepository,
                onSensorClick = openDetail,
            )
        },
        ShellDestination.Scan to { _ ->
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
        ShellDestination.Settings to { _ ->
            SettingsScreen(
                unit = unit,
                policy = policy,
                themeMode = themeMode.toFeature(),
                dynamicColor = dynamicColor,
                hiddenSensors = hiddenSensors,
                autostartSupported = autostartManager.supported,
                autostartEnabled = autostartEnabled,
                onUnitChange = settingsRepository::setTemperatureUnit,
                onPolicyChange = settingsRepository::setCoalescingPolicy,
                onThemeChange = { settingsRepository.setThemeMode(it.toData()) },
                onDynamicColorChange = settingsRepository::setDynamicColor,
                onUnhideSensor = { s -> scope.launch { sensorRepository.setHidden(s.id, false) } },
                onAutostartChange = { enabled ->
                    scope.launch {
                        val result = if (enabled) autostartManager.enable() else autostartManager.disable()
                        autostartEnabled = autostartManager.isEnabled()
                        val msg = when (result) {
                            AutostartResult.Ok -> if (enabled) {
                                getString(Res.string.snack_autostart_will_start)
                            } else getString(Res.string.snack_autostart_disabled)
                            AutostartResult.NotSupported -> getString(Res.string.snack_autostart_unsupported)
                            is AutostartResult.Failed -> getString(Res.string.snack_autostart_failed, result.message)
                        }
                        snackbarState.showSnackbar(msg)
                    }
                },
            )
        },
    )

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    TdashTheme(darkTheme = isDark, dynamicColor = dynamicColor) {
        TdashAppShell(
            shellScreens = shellScreens,
            sensorDetailScreen = { sensorId ->
                SensorDetailRoute(
                    sensorId = sensorId,
                    allSensors = sensors,
                    unit = unit,
                    sensorRepository = sensorRepository,
                    readingRepository = readingRepository,
                    alertRepository = alertRepository,
                    historySharer = historySharer,
                    backfillCoordinator = backfillCoordinator,
                    snackbarState = snackbarState,
                )
            },
            useCompactLayout = useCompactLayout,
            snackbarState = snackbarState,
        )
    }
}

private fun ThemeMode.toFeature(): ThemeOption = when (this) {
    ThemeMode.SYSTEM -> ThemeOption.SYSTEM
    ThemeMode.LIGHT -> ThemeOption.LIGHT
    ThemeMode.DARK -> ThemeOption.DARK
}

private fun ThemeOption.toData(): ThemeMode = when (this) {
    ThemeOption.SYSTEM -> ThemeMode.SYSTEM
    ThemeOption.LIGHT -> ThemeMode.LIGHT
    ThemeOption.DARK -> ThemeMode.DARK
}

@Composable
private fun DashboardRoute(
    sensors: List<Sensor>,
    unit: TemperatureUnit,
    readingRepository: ReadingRepository,
    sensorRepository: SensorRepository,
    onSensorClick: (SensorId) -> Unit,
) {
    var rows by remember { mutableStateOf<List<DashboardSensorRow>>(emptyList()) }
    var actionSensor by remember { mutableStateOf<Sensor?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sensors) {
        rows = sensors.map { sensor ->
            val recent = readingRepository.findInRange(
                sensorId = sensor.id,
                from = Clock.System.now() - 1.days,
                until = Clock.System.now(),
            )
            val latest = recent.maxByOrNull { it.validUntil }
            DashboardSensorRow(
                sensor = sensor,
                currentTemperatureC = latest?.temperatureC,
                currentHumidityPct = latest?.humidityPct,
                batteryPct = latest?.batteryPct,
                rssi = latest?.rssiAvg,
                sparklineValues = recent.mapNotNull { it.temperatureC }.takeLast(60),
            )
        }
    }
    DashboardScreen(
        rows = rows,
        unit = unit,
        onSensorClick = onSensorClick,
        onSensorLongClick = { id -> actionSensor = sensors.firstOrNull { it.id == id } },
    )

    actionSensor?.let { sensor ->
        SensorActionSheet(
            sensor = sensor,
            onRename = { newName -> scope.launch { sensorRepository.rename(sensor.id, newName) } },
            onChangeColor = { argb -> scope.launch { sensorRepository.setColor(sensor.id, argb) } },
            onHide = { scope.launch { sensorRepository.setHidden(sensor.id, !sensor.hidden) } },
            onDelete = { scope.launch { sensorRepository.delete(sensor.id) } },
            onDismiss = { actionSensor = null },
        )
    }
}

@Composable
private fun SensorDetailRoute(
    sensorId: SensorId,
    allSensors: List<Sensor>,
    unit: TemperatureUnit,
    sensorRepository: SensorRepository,
    readingRepository: ReadingRepository,
    alertRepository: SensorAlertRepository,
    historySharer: HistorySharer,
    backfillCoordinator: com.adsamcik.temperaturedashboard.shared.backfill.BackfillCoordinator,
    snackbarState: SnackbarHostState,
) {
    var range by remember { mutableStateOf(HistoryRange.Day) }
    var overlaySensorId by remember { mutableStateOf<SensorId?>(null) }
    val sensor by sensorRepository.observe(sensorId).collectAsState(initial = null)
    val window = remember(range) { range.toDuration() }
    val now = remember(window) { Clock.System.now() }
    val intervals by readingRepository.observeInRange(
        sensorId = sensorId,
        from = now - window,
        until = now,
    ).collectAsState(initial = emptyList())
    val alerts by alertRepository.observeForSensor(sensorId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val overlaySensor = overlaySensorId?.let { id -> allSensors.firstOrNull { it.id == id } }
    var overlayIntervals by remember { mutableStateOf<List<ReadingInterval>>(emptyList()) }
    LaunchedEffect(overlaySensorId, range) {
        overlayIntervals = overlaySensorId?.let {
            readingRepository.findInRange(it, now - window, now)
        }.orEmpty()
    }

    val stats = remember(intervals, window) {
        com.adsamcik.temperaturedashboard.core.model.IntervalAggregator.aggregate(intervals, window)
    }

    val currentSensor = sensor
    val capability = currentSensor?.profileId
        ?.let { com.adsamcik.temperaturedashboard.shared.backfill.BackfillRegistry.forSensor(it) }
    val syncHistoryCallback: (() -> Unit)? = if (capability != null && currentSensor != null) {
        {
            scope.launch {
                snackbarState.showSnackbar(getString(Res.string.snack_connecting_to, currentSensor.displayName))
                val result = backfillCoordinator.run(currentSensor)
                val msg = when (result) {
                    is com.adsamcik.temperaturedashboard.shared.backfill.BackfillResult.Ok ->
                        getString(Res.string.snack_sync_ok, result.readingsIngested)
                    is com.adsamcik.temperaturedashboard.shared.backfill.BackfillResult.Skipped ->
                        getString(Res.string.snack_sync_skipped, result.reason)
                    is com.adsamcik.temperaturedashboard.shared.backfill.BackfillResult.Failed ->
                        getString(Res.string.snack_sync_failed, result.message)
                }
                snackbarState.showSnackbar(msg)
            }
        }
    } else null

    SensorDetailScreen(
        sensor = sensor,
        intervals = intervals,
        stats = stats,
        range = range,
        unit = unit,
        alerts = alerts,
        candidateOverlays = allSensors.filter { it.id != sensorId },
        overlay = OverlayChoice(overlaySensor, overlayIntervals),
        windowStart = now - window,
        windowEnd = now,
        onRangeChange = { range = it },
        onOverlayChange = { overlaySensorId = it },
        onToggleAlert = { alert, enabled ->
            scope.launch { alertRepository.setEnabled(alert, enabled) }
        },
        onAddAlert = { kind, cooldown ->
            scope.launch { alertRepository.add(sensorId, kind, cooldown) }
        },
        onDeleteAlert = { id -> scope.launch { alertRepository.delete(id) } },
        onExportCsv = {
            scope.launch {
                val current = sensor ?: return@launch
                snackbarState.showSnackbar(historySharer.exportCsv(current, intervals).toUserMessage())
            }
        },
        onExportJson = {
            scope.launch {
                val current = sensor ?: return@launch
                snackbarState.showSnackbar(historySharer.exportJson(current, intervals).toUserMessage())
            }
        },
        onSyncHistory = syncHistoryCallback,
        syncHistoryLabel = capability?.displayLabel,
    )
}

private suspend fun ExportResult.toUserMessage(): String = when (this) {
    is ExportResult.Saved -> getString(Res.string.snack_export_ok, rowCount, location)
    is ExportResult.Failed -> getString(Res.string.snack_export_failed, message)
}

private fun HistoryRange.toDuration() = when (this) {
    HistoryRange.Hour -> 1.hours
    HistoryRange.Day -> 1.days
    HistoryRange.Week -> 7.days
    HistoryRange.Month -> 30.days
    HistoryRange.Year -> 365.days
}
