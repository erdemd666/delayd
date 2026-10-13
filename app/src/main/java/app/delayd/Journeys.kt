package app.delayd

import java.time.Duration

/**
 * Rides that follow each other at the same place are one journey: either one ride a long stop split,
 * or a connection, where the delay counts at the final destination.
 * shortcut: assumes one ticket for every connection under [MAX_GAP]; add a per-trip "separate ticket" flag if that bites.
 */
object Journeys {
    /** Rides this close are one connection: one ticket, the delay counts at the final destination. */
    val MAX_GAP: Duration = Duration.ofMinutes(30)
    /** Pieces of one ride split by a long stop in the open (blockage, waiting for a free track) are found as a whole this long. */
    val LONG_GAP: Duration = Duration.ofHours(3)
    const val MAX_JUMP_M = 1_000.0

    /** [next] picks up where a ride that ended at [end] left off. */
    fun connects(end: Fix, next: Fix, gap: Duration = LONG_GAP) =
        !next.time.isBefore(end.time) && Duration.between(end.time, next.time) <= gap && distanceM(end, next) <= MAX_JUMP_M

    /** Consecutive rides (sorted by start) that may belong together, grouped. */
    fun chains(rides: List<Ride>): List<List<Ride>> = group(rides, LONG_GAP)

    private fun group(rides: List<Ride>, gap: Duration) =
        rides.sortedBy { it.start.time }.fold(mutableListOf<MutableList<Ride>>()) { acc, r ->
            if (acc.lastOrNull()?.last()?.let { connects(it.end, r.start, gap) } == true) acc.last() += r else acc += mutableListOf(r)
            acc
        }

    /** One trip for a whole ride, else one per connection (all legs have a train) or per leg that has one. */
    fun resolve(legs: List<Ride>, find: (Ride) -> Trip?): List<Trip> {
        if (legs.size == 1) return listOfNotNull(findOrSplit(legs[0], find))
        find(Ride(legs.first().start, legs.last().end, legs.flatMap { it.via }))?.let { return listOf(it) } // a long stop split one ride
        return group(legs, MAX_GAP).flatMap { g ->
            val trips = g.map { findOrSplit(it, find) }
            if (g.size > 1 && trips.all { it != null }) listOf(trips.filterNotNull().reduce(::join)) else trips.filterNotNull()
        }
    }

    /**
     * A change of trains under [TripDetector.END_AFTER] is one ride that matches no single train: cut it at its longest
     * standstill and ask for a train per leg. The lookups only succeed where a train really stops, i.e. at a station.
     */
    private fun findOrSplit(ride: Ride, find: (Ride) -> Trip?): Trip? = find(ride) ?: ride.pause?.let { (from, to) ->
        val a = find(Ride(ride.start, from, ride.via.filter { it.time < from.time })) ?: return null
        val b = find(Ride(to, ride.end, ride.via.filter { it.time > to.time })) ?: return null
        join(a, b)
    }

    private fun join(a: Trip, b: Trip) = Trip("${a.train} + ${b.train}", a.from, b.to, b.scheduledArrival, b.arrival,
        a.realtime && b.realtime, replaced = b.replaced ?: a.replaced, legs = a.legs + b.legs)
}
