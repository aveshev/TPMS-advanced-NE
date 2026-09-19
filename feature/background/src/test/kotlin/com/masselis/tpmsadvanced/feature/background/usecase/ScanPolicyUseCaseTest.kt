package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ChargingStateUseCase.State
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.JUST_SCAN
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class ScanPolicyUseCaseTest {

    private lateinit var persistentScanning: MutableStateFlow<Boolean>
    private lateinit var justScan: MutableStateFlow<Boolean>
    private lateinit var activateOnCableCharging: MutableStateFlow<Boolean>
    private lateinit var activateOnWirelessCharging: MutableStateFlow<Boolean>
    private lateinit var activateOnAndroidAuto: MutableStateFlow<Boolean>
    private lateinit var suspensionReasons: MutableStateFlow<Set<Reason>>
    private lateinit var charging: MutableStateFlow<State>
    private lateinit var androidAuto: MutableStateFlow<Boolean>

    context(scope: TestScope)
    private fun test() = ScanPolicyUseCase(
        mockk<AppPreferences> {
            every { persistentScanning } returns this@ScanPolicyUseCaseTest.persistentScanning
            every { justScan } returns this@ScanPolicyUseCaseTest.justScan
            every { activateOnCableCharging } returns this@ScanPolicyUseCaseTest.activateOnCableCharging
            every { activateOnWirelessCharging } returns this@ScanPolicyUseCaseTest.activateOnWirelessCharging
            every { activateOnAndroidAuto } returns this@ScanPolicyUseCaseTest.activateOnAndroidAuto
        },
        mockk<ScanSuspensionUseCase> {
            every { this@mockk.suspensionReasons } returns this@ScanPolicyUseCaseTest.suspensionReasons
        },
        mockk<ChargingStateUseCase> { every { state } returns charging },
        mockk<AndroidAutoUseCase> { every { connected } returns androidAuto },
        scope.backgroundScope,
    )

    @Before
    fun setup() {
        persistentScanning = MutableStateFlow(true)
        justScan = MutableStateFlow(false)
        activateOnCableCharging = MutableStateFlow(true)
        activateOnWirelessCharging = MutableStateFlow(true)
        activateOnAndroidAuto = MutableStateFlow(true)
        suspensionReasons = MutableStateFlow(emptySet())
        charging = MutableStateFlow(State(cable = false, wireless = false))
        androidAuto = MutableStateFlow(false)
    }

    @Test
    fun `without persistent scanning the service scans as long as it runs`() = runTest {
        persistentScanning.value = false
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(MANUAL)), awaitItem())
        }
    }

    @Test
    fun `nothing to activate scanning is idle`() = runTest {
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `plugging a cable activates scanning and unplugging goes back to idle`() = runTest {
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            charging.value = State(cable = true, wireless = false)
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            charging.value = State(cable = false, wireless = false)
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `wireless charging activates scanning`() = runTest {
        charging.value = State(cable = false, wireless = true)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(WIRELESS)), awaitItem())
        }
    }

    @Test
    fun `a disabled charging condition is ignored`() = runTest {
        activateOnCableCharging.value = false
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a suspend condition suspends an activated scan and releases it afterwards`() = runTest {
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
            suspensionReasons.value = setOf(Reason.WIFI)
            assertEquals(ScanDecision.Suspended(setOf(Reason.WIFI)), awaitItem())
            suspensionReasons.value = emptySet()
            assertEquals(ScanDecision.Active(setOf(CABLE)), awaitItem())
        }
    }

    @Test
    fun `just scan activates scanning without charging`() = runTest {
        justScan.value = true
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(JUST_SCAN)), awaitItem())
        }
    }

    @Test
    fun `connecting Android Auto activates scanning and disconnecting goes back to idle`() = runTest {
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
            androidAuto.value = true
            assertEquals(ScanDecision.Active(setOf(ANDROID_AUTO)), awaitItem())
            androidAuto.value = false
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `a disabled Android Auto condition is ignored`() = runTest {
        activateOnAndroidAuto.value = false
        androidAuto.value = true
        test().decision.test {
            assertEquals(ScanDecision.Idle, awaitItem())
        }
    }

    @Test
    fun `Android Auto and a cable are both reported as causes`() = runTest {
        androidAuto.value = true
        charging.value = State(cable = true, wireless = false)
        test().decision.test {
            assertEquals(ScanDecision.Active(setOf(CABLE, ANDROID_AUTO)), awaitItem())
        }
    }

    @Test
    fun `Android Auto is suspended by a suspend condition like any other`() = runTest {
        androidAuto.value = true
        suspensionReasons.value = setOf(Reason.DOZE)
        test().decision.test {
            assertEquals(ScanDecision.Suspended(setOf(Reason.DOZE)), awaitItem())
        }
    }
}
