package com.adsamcik.temperaturedashboard.core.model

import kotlin.jvm.JvmInline

/** Display unit for temperatures. Internal storage is always Celsius. */
enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
    KELVIN("K"),
}

/** A temperature measured in degrees Celsius. */
@JvmInline
value class Celsius(val value: Double) {
    fun toFahrenheit(): Double = value * 9.0 / 5.0 + 32.0
    fun toKelvin(): Double = value + ABSOLUTE_ZERO_OFFSET
    fun convertTo(unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.CELSIUS -> value
        TemperatureUnit.FAHRENHEIT -> toFahrenheit()
        TemperatureUnit.KELVIN -> toKelvin()
    }

    companion object {
        private const val ABSOLUTE_ZERO_OFFSET = 273.15
        fun fromFahrenheit(f: Double): Celsius = Celsius((f - 32.0) * 5.0 / 9.0)
        fun fromKelvin(k: Double): Celsius = Celsius(k - ABSOLUTE_ZERO_OFFSET)
    }
}

/** Relative humidity as a percentage in `[0, 100]`. */
@JvmInline
value class HumidityPercent(val value: Double) {
    init {
        require(value in MIN..MAX) { "Humidity must be in [0,100], got $value" }
    }

    companion object {
        const val MIN: Double = 0.0
        const val MAX: Double = 100.0
        fun clamp(value: Double): HumidityPercent =
            HumidityPercent(value.coerceIn(MIN, MAX))
    }
}

/** Battery level as a percentage in `[0, 100]`. */
@JvmInline
value class BatteryPercent(val value: Int) {
    init {
        require(value in MIN..MAX) { "Battery must be in [0,100], got $value" }
    }

    companion object {
        const val MIN: Int = 0
        const val MAX: Int = 100
        fun clamp(value: Int): BatteryPercent =
            BatteryPercent(value.coerceIn(MIN, MAX))
    }
}

/** Received Signal Strength Indicator in dBm. Typical range -100..-30. */
@JvmInline
value class Rssi(val dBm: Int)
