package com.masselis.tpmsadvanced.feature.background.interfaces

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAlerts
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Action
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Companion.action
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Shown
import org.junit.Test
import kotlin.test.assertEquals

@Suppress("MaxLineLength")
internal class AlertNotifierTest {

    private val thresholds = AlertThresholds(200f.kpa, 300f.kpa, 90f.celsius, 2.6f.volts)

    private fun alerts(vararg kpa: Float, sensorId: Int = 1) = kpa
        .withIndex()
        .fold(TyreAlerts()) { alerts, (index, value) ->
            alerts.next(TyreAtmosphere(index * 60.0, sensorId, value.kpa, 30f.celsius), thresholds, null)
        }

    private val notSnoozed: (AlertLevel) -> Boolean = { false }

    private fun action(alerts: TyreAlerts, shown: Shown?, isHeld: Boolean = false, isSnoozed: (AlertLevel) -> Boolean = notSnoozed) =
        action(alerts, PRESSURE, shown, isHeld, isSnoozed)

    @Test
    fun `a new alert sounds`() {
        assertEquals(Action.Post(RED, sound = true), action(alerts(190f), null))
        assertEquals(Action.Post(AMBER, sound = true), action(alerts(205f), null))
    }

    @Test
    fun `a higher level sounds`() {
        assertEquals(Action.Post(CRIMSON, sound = true), action(alerts(90f), Shown(1, RED), isHeld = true))
    }

    @Test
    fun `red sounds again at each reading, restarting the hold`() {
        assertEquals(Action.Post(RED, sound = true), action(alerts(190f), Shown(1, RED), isHeld = true))
    }

    @Test
    fun `amber updates silently`() {
        assertEquals(Action.Post(AMBER, sound = false), action(alerts(205f), Shown(1, AMBER), isHeld = true))
    }

    @Test
    fun `a lower level waits for the hold to end`() {
        assertEquals(Action.Hold(RED), action(alerts(190f), Shown(1, CRIMSON), isHeld = true))
        assertEquals(Action.Hold(null), action(alerts(250f), Shown(1, RED), isHeld = true))
    }

    @Test
    fun `a lower level after the hold updates silently`() {
        assertEquals(Action.Post(RED, sound = false), action(alerts(190f), Shown(1, CRIMSON)))
    }

    @Test
    fun `a cleared class after the hold cancels its notification`() {
        assertEquals(Action.Cancel, action(alerts(250f), Shown(1, RED)))
        assertEquals(Action.None, action(alerts(250f), null))
    }

    @Test
    fun `another sensor's alert sounds`() {
        assertEquals(Action.Post(AMBER, sound = true), action(alerts(205f), Shown(2, AMBER), isHeld = true))
    }

    @Test
    fun `another sensor without alert clears the previous one's`() {
        assertEquals(Action.Cancel, action(alerts(250f), Shown(2, RED), isHeld = true))
    }

    @Test
    fun `a snoozed level doesn't post`() {
        assertEquals(Action.None, action(alerts(190f), null) { it <= RED })
    }

    @Test
    fun `a higher level than snoozed posts`() {
        assertEquals(Action.Post(CRIMSON, sound = true), action(alerts(90f), null) { it <= RED })
    }

    @Test
    fun `classes are apart`() {
        assertEquals(Action.None, action(alerts(190f), TEMPERATURE, null, false, notSnoozed))
    }
}
