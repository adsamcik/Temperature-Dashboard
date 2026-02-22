package com.adsamcik.temperaturedashboard.data

enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT;

    fun convert(tempCelsius: Double): Double = when (this) {
        CELSIUS -> tempCelsius
        FAHRENHEIT -> tempCelsius * 9.0 / 5.0 + 32.0
    }

    fun symbol(): String = when (this) {
        CELSIUS -> "°C"
        FAHRENHEIT -> "°F"
    }
}
