package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class NumberInputTest {

    private lateinit var range: ClosedFloatingPointRange<Float>
    private var step: Float = 0f

    private fun test() = NumberInput(range, step)

    @Before
    fun setup() {
        range = 0f..2.6f
        step = 0.1f
    }

    @Test
    fun `both decimal separators are accepted`() {
        val input = test()
        assertEquals(2.2f, input.accepted("2.2"))
        assertEquals(2.2f, input.accepted("2,2"))
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals(2.2f, test().accepted(" 2.2 "))
    }

    @Test
    fun `text which is not a number is refused`() {
        val input = test()
        assertNull(input.accepted(""))
        assertNull(input.accepted("abc"))
        assertNull(input.accepted("2.2.2"))
    }

    @Test
    fun `values outside the range are refused`() {
        val input = test()
        assertNull(input.accepted("2.7"))
        assertNull(input.accepted("-0.1"))
    }

    @Test
    fun `a bound typed as displayed is accepted and set to the exact bound`() {
        // e.g. a cold temperature's lower bound converted to fahrenheit, displayed without decimals
        range = 41.004f..113f
        step = 1f
        assertEquals(41.004f, test().accepted("41"))
    }

    @Test
    fun `a value on a step moves by a whole step`() {
        val input = test()
        assertEquals(2.3f, input.increased(2.2f), DELTA)
        assertEquals(2.1f, input.decreased(2.2f), DELTA)
    }

    @Test
    fun `a value between steps moves to the nearest step in that direction`() {
        val input = test()
        assertEquals(2.3f, input.increased(2.25f), DELTA)
        assertEquals(2.2f, input.decreased(2.25f), DELTA)
    }

    @Test
    fun `stepping stops at the bounds`() {
        range = 0f..2.55f
        val input = test()
        assertEquals(2.55f, input.increased(2.5f), DELTA)
        assertFalse(input.canIncrease(2.55f))
        assertFalse(input.canDecrease(0f))
        assertTrue(input.canDecrease(2.55f))
    }

    @Test
    fun `an out of range value typed can be brought back by stepping`() {
        val input = test()
        assertNull(input.accepted("5"))
        assertNotNull(input.parse("5"))
        assertTrue(input.canDecrease(5f))
        assertEquals(2.6f, input.decreased(5f), DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
