package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.feature.main.usecase.SensorBindingUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the sensor configuration offers for one location of the vehicle */
internal class TyreActionsViewModel(
    private val sensorBindingUseCase: SensorBindingUseCase,
) : ViewModel() {

    /** The sensor assigned to this location, null while there's none */
    val boundSensor: StateFlow<Sensor?> = sensorBindingUseCase.boundSensor()

    fun delete() {
        viewModelScope.launch { sensorBindingUseCase.unbind() }
    }
}
