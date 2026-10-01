package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.ScanMode.LOW_LATENCY
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Device
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconDiscoveryUseCase.Page
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("MaxLineLength")
internal class BeaconDiscoveryUseCaseTest {

    private lateinit var advertisements: MutableSharedFlow<Advertisement>
    private lateinit var bluetoothOn: MutableStateFlow<Boolean>
    private lateinit var reloads: MutableSharedFlow<Unit>
    private lateinit var scanner: BluetoothLeScanner

    context(scope: TestScope)
    private fun test() = BeaconDiscoveryUseCase(scanner, bluetoothOn, scope.testScheduler.timeSource)

    @Before
    fun setup() {
        advertisements = MutableSharedFlow()
        bluetoothOn = MutableStateFlow(true)
        reloads = MutableSharedFlow()
        scanner = mockk { every { advertisements(any(), any()) } returns advertisements }
    }

    @Test
    fun `every named device heard is listed, refreshed every second`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            verify { scanner.advertisements(LOW_LATENCY, null) }
            advertisements.emit(advertisement(BIKE, "CFMOTOR_ee64a312381a", rssi = -84))
            expectNoEvents()
            delay(1.seconds)
            assertEquals(listOf(device(BIKE, "CFMOTOR_ee64a312381a", rssi = -84)), awaitItem())
        }
    }

    @Test
    fun `an unnamed device is left out`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            advertisements.emit(advertisement("4A:1B:2C:3D:4E:5F", name = null, rssi = -50))
            delay(1.seconds)
            expectNoEvents()
        }
    }

    @Test
    fun `a name sent in some packets only is kept`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            advertisements.emit(advertisement(BIKE, "CFMOTOR_ee64a312381a", rssi = -84))
            advertisements.emit(advertisement(BIKE, name = null, rssi = -84))
            delay(1.seconds)
            assertEquals("CFMOTOR_ee64a312381a", awaitItem().single().name)
        }
    }

    @Test
    fun `the signal is the median of the last packets`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            listOf(-70, -90, -72, -50, -71).forEach { advertisements.emit(advertisement(BIKE, "Bike", rssi = it)) }
            delay(1.seconds)
            assertEquals(-71, awaitItem().single().rssi)
        }
    }

    @Test
    fun `the period is the median gap between packets, a missed one not counting`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            // 300 ms apart, one packet missed between the third and the fourth
            listOf(0, 300, 600, 1200, 1500, 1800).forEach {
                advertisements.emit(advertisement(BIKE, "Bike", rssi = -84, timestamp = it.milliseconds))
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
                advertisements.emit(advertisement(BIKE, "Bike", rssi = -84, timestamp = it.milliseconds))
            }
            delay(1.seconds)
            assertNull(awaitItem().single().period)
        }
    }

    @Test
    fun `a device gone quiet stays, told as quiet`() = runTest {
        test().devices().test {
            assertEquals(emptyList(), awaitItem())
            advertisements.emit(advertisement(BIKE, "Bike", rssi = -84))
            delay(1.seconds)
            assertFalse(awaitItem().single().isQuiet)
            delay(30.seconds)
            assertTrue(awaitItem().single().isQuiet)
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
    fun `the page searches for 5 seconds before listing the loudest first`() = runTest {
        test().page(reloads).test {
            assertEquals(Page.Searching(5.seconds, found = 0), awaitItem())
            advertisements.emit(advertisement(TAG, "Tag", rssi = -80))
            advertisements.emit(advertisement(BIKE, "Bike", rssi = -60))
            // Between two steps of the countdown. Counted once the list is refreshed, within a second.
            delay(2.5.seconds)
            assertEquals(Page.Searching(3.seconds, found = 2), expectMostRecentItem())
            delay(2.seconds)
            assertEquals(Page.Searching(1.seconds, found = 2), expectMostRecentItem())
            assertEquals(
                Page.Listed(listOf(device(BIKE, "Bike", rssi = -60), device(TAG, "Tag", rssi = -80)), notShown = 0),
                awaitItem()
            )
        }
    }

    @Test
    fun `a device too weak to ever count as nearby is not listed`() = runTest {
        test().page(reloads).test {
            advertisements.emit(advertisement(BIKE, "Bike", rssi = -60))
            advertisements.emit(advertisement(TAG, "Tag", rssi = BeaconPresenceUseCase.MIN_RSSI - 1))
            assertEquals(listOf(BIKE), awaitListed().devices.map { it.address })
        }
    }

    @Test
    fun `the order stays while signals change, the new devices being counted`() = runTest {
        test().page(reloads).test {
            advertisements.emit(advertisement(BIKE, "Bike", rssi = -60))
            advertisements.emit(advertisement(TAG, "Tag", rssi = -80))
            assertEquals(listOf(BIKE, TAG), awaitListed().devices.map { it.address })
            // Louder than the bike now, and a new device
            repeat(11) { advertisements.emit(advertisement(TAG, "Tag", rssi = -40)) }
            advertisements.emit(advertisement(WATCH, "Watch", rssi = -50))
            delay(1.seconds)
            expectMostRecentItem().let { page ->
                assertIs<Page.Listed>(page)
                assertEquals(listOf(BIKE, TAG), page.devices.map { it.address })
                assertEquals(-40, page.devices.last().rssi)
                assertEquals(1, page.notShown)
            }
        }
    }

    @Test
    fun `reloading sorts again, adds the new devices and drops the weak and quiet ones`() = runTest {
        test().page(reloads).test {
            advertisements.emit(advertisement(BIKE, "Bike", rssi = -60))
            advertisements.emit(advertisement(TAG, "Tag", rssi = -80))
            assertEquals(listOf(BIKE, TAG), awaitListed().devices.map { it.address })
            delay(25.seconds)
            repeat(11) { advertisements.emit(advertisement(TAG, "Tag", rssi = -70)) }
            advertisements.emit(advertisement(WATCH, "Watch", rssi = -50))
            delay(10.seconds)
            expectMostRecentItem().let { page ->
                assertIs<Page.Listed>(page)
                // Still listed while quiet, until the next reload
                assertTrue(page.devices.first { it.address == BIKE }.isQuiet)
                assertEquals(1, page.notShown)
            }
            reloads.emit(Unit)
            assertEquals(Page.Listed(listOf(device(WATCH, "Watch", rssi = -50), device(TAG, "Tag", rssi = -70)), notShown = 0), awaitItem())
        }
    }

    @Test
    fun `a resolvable private address may change`() {
        assertTrue(device("4A:1B:2C:3D:4E:5F").mayChangeAddress)
        assertTrue(device("7F:1B:2C:3D:4E:5F").mayChangeAddress)
        // Static random, as the CFMOTOR beacons use
        assertFalse(device(BIKE).mayChangeAddress)
        assertFalse(device("C4:CD:82:63:55:15").mayChangeAddress)
        assertFalse(device("80:EA:CA:10:20:30").mayChangeAddress)
    }

    /** Lets the search end, skipping its countdown */
    private suspend fun app.cash.turbine.ReceiveTurbine<Page>.awaitListed(): Page.Listed {
        delay(5.seconds)
        while (true) {
            val page = awaitItem()
            if (page is Page.Listed) return page
        }
    }

    private fun device(address: String, name: String = "Device", rssi: Int = -60) =
        Device(address, name, rssi, period = null, isTyreSensor = false, isQuiet = false)

    private fun advertisement(
        address: String,
        name: String?,
        rssi: Int,
        timestamp: Duration = Duration.ZERO,
    ) = Advertisement(address, name, rssi, timestamp, isTyreSensor = false)

    private companion object {
        const val BIKE = "EE:64:A3:12:38:1A"
        const val TAG = "C4:CD:82:63:55:15"
        const val WATCH = "C4:CD:82:63:55:41"
    }
}
