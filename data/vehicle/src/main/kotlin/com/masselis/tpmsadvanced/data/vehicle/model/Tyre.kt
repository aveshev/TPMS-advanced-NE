package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.parcelize.Parcelize

public sealed interface Tyre : Parcelable {
    public val timestamp: Double
    public val rssi: Int
    public val sensorId: Int
    public val pressure: Pressure
    public val temperature: Temperature
    public val battery: UShort
    public val isAlarm: Boolean

    /**
     * The status byte of the packet, as broadcast: shown for debugging, since what most of its
     * bits mean is unknown. null when the sensor has no such byte, or when this tyre was read from
     * the database, which doesn't store it.
     */
    public val flags: UByte?

    public sealed interface SensorInput : Tyre

    @Parcelize
    public data class Unlocated(
        override val timestamp: Double,
        override val rssi: Int,
        override val sensorId: Int,
        override val pressure: Pressure,
        override val temperature: Temperature,
        override val battery: UShort,
        override val isAlarm: Boolean,
        override val flags: UByte? = null,
    ) : Tyre, SensorInput

    @Parcelize
    public data class Located(
        override val timestamp: Double,
        override val rssi: Int,
        override val sensorId: Int,
        override val pressure: Pressure,
        override val temperature: Temperature,
        override val battery: UShort,
        override val isAlarm: Boolean,
        val location: Location,
        override val flags: UByte? = null,
    ) : Tyre {
        public constructor(tyre: Tyre, location: Location) : this(
            tyre.timestamp,
            tyre.rssi,
            tyre.sensorId,
            tyre.pressure,
            tyre.temperature,
            tyre.battery,
            tyre.isAlarm,
            location,
            tyre.flags,
        )
    }

    @Parcelize
    public data class SensorLocated(
        override val timestamp: Double,
        override val rssi: Int,
        override val sensorId: Int,
        override val pressure: Pressure,
        override val temperature: Temperature,
        override val battery: UShort,
        override val isAlarm: Boolean,
        val location: SensorLocation,
        override val flags: UByte? = null,
    ) : Tyre, SensorInput
}

/**
 * The atmosphere shown for this record: an alarm reads as no pressure at all, and [calibration]
 * corrects the pressure when the vehicle has one.
 */
public fun Tyre.toAtmosphere(calibration: PressureCalibration?): TyreAtmosphere = TyreAtmosphere(
    timestamp,
    sensorId,
    if (isAlarm) 0f.kpa else pressure,
    temperature,
    flags,
).let { atmosphere ->
    calibration
        ?.let { atmosphere.copy(pressure = it.applyTo(atmosphere.pressure)) }
        ?: atmosphere
}
