package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS

/**
 * A tyre losing [perHour] of pressure, measured from the reading of [since] to the one of [until],
 * and expected to reach [flatMark] at [flatAt]. The timestamps are in seconds. [refilledAt] is the
 * latest time the tyre was pumped up, if any: a loss found after it is a new one. A loss which
 * isn't [isWarning] is only the latest measure, whatever the [Rule] says of it: it can be zero, or
 * negative for a tyre gaining pressure, which never reaches [flatMark] ([flatAt] is infinite).
 */
@Parcelize
public data class PressureLoss(
    val perHour: Pressure,
    val since: Double,
    val until: Double,
    val flatAt: Double,
    val flatMark: Pressure,
    val refilledAt: Double? = null,
    val isWarning: Boolean = true,
) : Parcelable {

    /** From the latest reading to [flatMark], zero once it's there */
    public val timeToFlat: Duration
        get() = (flatAt - until).coerceAtLeast(0.0).seconds

    /**
     * Warns about a tyre losing pressure fast enough to fall to [flatMark], two thirds of the
     * vehicle's [lowPressure] alert, within [horizon], once it lost at least [minDropShare] of
     * [lowPressure].
     */
    @Suppress("MagicNumber")
    public data class Rule(
        val lowPressure: Pressure,
        val horizon: Duration,
        val minDropShare: Float = DEFAULT_MIN_DROP_SHARE,
    ) {

        public val flatMark: Pressure
            get() = (lowPressure.kpa * 2f / 3f).kpa

        /**
         * A smaller drop can be the sensors' resolution (3 kPa at worst), the weather (±3 kPa) or
         * the temperature compensation's error, which grows with the pressure
         */
        public val minDrop: Pressure
            get() = maxOf(lowPressure.kpa * minDropShare, 7f).kpa

        /**
         * A rise this large, after the temperature compensation, is someone pumping the tyre up
         * rather than a sensor reading it warmer
         */
        public val refillGate: Pressure
            get() = (lowPressure.kpa / 10f).kpa

        public companion object {
            public const val DEFAULT_MIN_DROP_SHARE: Float = 0.075f
        }
    }

    /**
     * Follows a single tyre's readings, fed one by one to [next]. The pressures are compared at
     * 20 °C, so a tyre cooling down once parked doesn't look like it's leaking.
     *
     * The reference is the lowest reading since the tyre was last pumped up, readings joining it
     * once they're over an hour old, or the oldest reading of the last hour if lower. A drop is
     * spread over the time since that oldest reading, or since the last reading before a gap in the
     * readings, such as the vehicle being parked: a leak which started an hour ago isn't diluted by
     * the days before. A single reading is enough, some sensors only broadcast on a change.
     *
     * Once found, [loss] stays until the tyre is pumped up, a later reading replacing it with the
     * loss it finds, if any. [measured] is the latest reading's loss, or gain, whatever the rule
     * says of it: the drop gate, the time limit and the refill gate only apply to [loss].
     */
    public data class Tracker(
        val loss: PressureLoss? = null,
        val measured: PressureLoss? = null,
        /** The lowest of the readings since the latest refill, but those of the last hour */
        private val lowest: TyreAtmosphere? = null,
        /** The readings of the last hour, oldest first */
        private val recent: List<TyreAtmosphere> = emptyList(),
        private val latest: TyreAtmosphere? = null,
        private val refilledAt: Double? = null,
    ) {

        @Suppress("CyclomaticComplexMethod", "LongMethod", "MaxLineLength", "NestedBlockDepth")
        public fun next(reading: TyreAtmosphere, rule: Rule): Tracker = when {
            // The live readings start with the latest stored one
            latest != null && reading.timestamp <= latest.timestamp -> this
            // The sensor taken off the valve reads the atmosphere, whether to pump the tyre up or
            // not: some air is let out either way, the readings start over from the next one
            reading.pressure < OFF_VALVE -> Tracker(refilledAt = reading.timestamp)
            // Another sensor was bound to this tyre
            latest != null && reading.sensorId != latest.sensorId -> Tracker().next(reading, rule)
            else -> recent
                .partition { it.timestamp < reading.timestamp - WINDOW.toDouble(SECONDS) }
                .let { (aged, recent) ->
                    copy(recent = recent, lowest = (aged + listOfNotNull(lowest)).minByOrNull { it.normalisedPressure })
                }
                .let { tracker ->
                    (tracker.recent.firstOrNull() ?: tracker.latest)
                        ?.let { anchor -> anchor to listOfNotNull(tracker.lowest, anchor).minBy { it.normalisedPressure } }
                        ?.let { (anchor, reference) ->
                            reference.normalisedPressure.kpa
                                .minus(reading.normalisedPressure.kpa)
                                .takeIf { reading.timestamp > anchor.timestamp }
                                ?.let { drop -> drop to drop / ((reading.timestamp - anchor.timestamp) / SECONDS_PER_HOUR).toFloat() }
                                ?.let { (drop, perHour) ->
                                    drop to PressureLoss(
                                        perHour.kpa,
                                        anchor.timestamp,
                                        reading.timestamp,
                                        flatAt = perHour
                                            .takeIf { it > 0f }
                                            ?.let {
                                                reading.pressure.kpa
                                                    .minus(rule.flatMark.kpa)
                                                    .coerceAtLeast(0f)
                                                    .div(it)
                                                    .times(SECONDS_PER_HOUR)
                                                    .plus(reading.timestamp)
                                            }
                                            ?: Double.POSITIVE_INFINITY,
                                        flatMark = rule.flatMark,
                                        refilledAt = tracker.refilledAt,
                                        isWarning = false,
                                    )
                                }
                                .let { measured ->
                                    when {
                                        // The pump up is measured too, as a gain
                                        reading.normalisedPressure.kpa >= reference.normalisedPressure.kpa + rule.refillGate.kpa ->
                                            Tracker(
                                                measured = measured?.second?.copy(refilledAt = reading.timestamp),
                                                refilledAt = reading.timestamp,
                                            )

                                        else -> tracker.copy(
                                            measured = measured?.second,
                                            loss = measured
                                                ?.takeIf { (drop, loss) -> drop >= rule.minDrop.kpa && loss.timeToFlat <= rule.horizon }
                                                ?.second
                                                ?.copy(isWarning = true)
                                                ?: tracker.loss,
                                        )
                                    }
                                }
                        }
                        ?: tracker
                }
                .let { it.copy(recent = it.recent + reading, latest = reading) }
        }

        private companion object {
            val WINDOW = 1.hours
            val OFF_VALVE = 10f.kpa
            const val SECONDS_PER_HOUR = 3600.0
        }
    }
}

/**
 * The pressure this tyre would have at 20 °C. Tyres being a fixed volume, their absolute pressure
 * follows their absolute temperature: about 1 kPa per °C for a car tyre.
 */
@Suppress("MagicNumber")
private val TyreAtmosphere.normalisedPressure: Pressure
    get() = pressure.kpa
        .plus(ATMOSPHERE_KPA)
        .times(293.15f / (temperature.celsius + 273.15f))
        .minus(ATMOSPHERE_KPA)
        .kpa

private const val ATMOSPHERE_KPA = 101.325f
