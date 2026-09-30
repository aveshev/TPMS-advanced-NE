package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.parcelize.Parcelize

public sealed interface Tyre : Parcelable {
    public val timestamp: Double
    public val rssi: Int
    public val sensorId: Int
    public val pressure: Pressure
    public val temperature: Temperature

    /** The battery byte as the sensor sent it, its unit depends on the sensor */
    public val battery: UShort

    /** An alarm raised by the sensor itself, only Sysgration sensors send one */
    public val isAlarm: Boolean

    /** Null for the sensors which don't report their battery as a voltage (Sysgration) */
    public val batteryVoltage: Voltage?

    /**
     * The status byte of the packet, as broadcast: shown for debugging, since what most of its
     * bits mean is unknown. null when the sensor has no such byte, or for a reading stored before
     * the database kept it.
     */
    public val flags: UByte?

    /**
     * The whole advertisement as received, in hexadecimal, so that what the decoders don't read
     * yet can be looked into later from an exported database. null for the readings which didn't
     * come from a scan (demo mode), or were stored before the database kept it.
     */
    public val raw: String?

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
        override val batteryVoltage: Voltage? = null,
        override val flags: UByte? = null,
        override val raw: String? = null,
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
        override val batteryVoltage: Voltage? = null,
        override val flags: UByte? = null,
        override val raw: String? = null,
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
            tyre.batteryVoltage,
            tyre.flags,
            tyre.raw,
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
        override val batteryVoltage: Voltage? = null,
        override val flags: UByte? = null,
        override val raw: String? = null,
    ) : Tyre, SensorInput
}

/**
 * The atmosphere shown for this record, its pressure corrected by [calibration] when the vehicle has
 * one
 */
public fun Tyre.toAtmosphere(calibration: PressureCalibration?): TyreAtmosphere = TyreAtmosphere(
    timestamp,
    sensorId,
    pressure,
    temperature,
    batteryVoltage,
    isAlarm,
    flags,
).let { atmosphere ->
    calibration
        ?.let { atmosphere.copy(pressure = it.applyTo(atmosphere.pressure)) }
        ?: atmosphere
}
