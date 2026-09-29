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

        override fun createFromParcel(parcel: Parcel): Voltage {
            return Voltage(parcel)
        }

        override fun newArray(size: Int): Array<Voltage?> {
            return arrayOfNulls(size)
        }
    }
}
