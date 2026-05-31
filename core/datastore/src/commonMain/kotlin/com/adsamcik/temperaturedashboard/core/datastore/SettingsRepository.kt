package com.adsamcik.temperaturedashboard.core.datastore

import com.adsamcik.temperaturedashboard.core.model.CoalescingPolicy
import com.adsamcik.temperaturedashboard.core.model.TemperatureUnit
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getDoubleFlow
import com.russhwolf.settings.coroutines.getLongFlow
import com.russhwolf.settings.coroutines.getStringFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reactive wrapper over [ObservableSettings] for cross-platform app preferences.
 *
 * Backing storage is platform-specific:
 *  - Android: SharedPreferences (default)
 *  - Desktop: java.util.prefs.Preferences (user-level)
 * Multiplatform Settings abstracts the difference.
 */
@OptIn(ExperimentalSettingsApi::class)
class SettingsRepository(private val settings: ObservableSettings) {

    fun observeTemperatureUnit(): Flow<TemperatureUnit> =
        settings.getStringFlow(KEY_TEMPERATURE_UNIT, TemperatureUnit.CELSIUS.name)
            .map { runCatching { TemperatureUnit.valueOf(it) }.getOrDefault(TemperatureUnit.CELSIUS) }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        settings.putString(KEY_TEMPERATURE_UNIT, unit.name)
    }

    fun observeCoalescingPolicy(): Flow<CoalescingPolicy> = combine(
        settings.getDoubleFlow(KEY_TEMP_THRESHOLD, CoalescingPolicy.DEFAULT_TEMPERATURE_THRESHOLD_C),
        settings.getDoubleFlow(KEY_HUM_THRESHOLD, CoalescingPolicy.DEFAULT_HUMIDITY_THRESHOLD_PCT),
        settings.getLongFlow(KEY_STALE_WINDOW_MS, CoalescingPolicy.DEFAULT_STALE_WINDOW.inWholeMilliseconds),
        settings.getLongFlow(KEY_MAX_INTERVAL_MS, CoalescingPolicy.DEFAULT_MAX_INTERVAL_DURATION.inWholeMilliseconds),
    ) { tempT, humT, staleMs, maxMs ->
        CoalescingPolicy(
            temperatureThresholdC = tempT,
            humidityThresholdPct = humT,
            staleWindow = staleMs.milliseconds,
            maxIntervalDuration = maxMs.milliseconds,
        )
    }

    fun setCoalescingPolicy(policy: CoalescingPolicy) {
        settings.putDouble(KEY_TEMP_THRESHOLD, policy.temperatureThresholdC)
        settings.putDouble(KEY_HUM_THRESHOLD, policy.humidityThresholdPct)
        settings.putLong(KEY_STALE_WINDOW_MS, policy.staleWindow.inWholeMilliseconds)
        settings.putLong(KEY_MAX_INTERVAL_MS, policy.maxIntervalDuration.inWholeMilliseconds)
    }

    private companion object {
        const val KEY_TEMPERATURE_UNIT = "ui.temperature_unit"
        const val KEY_TEMP_THRESHOLD = "coalesce.temp_threshold_c"
        const val KEY_HUM_THRESHOLD = "coalesce.hum_threshold_pct"
        const val KEY_STALE_WINDOW_MS = "coalesce.stale_window_ms"
        const val KEY_MAX_INTERVAL_MS = "coalesce.max_interval_ms"
    }
}
