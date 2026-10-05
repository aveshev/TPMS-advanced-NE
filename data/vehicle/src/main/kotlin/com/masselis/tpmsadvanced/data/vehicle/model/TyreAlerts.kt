package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_REMOVED
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss.Tracker.Companion.OFF_VALVE
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss.Tracker.Companion.RIDE_GAP
import kotlin.time.DurationUnit.SECONDS

/**
 * Follows a single tyre's readings, fed one by one to [next], for its alerts, see docs/alerts.md.
 *
 * [levels] is what the latest reading shows. [notifiable] is what it may notify about, once each
 * level is confirmed: an amber level takes [AMBER_CONFIRMATIONS] readings in a row at amber or
 * above, a red battery [BATTERY_RED_CONFIRMATIONS] readings in a row at red, the other levels
 * notify from the first reading.
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
    /** How many readings in a row each class was at amber or above */
    private val amberStreaks: Map<AlertClass, Int> = emptyMap(),
    /** How many readings in a row each class was at red or above */
    private val redStreaks: Map<AlertClass, Int> = emptyMap(),
) {

    /**
     * [reading] being [latest] again re-evaluates its [levels], for the [thresholds] or the [loss]
     * which changed since, without counting it as another reading.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "MaxLineLength")
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
            .let { isRemoved ->
                thresholds
                    .levels(reading, loss != null, isRemoved)
                    .let { levels ->
                        AlertClass
                            .entries
                            .associateWith { levels[it] }
                            .let { byClass ->
                                copy(
                                    latest = reading,
                                    isRemoved = isRemoved,
                                    levels = levels,
                                    amberStreaks = byClass.mapValues { (alertClass, level) ->
                                        if (level != null) amberStreaks.getOrElse(alertClass) { 0 } + 1 else 0
                                    },
                                    redStreaks = byClass.mapValues { (alertClass, level) ->
                                        if (level != null && level >= RED) redStreaks.getOrElse(alertClass) { 0 } + 1 else 0
                                    },
                                )
                            }
                    }
            }
            .let { alerts -> alerts.copy(notifiable = alerts.confirmed()) }
    }

    @Suppress("MaxLineLength")
    private fun confirmed(): Map<AlertClass, AlertLevel> = levels
        .mapNotNull { (alertClass, level) ->
            when {
                // Removed, the sensor may never send anything again
                alertClass == SENSOR_REMOVED -> level
                alertClass == BATTERY && level >= RED && redStreaks.getValue(alertClass) >= BATTERY_RED_CONFIRMATIONS -> level
                alertClass != BATTERY && level >= RED -> level
                amberStreaks.getValue(alertClass) >= AMBER_CONFIRMATIONS -> AlertLevel.AMBER
                else -> null
            }?.let { alertClass to it }
        }
        .toMap()

    public companion object {
        /** A reading hovering on a boundary doesn't make an amber alert */
        public const val AMBER_CONFIRMATIONS: Int = 3

        /** The voltage dips in the cold and while the sensor sends, recovering afterwards */
        public const val BATTERY_RED_CONFIRMATIONS: Int = 5
    }
}
