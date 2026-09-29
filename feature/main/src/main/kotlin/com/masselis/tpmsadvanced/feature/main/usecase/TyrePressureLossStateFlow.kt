package com.masselis.tpmsadvanced.feature.main.usecase

import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.toAtmosphere
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlin.time.DurationUnit.SECONDS

/**
 * The tyre's early leak warning, null while it isn't losing pressure or the vehicle's
 * [VehiclePressureLossUseCase] is off. The readings of the longest window are kept in memory,
 * starting with the stored ones so a restarted app or service doesn't forget a loss in progress.
 * They're kept as read, the calibration being applied to all of them at once: changing it can't
 * look like a loss.
 */
@Suppress("OPT_IN_TO_INHERITANCE", "LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
public class TyrePressureLossStateFlow internal constructor(
    vehicle: Vehicle,
    location: Location,
    tyreDatabase: TyreDatabase,
    listenTyreUseCase: ListenTyreUseCase,
    calibrationUseCase: VehicleCalibrationUseCase,
    pressureLossUseCase: VehiclePressureLossUseCase,
    scope: CoroutineScope,
    stateFlow: StateFlow<PressureLoss?> = flow {
        tyreDatabase
            .sinceByTyreLocationByVehicle(location, vehicle.uuid, now() - HISTORY_SECONDS)
            .execute()
            // Only the latest sensor, another one may have been bound here before
            .let { stored -> stored.filter { it.sensorId == stored.last().sensorId } }
            .also { emit(it) }
    }
        .flowOn(Dispatchers.IO)
        .flatMapLatest { stored ->
            listenTyreUseCase
                .listen()
                .runningFold(stored) { history, record ->
                    history
                        // The listened flow starts with the latest stored record
                        .plus(record)
                        .distinct()
                        .filter { it.sensorId == record.sensorId }
                        .filter { it.timestamp >= record.timestamp - HISTORY_SECONDS }
                }
        }
        .combine(calibrationUseCase.calibration) { history, calibration ->
            history.map { it.toAtmosphere(calibration) }
        }
        .combine(pressureLossUseCase.rule) { history, rule -> rule?.detect(history) }
        .flowOn(Dispatchers.Default)
        .catch {
            Logger.withTag("TyrePressureLossStateFlow").e("Failed to check the pressure loss", it)
            emit(null)
        }
        .stateIn(scope, WhileSubscribed(), null),
) : StateFlow<PressureLoss?> by stateFlow {

    private companion object {
        private val HISTORY_SECONDS = VehiclePressureLossUseCase
            .WINDOWS
            .max()
            .toDouble(SECONDS)
    }
}
