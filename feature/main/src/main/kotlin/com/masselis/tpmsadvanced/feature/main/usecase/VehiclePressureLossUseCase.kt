package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
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
import kotlin.time.Duration.Companion.minutes

/**
 * The vehicle's early leak warning: a tyre losing [amount] within [window] warns before it reaches
 * the low pressure alert. Turning it off keeps [amount] and [window].
 */
@OptIn(FlowPreview::class)
public class VehiclePressureLossUseCase internal constructor(
    vehicle: Vehicle,
    scope: CoroutineScope,
    database: VehicleDatabase,
) {

    public val isEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(database.selectPressureLoss(vehicle.uuid))
    public val amount: MutableStateFlow<Pressure> =
        MutableStateFlow(database.selectPressureLossAmount(vehicle.uuid))
    public val window: MutableStateFlow<Duration> =
        MutableStateFlow(database.selectPressureLossWindow(vehicle.uuid))

    /** The rule checking the tyres, null while it's off */
    public val rule: Flow<PressureLoss.Rule?> =
        combine(isEnabled, amount, window) { enabled, amount, window ->
            PressureLoss.Rule(amount, window).takeIf { enabled }
        }

    init {
        isEnabled
            .debounce(100.milliseconds)
            .onEach { database.updatePressureLoss(it, vehicle.uuid) }
            .launchIn(scope)

        amount
            .debounce(100.milliseconds)
            .onEach { database.updatePressureLossAmount(it, vehicle.uuid) }
            .launchIn(scope)

        window
            .debounce(100.milliseconds)
            .onEach { database.updatePressureLossWindow(it, vehicle.uuid) }
            .launchIn(scope)
    }

    public companion object {
        public val WINDOWS: List<Duration> = listOf(10.minutes, 30.minutes, 1.hours, 2.hours)
    }
}
