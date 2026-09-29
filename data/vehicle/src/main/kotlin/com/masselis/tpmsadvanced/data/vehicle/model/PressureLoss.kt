package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.HOURS
import kotlin.time.DurationUnit.SECONDS

/**
 * A tyre losing [perHour] of pressure, measured from the reading of [since] to the one of [until],
 * and expected to reach [flatMark] at [flatAt]. The timestamps are in seconds. [refilledAt] is the
 * latest time the tyre was pumped up, if any: a loss found after it is a new one.
 */
@Parcelize
public data class PressureLoss(
    val perHour: Pressure,
    val since: Double,
    val until: Double,
    val flatAt: Double,
    val flatMark: Pressure,
    val refilledAt: Double? = null,
) : Parcelable {

    /** From the latest reading to [flatMark], zero once it's there */
    public val timeToFlat: Duration
        get() = (flatAt - until).coerceAtLeast(0.0).seconds

    /**
     * Warns about a tyre losing pressure fast enough to fall to [flatMark], two thirds of the
     * vehicle's [lowPressure] alert, within [horizon].
     */
    @Suppress("MaxLineLength")
    public data class Rule(val lowPressure: Pressure, val horizon: Duration) {

        @Suppress("MagicNumber")
        public val flatMark: Pressure
            get() = (lowPressure.kpa * 2f / 3f).kpa

        /**
         * A rise this large, after the temperature compensation, is someone pumping the tyre up
         * rather than a sensor reading it warmer
         */
        @Suppress("MagicNumber")
        public val refillGate: Pressure
            get() = (lowPressure.kpa / 10f).kpa

        /**
         * [history] are a single sensor's readings, oldest first. The latest one is compared with
         * each cold reading since the tyre was last pumped up: the temperature compensation is the
         * most accurate between cold tyres. The fastest loss wins, so a leak which started an hour
         * ago isn't diluted by the days before. A single reading is enough, as some sensors only
         * broadcast when the pressure changes.
         */
        public fun detect(history: List<TyreAtmosphere>): PressureLoss? = history
            // A pressure of 0 is an alarm or a flat tyre, the low pressure alert already covers it
            .filter { it.pressure.hasPressure() }
            .let { readings ->
                readings
                    .withIndex()
                    .fold(Segment(0, null)) { segment, (index, reading) ->
                        segment
                            .lowest
                            ?.takeIf { reading.normalisedPressure.kpa >= it.kpa + refillGate.kpa }
                            ?.let { Segment(index, reading.normalisedPressure) }
                            ?: Segment(segment.start, listOfNotNull(segment.lowest, reading.normalisedPressure).min())
                    }
                    .start
                    .let { start -> readings.subList(start, readings.size) to readings.getOrNull(start)?.takeIf { start > 0 } }
            }
            .let { (segment, refill) -> segment.lastOrNull()?.let { Triple(segment, refill?.timestamp, it) } }
            ?.let { (segment, refilledAt, latest) ->
                segment
                    .dropLast(1)
                    .filter { it.timestamp >= latest.timestamp - LOOKBACK.toDouble(SECONDS) }
                    .let { earlier ->
                        earlier
                            .minOfOrNull { it.temperature.celsius }
                            ?.let { coldest ->
                                earlier.filter { it.temperature.celsius <= coldest + COLD_MARGIN_CELSIUS }
                            }
                            .orEmpty()
                    }
                    .filter { it.normalisedPressure.kpa - latest.normalisedPressure.kpa >= MIN_DROP.kpa }
                    .filter { it.timestamp < latest.timestamp }
                    .map { reference ->
                        PressureLoss(
                            reference.normalisedPressure.kpa
                                .minus(latest.normalisedPressure.kpa)
                                .div((latest.timestamp - reference.timestamp).seconds.toDouble(HOURS).toFloat())
                                .kpa,
                            reference.timestamp,
                            latest.timestamp,
                            flatAt = 0.0,
                            flatMark = flatMark,
                            refilledAt = refilledAt,
                        )
                    }
                    .maxByOrNull { it.perHour }
                    ?.let { loss ->
                        loss.copy(
                            flatAt = latest.pressure.kpa
                                .minus(flatMark.kpa)
                                .coerceAtLeast(0f)
                                .div(loss.perHour.kpa)
                                .toDouble()
                                .hours
                                .toDouble(SECONDS)
                                .plus(latest.timestamp)
                        )
                    }
                    ?.takeIf { it.timeToFlat <= horizon }
            }

        /** The index of the readings since the tyre was last pumped up, and their lowest pressure */
        private data class Segment(val start: Int, val lowest: Pressure?)

        public companion object {
            /** Above the sensors' resolution (3 kPa at worst) and the weather's air pressure changes */
            public val MIN_DROP: Pressure = 10f.kpa

            /** A leak that matters flattens the tyre within a day, the longest horizon */
            public val LOOKBACK: Duration = 48.hours

            /** How much warmer than the coldest reading a reading can be to still count as cold */
            private const val COLD_MARGIN_CELSIUS = 3f
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
