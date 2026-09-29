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

    // Flat mark at 100 kPa, a refill is a 15 kPa rise
    private val rule = PressureLoss.Rule(150f.kpa, 10.hours)

    /**
     * A reading [hour] hours in, at 20 °C unless told otherwise: the rates are computed at that
     * temperature
     */
    private fun reading(hour: Double, kpa: Float, celsius: Float = 20f) =
        TyreAtmosphere(hour * SECONDS_PER_HOUR, 1, kpa.kpa, celsius.celsius)

    @Test
    fun `a steady tyre loses nothing`() {
        assertNull(rule.detect((0..24).map { reading(it.toDouble(), 230f) }))
    }

    @Test
    fun `an overnight loss is warned about from the first reading of the morning`() {
        val loss = rule.detect(
            listOf(
                reading(0.0, 230f),
                // Riding, then parked hot
                reading(0.5, 250f, 30f),
                reading(1.0, 252f, 32f),
                reading(12.0, 150f),
            )
        )
        assertNotNull(loss)
        // 80 kPa in 12 h, then 50 kPa left to the flat mark
        assertEquals(80f / 12f, loss.perHour.kpa, 0.01f)
        assertEquals(0.0, loss.since)
        assertEquals(7.5, loss.timeToFlat.toDouble(HOURS), 0.01)
        assertEquals(100f, loss.flatMark.kpa, 0.01f)
    }

    @Test
    fun `a loss too slow to flatten the tyre within the horizon is ignored`() {
        assertNull(rule.detect(listOf(reading(0.0, 230f), reading(12.0, 210f))))
    }

    @Test
    fun `a leak which just started isn't diluted by the days before`() {
        val loss = rule.detect(
            listOf(
                reading(0.0, 230f),
                reading(24.0, 230f),
                reading(25.0, 205f),
            )
        )
        assertNotNull(loss)
        assertEquals(24.0 * SECONDS_PER_HOUR, loss.since)
        assertEquals(25f, loss.perHour.kpa, 0.01f)
    }

    @Test
    fun `a drop smaller than the sensors and the weather can explain is ignored`() {
        assertNull(rule.detect(listOf(reading(0.0, 230f), reading(0.1, 222f))))
    }

    @Test
    fun `a hot reading isn't used as the reference`() {
        // The sensor's temperature lags behind the air while warming up: this reads too high once
        // compensated, and would look like a quick loss as the tyre cools down
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 230f, 10f),
                    reading(1.0, 262f, 25f),
                    reading(1.5, 230f, 10f),
                )
            )
        )
    }

    @Test
    fun `a tyre cooling down once parked isn't losing pressure`() {
        // The same air, from 40 °C to 10 °C: (230 + 101.325) × 283.15 / 313.15 − 101.325
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 230f, 40f),
                    reading(0.5, 214f, 25f),
                    reading(3.0, 198.3f, 10f),
                )
            )
        )
    }

    @Test
    fun `pumping the tyre up starts afresh`() {
        val history = listOf(
            reading(0.0, 230f),
            reading(12.0, 150f),
            reading(12.2, 230f),
        )
        assertNull(rule.detect(history))
        val loss = rule.detect(history + reading(13.2, 205f))
        assertNotNull(loss)
        assertEquals(12.2 * SECONDS_PER_HOUR, loss.refilledAt)
        assertEquals(12.2 * SECONDS_PER_HOUR, loss.since)
    }

    @Test
    fun `an alarm reading no pressure is left to the low pressure alert`() {
        assertNull(rule.detect(listOf(reading(0.0, 230f), reading(1.0, 0f))))
    }

    @Test
    fun `no reading, no loss`() {
        assertNull(rule.detect(emptyList()))
    }

    private companion object {
        const val SECONDS_PER_HOUR = 3600.0
    }
}
