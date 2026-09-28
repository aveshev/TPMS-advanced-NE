package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class VoltageTest {

    @Test
    fun `a voltage equal to the threshold is at or below it`() {
        assertTrue(2.6f.volts.isAtOrBelow(2.6f.volts))
        assertTrue(2.5f.volts.isAtOrBelow(2.6f.volts))
        assertFalse(2.7f.volts.isAtOrBelow(2.6f.volts))
    }

    @Test
    fun `tolerates the float rounding of 0,1 V steps`() {
        // 27 tenths as sent by a sensor, against a 2.6 V threshold raised by 0.1 V
        assertTrue(27.toFloat().div(10f).volts.isAtOrBelow(2.6f.volts + 0.1f.volts))
        assertFalse(2.8f.volts.isAtOrBelow(2.6f.volts + 0.1f.volts))
    }

    @Test
    fun `is displayed with one decimal`() {
        assertEquals("2.7 V", 2.68f.volts.string())
    }
}
