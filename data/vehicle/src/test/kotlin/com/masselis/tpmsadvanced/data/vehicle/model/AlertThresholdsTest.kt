package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_ALARM
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_REMOVED
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.psi
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Suppress("MaxLineLength")
internal class AlertThresholdsTest {

    private val thresholds = AlertThresholds(200f.kpa, 300f.kpa, 90f.celsius, 2.6f.volts)

    @Test
    fun `pressure in range doesn't alert`() {
        assertNull(thresholds.pressureLevel(250f.kpa))
        assertNull(thresholds.pressureLevel(206.1f.kpa))
        assertNull(thresholds.pressureLevel(290.9f.kpa))
    }

    @Test
    fun `low pressure levels, a boundary being the more severe level`() {
        assertEquals(AMBER, thresholds.pressureLevel(206f.kpa))
        assertEquals(AMBER, thresholds.pressureLevel(200.1f.kpa))
        assertEquals(RED, thresholds.pressureLevel(200f.kpa))
        assertEquals(RED, thresholds.pressureLevel(150.1f.kpa))
        assertEquals(CRIMSON, thresholds.pressureLevel(150f.kpa))
        assertEquals(CRIMSON, thresholds.pressureLevel(0f.kpa))
    }

    @Test
    fun `high pressure levels, a boundary being the more severe level`() {
        assertEquals(AMBER, thresholds.pressureLevel(291f.kpa))
        assertEquals(RED, thresholds.pressureLevel(300f.kpa))
        assertEquals(RED, thresholds.pressureLevel(359.9f.kpa))
        assertEquals(CRIMSON, thresholds.pressureLevel(360f.kpa))
    }

    @Test
    fun `a threshold typed in another unit is still reached by the same pressure`() {
        assertEquals(RED, AlertThresholds(29f.psi, 50f.psi, 90f.celsius, 2.6f.volts).pressureLevel(29f.psi))
    }

    @Test
    fun `temperature levels, a boundary being the more severe level`() {
        assertNull(thresholds.temperatureLevel(79f.celsius))
        assertEquals(AMBER, thresholds.temperatureLevel(80f.celsius))
        assertEquals(RED, thresholds.temperatureLevel(90f.celsius))
        assertEquals(RED, thresholds.temperatureLevel(109f.celsius))
        assertEquals(CRIMSON, thresholds.temperatureLevel(110f.celsius))
    }

    @Test
    fun `battery levels, the alarm voltage being red`() {
        assertNull(thresholds.batteryLevel(2.8f.volts, 20f.celsius))
        assertEquals(AMBER, thresholds.batteryLevel(2.7f.volts, 20f.celsius))
        assertEquals(RED, thresholds.batteryLevel(2.6f.volts, 20f.celsius))
        assertEquals(RED, thresholds.batteryLevel(2.4f.volts, 20f.celsius))
    }

    @Test
    fun `battery levels follow the alarm down in the cold, no further than the brownout margin`() {
        // 2.6 V at 0°C holds as much charge as about 2.7 V at 20°C
        assertEquals(AMBER, thresholds.batteryLevel(2.6f.volts, 0f.celsius))
        assertEquals(RED, thresholds.batteryLevel(2.5f.volts, 0f.celsius))
        assertEquals(RED, thresholds.batteryLevel(2.5f.volts, (-25f).celsius))
        // Warm tyres don't move the alarm
        assertEquals(RED, thresholds.batteryLevel(2.6f.volts, 60f.celsius))
    }

    @Test
    fun `levels gathers every alerting class`() {
        assertEquals(
            mapOf(PRESSURE to RED, TEMPERATURE to AMBER, BATTERY to AMBER, PRESSURE_LOSS to AMBER, SENSOR_ALARM to AMBER),
            thresholds.levels(
                TyreAtmosphere(0.0, 1, 190f.kpa, 85f.celsius, 2.7f.volts, isSensorAlarm = true),
                isLeaking = true,
                isRemoved = false,
            ),
        )
    }

    @Test
    fun `a removed sensor's pressure isn't the tyre's`() {
        assertEquals(
            mapOf(SENSOR_REMOVED to AMBER),
            thresholds.levels(TyreAtmosphere(0.0, 1, 2f.kpa, 20f.celsius), isLeaking = false, isRemoved = true),
        )
    }
}
