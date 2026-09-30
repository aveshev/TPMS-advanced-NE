package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The leak warning, set for the whole app in the developer options: a tyre losing pressure while
 * riding, see [PressureLoss.Tracker].
 */
public class VehiclePressureLossUseCase internal constructor(appPreferences: AppPreferences) {

    /** The rule checking the tyres, null while it's off */
    public val rule: Flow<PressureLoss.Rule?> = combine(
        appPreferences.pressureLoss,
        appPreferences.pressureLossMinDrop,
    ) { enabled, minDrop ->
        PressureLoss.Rule(minDrop.kpa).takeIf { enabled }
    }

    public companion object {
        /** From one and a half to four steps of the sensors' resolution, 3.45 kPa at worst */
        public val MIN_DROPS: List<Pressure> = listOf(5f.kpa, 7f.kpa, 10f.kpa, 14f.kpa)
    }
}
