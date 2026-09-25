package com.masselis.tpmsadvanced.data.unit.model

public enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT;

    public fun string(): String = when (this) {
        CELSIUS -> "celsius"
        FAHRENHEIT -> "fahrenheit"
    }

    /** The unit as written after a value, e.g. "°C" in "20 °C" */
    public fun symbol(): String = when (this) {
        CELSIUS -> "°C"
        FAHRENHEIT -> "°F"
    }
}
