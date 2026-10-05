package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_REMOVED
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TyreAlertsTest {

    private val thresholds = AlertThresholds(200f.kpa, 300f.kpa, 90f.celsius, 2.6f.volts)

    private val loss = PressureLoss(10f.kpa, 7f.kpa, 0.0, 60.0)

    /** A reading [minute] minutes in */
    private fun reading(minute: Double, kpa: Float, volts: Float? = null, sensorId: Int = 1) =
        TyreAtmosphere(minute * SECONDS_PER_MINUTE, sensorId, kpa.kpa, 30f.celsius, volts?.volts)

    private fun track(vararg readings: TyreAtmosphere, loss: PressureLoss? = null) =
        readings.fold(TyreAlerts()) { alerts, reading -> alerts.next(reading, thresholds, loss) }

    @Test
    fun `red notifies from the first reading`() {
        track(reading(0.0, 190f)).also {
            assertEquals(mapOf(PRESSURE to RED), it.levels)
            assertEquals(mapOf(PRESSURE to RED), it.notifiable)
        }
    }

    @Test
    fun `amber shows at once but notifies from the third reading in a row`() {
        track(reading(0.0, 205f), reading(1.0, 204f)).also {
            assertEquals(mapOf(PRESSURE to AMBER), it.levels)
            assertEquals(emptyMap(), it.notifiable)
        }
        assertEquals(mapOf(PRESSURE to AMBER), track(reading(0.0, 205f), reading(1.0, 204f), reading(2.0, 205f)).notifiable)
    }

    @Test
    fun `a reading back in range starts the amber count over`() {
        assertEquals(
            emptyMap(),
            track(reading(0.0, 205f), reading(1.0, 204f), reading(2.0, 210f), reading(3.0, 205f), reading(4.0, 204f)).notifiable,
        )
    }

    @Test
    fun `red readings count towards amber`() {
        assertEquals(
            mapOf(PRESSURE to AMBER),
            track(reading(0.0, 190f), reading(1.0, 195f), reading(2.0, 205f)).notifiable,
        )
    }

    @Test
    fun `a red battery notifies from the fifth reading in a row, amber meanwhile`() {
        assertEquals(emptyMap(), track(reading(0.0, 250f, 2.6f), reading(1.0, 250f, 2.6f)).notifiable)
        assertEquals(
            mapOf(BATTERY to AMBER),
            track(*(0..3).map { reading(it.toDouble(), 250f, 2.6f) }.toTypedArray()).notifiable,
        )
        assertEquals(
            mapOf(BATTERY to RED),
            track(*(0..4).map { reading(it.toDouble(), 250f, 2.6f) }.toTypedArray()).notifiable,
        )
    }

    @Test
    fun `a sensor taken off the valve is removed, not crimson`() {
        track(reading(0.0, 195f), reading(2.0, 1f)).also {
            assertTrue(it.isRemoved)
            assertEquals(mapOf(SENSOR_REMOVED to AMBER), it.levels)
            assertEquals(mapOf(SENSOR_REMOVED to AMBER), it.notifiable)
        }
    }

    @Test
    fun `a removed sensor stays removed while it reads the open air`() {
        assertTrue(track(reading(0.0, 230f), reading(2.0, 1f), reading(60.0, 3f)).isRemoved)
    }

    @Test
    fun `a removed sensor put back reads the tyre again`() {
        track(reading(0.0, 230f), reading(2.0, 1f), reading(4.0, 190f)).also {
            assertFalse(it.isRemoved)
            assertEquals(mapOf(PRESSURE to RED), it.notifiable)
        }
    }

    @Test
    fun `a tyre going flat through crimson readings is crimson`() {
        track(reading(0.0, 230f), reading(1.0, 150f), reading(2.0, 90f), reading(3.0, 1f)).also {
            assertFalse(it.isRemoved)
            assertEquals(mapOf(PRESSURE to CRIMSON), it.notifiable)
        }
    }

    @Test
    fun `a tyre found flat long after its previous reading is crimson`() {
        track(reading(0.0, 230f), reading(12 * 60.0, 1f)).also {
            assertFalse(it.isRemoved)
            assertEquals(mapOf(PRESSURE to CRIMSON), it.notifiable)
        }
    }

    @Test
    fun `a flat tyre without any previous reading is crimson`() {
        assertEquals(mapOf(PRESSURE to CRIMSON), track(reading(0.0, 1f)).notifiable)
    }

    @Test
    fun `the same reading again is re-evaluated without counting`() {
        val first = track(reading(0.0, 205f), reading(1.0, 204f))
        first.next(reading(1.0, 204f), thresholds, loss).also {
            assertEquals(mapOf(PRESSURE to AMBER, PRESSURE_LOSS to AMBER), it.levels)
            assertEquals(emptyMap(), it.notifiable)
        }
        assertEquals(
            emptyMap(),
            first.next(reading(1.0, 204f), thresholds, null).next(reading(1.0, 204f), thresholds, null).notifiable,
        )
    }

    @Test
    fun `an older reading is ignored`() {
        assertEquals(mapOf(PRESSURE to RED), track(reading(1.0, 190f), reading(0.0, 250f)).levels)
    }

    @Test
    fun `another sensor starts over`() {
        assertEquals(
            emptyMap(),
            track(reading(0.0, 205f), reading(1.0, 204f), reading(2.0, 205f, sensorId = 2)).notifiable,
        )
    }

    @Test
    fun `a leak is amber`() {
        assertEquals(
            mapOf(PRESSURE_LOSS to AMBER),
            track(reading(0.0, 250f), reading(1.0, 249f), reading(2.0, 248f), loss = loss).notifiable,
        )
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
