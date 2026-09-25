package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle

internal class RenameVehicleUseCase(
    private val vehicle: Vehicle,
    private val database: VehicleDatabase,
) {

    suspend fun rename(name: String) = name
        .trim()
        .also { require(it.isNotEmpty()) { "A vehicle name cannot be blank" } }
        .let { database.updateName(it, vehicle.uuid) }
}
