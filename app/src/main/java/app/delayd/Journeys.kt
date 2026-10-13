package app.delayd

import java.time.Duration

/**
 * Rides that follow each other at the same place are one journey: either one ride a long stop split,
 * or a connection, where the delay counts at the final destination.
 * shortcut: assumes one ticket for every connection under [MAX_GAP]; add a per-trip "separate ticket" flag if that bites.
 */
object Journeys {
    val MAX_GAP: Duration = Duration.ofMinutes(30)
    const val MAX_JUMP_M = 1_000.0

    /** [next] picks up where a ride that ended at [end] left off. */
    fun connects(end: Fix, next: Fix) =
        !next.time.isBefore(end.time) && Duration.between(end.time, next.time) <= MAX_GAP && distanceM(end, next) <= MAX_JUMP_M

    /** Consecutive rides (sorted by start) that connect, grouped. */
    fun chains(rides: List<Ride>): List<List<Ride>> =
        rides.sortedBy { it.start.time }.fold(mutableListOf<MutableList<Ride>>()) { acc, r ->
            if (acc.lastOrNull()?.last()?.let { connects(it.end, r.start) } == true) acc.last() += r else acc += mutableListOf(r)
            acc
        }

    /** One trip for a whole journey when every leg has a train, else the legs that have one. */
    fun resolve(legs: List<Ride>, find: (Ride) -> Trip?): List<Trip> {
        if (legs.size == 1) return listOfNotNull(find(legs[0]))
        find(Ride(legs.first().start, legs.last().end))?.let { return listOf(it) } // a long stop split one ride
        val trips = legs.map { find(it) }
        return if (trips.all { it != null }) listOf(trips.filterNotNull().reduce(::join)) else trips.filterNotNull()
    }

    private fun join(a: Trip, b: Trip) = Trip("${a.train} + ${b.train}", a.from, b.to, b.scheduledArrival, b.arrival,
        a.realtime && b.realtime, replaced = b.replaced ?: a.replaced, legs = a.legs + b.legs)
}
