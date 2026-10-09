package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.usecase.DemoOrBleScannerUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.LocatedTyreScannerUseCase
import com.masselis.tpmsadvanced.feature.main.usecase.SensorBindingUseCase
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How one location of the vehicle can be tapped to assign, or manage, its sensor */
internal class TyreActionsViewModel(
    private val sensorBindingUseCase: SensorBindingUseCase,
    scannerUseCase: LocatedTyreScannerUseCase,
    demoOrBleScannerUseCase: DemoOrBleScannerUseCase,
) : ViewModel() {

    sealed interface State {
        /** The demo's readings are shown without any sensor to assign */
        data object Demo : State

        data class Assigned(val sensor: Sensor) : State

        /** No sensor here yet, [detected] can be assigned without scanning anything */
        data class Unassigned(val detected: Sensor?) : State
    }

    val stateFlow: StateFlow<State> = combine(
        sensorBindingUseCase.boundSensor(),
        scannerUseCase.detectedSensor(),
        demoOrBleScannerUseCase.isDemo,
        ::state,
    ).stateIn(
        viewModelScope,
        WhileSubscribed(),
        // From the current values, so a bound tyre doesn't flash "Tap to assign"
        state(sensorBindingUseCase.boundSensor().value, null, demoOrBleScannerUseCase.isDemo.value),
    )

    private fun state(bound: Sensor?, detected: Sensor?, isDemo: Boolean): State = when {
        isDemo -> State.Demo
        bound != null -> State.Assigned(bound)
        else -> State.Unassigned(detected)
    }

    fun assign(sensor: Sensor) {
        viewModelScope.launch { sensorBindingUseCase.bind(sensor) }
    }

    fun delete() {
        viewModelScope.launch { sensorBindingUseCase.unbind() }
    }
}
