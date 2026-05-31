package com.adsamcik.temperaturedashboard.core.model

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * User-defined trigger that fires a notification when a sensor's value
 * crosses a threshold.
 *
 * The [cooldown] guards against spam: once a kind fires, it can't fire again
 * for that long even if the value stays past the threshold.
 */
data class SensorAlert(
    val id: Long,
    val sensorId: SensorId,
    val kind: AlertKind,
    val enabled: Boolean,
    val cooldown: Duration,
    val lastFired: Instant?,
) {
    /** True if [now] is still within the cooldown after the last firing. */
    fun isInCooldown(now: Instant): Boolean {
        val last = lastFired ?: return false
        return (now - last) < cooldown
    }

    /** @return the alert message if [reading] tripped this alert, else null. */
    fun check(reading: Reading): String? = when (kind) {
        is AlertKind.TempAbove -> reading.temperatureC
            ?.takeIf { it > kind.celsius }
            ?.let { "Temperature ${formatOne(it)} °C above ${formatOne(kind.celsius)} °C" }
        is AlertKind.TempBelow -> reading.temperatureC
            ?.takeIf { it < kind.celsius }
            ?.let { "Temperature ${formatOne(it)} °C below ${formatOne(kind.celsius)} °C" }
        is AlertKind.HumidityAbove -> reading.humidityPct
            ?.takeIf { it > kind.percent }
            ?.let { "Humidity ${it.toInt()} % above ${kind.percent.toInt()} %" }
        is AlertKind.HumidityBelow -> reading.humidityPct
            ?.takeIf { it < kind.percent }
            ?.let { "Humidity ${it.toInt()} % below ${kind.percent.toInt()} %" }
        is AlertKind.BatteryBelow -> reading.batteryPct
            ?.takeIf { it < kind.percent }
            ?.let { "Battery $it % below ${kind.percent} %" }
    }

    companion object {
        val DEFAULT_COOLDOWN: Duration = 30.minutes
    }
}

/** What kind of threshold an alert watches. */
sealed interface AlertKind {
    data class TempAbove(val celsius: Double) : AlertKind
    data class TempBelow(val celsius: Double) : AlertKind
    data class HumidityAbove(val percent: Double) : AlertKind
    data class HumidityBelow(val percent: Double) : AlertKind
    data class BatteryBelow(val percent: Int) : AlertKind

    /** Stable name used as a DB column and in change-tracking. */
    val storageKey: String get() = when (this) {
        is TempAbove -> "TEMP_ABOVE"
        is TempBelow -> "TEMP_BELOW"
        is HumidityAbove -> "HUM_ABOVE"
        is HumidityBelow -> "HUM_BELOW"
        is BatteryBelow -> "BATT_BELOW"
    }

    /** Numeric threshold value stored alongside [storageKey]. */
    val threshold: Double get() = when (this) {
        is TempAbove -> celsius
        is TempBelow -> celsius
        is HumidityAbove -> percent
        is HumidityBelow -> percent
        is BatteryBelow -> percent.toDouble()
    }

    companion object {
        fun fromStorage(key: String, threshold: Double): AlertKind = when (key) {
            "TEMP_ABOVE" -> TempAbove(threshold)
            "TEMP_BELOW" -> TempBelow(threshold)
            "HUM_ABOVE" -> HumidityAbove(threshold)
            "HUM_BELOW" -> HumidityBelow(threshold)
            "BATT_BELOW" -> BatteryBelow(threshold.toInt())
            else -> error("Unknown alert storage key: $key")
        }
    }
}

private fun formatOne(v: Double): String {
    val r = (v * 10).toLong()
    val whole = r / 10
    val tenths = kotlin.math.abs(r - whole * 10)
    return if (v < 0 && whole == 0L) "-0.$tenths" else "$whole.$tenths"
}
