package com.masselis.tpmsadvanced.feature.background.usecase

import android.os.BatteryManager.BATTERY_PLUGGED_AC
import android.os.BatteryManager.BATTERY_PLUGGED_DOCK
import android.os.BatteryManager.BATTERY_PLUGGED_USB
import android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ChargingStateUseCase.State
import org.junit.Test
import kotlin.test.assertEquals

internal class ChargingStateUseCaseTest {

    @Test
    fun `unplugged is neither cable nor wireless`() {
        assertEquals(State(cable = false, wireless = false), State.fromPlugged(0))
    }

    @Test
    fun `ac and usb are a cable`() {
        assertEquals(State(cable = true, wireless = false), State.fromPlugged(BATTERY_PLUGGED_AC))
        assertEquals(State(cable = true, wireless = false), State.fromPlugged(BATTERY_PLUGGED_USB))
    }

    @Test
    fun `a dock is a cable`() {
        assertEquals(State(cable = true, wireless = false), State.fromPlugged(BATTERY_PLUGGED_DOCK))
    }

    @Test
    fun `wireless is not a cable`() {
        assertEquals(State(cable = false, wireless = true), State.fromPlugged(BATTERY_PLUGGED_WIRELESS))
    }

    @Test
    fun `a phone can be reported as plugged through both`() {
        assertEquals(
            State(cable = true, wireless = true),
            State.fromPlugged(BATTERY_PLUGGED_USB or BATTERY_PLUGGED_WIRELESS)
        )
    }
}
