package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

/**
 * Debug builds only: readings typed in the app, to try the alerts away from the sensors. Merged
 * into the scan right after its decoders, they go wherever a real one goes: matched to a tyre by
 * its binding or its location, stored, shown and alerted on. Only received while a scan runs, as
 * the main screen keeps one running.
 */
public object SimulatedReadings {

    public const val IS_AVAILABLE: Boolean = true

    private val readings = MutableSharedFlow<Tyre.SensorInput>(extraBufferCapacity = 16)

    public fun send(reading: Tyre.SensorInput) {
        readings.tryEmit(reading)
    }

    internal fun Flow<Tyre.SensorInput>.withSimulatedReadings(): Flow<Tyre.SensorInput> = merge(this, readings)
}
