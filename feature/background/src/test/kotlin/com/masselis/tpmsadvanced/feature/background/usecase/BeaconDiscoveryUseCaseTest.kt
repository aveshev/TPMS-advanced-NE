package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Device
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class BeaconDiscoveryUseCaseTest {

    private lateinit var advertisements: MutableSharedFlow<Advertisement>
    private lateinit var bluetoothOn: MutableStateFlow<Boolean>
    private lateinit var scanner: BluetoothLeScanner

    context(scope: TestScope)
    private fun test() = BeaconDiscoveryUseCase(scanner, bluetoothOn, scope.testScheduler.timeSource)

    @Before
    fun setup() {
        advertisements = MutableSharedFlow()
        bluetoothOn = MutableStateFlow(true)
        scanner = mockk { every { advertisements(any(), any()) } returns advertisements }
    }

    @Test
    fun `every device heard is listed, refreshed every second`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            verify { scanner.advertisements(LOW_LATENCY, null) }
            advertisements.emit(advertisement("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", rssi = -84))
            expectNoEvents()
            delay(1.seconds)
            assertEquals(
                listOf(Device("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", -84, period = null, isTyreSensor = false)),
                awaitItem()
            )
        }
    }

    @Test
    fun `the period is the median gap between packets, a missed one not counting`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            // 300 ms apart, one packet missed between the third and the fourth
            listOf(0, 300, 600, 1200, 1500, 1800).forEach {
                advertisements.emit(advertisement("EE:64:A3:12:38:1A", rssi = -84, timestamp = it.milliseconds))
            }
            delay(1.seconds)
            assertEquals(300.milliseconds, awaitItem().single().period)
        }
    }

    @Test
    fun `no period is told from too few packets`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            listOf(0, 300).forEach {
                advertisements.emit(advertisement("EE:64:A3:12:38:1A", rssi = -84, timestamp = it.milliseconds))
            }
            delay(1.seconds)
            assertNull(awaitItem().single().period)
        }
    }

    @Test
    fun `a name sent in some packets only is kept`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            advertisements.emit(advertisement("EE:64:A3:12:38:1A", "CFMOTOR_ee64a312381a", rssi = -84))
            advertisements.emit(advertisement("EE:64:A3:12:38:1A", name = null, rssi = -80))
            delay(1.seconds)
            assertEquals("CFMOTOR_ee64a312381a", awaitItem().single().name)
        }
    }

    @Test
    fun `the loudest devices come first, then the first found`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            advertisements.emit(advertisement("00:00:00:00:00:01", rssi = -95, timestamp = 1.milliseconds))
            advertisements.emit(advertisement("00:00:00:00:00:02", rssi = -62, timestamp = 2.milliseconds))
            // Same 10 dB band as the first one: not moved above it for a few dB
            advertisements.emit(advertisement("00:00:00:00:00:03", rssi = -92, timestamp = 3.milliseconds))
            delay(1.seconds)
            assertEquals(
                listOf("00:00:00:00:00:02", "00:00:00:00:00:01", "00:00:00:00:00:03"),
                awaitItem().map { it.address }
            )
        }
    }

    @Test
    fun `a device gone quiet leaves the list`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            advertisements.emit(advertisement("EE:64:A3:12:38:1A", rssi = -84))
            delay(1.seconds)
            assertEquals(1, awaitItem().size)
            delay(30.seconds)
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `nothing is listed while Bluetooth is off`() = runTest {
        bluetoothOn.value = false
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            verify(exactly = 0) { scanner.advertisements(any(), any()) }
        }
    }

    @Test
    fun `a resolvable private address may change`() {
        assertTrue(device("4A:1B:2C:3D:4E:5F").mayChangeAddress)
        assertTrue(device("7F:1B:2C:3D:4E:5F").mayChangeAddress)
        // Static random, as the CFMOTOR beacons use
        assertFalse(device("EE:64:A3:12:38:1A").mayChangeAddress)
        assertFalse(device("C4:CD:82:63:55:15").mayChangeAddress)
        assertFalse(device("80:EA:CA:10:20:30").mayChangeAddress)
    }

    private fun device(address: String) = Device(address, null, -60, null, isTyreSensor = false)

    private fun advertisement(
        address: String,
        name: String? = null,
        rssi: Int,
        timestamp: Duration = Duration.ZERO,
    ) = Advertisement(address, name, rssi, timestamp, isTyreSensor = false)
}
