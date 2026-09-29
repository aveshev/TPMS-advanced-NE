package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.test.mockkQueryOneOrNull
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class ListenTyreWithDatabaseUseCaseTest {

    private lateinit var vehicle: Vehicle
    private lateinit var location: Location
    private lateinit var tyreDatabase: TyreDatabase
    private lateinit var listenTyreUseCase: ListenTyreUseCase
    private lateinit var boundSensor: MutableStateFlow<Sensor?>
    private lateinit var sensorBindingUseCase: SensorBindingUseCase

    private fun CoroutineScope.test() = ListenTyreWithDatabaseUseCase.Impl(
        vehicle,
        location,
        tyreDatabase,
        listenTyreUseCase,
        sensorBindingUseCase,
        this
    )

    @Before
    fun setup() {
        vehicle = mockk {
            val uuid = UUID.randomUUID()
            every { this@mockk.uuid } returns uuid
        }
        location = Location.Wheel(FRONT_LEFT)
        tyreDatabase = mockk {
            coEvery { insert(any(), any()) } returns Unit
            every { latestByTyreLocationByVehicle(any<Location.Wheel>(), any()) } returns
                    mockkQueryOneOrNull(null as Tyre.Located?)
        }
        listenTyreUseCase = mockk {
            every { listen() } returns MutableSharedFlow()
        }
        boundSensor = MutableStateFlow(null)
        sensorBindingUseCase = mockk {
            every { boundSensor() } returns this@ListenTyreWithDatabaseUseCaseTest.boundSensor
        }
    }

    @Test
    fun `2 tyres emit with same id at front left`() = runTest {
        val tyresToEmit = listOf(
            Tyre.Located(now(), -20, 1, 1f.bar, 1f.celsius, 50u, false, Location.Wheel(FRONT_LEFT)),
            Tyre.Located(now(), -30, 1, 2f.bar, 2f.celsius, 25u, false, Location.Wheel(FRONT_LEFT))
        )
        every { listenTyreUseCase.listen() } returns tyresToEmit
            .asFlow()
            .onCompletion { awaitCancellation() }
        test().listen().test {
            assertEquals(tyresToEmit[0], awaitItem())
            assertEquals(tyresToEmit[1], awaitItem())
        }
        coVerify(exactly = 2) { tyreDatabase.insert(any(), any()) }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `No tyre emit but a cache exists`() = runTest {
        val savedTyre =
            Tyre.Located(now(), -20, 1, 1f.bar, 1f.celsius, 1u, false, Location.Wheel(FRONT_LEFT))
        every { tyreDatabase.latestByTyreLocationByVehicle(location, any()) } returns
                mockkQueryOneOrNull(savedTyre)
        test().listen().test {
            assertEquals(savedTyre, awaitItem())
        }
        coVerify(exactly = 0) { tyreDatabase.insert(any(), any()) }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `with a bound sensor, replays its latest record rather than another sensor's`() = runTest {
        val foreignTyre =
            Tyre.Located(now(), -20, 2, 3f.bar, 1f.celsius, 1u, false, Location.Wheel(FRONT_LEFT))
        val boundTyre =
            Tyre.Located(now() - 60, -20, 1, 2f.bar, 1f.celsius, 1u, false, Location.Wheel(FRONT_LEFT))
        boundSensor.value = Sensor(1, location)
        every { tyreDatabase.latestByTyreLocationByVehicle(location, any()) } returns
                mockkQueryOneOrNull(foreignTyre)
        every { tyreDatabase.latestBySensorByTyreLocationByVehicle(1, location, any()) } returns
                mockkQueryOneOrNull(boundTyre)
        test().listen().test {
            assertEquals(boundTyre, awaitItem())
        }
        coroutineContext.cancelChildren()
    }
}
