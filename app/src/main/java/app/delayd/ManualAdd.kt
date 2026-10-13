package app.delayd

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The [Trip] for a hand-picked ride, same model as a detected one. [typed] is the arrival the user entered;
 * null keeps the live arrival. With no live data (history gone) the typed time, or the plan, stands in and the
 * trip is marked [Trip.userReported]. [stop] must have a scheduled arrival (see [reachedStops]).
 */
fun manualTrip(dep: Departure, stop: Stop, typed: Instant? = null): Trip {
    val scheduled = requireNotNull(stop.scheduledArrival)
    val live = dep.realtime && stop.arrival != null && typed == null
    return Trip(dep.train, dep.from, stop.name, scheduled, if (live) stop.arrival!! else typed ?: scheduled,
        realtime = live, userReported = !live)
}

/** Stops the train has already served at [now]: a trip can only be added once it is over. */
fun reachedStops(dep: Departure, now: Instant) = dep.next.filter { it.scheduledArrival?.let { t -> t <= now } == true }

/** The instant for a typed clock time that lies closest to [near] (a ride may cross midnight). */
fun nearestInstant(near: Instant, clock: LocalTime, zone: ZoneId): Instant {
    val day = near.atZone(zone).toLocalDate()
    return listOf(-1L, 0L, 1L).map { day.plusDays(it).atTime(clock).atZone(zone).toInstant() }
        .minBy { kotlin.math.abs(it.epochSecond - near.epochSecond) }
}
