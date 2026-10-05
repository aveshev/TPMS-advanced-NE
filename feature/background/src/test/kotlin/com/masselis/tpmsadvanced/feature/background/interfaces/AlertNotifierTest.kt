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

    @Test
    fun `a new alert sounds`() {
        assertEquals(Action.Post(RED, sound = true), action(alerts(190f), PRESSURE, null, notSnoozed))
    }

    @Test
    fun `an unconfirmed amber doesn't post`() {
        assertEquals(Action.None, action(alerts(205f), PRESSURE, null, notSnoozed))
    }

    @Test
    fun `a higher level sounds`() {
        assertEquals(Action.Post(CRIMSON, sound = true), action(alerts(90f), PRESSURE, Shown(1, RED), notSnoozed))
    }

    @Test
    fun `red sounds again at each reading`() {
        assertEquals(Action.Post(RED, sound = true), action(alerts(190f), PRESSURE, Shown(1, RED), notSnoozed))
    }

    @Test
    fun `amber updates silently`() {
        assertEquals(Action.Post(AMBER, sound = false), action(alerts(205f, 205f, 205f), PRESSURE, Shown(1, AMBER), notSnoozed))
    }

    @Test
    fun `a lower level updates silently`() {
        assertEquals(Action.Post(RED, sound = false), action(alerts(190f), PRESSURE, Shown(1, CRIMSON), notSnoozed))
    }

    @Test
    fun `another sensor's alert sounds`() {
        assertEquals(Action.Post(AMBER, sound = true), action(alerts(205f, 205f, 205f), PRESSURE, Shown(2, AMBER), notSnoozed))
    }

    @Test
    fun `a cleared class cancels its notification`() {
        assertEquals(Action.Cancel, action(alerts(250f), PRESSURE, Shown(1, RED), notSnoozed))
        assertEquals(Action.None, action(alerts(250f), PRESSURE, null, notSnoozed))
    }

    @Test
    fun `an unconfirmed level leaves the notification as it is`() {
        assertEquals(Action.None, action(alerts(250f, 205f), PRESSURE, Shown(1, AMBER), notSnoozed))
    }

    @Test
    fun `a snoozed level doesn't post`() {
        assertEquals(Action.None, action(alerts(190f), PRESSURE, null) { it <= RED })
    }

    @Test
    fun `a higher level than snoozed posts`() {
        assertEquals(Action.Post(CRIMSON, sound = true), action(alerts(90f), PRESSURE, null) { it <= RED })
    }

    @Test
    fun `classes are apart`() {
        assertEquals(Action.None, action(alerts(190f), TEMPERATURE, null, notSnoozed))
    }
}
