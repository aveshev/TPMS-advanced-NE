package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The early leak warning, set for the whole app in the developer options and applied to the
 * vehicle's low pressure alerts: a tyre losing pressure fast enough to fall to two thirds of its
 * low pressure alert within the chosen time, see [PressureLoss.Rule].
 */
public class VehiclePressureLossUseCase internal constructor(
    private val appPreferences: AppPreferences,
    private val rangesUseCase: VehicleRangesUseCase,
) {

    /** The rule checking the tyre at [location], null while it's off */
    public fun rule(location: Location): Flow<PressureLoss.Rule?> = combine(
        appPreferences.pressureLoss,
        appPreferences.pressureLossHours,
        appPreferences.pressureLossMinDrop,
        rangesUseCase.resolvedLowPressure(location),
    ) { enabled, hours, minDrop, lowPressure ->
        PressureLoss.Rule(lowPressure, hours.hours, minDrop).takeIf { enabled }
    }

    public companion object {
        public val HORIZONS: List<Duration> = listOf(2.hours, 5.hours, 10.hours, 24.hours)
        public val MIN_DROPS: List<Float> = listOf(0.05f, 0.075f, 0.1f, 0.15f)
    }
}
