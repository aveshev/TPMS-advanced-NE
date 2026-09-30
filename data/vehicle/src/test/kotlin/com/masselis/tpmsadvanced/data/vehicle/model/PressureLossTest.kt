package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class PressureLossTest {

    // A warning from a 7 kPa drop
    private val rule = PressureLoss.Rule()

    /** A reading [minute] minutes in, at 30 °C unless told otherwise */
    private fun reading(minute: Double, kpa: Float, celsius: Float = 30f, sensorId: Int = 1) =
        TyreAtmosphere(minute * SECONDS_PER_MINUTE, sensorId, kpa.kpa, celsius.celsius)

    private fun tracker(vararg readings: TyreAtmosphere, rule: PressureLoss.Rule = this.rule) =
        readings.fold(PressureLoss.Tracker()) { tracker, reading -> tracker.next(reading, rule) }

    private fun track(vararg readings: TyreAtmosphere, rule: PressureLoss.Rule = this.rule) =
        tracker(*readings, rule = rule).loss

    @Test
    fun `a tyre warming up loses nothing`() {
        assertNull(track(*(0..20).map { reading(it.toDouble(), 200f + it, 30f + it / 2f) }.toTypedArray()))
    }

    @Test
    fun `a puncture while riding is warned about`() {
        val loss = track(
            reading(0.0, 200f),
            reading(5.0, 210f, 35f),
            reading(10.0, 214f, 38f),
            reading(12.0, 210f, 38f),
            reading(14.0, 206f, 38f),
            reading(16.0, 202f, 38f),
        )
        assertNotNull(loss)
        // From the peak, 12 kPa in 6 minutes
        assertEquals(10.0 * SECONDS_PER_MINUTE, loss.since)
        assertEquals(12f, loss.drop.kpa, 0.01f)
        assertEquals(120f, loss.perHour.kpa, 0.01f)
    }

    @Test
    fun `a single reading under the minimum drop isn't enough`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 206f)))
    }

    @Test
    fun `a flickering reading isn't a loss`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 206f), reading(3.0, 210f), reading(4.0, 206f)))
    }

    @Test
    fun `a drop smaller than the minimum isn't a loss`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 210f), reading(4.0, 210f)))
    }

    @Test
    fun `the minimum drop is a setting`() {
        val readings = arrayOf(reading(0.0, 214f), reading(2.0, 210f), reading(4.0, 210f))
        assertNotNull(track(*readings, rule = PressureLoss.Rule(4f.kpa)))
    }

    @Test
    fun `a tyre cooling down at a stop isn't a loss`() {
        assertNull(track(reading(0.0, 214f, 36f), reading(2.0, 206f, 33f), reading(4.0, 202f, 29f)))
    }

    @Test
    fun `the peak is the latest reading at the highest pressure`() {
        val loss = track(reading(0.0, 214f), reading(6.0, 214f), reading(8.0, 206f), reading(10.0, 206f))
        assertEquals(6.0 * SECONDS_PER_MINUTE, loss?.since)
    }

    @Test
    fun `a loss stays until the pressure is back to its highest`() {
        val lossy = arrayOf(reading(0.0, 214f), reading(2.0, 206f), reading(4.0, 206f), reading(5.0, 210f))
        assertNotNull(track(*lossy))
        assertNull(track(*lossy, reading(6.0, 214f)))
    }

    @Test
    fun `a new ride starts over`() {
        val lossy = arrayOf(reading(0.0, 214f), reading(2.0, 206f), reading(4.0, 206f))
        assertNull(track(*lossy, reading(14.0, 190f)))
    }

    @Test
    fun `the rides are split by a gap of ten minutes`() {
        // The pressure dropped while parked, 20 kPa between the rides
        assertNull(track(reading(0.0, 214f), reading(9.0 * 60, 194f), reading(9.0 * 60 + 1, 194f)))
    }

    @Test
    fun `a sensor off the valve starts over`() {
        assertNull(track(reading(0.0, 214f), reading(1.0, 0.7f), reading(2.0, 200f), reading(3.0, 200f)))
    }

    @Test
    fun `another sensor starts over`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 200f, sensorId = 2), reading(3.0, 200f, sensorId = 2)))
    }

    @Test
    fun `older readings are ignored`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 214f), reading(1.0, 200f), reading(1.5, 200f)))
    }

    @Test
    fun `every reading is measured`() {
        assertEquals(0f, tracker(reading(0.0, 214f)).measured?.perHour?.kpa)
        val measured = assertNotNull(tracker(reading(0.0, 214f), reading(6.0, 211f)).measured)
        assertFalse(measured.isWarning)
        assertEquals(30f, measured.perHour.kpa, 0.01f)
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
