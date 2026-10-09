package com.masselis.tpmsadvanced.feature.qrcode.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID

internal interface BoundSensorMapUseCase {
    /** Assigns [sensors] to [vehicleUuid], replacing the sensors of their wheels */
    suspend fun bind(vehicleUuid: UUID, sensors: List<Sensor>)

    class Impl(private val sensorDatabase: SensorDatabase) : BoundSensorMapUseCase {
        override suspend fun bind(vehicleUuid: UUID, sensors: List<Sensor>) = coroutineScope {
            sensors
                .map { async { sensorDatabase.upsert(it, vehicleUuid) } }
                .awaitAll()
        }.let { } // Returns Unit
    }

    object NoOp : BoundSensorMapUseCase {
        override suspend fun bind(vehicleUuid: UUID, sensors: List<Sensor>) {}
    }
}
