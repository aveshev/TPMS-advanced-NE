package com.masselis.tpmsadvanced.data.vehicle.interfaces

import android.database.sqlite.SQLiteConstraintException
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.interfaces.impl.AdvertisingPacket
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.DELTA_THREE_WHEELER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.SINGLE_AXLE_TRAILER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.TADPOLE_THREE_WHEELER

@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
internal fun Database.Companion.afterVersion3(
    locationAdapter: ColumnAdapter<Vehicle.Kind.Location, Long>
) = AfterVersion(3) { driver ->
    driver
        .executeQuery(
            null,
            "SELECT location, vehicleId FROM Sensor",
            {
                val result = mutableListOf<Pair<SensorLocation, String>>()
                while (it.next().value)
                    result += Pair(
                        SensorLocation.entries[it.getLong(0)!!.toInt()],
                        it.getString(1)!!
                    )
                QueryResult.Value(result.toList())
            },
            0,
        )
        .value
        .forEach { (sensorLocation, vehicleUuid) ->
            driver
                .executeQuery(
                    identifier = null,
                    sql = "SELECT kind FROM Vehicle WHERE Vehicle.uuid = ?",
                    parameters = 1,
                    binders = { bindString(0, vehicleUuid) },
                    mapper = { cursor ->
                        cursor.next()
                        cursor.getString(0)!!
                            .let { Vehicle.Kind.valueOf(it) }
                            .let { QueryResult.Value(it) }
                    },
                )
                .value
                .let { kind ->
                    when (kind) {
                        CAR ->
                            Vehicle.Kind.Location.Wheel(sensorLocation)

                        SINGLE_AXLE_TRAILER -> when (sensorLocation) {
                            FRONT_LEFT, REAR_LEFT -> Vehicle.Kind.Location.Side(LEFT)
                            FRONT_RIGHT, REAR_RIGHT -> Vehicle.Kind.Location.Side(RIGHT)
                        }

                        MOTORCYCLE -> when (sensorLocation) {
                            FRONT_LEFT, FRONT_RIGHT -> Vehicle.Kind.Location.Axle(FRONT)
                            REAR_LEFT, REAR_RIGHT -> Vehicle.Kind.Location.Axle(REAR)
                        }

                        TADPOLE_THREE_WHEELER -> when (sensorLocation) {
                            FRONT_LEFT, FRONT_RIGHT -> Vehicle.Kind.Location.Wheel(sensorLocation)
                            REAR_LEFT, REAR_RIGHT -> Vehicle.Kind.Location.Axle(REAR)
                        }

                        DELTA_THREE_WHEELER -> when (sensorLocation) {
                            FRONT_LEFT, FRONT_RIGHT -> Vehicle.Kind.Location.Axle(FRONT)
                            REAR_LEFT, REAR_RIGHT -> Vehicle.Kind.Location.Wheel(sensorLocation)
                        }
                    }
                }
                .let(locationAdapter::encode)
                .let { encodedLocation ->
                    try {
                        driver.execute(
                            null,
                            "UPDATE Sensor SET location = ? WHERE vehicleId = ?",
                            2
                        ) {
                            bindLong(0, encodedLocation)
                            bindString(1, vehicleUuid)
                        }
                    } catch (_: SQLiteConstraintException) {
                        // Appends if a vehicle like a motorcycle has 2 front wheels associated.
                        // The new database schema version 4 doesn't accept this case so the first
                        // sensor is set as the front wheel, the 2nd one is dropped from the
                        // database.
                    }
                    driver.execute(
                        null,
                        "UPDATE Tyre SET location = ? WHERE vehicleId = ?",
                        2
                    ) {
                        bindLong(0, encodedLocation)
                        bindString(1, vehicleUuid)
                    }
                }
        }
}

/**
 * Moves the rows 10.sqm kept in temporary tables into the rebuilt ones, their text turned into
 * bytes. A sensor gets the brand of its latest reading, a sensor without any is unbound: nothing
 * tells what it is. Plain SQL, SQLDelight's dialect not knowing unhex().
 */
@Suppress("MagicNumber", "MaxLineLength", "LongMethod")
internal fun Database.Companion.afterVersion10() = AfterVersion(10) { driver ->
    driver.execute(
        null,
        """
            INSERT INTO Vehicle(uuid, name, isFavourite, lowPressure, highPressure, lowTemp, normalTemp, highTemp, kind, isDeleting, rearLowPressure, rearHighPressure, separateRearPressure, pressureCalibration, pressureOffset, pressureMultiplier, lowBatteryVoltage)
            SELECT unhex(replace(uuid, '-', '')), name, isFavourite, lowPressure, highPressure, lowTemp, normalTemp, highTemp, kind, isDeleting, rearLowPressure, rearHighPressure, separateRearPressure, pressureCalibration, pressureOffset, pressureMultiplier, lowBatteryVoltage
            FROM temp.VehicleBefore11
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
            INSERT OR IGNORE INTO Reading(vehicleId, location, timestamp, sensorId, rssi, raw)
            SELECT unhex(replace(vehicleId, '-', '')), location, timestamp, id, rssi, unhex(raw)
            FROM temp.TyreBefore11
        """.trimIndent(),
        0,
    )
    driver
        .executeQuery(
            identifier = null,
            sql = """
                SELECT Sensor.id, Sensor.location, unhex(replace(Sensor.vehicleId, '-', '')), (
                    SELECT unhex(raw) FROM temp.TyreBefore11 AS Tyre
                    WHERE Tyre.vehicleId = Sensor.vehicleId AND Tyre.location = Sensor.location AND Tyre.id = Sensor.id
                    ORDER BY timestamp DESC
                    LIMIT 1
                )
                FROM temp.SensorBefore11 AS Sensor
            """.trimIndent(),
            mapper = { cursor ->
                buildList {
                    while (cursor.next().value) add(
                        listOf(cursor.getLong(0)!!, cursor.getLong(1)!!, cursor.getBytes(2)!!, cursor.getBytes(3))
                    )
                }.let { QueryResult.Value(it) }
            },
            parameters = 0,
        )
        .value
        .forEach { (id, location, vehicleId, raw) ->
            (raw as ByteArray?)
                ?.let { AdvertisingPacket(it).decode()?.brand }
                ?.also { brand ->
                    driver.execute(null, "INSERT INTO Sensor(id, location, vehicleId, brand) VALUES (?, ?, ?, ?)", 4) {
                        bindLong(0, id as Long)
                        bindLong(1, location as Long)
                        bindBytes(2, vehicleId as ByteArray)
                        bindLong(3, brand.code)
                    }
                }
        }
    listOf("VehicleBefore11", "SensorBefore11", "TyreBefore11")
        .forEach { driver.execute(null, "DROP TABLE temp.$it", 0) }
}
