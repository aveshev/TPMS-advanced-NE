package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit.SECONDS

/**
 * A tyre which lost [drop] since its highest reading of the ride, at [since], to its latest one, at
 * [until], [perHour] on average. The timestamps are in seconds.
 */
@Parcelize
public data class PressureLoss(
    val perHour: Pressure,
    val drop: Pressure,
    val since: Double,
    val until: Double,
) : Parcelable {

    /** Warns about a tyre which lost at least [minDrop] since its highest reading of the ride */
    public data class Rule(val minDrop: Pressure = DEFAULT_MIN_DROP) {
        public companion object {
            /** Two steps of the sensors' resolution, 3.45 kPa (0.5 psi) at worst */
            public val DEFAULT_MIN_DROP: Pressure = 7f.kpa
        }
    }

    /**
     * Follows a single tyre's readings, fed one by one to [next], looking for a leak while riding.
     * Riding warms the tyre up, which only ever raises its pressure: the pressure falling from its
     * highest reading of the ride, while the tyre isn't cooling down, is air getting out. There's
     * no temperature compensation, the sensors' temperature lagging too far behind the air's while
     * the tyre warms up.
     *
     * The sensors only broadcast on a pressure change, so there's no reading of a parked tyre to
     * compare rides with: a leak too slow to show within a ride is left to the low pressure alert,
     * which catches it once the tyre is cold. A ride starts with the first reading after a gap of
     * [RIDE_GAP].
     *
     * Once found, [loss] stays until the ride ends or the pressure gets back to its highest.
     */
    public data class Tracker(
        val loss: PressureLoss? = null,
        /** The latest of the ride's highest readings */
        private val peak: TyreAtmosphere? = null,
        private val latest: TyreAtmosphere? = null,
        /** How many readings in a row lost at least the minimum drop, the tyre not cooling down */
        private val confirmations: Int = 0,
    ) {

        @Suppress("CyclomaticComplexMethod", "MaxLineLength", "NestedBlockDepth")
        public fun next(reading: TyreAtmosphere, rule: Rule): Tracker = when {
            // The live readings start with the latest stored one
            latest != null && reading.timestamp <= latest.timestamp -> this
            // The sensor taken off the valve reads the atmosphere, whether to pump the tyre up or
            // not: some air is let out either way, the readings start over from the next one
            reading.pressure < OFF_VALVE -> Tracker()
            // Another sensor was bound to this tyre, or another ride starts
            latest != null && (reading.sensorId != latest.sensorId || reading.timestamp - latest.timestamp >= RIDE_GAP.toDouble(SECONDS)) ->
                Tracker().next(reading, rule)
            // The same pressure later on is the peak too: a leak starting then is measured from it
            peak == null || reading.pressure >= peak.pressure ->
                Tracker(peak = reading, latest = reading)

            else -> (peak.pressure.kpa - reading.pressure.kpa)
                .let { drop ->
                    PressureLoss(
                        (drop / ((reading.timestamp - peak.timestamp) / SECONDS_PER_HOUR).toFloat()).kpa,
                        drop.kpa,
                        peak.timestamp,
                        reading.timestamp,
                    )
                }
                .let { measured ->
                    (measured.drop >= rule.minDrop && peak.temperature.celsius - reading.temperature.celsius <= TEMPERATURE_FALL)
                        .let { if (it) confirmations + 1 else 0 }
                        .let { confirmations ->
                            copy(
                                loss = measured.takeIf { confirmations >= CONFIRMATIONS } ?: loss,
                                latest = reading,
                                confirmations = confirmations,
                            )
                        }
                }
        }

        public companion object {
            public val RIDE_GAP: Duration = 10.minutes
            private val OFF_VALVE = 10f.kpa

            /** A stop cools the tyre down, its pressure falling with it */
            private const val TEMPERATURE_FALL = 1f

            /** A single reading can be a step of the sensor's resolution flickering */
            private const val CONFIRMATIONS = 2
            private const val SECONDS_PER_HOUR = 3600.0
        }
    }
}
