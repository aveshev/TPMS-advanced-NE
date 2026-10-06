package com.masselis.tpmsadvanced.feature.background.interfaces

import app.cash.turbine.test
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Failure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Active
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.BluetoothOff
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Idle
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.MonitoringFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanFailure
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.ScanRetrying
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Starting
import com.masselis.tpmsadvanced.feature.background.interfaces.ServiceNotifier.State.Suspended
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class ServiceStatesTest {

    private lateinit var decision: MutableStateFlow<ScanDecision>
    private lateinit var bluetoothOn: MutableStateFlow<Boolean>
    /** What the scan shows while it runs */
    private lateinit var active: MutableStateFlow<State>

    /** Ends the scan running at the time, like the scanner does */
    private lateinit var scanFailures: MutableSharedFlow<Throwable>

    /** How many times the scan was started */
    private var scans = 0

    context(scope: TestScope)
    private fun test() = decision.serviceStates(bluetoothOn, scope.testScheduler.timeSource) {
        flow {
            scans++
            emitAll(merge(active, scanFailures.map { throw it }))
        }
    }

    context(scope: TestScope)
    private fun elapsed() = scope.testScheduler.currentTime.milliseconds

    @Before
    fun setup() {
        // Logcat isn't there on the JVM, the failures are logged
        Logger.setLogWriters(CommonWriter())
        decision = MutableStateFlow(ScanDecision.Active(setOf(CABLE)))
        bluetoothOn = MutableStateFlow(true)
        active = MutableStateFlow(ACTIVE)
        scanFailures = MutableSharedFlow()
        scans = 0
    }

    @Test
    fun `the scan's states are shown while active and Bluetooth is on`() = runTest {
        test().test {
            assertEquals(Starting, awaitItem())
            assertEquals(ACTIVE, awaitItem())
            assertEquals(1, scans)
        }
    }

    @Test
    fun `suspended and idle don't scan`() = runTest {
        decision.value = ScanDecision.Suspended(setOf(WIFI))
        test().test {
            assertEquals(Starting, awaitItem())
            assertEquals(Suspended(ScanDecision.Suspended(setOf(WIFI))), awaitItem())
            decision.value = ScanDecision.Idle
            assertEquals(Idle, awaitItem())
            assertEquals(0, scans)
        }
    }

    @Test
    fun `active while Bluetooth is off tells it without scanning`() = runTest {
        bluetoothOn.value = false
        test().test {
            assertEquals(Starting, awaitItem())
            assertEquals(BluetoothOff, awaitItem())
            assertEquals(0, scans)
        }
    }

    @Test
    fun `scanning starts by itself once Bluetooth is turned on`() = runTest {
        bluetoothOn.value = false
        test().test {
            skipItems(2)
            bluetoothOn.value = true
            assertEquals(ACTIVE, awaitItem())
            assertEquals(1, scans)
        }
    }

    @Test
    fun `Bluetooth turned off while scanning is told, and scanning starts over once it's back`() = runTest {
        test().test {
            skipItems(2)
            bluetoothOn.value = false
            assertEquals(BluetoothOff, awaitItem())
            bluetoothOn.value = true
            assertEquals(ACTIVE, awaitItem())
            assertEquals(2, scans)
        }
    }

    @Test
    fun `a scan ended by Bluetooth turning off is told before the broadcast says so`() = runTest {
        test().test {
            skipItems(2)
            scanFailures.emit(Failure.BluetoothOff(STATE_OFF))
            assertEquals(BluetoothOff, awaitItem())
            // Then the broadcast arrives, nothing scans until it's turned on again
            bluetoothOn.value = false
            delay(10.minutes)
            expectNoEvents()
            assertEquals(1, scans)
            bluetoothOn.value = true
            assertEquals(ACTIVE, awaitItem())
        }
    }

    @Test
    fun `a refused scan is retried, waiting longer each time it fails in a row`() = runTest {
        test().test {
            skipItems(2)
            listOf(5, 10, 20, 40, 60, 60).forEach { seconds ->
                scanFailures.emit(Failure.Scan(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED))
                assertEquals(ScanRetrying(SCAN_FAILED_APPLICATION_REGISTRATION_FAILED), awaitItem())
                val failedAt = elapsed()
                assertEquals(ACTIVE, awaitItem())
                assertEquals(seconds.seconds, elapsed() - failedAt)
            }
            assertEquals(7, scans)
        }
    }

    @Test
    fun `a scan that ran for a while is retried after the shortest delay again`() = runTest {
        test().test {
            skipItems(2)
            repeat(3) {
                scanFailures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
                skipItems(2)
            }
            delay(2.minutes)
            scanFailures.emit(Failure.Scan(SCAN_FAILED_INTERNAL_ERROR))
            skipItems(1)
            val failedAt = elapsed()
            assertEquals(ACTIVE, awaitItem())
            assertEquals(5.seconds, elapsed() - failedAt)
        }
    }

    @Test
    fun `a phone that can't scan ends with a scan failure`() = runTest {
        test().test {
            skipItems(2)
            scanFailures.emit(Failure.Scan(SCAN_FAILED_FEATURE_UNSUPPORTED))
            assertEquals(ScanFailure(SCAN_FAILED_FEATURE_UNSUPPORTED), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a failure besides the scan ends monitoring, without blaming Bluetooth`() = runTest {
        test().test {
            skipItems(2)
            scanFailures.emit(IllegalStateException("Database closed"))
            assertEquals(MonitoringFailure, awaitItem())
            awaitComplete()
        }
    }

    private companion object {
        val ACTIVE = Active(ScanDecision.Active(setOf(CABLE)))

        // BluetoothAdapter and ScanCallback constants
        const val STATE_OFF = 10
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        const val SCAN_FAILED_INTERNAL_ERROR = 3
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
    }
}
