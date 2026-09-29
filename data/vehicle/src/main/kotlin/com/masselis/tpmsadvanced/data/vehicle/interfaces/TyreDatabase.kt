package com.masselis.tpmsadvanced.data.vehicle.interfaces

import com.masselis.tpmsadvanced.core.database.QueryList
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
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
            vehicleId
        )
    }

    public fun latestByTyreLocationByVehicle(
        location: Location,
        vehicleId: UUID
    ): QueryOneOrNull<Tyre.Located> = queries
        .latestByTyreLocationByVehicle(
            location,
            vehicleId
        ) { id, timestamp, rssi, _, pressure, temperature, battery, isAlarm ->
            Tyre.Located(timestamp, rssi, id, pressure, temperature, battery, isAlarm, location)
        }
        .asOneOrNull()

    /** The records of [location] from [since] onwards, oldest first */
    public fun sinceByTyreLocationByVehicle(
        location: Location,
        vehicleId: UUID,
        since: Double,
    ): QueryList<Tyre.Located> = queries
        .sinceByTyreLocationByVehicle(
            location,
            vehicleId,
            since,
        ) { id, timestamp, rssi, _, pressure, temperature, battery, isAlarm ->
            Tyre.Located(timestamp, rssi, id, pressure, temperature, battery, isAlarm, location)
        }
        .asList()

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
