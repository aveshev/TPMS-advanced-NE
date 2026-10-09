package com.masselis.tpmsadvanced.feature.unlocated.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Where assigning a sensor to a wheel by Bluetooth stands. The user puts the sensor on the wheel,
 * takes it off and puts it back, each forcing it to send an update: the sensors whose pressure
 * follows are kept from one step to the next, until a single one is back on the wheel. Only the
 * last steps confirm which sensor it is, so a loose pick in the first ones can't assign the wrong
 * sensor: the flow just keeps listening.
 *
 * Readings are told apart by [Readings]: the first one heard from a sensor by its absolute
 * pressure, the next ones by the change since.
 */
internal sealed interface AssignStep : Parcelable {

    /** Is the sensor on the wheel right now? */
    @Parcelize
    data object Ask : AssignStep

    /**
     * Waiting for the sensor to be put on the wheel. [found] were heard on a wheel, [low] were first
     * heard below [ON_WHEEL_KPA]: on a tyre at a very low pressure maybe, see [lowPressure].
     */
    @Parcelize
    data class PutOn(val found: Set<Int> = emptySet(), val low: Set<Int> = emptySet()) : AssignStep {
        /** Every sensor found is taken off the wheel next, to tell which one is the user's */
        fun next(): TakeOff = TakeOff(found)

        /** The sensors heard below [ON_WHEEL_KPA] are taken off the wheel instead */
        fun lowPressure(): TakeOff = TakeOff(low)
    }

    /** Waiting for the sensor to be taken off the wheel, one of [candidates] or any when null */
    @Parcelize
    data class TakeOff(val candidates: Set<Int>?, val off: Set<Int> = emptySet()) : AssignStep {
        fun next(): PutBack = PutBack(off)
    }

    /** Waiting for one of [candidates] to be put back on the wheel */
    @Parcelize
    data class PutBack(val candidates: Set<Int>) : AssignStep

    /** [sensorId] came back on the wheel: it's the one */
    @Parcelize
    data class Found(val sensorId: Int) : AssignStep

    /** This step after a reading of [sensorId] at [kpa], given the readings before it */
    fun after(sensorId: Int, kpa: Float, readings: Readings): AssignStep {
        val previous = readings[sensorId]
        return when (this) {
            Ask, is Found -> this

            is PutOn -> when {
                isOn(previous, kpa) -> copy(found = found + sensorId)
                previous == null -> copy(low = low + sensorId)
                else -> this
            }

            is TakeOff -> this
                .takeIf { candidates == null || sensorId in candidates }
                ?.takeIf { isOff(previous, kpa) }
                ?.copy(off = off + sensorId)
                ?: this

            is PutBack -> Found(sensorId)
                .takeIf { sensorId in candidates && isOn(previous, kpa) }
                ?: this
        }
    }

    /** The last pressure heard from each sensor, in kPa */
    @Parcelize
    @JvmInline
    value class Readings(private val kpa: Map<Int, Float> = emptyMap()) : Parcelable {
        operator fun get(sensorId: Int): Float? = kpa[sensorId]
        operator fun plus(reading: Pair<Int, Float>): Readings = Readings(kpa + reading)
    }

    companion object {
        /**
         * Sensors subtract the atmospheric pressure: one off a wheel reads close to 0, one on a
         * wheel above this, unless the tyre is nearly flat
         */
        const val ON_WHEEL_KPA = 10f

        /** A change of at least this much, put on or taken off a wheel. Altitude shifts both alike */
        const val CHANGE_KPA = 15f

        /** First heard above [ON_WHEEL_KPA], or rose by [CHANGE_KPA] since last heard */
        private fun isOn(previous: Float?, kpa: Float) =
            previous?.let { kpa - it >= CHANGE_KPA } ?: (kpa > ON_WHEEL_KPA)

        /**
         * Dropped by [CHANGE_KPA] since last heard, or first heard below [ON_WHEEL_KPA]: a sensor
         * taken off before it was ever heard on its wheel. Putting it back confirms it.
         */
        private fun isOff(previous: Float?, kpa: Float) =
            previous?.let { it - kpa >= CHANGE_KPA } ?: (kpa < ON_WHEEL_KPA)
    }
}
