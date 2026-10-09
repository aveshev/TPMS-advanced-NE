package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.sensorsFor
import com.masselis.tpmsadvanced.feature.qrcode.usecase.BoundSensorMapUseCase
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** What a scanned QR code leads to on the vehicle [vehicleUuid] it was scanned for, see [QrCodeResult] */
@AssistedInject
internal class QrCodeResultViewModel(
    vehicleDatabase: VehicleDatabase,
    sensorDatabase: SensorDatabase,
    private val boundSensorMapUseCase: BoundSensorMapUseCase,
    @Assisted vehicleUuid: UUID,
    @Assisted result: QrCodeResult,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        operator fun invoke(vehicleUuid: UUID, result: QrCodeResult): QrCodeResultViewModel
    }

    sealed interface State {
        data object Loading : State

        /** Assign [sensors] to [vehicle]? [overwrites] when one of its wheels has another sensor */
        data class Ask(val vehicle: Vehicle, val sensors: List<Sensor>, val overwrites: Boolean) : State

        /** [vehicle] has fewer wheels than the code's [count] sensors */
        data class TooManySensors(val vehicle: Vehicle, val count: Int) : State

        /** The code can't be assigned as it is */
        @JvmInline
        value class Unusable(val result: QrCodeResult) : State

        /** Every sensor of the code was assigned to its wheel */
        data object Assigned : State
    }

    private val mutableStateFlow = MutableStateFlow<State>(State.Loading)
    val stateFlow: StateFlow<State> = mutableStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicle = withContext(IO) { vehicleDatabase.selectByUuid(vehicleUuid).execute() }
            mutableStateFlow.value = (result as? QrCodeResult.Sensors)
                ?.let { found ->
                    found
                        .sensors
                        .sensorsFor(vehicle.kind)
                        ?.let { sensors ->
                            val assigned = withContext(IO) {
                                sensorDatabase.selectListByVehicleId(vehicle.uuid).execute()
                            }
                            State.Ask(
                                vehicle,
                                sensors,
                                overwrites = sensors.any { sensor ->
                                    assigned.any { it.location == sensor.location && it.id != sensor.id }
                                },
                            )
                        }
                        ?: State.TooManySensors(vehicle, found.sensors.size)
                }
                ?: State.Unusable(result)
        }
    }

    fun bind() = viewModelScope.launch {
        val state = mutableStateFlow.value as? State.Ask ?: return@launch
        boundSensorMapUseCase.bind(state.vehicle.uuid, state.sensors)
        mutableStateFlow.value = State.Assigned
    }
}
