package com.masselis.tpmsadvanced.feature.background.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.DOZE
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class ScanStatusAnnouncerTest {

    private lateinit var decision: MutableSharedFlow<ScanDecision>

    // The speaking side stays off: no text-to-speech in unit tests
    private fun TestScope.test() = ScanStatusAnnouncer(
        mockk<AppPreferences> {
            every { debugOptions } returns MutableStateFlow(false)
            every { announceScanStatus } returns MutableStateFlow(false)
        },
        mockk<ScanPolicyUseCase> {
            every { decision } returns this@ScanStatusAnnouncerTest.decision
        },
        backgroundScope,
    )

    @Before
    fun setup() {
        decision = MutableSharedFlow()
    }

    @Test
    fun `each status change is told, not the status when collecting`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Idle)
            decision.emit(ScanDecision.Active(setOf(CABLE)))
            assertEquals("TPMS active", awaitItem())
            decision.emit(ScanDecision.Suspended(setOf(DOZE)))
            assertEquals("TPMS suspended", awaitItem())
            decision.emit(ScanDecision.Idle)
            assertEquals("TPMS idle", awaitItem())
        }
    }

    @Test
    fun `a change of decision keeping the same status is not told again`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Idle)
            decision.emit(ScanDecision.Active(setOf(CABLE)))
            assertEquals("TPMS active", awaitItem())
            decision.emit(ScanDecision.Active(setOf(CABLE, WIRELESS)))
            decision.emit(ScanDecision.Idle)
            assertEquals("TPMS idle", awaitItem())
        }
    }

    @Test
    fun `turning persistent scanning off is told as off`() = runTest {
        test().statusChanges.test {
            decision.emit(ScanDecision.Active(setOf(CABLE)))
            decision.emit(ScanDecision.Active(setOf(MANUAL)))
            assertEquals("TPMS off", awaitItem())
        }
    }
}
