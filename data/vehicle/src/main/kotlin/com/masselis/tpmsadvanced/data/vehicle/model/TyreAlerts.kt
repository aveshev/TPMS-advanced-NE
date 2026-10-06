package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss.Tracker.Companion.OFF_VALVE
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss.Tracker.Companion.RIDE_GAP
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit.SECONDS

/**
 * Follows a single tyre's readings, fed one by one to [next], for its alerts, see docs/alerts.md.
 *
 * [levels] is what the latest reading shows. [notifiable] is what it notifies about: the same,
 * except for a red battery, which takes two readings at red [BATTERY_CONFIRMATION] apart and is
 * amber meanwhile.
 */
public data class TyreAlerts(
    val latest: TyreAtmosphere? = null,
    /**
     * The sensor was taken off the valve, to pump the tyre up most likely: its pressure jumped
     * straight from a recent reading to the open air's. A real deflation goes through the readings
     * in between, the sensor sending more often while its pressure keeps changing, the last one
     * before the open air being crimson. It lasts until the sensor reads a tyre again.
     */
    val isRemoved: Boolean = false,
    val levels: Map<AlertClass, AlertLevel> = emptyMap(),
    val notifiable: Map<AlertClass, AlertLevel> = emptyMap(),
    /** When the battery's readings in a row at red started, null while it isn't red */
    private val batteryRedSince: Double? = null,
) {

    /**
     * [reading] being [latest] again re-evaluates its [levels], for the [thresholds] or the [loss]
     * which changed since, without counting it as another reading.
     */
    @Suppress("MaxLineLength", "CyclomaticComplexMethod", "ComplexCondition")
    public fun next(
        reading: TyreAtmosphere,
        thresholds: AlertThresholds,
        loss: PressureLoss?,
    ): TyreAlerts = when {
        // Another sensor was bound to this tyre, whatever was going on was about the previous one
        latest != null && reading.sensorId != latest.sensorId -> TyreAlerts().next(reading, thresholds, loss)
        latest != null && reading.timestamp < latest.timestamp -> this
        latest != null && reading.timestamp == latest.timestamp ->
            copy(levels = thresholds.levels(latest, loss != null, isRemoved))

        else -> (
            reading.pressure < OFF_VALVE && (
                isRemoved || latest
                    ?.takeIf { reading.timestamp - it.timestamp < RIDE_GAP.toDouble(SECONDS) }
                    ?.let { thresholds.pressureLevel(it.pressure) != CRIMSON }
                    ?: false
                )
            )
            .let { isRemoved -> thresholds.levels(reading, loss != null, isRemoved) to isRemoved }
            .let { (levels, isRemoved) ->
                (batteryRedSince ?: reading.timestamp)
                    .takeIf { levels[BATTERY] == RED }
                    .let { batteryRedSince ->
                        copy(
                            latest = reading,
                            isRemoved = isRemoved,
                            levels = levels,
                            notifiable = levels.mapValues { (alertClass, level) ->
                                // A single low voltage can be a dip, see BATTERY_CONFIRMATION
                                if (alertClass == BATTERY && level == RED && batteryRedSince != null &&
                                    reading.timestamp - batteryRedSince < BATTERY_CONFIRMATION.toDouble(SECONDS)
                                ) AMBER
                                else level
                            },
                            batteryRedSince = batteryRedSince,
                        )
                    }
            }
    }

    public companion object {
        /**
         * The voltage dips in the cold and while the sensor sends, recovering afterwards: a battery
         * is red once it stays so this long. A sensor reporting once an hour isn't held back
         * for hours, as a count of readings would.
         */
        public val BATTERY_CONFIRMATION: Duration = 10.minutes
    }
}
