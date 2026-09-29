package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * The vehicle's early leak warning: a tyre losing pressure fast enough to fall to two thirds of the
 * low pressure alert within [horizon], see [PressureLoss.Rule]. Turning it off keeps [horizon].
 */
@OptIn(FlowPreview::class)
public class VehiclePressureLossUseCase internal constructor(
    vehicle: Vehicle,
    scope: CoroutineScope,
    database: VehicleDatabase,
    private val rangesUseCase: VehicleRangesUseCase,
) {

    public val isEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(database.selectPressureLoss(vehicle.uuid))
    public val horizon: MutableStateFlow<Duration> =
        MutableStateFlow(database.selectPressureLossHorizon(vehicle.uuid))

    /** The rule checking the tyre at [location], null while it's off */
    public fun rule(location: Location): Flow<PressureLoss.Rule?> = combine(
        isEnabled,
        horizon,
        rangesUseCase.resolvedLowPressure(location),
    ) { enabled, horizon, lowPressure ->
        PressureLoss.Rule(lowPressure, horizon).takeIf { enabled }
    }

    init {
        isEnabled
            .debounce(100.milliseconds)
            .onEach { database.updatePressureLoss(it, vehicle.uuid) }
            .launchIn(scope)

        horizon
            .debounce(100.milliseconds)
            .onEach { database.updatePressureLossHorizon(it, vehicle.uuid) }
            .launchIn(scope)
    }

    public companion object {
        public val HORIZONS: List<Duration> = listOf(2.hours, 5.hours, 10.hours, 24.hours)
    }
}
