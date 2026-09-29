package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

internal class PressureLossTest {

    private val rule = PressureLoss.Rule(20f.kpa, 30.minutes)

    /** A reading [minute] minutes in, at 20 °C unless told otherwise */
    private fun reading(minute: Double, kpa: Float, celsius: Float = 20f) =
        TyreAtmosphere(minute * SECONDS_PER_MINUTE, 1, kpa.kpa, celsius.celsius)

    @Test
    fun `a steady tyre loses nothing`() {
        assertNull(rule.detect((0..10).map { reading(it.toDouble(), 250f) }))
    }

    @Test
    fun `a loss persisting over the latest readings is detected`() {
        val loss = rule.detect(
            listOf(
                reading(0.0, 250f),
                reading(5.0, 240f),
                reading(10.0, 228f),
                reading(11.0, 227f),
            )
        )
        assertNotNull(loss)
        // The latest readings' highest pressure is compared, 228 kPa against 250 kPa
        assertEquals(22f, loss.amount.kpa, 0.01f)
        assertEquals(0.0, loss.since)
        assertEquals(11.0 * SECONDS_PER_MINUTE, loss.until)
        assertEquals(11.minutes, loss.duration)
    }

    @Test
    fun `a loss smaller than the rule's amount is ignored`() {
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 250f),
                    reading(10.0, 235f),
                    reading(11.0, 234f),
                )
            )
        )
    }

    @Test
    fun `a single low reading is ignored`() {
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 250f),
                    reading(10.0, 250f),
                    reading(11.0, 200f),
                )
            )
        )
    }

    @Test
    fun `a burst of copies of a low reading is ignored`() {
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 250f),
                    reading(10.0, 250f),
                    reading(11.0, 200f),
                    reading(11.001, 200f),
                    reading(11.002, 200f),
                )
            )
        )
    }

    @Test
    fun `a loss slower than the window is ignored`() {
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 250f),
                    reading(20.0, 240f),
                    reading(40.0, 230f),
                    reading(41.0, 229f),
                )
            )
        )
    }

    @Test
    fun `a tyre cooling down once parked isn't losing pressure`() {
        // The same air, from 50 °C to 20 °C: (250 + 101.325) × 293.15 / 323.15 − 101.325
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 250f, 50f),
                    reading(15.0, 230f, 35f),
                    reading(29.0, 217.4f, 20f),
                    reading(30.0, 217.4f, 20f),
                )
            )
        )
    }

    @Test
    fun `a leak is detected while the tyre warms up`() {
        // Warming from 20 °C to 40 °C adds about 24 kPa, the leak takes 30 kPa away
        assertNotNull(
            rule.detect(
                listOf(
                    reading(0.0, 250f, 20f),
                    reading(10.0, 245f, 40f),
                    reading(11.0, 245f, 40f),
                )
            )
        )
    }

    @Test
    fun `a pumped up tyre isn't losing pressure`() {
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 200f),
                    reading(10.0, 250f),
                    reading(11.0, 250f),
                )
            )
        )
    }

    @Test
    fun `an alarm reading no pressure is left to the low pressure alert`() {
        assertNull(
            rule.detect(
                listOf(
                    reading(0.0, 250f),
                    reading(10.0, 0f),
                    reading(11.0, 0f),
                )
            )
        )
    }

    @Test
    fun `no reading, no loss`() {
        assertNull(rule.detect(emptyList()))
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
