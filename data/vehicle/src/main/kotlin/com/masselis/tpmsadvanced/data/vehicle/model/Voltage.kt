package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcel
import android.os.Parcelable

/**
 * A sensor's battery voltage. Most sensors report it in steps of 0.1 V, so comparisons go through
 * [isAtOrBelow] which tolerates the float rounding of those steps (2.6f + 0.1f isn't exactly 2.7f).
 */
/* Cannot use @Parcelize here: https://issuetracker.google.com/issues/177856519 */
@JvmInline
public value class Voltage(public val volts: Float) : Parcelable, Comparable<Voltage> {

    public fun string(): String = "%.1f V".format(volts)

    public fun numberString(): String = "%.1f".format(volts)

    public fun isAtOrBelow(other: Voltage): Boolean = volts <= other.volts + EPSILON_VOLTS

    public operator fun plus(other: Voltage): Voltage = Voltage(volts + other.volts)

    public operator fun minus(other: Voltage): Voltage = Voltage(volts - other.volts)

    /**
     * Where this low voltage alarm, set for a battery at [ALARM_REFERENCE_TEMPERATURE], stands for a
     * sensor reading at [temperature].
     *
     * A coin cell's voltage sags in the cold, its internal resistance rising, without its charge
     * changing: the alarm comes down [COLD_SAG_PER_DEGREE] for each degree below the reference, so
     * a battery doesn't look depleted on a cold morning. That sag is also what browns the sensor
     * out, at a voltage which doesn't change with the temperature: the alarm never comes down more
     * than [BROWNOUT_MARGIN]. In the deep cold the battery's sag reaches that floor at a higher
     * charge than the alarm stands for, and the alarm goes off earlier than at the reference.
     *
     * Above the reference the voltage barely rises, the alarm stays where it is.
     */
    public fun alarmAt(temperature: Temperature): Voltage = Voltage(
        volts - (ALARM_REFERENCE_TEMPERATURE.celsius - temperature.celsius)
            .coerceAtLeast(0f)
            .times(COLD_SAG_PER_DEGREE.volts)
            .coerceAtMost(BROWNOUT_MARGIN.volts)
    )

    override operator fun compareTo(other: Voltage): Int = volts.compareTo(other.volts)

    private constructor(parcel: Parcel) : this(parcel.readFloat())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(volts)
    }

    override fun describeContents(): Int = 0

    public companion object CREATOR : Parcelable.Creator<Voltage> {

        /** Far below the 0.01 V step of the finest sensors */
        private const val EPSILON_VOLTS = 0.001f

        public val Float.volts: Voltage get() = Voltage(this)

        /** The temperature the low voltage alarm is set for, see [alarmAt] */
        public val ALARM_REFERENCE_TEMPERATURE: Temperature = Temperature(20f)

        /** How much a coin cell sags per degree below the reference, under a sensor's load */
        public val COLD_SAG_PER_DEGREE: Voltage = Voltage(0.005f)

        /**
         * How far above the brownout the alarm is set: the cold can't take the alarm lower than
         * this, the sensor would go silent before alarming
         */
        public val BROWNOUT_MARGIN: Voltage = Voltage(0.1f)

        override fun createFromParcel(parcel: Parcel): Voltage {
            return Voltage(parcel)
        }

        override fun newArray(size: Int): Array<Voltage?> {
            return arrayOfNulls(size)
        }
    }
}
