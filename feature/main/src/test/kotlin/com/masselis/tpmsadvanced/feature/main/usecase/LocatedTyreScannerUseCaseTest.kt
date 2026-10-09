package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.demo.DemoLeScanner
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    private var showsUnbound = false

    private lateinit var location: Location

    @Before
    fun setup() {
        source = mockk()
        boundSensor = MutableStateFlow(null)
        boundElsewhere = emptySet()
        showsUnbound = false
        location = Location.Wheel(FRONT_LEFT)
        sensorBindingUseCase = mockk {
            every { boundSensor() } returns this@LocatedTyreScannerUseCaseTest.boundSensor
            every { isBound(any()) } answers {
                firstArg<Int>().let { it == boundSensor.value?.id || it in boundElsewhere }
            }
            every { boundVehicle(any()) } answers {
                flowOf(mockk<Vehicle>().takeIf { firstArg<Sensor>().id in boundElsewhere })
            }
        }
    }

    private fun test() = LocatedTyreScannerUseCase(source, location, sensorBindingUseCase, showsUnbound)

    private fun sysgration(id: Int, at: SensorLocation = FRONT_LEFT) =
        Tyre.SensorLocated(0.0, -60, id, 2f.bar, 20f.celsius, SYSGRATION, false, at)

    private fun pecham(id: Int) = Tyre.Unlocated(0.0, -60, id, 2f.bar, 20f.celsius, PECHAM, false)

    /** The records this location keeps out of [tyres], in order */
    private suspend fun keeps(vararg tyres: Tyre.SensorInput): List<Tyre.Located> = detects(*tyres).first

    /** The records this location keeps out of [tyres], and the sensor it detected once scanned */
    private suspend fun detects(vararg tyres: Tyre.SensorInput): Pair<List<Tyre.Located>, Sensor?> = tyres
        .also { every { source.normalScan() } returns flowOf(*it) }
        .let { test() }
        .let { it.normalScan().toList() to it.detectedSensor().first() }

    @Test
    fun `a bound sensor is kept, at its bound location`() = runTest {
        boundSensor.value = Sensor(1, location, PECHAM)
        assertEquals(listOf(Tyre.Located(pecham(1), location)), keeps(pecham(1)))
    }

    @Test
    fun `a location with a bound sensor drops the others, advertising this location or not`() = runTest {
        boundSensor.value = Sensor(1, location, PECHAM)
        assertEquals(listOf(1), keeps(pecham(2), sysgration(3), pecham(1)).map { it.sensorId })
    }

    @Test
    fun `a free location detects a sensor advertising it, without showing its readings`() = runTest {
        assertEquals(emptyList<Tyre.Located>() to Sensor(3, location, SYSGRATION), detects(sysgration(3)))
    }

    @Test
    fun `the demo shows a sensor advertising a free location, without detecting it`() = runTest {
        showsUnbound = true
        assertEquals(listOf(Tyre.Located(sysgration(3), location)) to null, detects(sysgration(3)))
    }

    @Test
    fun `a detected sensor bound elsewhere afterwards is no longer detected`() = runTest {
        every { source.normalScan() } returns flowOf(sysgration(3))
        test().also { it.normalScan().toList() }.also { boundElsewhere = setOf(3) }
            .let { assertEquals(null, it.detectedSensor().first()) }
    }

    @Test
    fun `a location with a bound sensor detects nothing`() = runTest {
        boundSensor.value = Sensor(1, location, PECHAM)
        assertEquals(null, detects(sysgration(3)).second)
    }

    @Test
    fun `a free location drops a sensor advertising another location`() = runTest {
        assertEquals(emptyList<Tyre.Located>() to null, detects(sysgration(3, at = FRONT_RIGHT)))
    }

    @Test
    fun `a free location drops a sensor advertising it but bound elsewhere`() = runTest {
        boundElsewhere = setOf(3)
        assertEquals(emptyList<Tyre.Located>() to null, detects(sysgration(3)))
    }

    @Test
    fun `a free location drops a sensor which doesn't advertise its location`() = runTest {
        assertEquals(emptyList(), keeps(pecham(2)))
    }

    @Test
    fun `the demo shows its spare sensor at a free spare, and nothing else there`() = runTest {
        showsUnbound = true
        location = Location.Spare
        assertEquals(
            listOf(DemoLeScanner.SPARE_SENSOR_ID),
            keeps(sysgration(3), pecham(2), pecham(DemoLeScanner.SPARE_SENSOR_ID)).map { it.sensorId },
        )
    }

    @Test
    fun `out of the demo, the demo's spare sensor isn't shown at a free spare`() = runTest {
        location = Location.Spare
        assertEquals(emptyList(), keeps(pecham(DemoLeScanner.SPARE_SENSOR_ID)))
    }
}
