package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS

/**
 * A tyre which lost [amount] of pressure from [since] to [until], both being timestamps in seconds.
 * The pressures are compared at the same temperature, so a tyre cooling down once parked doesn't
 * look like it's leaking.
 */
@Parcelize
public data class PressureLoss(
    val amount: Pressure,
    val since: Double,
    val until: Double,
) : Parcelable {

    public val duration: Duration
        get() = (until - since).seconds

    /** Raises a [PressureLoss] once a tyre lost at least [amount] within [window] */
    public data class Rule(val amount: Pressure, val window: Duration) {

        /**
         * [history] are a single sensor's readings, oldest first. The latest readings must all be
         * [amount] below the highest one read in the [window] before them, and they must span at
         * least [PERSISTENCE]: a single low reading, or a burst of copies of it, isn't enough.
         */
        public fun detect(history: List<TyreAtmosphere>): PressureLoss? = history
            // A pressure of 0 is an alarm or a flat tyre, the low pressure alert already covers it
            .filter { it.pressure.hasPressure() }
            .takeIf { it.isNotEmpty() }
            ?.let { readings ->
                readings
                    .lastOrNull { it.timestamp <= readings.last().timestamp - PERSISTENCE.toDouble(SECONDS) }
                    ?.let { firstLow -> readings.partition { it.timestamp < firstLow.timestamp } }
            }
            ?.let { (earlier, latest) ->
                earlier
                    .filter { it.timestamp >= latest.last().timestamp - window.toDouble(SECONDS) }
                    .maxByOrNull { it.normalisedPressure }
                    ?.let { reference ->
                        PressureLoss(
                            reference.normalisedPressure.kpa
                                .minus(latest.maxOf { it.normalisedPressure }.kpa)
                                .kpa,
                            reference.timestamp,
                            latest.last().timestamp,
                        )
                    }
            }
            ?.takeIf { it.amount >= amount }

        public companion object {
            /** Sensors broadcast about once a minute while moving, some of them in bursts */
            public val PERSISTENCE: Duration = 15.seconds
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
