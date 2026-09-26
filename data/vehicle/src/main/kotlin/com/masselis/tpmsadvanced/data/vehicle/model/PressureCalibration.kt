package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa

/**
 * Corrects the pressure read from a sensor which is off by a few units: the read pressure is
 * multiplied by [multiplier] then shifted by [offset].
 */
public data class PressureCalibration(
    public val offset: Pressure,
    public val multiplier: Float,
) {

    /**
     * A pressure of 0, which is how an alarming or flat tyre is reported, is left as is so an
     * [offset] never hides it. A correction below 0 is clamped to 0 for the same reason.
     */
    public fun applyTo(pressure: Pressure): Pressure = pressure
        .takeIf { it.hasPressure() }
        ?.kpa
        ?.times(multiplier)
        ?.plus(offset.kpa)
        ?.coerceAtLeast(0f)
        ?.kpa
        ?: pressure
}
