package com.masselis.tpmsadvanced.feature.unlocated.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import java.util.UUID

internal interface BindSensorToVehicleUseCase {
    suspend fun bind(vehicleUuid: UUID, sensor: Sensor, tyre: Tyre)

    class Impl(
        private val sensorDatabase: SensorDatabase,
        private val readingDatabase: ReadingDatabase,
    ) : BindSensorToVehicleUseCase {

        override suspend fun bind(vehicleUuid: UUID, sensor: Sensor, tyre: Tyre) {
            sensorDatabase.upsert(sensor, vehicleUuid)
            readingDatabase.insert(Tyre.Located(tyre, sensor.location), vehicleUuid)
        }
    }

    object NoOp : BindSensorToVehicleUseCase {
        override suspend fun bind(
            vehicleUuid: UUID,
            sensor: Sensor,
            tyre: Tyre
        ) {
        }
    }
}
