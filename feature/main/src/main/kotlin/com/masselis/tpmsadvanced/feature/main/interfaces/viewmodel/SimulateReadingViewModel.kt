package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.unit.interfaces.UnitPreferences
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.SimulatedReadings
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Readings typed in the app, debug builds only, see [SimulatedReadings] */
internal class SimulateReadingViewModel(
    appPreferences: AppPreferences,
    unitPreferences: UnitPreferences,
    private val sensorDatabase: SensorDatabase,
) : ViewModel() {

    /** Shown along with the other debug options */
    val isAvailable: StateFlow<Boolean> = appPreferences
        .debugOptions
        .map { it && SimulatedReadings.IS_AVAILABLE }
        .stateIn(viewModelScope, WhileSubscribed(), false)

    val pressureUnit = unitPreferences.pressure
    val temperatureUnit = unitPreferences.temperature

    /**
     * From the sensor bound to that tyre, as if it was received, or from a sensor advertising its
     * location (as Sysgration's do) for a tyre without one
     */
    @Suppress("MaxLineLength")
    fun send(
        vehicle: Vehicle,
        location: Location,
        pressure: Pressure,
        temperature: Temperature,
        voltage: Voltage?,
        isAlarm: Boolean,
    ) = viewModelScope.launch {
        withContext(IO) { sensorDatabase.selectByVehicleAndLocation(vehicle.uuid, location).execute() }
            .let { bound ->
                if (bound != null) Tyre.Unlocated(now(), RSSI, bound.id, pressure, temperature, BATTERY, isAlarm, voltage)
                else Tyre.SensorLocated(
                    now(),
                    RSSI,
                    UNBOUND_SENSOR_ID,
                    pressure,
                    temperature,
                    BATTERY,
                    isAlarm,
                    SensorLocation.entries.first { it matches location },
                    voltage,
                )
            }
            .let(SimulatedReadings::send)
    }

    private infix fun SensorLocation.matches(location: Location) = when (location) {
        is Location.Axle -> axle == location.axle
        is Location.Side -> side == location.side
        is Location.Wheel -> this == location.location
    }

    private companion object {
        const val RSSI = -60
        const val BATTERY: UShort = 100u

        /** Bound to no tyre, or it would only be shown at its binding's */
        const val UNBOUND_SENSOR_ID = 0x7E5700
    }
}
