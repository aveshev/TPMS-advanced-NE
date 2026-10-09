package com.masselis.tpmsadvanced.feature.unlocated.model

import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.Found
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.PutBack
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.PutOn
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.Readings
import com.masselis.tpmsadvanced.feature.unlocated.model.AssignStep.TakeOff
import org.junit.Test
import kotlin.test.assertEquals

internal class AssignStepTest {

    /** [step] after each reading, sensor ID to kPa, in order */
    private fun after(step: AssignStep, vararg readings: Pair<Int, Float>, before: Readings = Readings()) =
        readings
            .fold(step to before) { (step, heard), (id, kpa) -> step.after(id, kpa, heard) to heard + (id to kpa) }
            .first

    @Test
    fun `a sensor first heard above 10 kPa is on a wheel`() {
        assertEquals(PutOn(found = setOf(1)), after(PutOn(), 1 to 230f))
    }

    @Test
    fun `a sensor first heard below 10 kPa is kept for a low pressure tyre`() {
        assertEquals(PutOn(low = setOf(1)), after(PutOn(), 1 to 2f))
    }

    @Test
    fun `a sensor heard in the hand then put on a wheel is found`() {
        assertEquals(PutOn(found = setOf(1), low = setOf(1)), after(PutOn(), 1 to 0f, 1 to 210f))
    }

    @Test
    fun `a sensor rising by less than 15 kPa isn't on a wheel`() {
        assertEquals(PutOn(low = setOf(1)), after(PutOn(), 1 to 0f, 1 to 9f))
    }

    @Test
    fun `a sensor already found is found once`() {
        assertEquals(PutOn(found = setOf(1)), after(PutOn(), 1 to 230f, 1 to 231f))
    }

    @Test
    fun `taking off keeps the candidates dropping by 15 kPa`() {
        assertEquals(
            TakeOff(setOf(1, 2), off = setOf(1)),
            after(TakeOff(setOf(1, 2)), 1 to 230f, 2 to 230f, 1 to 0f, 2 to 220f),
        )
    }

    @Test
    fun `taking off ignores sensors that aren't candidates`() {
        assertEquals(TakeOff(setOf(1)), after(TakeOff(setOf(1)), 2 to 230f, 2 to 0f))
    }

    @Test
    fun `taking off any sensor counts one first heard below 10 kPa, taken off before it was heard on`() {
        assertEquals(TakeOff(null, off = setOf(3)), after(TakeOff(null), 3 to 0f))
    }

    @Test
    fun `a sensor first heard on a wheel isn't taken off`() {
        assertEquals(TakeOff(null), after(TakeOff(null), 3 to 230f))
    }

    @Test
    fun `putting back finds the candidate rising by 15 kPa`() {
        assertEquals(Found(2), after(PutBack(setOf(1, 2)), 2 to 230f, before = Readings() + (1 to 0f) + (2 to 0f)))
    }

    @Test
    fun `putting back ignores sensors that aren't candidates`() {
        assertEquals(PutBack(setOf(1)), after(PutBack(setOf(1)), 2 to 230f, before = Readings() + (2 to 0f)))
    }

    @Test
    fun `the whole flow finds the sensor taken off and put back, not its neighbour`() {
        // A neighbour's sensor (2) is heard on its wheel along with the user's (1)
        val putOn = after(PutOn(), 1 to 0f, 2 to 240f, 1 to 230f)
        assertEquals(setOf(1, 2), (putOn as PutOn).found)
        val heard = Readings() + (1 to 230f) + (2 to 240f)
        val takeOff = after(putOn.next(), 1 to 0f, 2 to 241f, before = heard)
        assertEquals(setOf(1), (takeOff as TakeOff).off)
        assertEquals(Found(1), after(takeOff.next(), 1 to 229f, before = heard + (1 to 0f)))
    }
}
