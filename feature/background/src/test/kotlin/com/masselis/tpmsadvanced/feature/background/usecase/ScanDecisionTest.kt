package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.JUST_SCAN
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.SMART
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.DOZE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.WIFI
import org.junit.Test
import kotlin.test.assertEquals

internal class ScanDecisionTest {

    @Test
    fun `nothing enabled is idle`() {
        assertEquals(ScanDecision.Idle, decide(emptySet(), setOf(CABLE), emptySet()))
    }

    @Test
    fun `enabled conditions that are not fulfilled are idle`() {
        assertEquals(ScanDecision.Idle, decide(setOf(CABLE, WIRELESS), emptySet(), emptySet()))
    }

    @Test
    fun `a fulfilled condition that is not enabled is ignored`() {
        assertEquals(ScanDecision.Idle, decide(setOf(WIRELESS), setOf(CABLE), emptySet()))
    }

    @Test
    fun `any enabled and fulfilled condition activates scanning`() {
        assertEquals(
            ScanDecision.Active(setOf(CABLE)),
            decide(setOf(CABLE, WIRELESS), setOf(CABLE), emptySet())
        )
    }

    @Test
    fun `every fulfilled condition is reported as a cause`() {
        assertEquals(
            ScanDecision.Active(setOf(CABLE, ANDROID_AUTO, SMART)),
            decide(
                setOf(CABLE, WIRELESS, ANDROID_AUTO, SMART),
                setOf(CABLE, ANDROID_AUTO, SMART),
                emptySet(),
            )
        )
    }

    @Test
    fun `a suspend condition wins over a fulfilled activate condition`() {
        assertEquals(
            ScanDecision.Suspended(setOf(WIFI)),
            decide(setOf(CABLE), setOf(CABLE), setOf(WIFI))
        )
    }

    @Test
    fun `every holding suspend condition is reported`() {
        assertEquals(
            ScanDecision.Suspended(setOf(DOZE, WIFI)),
            decide(setOf(CABLE), setOf(CABLE), setOf(DOZE, WIFI))
        )
    }

    @Test
    fun `a suspend condition without any activate condition is idle, not suspended`() {
        assertEquals(ScanDecision.Idle, decide(setOf(CABLE), emptySet(), setOf(WIFI)))
    }

    @Test
    fun `just scan activates scanning without any other condition`() {
        assertEquals(
            ScanDecision.Active(setOf(JUST_SCAN)),
            decide(setOf(JUST_SCAN, CABLE), emptySet(), emptySet())
        )
    }

    @Test
    fun `just scan makes the other conditions irrelevant`() {
        assertEquals(
            ScanDecision.Active(setOf(JUST_SCAN)),
            decide(setOf(JUST_SCAN, CABLE), setOf(CABLE), emptySet())
        )
    }

    @Test
    fun `just scan is still subject to suspend conditions`() {
        assertEquals(
            ScanDecision.Suspended(setOf(DOZE)),
            decide(setOf(JUST_SCAN), emptySet(), setOf(DOZE))
        )
    }

    @Test
    fun `explanations name what caused the decision`() {
        assertEquals(
            "Background scanning is active due to charging with a cable, Android Auto being connected",
            ScanDecision.Active(setOf(CABLE, ANDROID_AUTO)).explanation()
        )
        assertEquals(
            "Background scanning is active due to monitoring being started manually",
            ScanDecision.Active(setOf(MANUAL)).explanation()
        )
        assertEquals(
            "Background scanning is suspended due to the phone being idle (Doze), WiFi being connected",
            ScanDecision.Suspended(setOf(DOZE, WIFI)).explanation()
        )
        assertEquals(
            "Background scanning is idle: no activate condition is currently fulfilled",
            ScanDecision.Idle.explanation()
        )
    }
}
