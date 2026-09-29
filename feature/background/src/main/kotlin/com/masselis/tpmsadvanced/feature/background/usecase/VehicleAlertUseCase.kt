package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State.Battery.Level.LOW
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration.Companion.milliseconds

internal class VehicleAlertUseCase(
    locations: List<Location>,
    listenAtmosphere: (Location) -> Flow<TyreAtmosphere>,
    listenPressureLoss: (Location) -> Flow<PressureLoss?>,
    vehicleRangesUseCase: VehicleRangesUseCase,
) {

    constructor(vehicleComponent: VehicleComponent) : this(
        vehicleComponent.vehicle.kind.locations.toList(),
        { vehicleComponent.TyreComponent(it).tyreAtmosphereUseCase.listen() },
        { vehicleComponent.TyreComponent(it).tyrePressureLossStateFlow },
        vehicleComponent.vehicleRangesUseCase,
    )

    sealed interface Alert {
        data object None : Alert

        data class Pressure(val atmosphere: TyreAtmosphere) : Alert

        /** Raised by the sensor itself (Sysgration), maybe a leak the pressure doesn't show yet */
        data class SensorAlarm(val atmosphere: TyreAtmosphere) : Alert

        data class Temperature(val atmosphere: TyreAtmosphere) : Alert

        /**
         * An early leak warning, only reported when no tyre alerts for its pressure, a sensor alarm
         * or its temperature
         */
        data class PressureLoss(
            val location: Location,
            val loss: com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss,
        ) : Alert

        /** The least severe, only reported when no tyre alerts for anything else */
        data class LowBattery(val atmosphere: TyreAtmosphere) : Alert
    }

    @OptIn(FlowPreview::class)
    val alert: Flow<Alert> = combine(
        combine(
            locations.map { location ->
                listenAtmosphere(location)
                    .map<TyreAtmosphere, TyreAtmosphere?> { it }
                    // A tyre which never reported (dead, removed or unbound sensor) must not hold
                    // back the alerts of the others: combine only emits once each flow did
                    .onStart { emit(null) }
            }
        ) { it }
            .debounce(100.milliseconds),
        combine(
            locations.map {
                combine(
                    vehicleRangesUseCase.resolvedLowPressure(it),
                    vehicleRangesUseCase.resolvedHighPressure(it),
                ) { low, high -> low..high }
            }
        ) { it },
        vehicleRangesUseCase.highTemp,
        vehicleRangesUseCase.lowBatteryVoltage,
        combine(locations.map { listenPressureLoss(it).onStart { emit(null) } }) { it.toList() },
    ) { atmospheres, pressureRanges, highTemp, lowBatteryVoltage, losses ->
        atmospheres
            .withIndex()
            .mapNotNull { (index, atmosphere) -> atmosphere?.let { index to it } }
            .let { reported ->
                reported
                    .firstOrNull { (index, atmosphere) -> atmosphere.pressure !in pressureRanges[index] }
                    ?.let { (_, atmosphere) -> Alert.Pressure(atmosphere) }
                    ?: reported
                        .firstOrNull { (_, atmosphere) -> atmosphere.isSensorAlarm }
                        ?.let { (_, atmosphere) -> Alert.SensorAlarm(atmosphere) }
                    ?: reported
                        .firstOrNull { (_, atmosphere) -> atmosphere.temperature > highTemp }
                        ?.let { (_, atmosphere) -> Alert.Temperature(atmosphere) }
                    ?: locations
                        .zip(losses)
                        .firstNotNullOfOrNull { (location, loss) ->
                            loss?.let { Alert.PressureLoss(location, it) }
                        }
                    ?: reported
                        .firstOrNull { (_, atmosphere) ->
                            atmosphere
                                .batteryVoltage
                                ?.let { Battery.of(it, lowBatteryVoltage).level == LOW }
                                ?: false
                        }
                        ?.let { (_, atmosphere) -> Alert.LowBattery(atmosphere) }
                    ?: Alert.None
            }
    }
}
