package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit.HOURS

internal class PressureLossTest {

    // Flat mark at 100 kPa, a drop counts from 11.25 kPa, a refill is a 15 kPa rise
    private val rule = PressureLoss.Rule(150f.kpa, 10.hours)

    /**
     * A reading [hour] hours in, at 20 °C unless told otherwise: the rates are computed at that
     * temperature
     */
    private fun reading(hour: Double, kpa: Float, celsius: Float = 20f, sensorId: Int = 1) =
        TyreAtmosphere(hour * SECONDS_PER_HOUR, sensorId, kpa.kpa, celsius.celsius)

    private fun track(vararg readings: TyreAtmosphere, rule: PressureLoss.Rule = this.rule) =
        readings.fold(PressureLoss.Tracker()) { tracker, reading -> tracker.next(reading, rule) }.loss

    @Test
    fun `a steady tyre loses nothing`() {
        assertNull(track(*(0..48).map { reading(it / 2.0, 230f) }.toTypedArray()))
    }

    @Test
    fun `an overnight loss is warned about from the first reading of the morning`() {
        val loss = track(reading(0.0, 230f), reading(0.5, 229f), reading(12.0, 150f))
        assertNotNull(loss)
        // Spread over the time parked, since the last reading before it
        assertEquals(0.5 * SECONDS_PER_HOUR, loss.since)
        assertEquals(79f / 11.5f, loss.perHour.kpa, 0.01f)
        assertEquals(50.0 / (79.0 / 11.5), loss.timeToFlat.toDouble(HOURS), 0.01)
        assertEquals(100f, loss.flatMark.kpa, 0.01f)
    }

    @Test
    fun `a loss too slow to flatten the tyre within the horizon is ignored`() {
        assertNull(track(reading(0.0, 230f), reading(12.0, 210f)))
    }

    @Test
    fun `a leak which just started is spread over the last hour only`() {
        val loss = track(
            reading(0.0, 230f),
            reading(24.0, 230f),
            reading(24.25, 228f),
            reading(24.5, 222f),
            reading(25.0, 205f),
            reading(25.25, 200f),
        )
        assertNotNull(loss)
        assertEquals(24.25 * SECONDS_PER_HOUR, loss.since)
        assertEquals(28f, loss.perHour.kpa, 0.01f)
    }

    @Test
    fun `a drop smaller than the noise is ignored`() {
        assertNull(track(reading(0.0, 230f), reading(0.1, 220f)))
    }

    @Test
    fun `the minimum drop follows the low pressure alert`() {
        // A low pressure vehicle: flat mark at 53 kPa, a drop counts from 7 kPa
        assertNotNull(track(reading(0.0, 100f), reading(0.5, 92f), rule = PressureLoss.Rule(80f.kpa, 10.hours)))
    }

    @Test
    fun `a drop from before the last hour isn't counted in it`() {
        // 230 kPa yesterday, 220 kPa an hour ago: only 8 kPa were lost within the hour
        assertNull(track(reading(0.0, 230f), reading(23.5, 220f), reading(24.4, 212f)))
    }

    @Test
    fun `a tyre cooling down once parked isn't losing pressure`() {
        // The same air, from 40 °C to 10 °C: (230 + 101.325) × 283.15 / 313.15 − 101.325
        assertNull(track(reading(0.0, 230f, 40f), reading(0.5, 214f, 25f), reading(3.0, 198.3f, 10f)))
    }

    @Test
    fun `the warning stays until the tyre is pumped up`() {
        val history = arrayOf(reading(0.0, 230f), reading(0.5, 229f), reading(12.0, 150f), reading(12.1, 150f))
        assertNotNull(track(*history))
        assertNull(track(*history, reading(12.2, 230f)))
    }

    @Test
    fun `pumping the tyre up starts afresh`() {
        val loss = track(
            reading(0.0, 230f),
            reading(12.0, 150f),
            reading(12.2, 230f),
            reading(13.2, 205f),
        )
        assertNotNull(loss)
        assertEquals(12.2 * SECONDS_PER_HOUR, loss.refilledAt)
        assertEquals(12.2 * SECONDS_PER_HOUR, loss.since)
    }

    @Test
    fun `another sensor starts afresh`() {
        assertNull(track(reading(0.0, 230f), reading(0.5, 229f), reading(12.0, 150f, sensorId = 2)))
    }

    @Test
    fun `an alarm reading no pressure is left to the low pressure alert`() {
        assertNull(track(reading(0.0, 230f), reading(1.0, 0f)))
    }

    @Test
    fun `an older reading is ignored`() {
        assertNull(track(reading(0.0, 230f), reading(1.0, 229f), reading(0.5, 150f)))
    }

    private companion object {
        const val SECONDS_PER_HOUR = 3600.0
    }
}
