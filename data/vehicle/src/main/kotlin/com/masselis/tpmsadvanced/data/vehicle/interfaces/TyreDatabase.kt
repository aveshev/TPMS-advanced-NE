package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.database.QueryList
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
import com.masselis.tpmsadvanced.core.database.QueryOne
import com.masselis.tpmsadvanced.core.database.QueryOne.Companion.asOne
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull.Companion.asOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.Database
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.util.UUID

public class TyreDatabase internal constructor(
    database: Database
) {
    private val queries = database.tyreQueries

    public suspend fun insert(tyre: Tyre.Located, vehicleId: UUID): Unit = withContext(IO) {
        queries.insert(
            tyre.sensorId,
            tyre.timestamp,
            tyre.rssi,
            tyre.location,
            tyre.pressure,
            tyre.temperature,
            tyre.battery,
            tyre.isAlarm,
            vehicleId,
            tyre.batteryVoltage,
            tyre.raw,
            tyre.batteryPercent,
        )
    }

    public fun latestByTyreLocationByVehicle(
        location: Location,
        vehicleId: UUID
    ): QueryOneOrNull<Tyre.Located> = queries
        .latestByTyreLocationByVehicle(
            location,
            vehicleId
        ) { id, timestamp, rssi, _, pressure, temperature, battery, isAlarm, batteryVoltage, raw, batteryPercent ->
            Tyre.Located(
                timestamp,
                rssi,
                id,
                pressure,
                temperature,
                battery,
                isAlarm,
                location,
                batteryVoltage,
                raw,
                batteryPercent,
            )
        }
        .asOneOrNull()

    /** Same as [latestByTyreLocationByVehicle], only among the records of the sensor [sensorId] */
    public fun latestBySensorByTyreLocationByVehicle(
        sensorId: Int,
        location: Location,
        vehicleId: UUID
    ): QueryOneOrNull<Tyre.Located> = queries
        .latestBySensorByTyreLocationByVehicle(
            sensorId,
            location,
            vehicleId
        ) { id, timestamp, rssi, _, pressure, temperature, battery, isAlarm, batteryVoltage, raw, batteryPercent ->
            Tyre.Located(
                timestamp,
                rssi,
                id,
                pressure,
                temperature,
                battery,
                isAlarm,
                location,
                batteryVoltage,
                raw,
                batteryPercent,
            )
        }
        .asOneOrNull()

    /** All the stored records of [location], oldest first, at most [CAP] of them */
    public fun allByTyreLocationByVehicle(
        location: Location,
        vehicleId: UUID,
    ): QueryList<Tyre.Located> = queries
        .allByTyreLocationByVehicle(
            location,
            vehicleId,
        ) { id, timestamp, rssi, _, pressure, temperature, battery, isAlarm, batteryVoltage, raw, batteryPercent ->
            Tyre.Located(
                timestamp,
                rssi,
                id,
                pressure,
                temperature,
                battery,
                isAlarm,
                location,
                batteryVoltage,
                raw,
                batteryPercent,
            )
        }
        .asList()

    /** The records of [location] stored after [timestamp], oldest first */
    public fun afterByTyreLocationByVehicle(
        location: Location,
        vehicleId: UUID,
        timestamp: Double,
    ): QueryList<Tyre.Located> = queries
        .afterByTyreLocationByVehicle(
            vehicleId,
            location,
            timestamp,
        ) { id, timestamp, rssi, _, pressure, temperature, battery, isAlarm, batteryVoltage, raw, batteryPercent ->
            Tyre.Located(
                timestamp,
                rssi,
                id,
                pressure,
                temperature,
                battery,
                isAlarm,
                location,
                batteryVoltage,
                raw,
                batteryPercent,
            )
        }
        .asList()

    /**
     * What the vehicle's bound sensors report their battery as, from their latest reading: a sensor
     * without any reading yet doesn't count
     */
    public fun batteryKinds(vehicleId: UUID): QueryOne<BatteryKinds> = queries
        .batteryKindsByVehicle(vehicleId) { hasVoltage, hasPercent ->
            BatteryKinds(hasVoltage != 0L, hasPercent != 0L)
        }
        .asOne()

    public data class BatteryKinds(val hasVoltage: Boolean, val hasPercent: Boolean)

    /**
     * Keeps the readings of [location] below [CAP], about 1 MB with its index: once over it, the
     * oldest are deleted down to [KEPT] so this doesn't have to run at every insert.
     */
    public suspend fun prune(location: Location, vehicleId: UUID): Unit = withContext(IO) {
        queries.transaction {
            queries
                .countByTyreLocationByVehicle(vehicleId, location)
                .executeAsOne()
                .takeIf { it > CAP }
                ?.also { queries.keepLatestByTyreLocationByVehicle(vehicleId, location, KEPT) }
        }
    }

    public companion object {
        /** A reading takes about 123 bytes, index included */
        public const val CAP: Long = 8_000
        public const val KEPT: Long = 6_000
    }
}
