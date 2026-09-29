package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class LocatedTyreScannerUseCase(
    private val source: BluetoothLeScanner,
    private val currentLocation: Location,
    private val sensorBindingUseCase: SensorBindingUseCase,
) {
    fun highDutyScan(): Flow<Tyre.Located> = source.highDutyScan().mapWithLocation()

    fun normalScan(): Flow<Tyre.Located> = source.normalScan().mapWithLocation()

    // Filtered here, before ListenTyreWithDatabaseUseCase stores the records: a record of another
    // sensor stored for this location would be replayed at the next start, then dropped by
    // ListenBoundTyreUseCase, leaving the location empty.
    private fun Flow<Tyre.SensorInput>.mapWithLocation() = this
        .mapNotNull { tyre ->
            when (val sensor = sensorBindingUseCase.boundSensor().value) {
                null -> (tyre as? Tyre.SensorLocated)
                    // A sensor advertising its location (Sysgration) is shown there, unless it's
                    // bound: its binding says where it belongs, maybe to another vehicle
                    ?.takeIf { sensorInput ->
                        when (currentLocation) {
                            is Location.Axle -> currentLocation.axle == sensorInput.location.axle
                            is Location.Side -> currentLocation.side == sensorInput.location.side
                            is Location.Wheel -> currentLocation.location == sensorInput.location
                        }
                    }
                    ?.takeIf { sensorBindingUseCase.isBound(it.sensorId).not() }
                    ?.let { Tyre.Located(it, currentLocation) }

                // A location with a bound sensor only shows that sensor
                else -> tyre
                    .takeIf { it.sensorId == sensor.id }
                    ?.let { Tyre.Located(it, sensor.location) }
            }
        }
}
