package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.model.MoveChain
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The vehicle's sensors on their page, moving them, see [MoveChain] */
internal class ManageSensorsViewModel(
    private val vehicle: Vehicle,
    private val sensorDatabase: SensorDatabase,
) : ViewModel() {

    /** The vehicle's locations with a sensor */
    val occupied: StateFlow<Set<Location>> = sensorDatabase
        .selectListByVehicleId(vehicle.uuid)
        .asFlow()
        .map { sensors -> sensors.map { it.location }.toSet() }
        .stateIn(viewModelScope, WhileSubscribed(), emptySet())

    fun apply(chain: MoveChain) {
        viewModelScope.launch { sensorDatabase.move(vehicle.uuid, chain.moves(occupied.value)) }
    }
}
