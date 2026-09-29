package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.core.common.dematerializeCompletion
import com.masselis.tpmsadvanced.core.common.materializeCompletion
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

internal interface ListenTyreWithDatabaseUseCase : ListenTyreUseCase {
    class Impl(
        vehicle: Vehicle,
        location: Location,
        tyreDatabase: TyreDatabase,
        listenTyreUseCase: ListenTyreUseCase,
        scope: CoroutineScope,
    ) : ListenTyreWithDatabaseUseCase {

        // Only touched by the shared upstream, a single collector
        private var lastStored: Tyre.Located? = null
        private var storedCount = 0

        private val flow = listenTyreUseCase
            .listen()
            .onStart { tyreDatabase.prune(location, vehicle.uuid) }
            .onEach { tyre ->
                tyre
                    // Sensors send each reading several times in a burst, only the first one is
                    // stored. The signal strength changes from one to the next, it isn't compared.
                    .takeIf { new ->
                        lastStored
                            ?.let { last ->
                                new.sensorId == last.sensorId &&
                                    new.pressure == last.pressure &&
                                    new.temperature == last.temperature &&
                                    new.battery == last.battery &&
                                    new.isAlarm == last.isAlarm &&
                                    new.timestamp - last.timestamp < BURST_SECONDS
                            }
                            ?.not()
                            ?: true
                    }
                    ?.also { tyreDatabase.insert(it, vehicle.uuid) }
                    ?.also { lastStored = it }
                    ?.takeIf { ++storedCount % PRUNE_EVERY == 0 }
                    ?.also { tyreDatabase.prune(location, vehicle.uuid) }
            }
            .materializeCompletion()
            .shareIn(scope, WhileSubscribed())
            .dematerializeCompletion()
            .onStart {
                tyreDatabase
                    .latestByTyreLocationByVehicle(location, vehicle.uuid)
                    .execute()
                    ?.also { emit(it) }
            }
            .flowOn(Dispatchers.IO)

        override fun listen(): Flow<Tyre.Located> = flow

        private companion object {
            const val BURST_SECONDS = 60.0
            const val PRUNE_EVERY = 500
        }
    }

    class Wrapper(private val source: ListenTyreUseCase) : ListenTyreWithDatabaseUseCase {
        override fun listen(): Flow<Tyre.Located> = source.listen()
    }
}
