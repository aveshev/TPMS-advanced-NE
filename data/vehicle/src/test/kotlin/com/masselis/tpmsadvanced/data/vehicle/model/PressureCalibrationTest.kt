package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import org.junit.Test
import kotlin.test.assertEquals

internal class PressureCalibrationTest {

    @Test
    fun `multiplies then adds the offset`() {
        assertEquals(
            230f,
            PressureCalibration(offset = 10f.kpa, multiplier = 1.1f).applyTo(200f.kpa).kpa,
            0.001f,
        )
    }

    @Test
    fun `negative offset lowers the pressure`() {
        assertEquals(190f, PressureCalibration((-10f).kpa, 1f).applyTo(200f.kpa).kpa, 0.001f)
    }

    @Test
    fun `leaves a missing pressure at zero`() {
        assertEquals(0f.kpa, PressureCalibration(10f.kpa, 1.1f).applyTo(0f.kpa))
    }

    @Test
    fun `never goes below zero`() {
        assertEquals(0f.kpa, PressureCalibration((-50f).kpa, 1f).applyTo(20f.kpa))
    }
}
