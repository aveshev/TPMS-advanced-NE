package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.interfaces.TyreDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

internal class TyrePressureLossStateFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var stored: List<Tyre.Located>
    private lateinit var listened: List<Tyre.Located>
    private lateinit var calibration: MutableStateFlow<PressureCalibration?>
    private lateinit var rule: MutableStateFlow<PressureLoss.Rule?>

    @Before
    fun setup() {
        stored = emptyList()
        listened = emptyList()
        calibration = MutableStateFlow(null)
        rule = MutableStateFlow(PressureLoss.Rule(150f.kpa, 10.hours))
    }

    context(scope: TestScope)
    private fun test() = TyrePressureLossStateFlow(
        mockk<Vehicle> { every { uuid } returns VEHICLE_UUID },
        Wheel(FRONT_LEFT),
        mockk<TyreDatabase> {
            // any() can't match the Location value classes
            every { allByTyreLocationByVehicle(Wheel(FRONT_LEFT), VEHICLE_UUID) } returns mockk {
                every { execute() } returns stored
            }
        },
        mockk<ListenTyreUseCase> { every { listen() } returns flowOf(*listened.toTypedArray()) },
        mockk<VehicleCalibrationUseCase> {
            every { calibration } returns this@TyrePressureLossStateFlowTest.calibration
        },
        mockk<VehiclePressureLossUseCase> {
            every { rule(Wheel(FRONT_LEFT)) } returns this@TyrePressureLossStateFlowTest.rule
        },
        scope.backgroundScope,
    )

    private fun record(hour: Int, kpa: Float, sensorId: Int = 1) = Tyre.Located(
        START + hour * SECONDS_PER_HOUR,
        -50,
        sensorId,
        kpa.kpa,
        20f.celsius,
        100u,
        false,
        Wheel(FRONT_LEFT),
    )

    /** The flow computes on other dispatchers, the wait is in real time */
    private suspend fun TyrePressureLossStateFlow.awaitLoss(): PressureLoss? =
        withContext(Dispatchers.Default) { withTimeoutOrNull(1.seconds) { first { it != null } } }

    @Test
    fun `detects a loss started before the app was opened`() = runTest {
        stored = listOf(record(0, 230f), record(1, 229f))
        listened = listOf(record(12, 150f))
        val loss = test().awaitLoss()
        assertNotNull(loss)
        // Spread over the time parked, since the last reading before it
        assertEquals(START + SECONDS_PER_HOUR, loss.since)
    }

    @Test
    fun `forgets the readings of the previously bound sensor`() = runTest {
        stored = listOf(record(0, 230f, sensorId = 2), record(1, 230f, sensorId = 2))
        listened = listOf(record(12, 150f))
        assertNull(test().awaitLoss())
    }

    @Test
    fun `stays quiet while the warning is off`() = runTest {
        rule.value = null
        listened = listOf(record(0, 230f), record(12, 150f))
        assertNull(test().awaitLoss())
    }

    private companion object {
        const val START = 1_726_483_200.0
        const val SECONDS_PER_HOUR = 3600.0
        val VEHICLE_UUID: UUID = UUID.randomUUID()
    }
}
