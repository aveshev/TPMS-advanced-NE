package com.masselis.tpmsadvanced.data.vehicle.interfaces.impl

import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import kotlinx.coroutines.flow.Flow

/** Simulated readings are debug-only, see the debug source set. Release builds only scan. */
public object SimulatedReadings {

    public const val IS_AVAILABLE: Boolean = false

    @Suppress("UnusedParameter")
    public fun send(reading: Tyre.SensorInput): Unit = Unit

    internal fun Flow<Tyre.SensorInput>.withSimulatedReadings(): Flow<Tyre.SensorInput> = this
}
