package com.masselis.tpmsadvanced.feature.main.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlinx.parcelize.Parcelize

/**
 * Sensors moving along [locations]: each one's sensor goes to the next location. The first one's
 * sensor started the move, the others are where the user sent each sensor in turn.
 */
@Parcelize
internal data class MoveChain(val locations: List<Location>) : Parcelable {

    init {
        require(locations.isNotEmpty() && locations.distinct() == locations) { "Invalid chain: $locations" }
    }

    val start: Location get() = locations.first()

    /** Where the sensor that moved last was sent to, the one now asking for a location */
    val last: Location get() = locations.last()

    /**
     * The locations the last sensor can be sent to, among the vehicle's [all]: those not in the
     * chain yet, and the start once the chain goes through three locations or more, closing it.
     * Sent back to the start from the second location would be a swap.
     */
    fun options(all: Collection<Location>): List<Location> = all
        .filter { it !in locations || (it == start && locations.size >= CLOSING_SIZE) }

    fun canTap(location: Location, all: Collection<Location>): Boolean = location in options(all)

    fun plus(location: Location): MoveChain = MoveChain(locations + location)

    /**
     * The moves, each location's sensor to the next one. When the last location has a sensor too
     * ([occupied]), it goes to the start, which the first move left empty: a rotation.
     */
    fun moves(occupied: Set<Location>): List<Pair<Location, Location>> = locations
        .zipWithNext()
        .plus(listOfNotNull((last to start).takeIf { last in occupied && locations.size > 1 }))

    /**
     * What tapping [target] does, [occupied] being the vehicle's locations with a sensor among
     * [all] of them: the start or an empty location ends the move there. An occupied one asks,
     * for the first one, whether to just swap the sensors on a vehicle with more than two
     * locations, and continues the chain otherwise, see [continuing].
     */
    fun tap(target: Location, occupied: Set<Location>, all: Collection<Location>): Step {
        require(canTap(target, all)) { "$target can't be tapped in the chain $locations" }
        // Closed, the last sensor goes to the start, see moves
        if (target == start) return Step.Done(this)
        val chain = plus(target)
        return when {
            target !in occupied -> Step.Done(chain)
            all.size <= 2 -> Step.Done(chain)
            locations.size == 1 -> Step.AskSwapOrChain(chain)
            else -> chain.continuing(all)
        }
    }

    /**
     * Asks for the next location, or ends the move once there's a single one left to pick: the
     * start closes the chain, another location gets the last sensor, and its own goes to the
     * start if it has one
     */
    fun continuing(all: Collection<Location>): Step = options(all).let { options ->
        when {
            options.size > 1 -> Step.Continue(this)
            options.singleOrNull()?.let { it != start } == true -> Step.Done(plus(options.single()))
            else -> Step.Done(this)
        }
    }

    sealed interface Step {
        /** Ready to be applied, see [moves] */
        data class Done(val chain: MoveChain) : Step

        /** [chain]'s last location has a sensor: swap them, see [Done], or continue, see [continuing] */
        data class AskSwapOrChain(val chain: MoveChain) : Step

        /** Waiting for the location of [chain]'s last sensor */
        data class Continue(val chain: MoveChain) : Step
    }

    companion object {
        /** The chain's length from which its last sensor can go back to the start */
        private const val CLOSING_SIZE = 3

        fun from(start: Location): MoveChain = MoveChain(listOf(start))
    }
}
