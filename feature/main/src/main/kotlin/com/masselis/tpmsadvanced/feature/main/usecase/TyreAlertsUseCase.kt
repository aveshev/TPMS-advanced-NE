package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAlerts
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.runningFold

/**
 * The tyre's alert levels as it shows them, see [TyreAlerts]. A change of the thresholds or of the
 * leak re-evaluates the latest reading.
 */
public class TyreAlertsUseCase internal constructor(
    private val atmosphereUseCase: TyreAtmosphereUseCase,
    private val rangeUseCase: VehicleRangesUseCase,
    private val pressureLoss: Flow<PressureLoss?>,
    private val location: Location,
) {
    /** Starts with the tyre's first reading */
    public fun listen(): Flow<TyreAlerts> = combine(
        atmosphereUseCase.listen(),
        rangeUseCase.alertThresholds(location),
        pressureLoss,
        ::Triple,
    )
        .runningFold(TyreAlerts()) { alerts, (reading, thresholds, loss) -> alerts.next(reading, thresholds, loss) }
        .drop(1)
}
