package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.usecase.DemoOrBleScannerUseCase
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The sensor configuration of a vehicle, see `ConfigurableCurrentVehicle` */
internal class SensorConfigurationViewModel(
    vehicle: Vehicle,
    sensorDatabase: SensorDatabase,
    demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
) : ViewModel() {

    /**
     * The configuration can be left once the vehicle has a sensor. The demo's sensors are never
     * bound, it would never be. Starts `true` so a vehicle with sensors doesn't flash into the
     * configuration while they're counted.
     */
    val canFinish: StateFlow<Boolean> = combine(
        sensorDatabase.countByVehicle(vehicle.uuid).asFlow(),
        demoOrBleScannerUseCase.isDemo,
    ) { count, isDemo -> count > 0 || isDemo }
        .stateIn(viewModelScope, Eagerly, true)
}
