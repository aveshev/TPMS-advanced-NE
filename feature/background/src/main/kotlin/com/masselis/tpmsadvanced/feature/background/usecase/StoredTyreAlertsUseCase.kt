package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAlerts
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.toAtmosphere
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge

/**
 * The alerts of every tyre of every vehicle, from the readings stored by whatever scans, the main
 * screen or the monitor service: this never scans by itself. Only the readings stored after it
 * started listening are emitted, the ones stored before set the tyres' history up (see
 * docs/alerts.md, "Only new readings alert").
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StoredTyreAlertsUseCase(
    vehicleListUseCase: VehicleListUseCase,
    private val readingDatabase: ReadingDatabase,
) {

    /** A tyre's alerts once [TyreAlerts.latest], a new reading, went through */
    data class Update(
        val vehicle: Vehicle,
        val location: Location,
        val alerts: TyreAlerts,
        val thresholds: AlertThresholds,
        val loss: PressureLoss?,
    )

    val updates: Flow<Update> = vehicleListUseCase
        .vehicleListFlow
        // Editing a vehicle (ranges, name...) re-emits the list, which would start the tyres' history
        // over: only a change in the set of vehicles rebuilds them
        .distinctUntilChanged { old, new -> old.map(Vehicle::uuid) == new.map(Vehicle::uuid) }
        .flatMapLatest { vehicles ->
            vehicles
                .flatMap { vehicle -> vehicle.kind.locations.map { location -> updates(vehicle, location) } }
                .merge()
        }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun updates(vehicle: Vehicle, location: Location): Flow<Update> = flow {
        val component = VehicleComponent(vehicle)
        // The leak is followed with the calibration and the rule it started with, both changing
        // starts it over from the stored readings, as TyrePressureLossStateFlow does
        var calibration = component.vehicleCalibrationUseCase.calibration.first()
        var rule = component.vehiclePressureLossUseCase.rule.first()
        val stored = readingDatabase.allByLocation(location, vehicle.uuid).execute()
        var (tracker, alerts) = component
            .vehicleRangesUseCase
            .alertThresholds(location)
            .first()
            .let { thresholds ->
                stored.fold(PressureLoss.Tracker() to TyreAlerts()) { (tracker, alerts), record ->
                    record
                        .toAtmosphere(calibration)
                        .let { reading ->
                            (rule?.let { tracker.next(reading, it) } ?: tracker)
                                .let { it to alerts.next(reading, thresholds, it.loss) }
                        }
                }
            }
        var since = stored.lastOrNull()?.timestamp ?: Double.NEGATIVE_INFINITY
        // Emits on every change of the readings, of any tyre
        readingDatabase
            .latestByLocation(location, vehicle.uuid)
            .asFlow()
            .collect {
                readingDatabase
                    .afterByLocation(location, vehicle.uuid, since)
                    .execute()
                    .forEach { record ->
                        val newCalibration = component.vehicleCalibrationUseCase.calibration.first()
                        val newRule = component.vehiclePressureLossUseCase.rule.first()
                        if (newCalibration != calibration || newRule != rule) {
                            calibration = newCalibration
                            rule = newRule
                            tracker = rule
                                ?.let { lossRule ->
                                    readingDatabase
                                        .allByLocation(location, vehicle.uuid)
                                        .execute()
                                        .filter { it.timestamp < record.timestamp }
                                        .fold(PressureLoss.Tracker()) { tracker, stored ->
                                            tracker.next(stored.toAtmosphere(calibration), lossRule)
                                        }
                                }
                                ?: PressureLoss.Tracker()
                        }
                        val reading = record.toAtmosphere(calibration)
                        tracker = rule?.let { tracker.next(reading, it) } ?: tracker
                        val thresholds = component.vehicleRangesUseCase.alertThresholds(location).first()
                        alerts = alerts.next(reading, thresholds, tracker.loss)
                        since = record.timestamp
                        emit(Update(vehicle, location, alerts, thresholds, tracker.loss))
                    }
            }
    }.flowOn(Dispatchers.IO)
}
