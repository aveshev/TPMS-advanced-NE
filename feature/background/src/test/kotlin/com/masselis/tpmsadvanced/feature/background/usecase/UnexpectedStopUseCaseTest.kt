package com.masselis.tpmsadvanced.feature.background.usecase

import android.app.ApplicationExitInfo.REASON_ANR
import android.app.ApplicationExitInfo.REASON_CRASH
import android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
import android.app.ApplicationExitInfo.REASON_FREEZER
import android.app.ApplicationExitInfo.REASON_LOW_MEMORY
import android.app.ApplicationExitInfo.REASON_OTHER
import android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE
import android.app.ApplicationExitInfo.REASON_SIGNALED
import android.app.ApplicationExitInfo.REASON_UNKNOWN
import android.app.ApplicationExitInfo.REASON_USER_REQUESTED
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Crash
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.ForceStop
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.LowMemory
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.PermissionRevoked
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Killed
import com.masselis.tpmsadvanced.feature.background.usecase.UnexpectedStopUseCase.Cause.Unknown
import org.junit.Test
import kotlin.test.assertEquals

internal class UnexpectedStopUseCaseTest {

    @Test
    fun `a force stop is told apart from the system`() {
        assertEquals(ForceStop, Cause.of(REASON_USER_REQUESTED))
    }

    @Test
    fun `crashes and anrs are crashes`() {
        assertEquals(Crash, Cause.of(REASON_CRASH))
        assertEquals(Crash, Cause.of(REASON_ANR))
    }

    @Test
    fun `memory and permissions have their own cause`() {
        assertEquals(LowMemory, Cause.of(REASON_LOW_MEMORY))
        assertEquals(PermissionRevoked, Cause.of(REASON_PERMISSION_CHANGE))
    }

    @Test
    fun `other kills are the system's`() {
        assertEquals(Killed, Cause.of(REASON_SIGNALED))
        assertEquals(Killed, Cause.of(REASON_EXCESSIVE_RESOURCE_USAGE))
        assertEquals(Killed, Cause.of(REASON_FREEZER))
        assertEquals(Killed, Cause.of(REASON_OTHER))
    }

    @Test
    fun `an unknown reason stays unknown`() {
        assertEquals(Unknown, Cause.of(REASON_UNKNOWN))
    }
}
