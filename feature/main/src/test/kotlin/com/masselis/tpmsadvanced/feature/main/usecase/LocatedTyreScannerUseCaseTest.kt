package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class LocatedTyreScannerUseCaseTest {

    private lateinit var source: BluetoothLeScanner
    private lateinit var boundSensor: MutableStateFlow<Sensor?>
    private lateinit var boundElsewhere: Set<Int>
    private lateinit var sensorBindingUseCase: SensorBindingUseCase

    private val location = Location.Wheel(FRONT_LEFT)

    @Before
    fun setup() {
        source = mockk()
        boundSensor = MutableStateFlow(null)
        boundElsewhere = emptySet()
        sensorBindingUseCase = mockk {
            every { boundSensor() } returns this@LocatedTyreScannerUseCaseTest.boundSensor
            every { isBound(any()) } answers {
                firstArg<Int>().let { it == boundSensor.value?.id || it in boundElsewhere }
            }
        }
    }

    private fun test() = LocatedTyreScannerUseCase(source, location, sensorBindingUseCase)

    private fun sysgration(id: Int, at: SensorLocation = FRONT_LEFT) =
        Tyre.SensorLocated(0.0, -60, id, 2f.bar, 20f.celsius, 100u, false, at)

    private fun pecham(id: Int) = Tyre.Unlocated(0.0, -60, id, 2f.bar, 20f.celsius, 30u, false)

    /** The records this location keeps out of [tyres], in order */
    private suspend fun keeps(vararg tyres: Tyre.SensorInput): List<Tyre.Located> = tyres
        .also { every { source.normalScan() } returns flowOf(*it) }
        .let { test().normalScan().toList() }

    @Test
    fun `a bound sensor is kept, at its bound location`() = runTest {
        boundSensor.value = Sensor(1, location)
        assertEquals(listOf(Tyre.Located(pecham(1), location)), keeps(pecham(1)))
    }

    @Test
    fun `a location with a bound sensor drops the others, advertising this location or not`() = runTest {
        boundSensor.value = Sensor(1, location)
        assertEquals(listOf(1), keeps(pecham(2), sysgration(3), pecham(1)).map { it.sensorId })
    }

    @Test
    fun `a free location keeps a sensor advertising it`() = runTest {
        assertEquals(listOf(Tyre.Located(sysgration(3), location)), keeps(sysgration(3)))
    }

    @Test
    fun `a free location drops a sensor advertising another location`() = runTest {
        assertEquals(emptyList(), keeps(sysgration(3, at = FRONT_RIGHT)))
    }

    @Test
    fun `a free location drops a sensor advertising it but bound elsewhere`() = runTest {
        boundElsewhere = setOf(3)
        assertEquals(emptyList(), keeps(sysgration(3)))
    }

    @Test
    fun `a free location drops a sensor which doesn't advertise its location`() = runTest {
        assertEquals(emptyList(), keeps(pecham(2)))
    }
}
