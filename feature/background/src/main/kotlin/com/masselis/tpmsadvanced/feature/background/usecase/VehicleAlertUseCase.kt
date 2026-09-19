package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration.Companion.milliseconds

internal class VehicleAlertUseCase(vehicleComponent: VehicleComponent) {

    sealed interface Alert {
        data object None : Alert

        data class Pressure(val atmosphere: TyreAtmosphere) : Alert

        data class Temperature(val atmosphere: TyreAtmosphere) : Alert
    }

    @OptIn(FlowPreview::class)
    val alert: Flow<Alert> = vehicleComponent
        .vehicle
        .kind
        .locations
        .toList()
        .let { locations ->
            val comps = locations.map { vehicleComponent.TyreComponent(it) }
            val vehicleRangesUseCase = vehicleComponent.vehicleRangesUseCase
            combine(
                combine(comps.map { it.tyreAtmosphereUseCase.listen() }) { it }
                    .onStart { emit(emptyArray()) }
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
            ) { atmospheres, pressureRanges, highTemp ->
                atmospheres
                    .withIndex()
                    .firstOrNull { (index, atmosphere) ->
                        atmosphere.pressure !in pressureRanges[index]
                    }
                    ?.let { (_, atmosphere) -> Alert.Pressure(atmosphere) }
                    ?: atmospheres
                        .firstOrNull { it.temperature > highTemp }
                        ?.let(Alert::Temperature)
                    ?: Alert.None
            }
        }
}
