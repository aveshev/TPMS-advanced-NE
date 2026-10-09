package com.masselis.tpmsadvanced.feature.main.model

import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.model.MoveChain.Step
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class MoveChainTest {

    private val fl = Location.Wheel(FRONT_LEFT)
    private val fr = Location.Wheel(FRONT_RIGHT)
    private val rl = Location.Wheel(REAR_LEFT)
    private val rr = Location.Wheel(REAR_RIGHT)
    private val car = listOf(fl, fr, rl, rr)

    private val tadpoleRear = Location.Axle(REAR)
    private val tadpole = listOf(fl, fr, tadpoleRear)

    private val motorcycle = listOf(Location.Axle(FRONT), Location.Axle(REAR))

    @Test
    fun `an empty location gets the sensor and ends the move`() {
        val step = MoveChain.from(fl).tap(rr, occupied = setOf(fl), all = car)
        assertEquals(listOf(fl to rr), assertIs<Step.Done>(step).chain.moves(setOf(fl)))
    }

    @Test
    fun `the first occupied location asks whether to just swap`() {
        val occupied = setOf(fl, fr)
        val step = assertIs<Step.AskSwapOrChain>(MoveChain.from(fl).tap(fr, occupied, car))
        assertEquals(listOf(fl to fr, fr to fl), step.chain.moves(occupied))
    }

    @Test
    fun `a two locations vehicle swaps without asking`() {
        val (front, rear) = motorcycle
        val step = MoveChain.from(front).tap(rear, occupied = setOf(front, rear), all = motorcycle)
        assertEquals(listOf(front to rear, rear to front), assertIs<Step.Done>(step).chain.moves(setOf(front, rear)))
    }

    @Test
    fun `a three-wheeler's chain picks the last location by itself`() {
        val occupied = setOf(fl, fr, tadpoleRear)
        val asked = assertIs<Step.AskSwapOrChain>(MoveChain.from(fl).tap(fr, occupied, tadpole))
        val done = assertIs<Step.Done>(asked.chain.continuing(tadpole))
        assertEquals(listOf(fl to fr, fr to tadpoleRear, tadpoleRear to fl), done.chain.moves(occupied))
    }

    @Test
    fun `a three-wheeler's chain ends on an empty last location`() {
        val occupied = setOf(fl, fr)
        val asked = assertIs<Step.AskSwapOrChain>(MoveChain.from(fl).tap(fr, occupied, tadpole))
        val done = assertIs<Step.Done>(asked.chain.continuing(tadpole))
        assertEquals(listOf(fl to fr, fr to tadpoleRear), done.chain.moves(occupied))
    }

    @Test
    fun `a car's full rotation sends the last sensor to the start`() {
        val occupied = car.toSet()
        val asked = assertIs<Step.AskSwapOrChain>(MoveChain.from(fl).tap(fr, occupied, car))
        val waiting = assertIs<Step.Continue>(asked.chain.continuing(car))
        val done = assertIs<Step.Done>(waiting.chain.tap(rr, occupied, car))
        assertEquals(listOf(fl to fr, fr to rr, rr to rl, rl to fl), done.chain.moves(occupied))
    }

    @Test
    fun `a car's chain ends on the first empty location tapped`() {
        val occupied = setOf(fl, fr, rr)
        val asked = assertIs<Step.AskSwapOrChain>(MoveChain.from(fl).tap(fr, occupied, car))
        val waiting = assertIs<Step.Continue>(asked.chain.continuing(car))
        val done = assertIs<Step.Done>(waiting.chain.tap(rl, occupied, car))
        assertEquals(listOf(fl to fr, fr to rl), done.chain.moves(occupied))
    }

    @Test
    fun `every location moved from has a sensor and no sensor is lost`() {
        val occupied = car.toSet()
        val moves = MoveChain(listOf(fl, rl, rr, fr)).moves(occupied)
        assertEquals(occupied, moves.map { it.first }.toSet())
        assertEquals(occupied, moves.map { it.second }.toSet())
    }
}
