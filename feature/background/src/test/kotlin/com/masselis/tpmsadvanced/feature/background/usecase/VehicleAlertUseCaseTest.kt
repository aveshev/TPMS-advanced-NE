package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Axle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.feature.background.usecase.VehicleAlertUseCase.Alert
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleRangesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class VehicleAlertUseCaseTest {

    private lateinit var atmospheres: Map<Location, MutableSharedFlow<TyreAtmosphere>>
    private lateinit var vehicleRangesUseCase: VehicleRangesUseCase

    @Before
    fun setup() {
        // A tadpole three-wheeler: two front wheels and a rear axle
        atmospheres = listOf(Wheel(FRONT_LEFT), Wheel(FRONT_RIGHT), Axle(REAR))
            .associateWith { MutableSharedFlow(replay = 1) }
        vehicleRangesUseCase = mockk {
            // any() can't match the Location value classes, each location is stubbed
            atmospheres.keys.forEach { location ->
                every { resolvedLowPressure(location) } returns MutableStateFlow(150f.kpa)
                every { resolvedHighPressure(location) } returns MutableStateFlow(300f.kpa)
            }
            every { highTemp } returns MutableStateFlow(90f.celsius)
        }
    }

    private fun test() = VehicleAlertUseCase(
        atmospheres.keys.toList(),
        { location: Location -> atmospheres.getValue(location) as Flow<TyreAtmosphere> },
        vehicleRangesUseCase,
    )

    private fun atmosphere(kpa: Float, celsius: Float = 20f) =
        TyreAtmosphere(0.0, 1, kpa.kpa, celsius.celsius)

    @Test
    fun `nothing reported yet is no alert`() = runTest {
        test().alert.test {
            assertEquals(Alert.None, awaitItem())
        }
    }

    @Test
    fun `alerts on a low pressure while the other tyres never reported`() = runTest {
        atmospheres.getValue(Wheel(FRONT_LEFT)).emit(atmosphere(40f))
        test().alert.test {
            assertEquals(Alert.Pressure(atmosphere(40f)), awaitItem())
        }
    }

    @Test
    fun `alerts on a high temperature while the other tyres never reported`() = runTest {
        atmospheres.getValue(Axle(REAR)).emit(atmosphere(200f, celsius = 100f))
        test().alert.test {
            assertEquals(Alert.Temperature(atmosphere(200f, celsius = 100f)), awaitItem())
        }
    }

    @Test
    fun `a pressure alert wins over a temperature alert`() = runTest {
        atmospheres.getValue(Wheel(FRONT_LEFT)).emit(atmosphere(200f, celsius = 100f))
        atmospheres.getValue(Wheel(FRONT_RIGHT)).emit(atmosphere(40f))
        test().alert.test {
            assertEquals(Alert.Pressure(atmosphere(40f)), awaitItem())
        }
    }

    @Test
    fun `tyres within their ranges are no alert`() = runTest {
        atmospheres.values.forEach { it.emit(atmosphere(200f)) }
        test().alert.test {
            assertEquals(Alert.None, awaitItem())
        }
    }
}
