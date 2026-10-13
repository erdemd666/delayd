package app.delayd

import java.time.Duration
import java.time.Instant
import kotlin.math.*

data class Fix(val time: Instant, val lat: Double, val lon: Double)

/**
 * A fast movement between two standstills, e.g. a train ride from station to station.
 * [via] are up to three fixes on the way (at a quarter, half and three quarters of the time): they tell a train
 * from a bus or car that happens to start and end at the same stations at the same time.
 */
data class Ride(val start: Fix, val end: Fix, val via: List<Fix> = emptyList())

/**
 * Turns a stream of location fixes into rides.
 * A ride starts at the last slow fix before the phone moves faster than any walker or cyclist,
 * and ends at the first slow fix of a standstill that lasts [END_AFTER].
 * Short standstills (intermediate stations, signals) don't end the ride; longer ones split it, Journeys joins the pieces.
 * Cars match too; the train lookup filters them out.
 */
class TripDetector(private val onRide: (Ride) -> Unit) {
    private class Piece(val ride: Ride, val sent: Boolean)

    private var last: Fix? = null
    /** A fast-looking fix waits for the next one: a network jump out and back is not a movement. */
    private var held: Fix? = null
    private var still: Fix? = null
    @Volatile private var start: Fix? = null
    private var slowSince: Fix? = null
    private var carry: Piece? = null
    private val trail = ArrayList<Fix>()

    /** True from the first fast-looking fix on, so the service switches to GPS without waiting to be sure. */
    val moving get() = start != null || held != null
    /** Where the ride in progress began. */
    val current: Fix? get() = start

    fun add(fix: Fix) {
        held?.let { h ->
            held = null
            if (!isJump(last!!, h, fix)) step(h)
        }
        val prev = last
        if (prev != null && isFast(prev, fix)) held = fix else step(fix)
    }

    private fun step(fix: Fix) {
        val prev = last
        last = fix
        if (prev == null) { still = fix; return }
        val fast = isFast(prev, fix)

        val s = start
        if (s == null) {
            // fixes after a long silence (idle without polling) say nothing about speed in between
            if (Duration.between(prev.time, fix.time) > MAX_SILENCE) { still = fix; return }
            if (fast) { start = still ?: prev; trail.clear() } else still = fix
            return
        }
        if (trail.lastOrNull()?.let { Duration.between(it.time, fix.time) >= TRAIL_EVERY } != false) trail += fix
        // after a standstill the phone must really have left it: a GPS spike near a station is not the train departing
        if (fast && slowSince?.let { distanceM(it, fix) < RESUME_M } != true) { slowSince = null; return }
        val slow = slowSince ?: fix.also { slowSince = it }
        // a stop longer than END_AFTER splits the ride; Journeys joins the pieces later
        if (Duration.between(slow.time, fix.time) >= END_AFTER) {
            close(s, slow)
            start = null
            slowSince = null
            still = fix
        }
    }

    /** Pieces of one journey count together towards [MIN_RIDE_M]: a short piece before or after a long stop is still part of it. */
    private fun close(s: Fix, end: Fix) {
        val before = carry?.takeIf { Journeys.connects(it.ride.end, s) }
        val own = distanceM(s, end)
        if (own < MIN_PIECE_M) return // noise, not a piece of a ride
        val send = if (before == null) own >= MIN_RIDE_M else before.sent || distanceM(before.ride.start, end) >= MIN_RIDE_M
        val ride = Ride(s, end, checkpoints(s, end))
        if (send) {
            if (before != null && !before.sent) onRide(before.ride)
            onRide(ride)
        }
        carry = Piece(ride, send)
    }

    private fun checkpoints(s: Fix, end: Fix): List<Fix> {
        val span = Duration.between(s.time, end.time)
        val inside = trail.filter { it.time > s.time && it.time < end.time }
        return listOf(0.25, 0.5, 0.75).mapNotNull { f ->
            val t = s.time.plus(span.multipliedBy((f * 1000).toLong()).dividedBy(1000))
            inside.minByOrNull { Duration.between(it.time, t).abs() }
        }.distinct().also { trail.clear() }
    }

    companion object {
        private val TRAIL_EVERY: Duration = Duration.ofSeconds(30)
        const val FAST_MPS = 12.0 // ~43 km/h
        /** Over a data gap (tunnel, idle poll) the average includes the standing and accelerating, so the bar is lower. */
        const val FAST_OVER_GAP_MPS = 8.0 // ~29 km/h, still above any cyclist
        val GAP: Duration = Duration.ofSeconds(60)
        const val MIN_RIDE_M = 3_000.0
        /** The shortest piece of a ride split by a long stop. */
        const val MIN_PIECE_M = 1_000.0
        /** Fast again after a standstill only counts once the phone is this far from it. */
        const val RESUME_M = 150.0
        val MAX_SILENCE: Duration = Duration.ofMinutes(10)
        /** Standstill that ends a ride. Short enough to split a change of trains, intermediate stops stand 0.5-3 min. */
        val END_AFTER: Duration = Duration.ofMinutes(4)

        fun isFast(a: Fix, b: Fix) = speedMps(a, b) >= if (Duration.between(a.time, b.time) > GAP) FAST_OVER_GAP_MPS else FAST_MPS

        /** [x] is a jump when the next fix [y] is back where [z] was. A train is further along by then. */
        fun isJump(z: Fix, x: Fix, y: Fix) = distanceM(z, y) < distanceM(z, x) / 2
    }
}

fun speedMps(a: Fix, b: Fix): Double {
    val s = Duration.between(a.time, b.time).toMillis() / 1000.0
    return if (s <= 0) 0.0 else distanceM(a, b) / s
}

fun distanceM(a: Fix, b: Fix) = distanceM(a.lat, a.lon, b.lat, b.lon)

/** Haversine distance in meters. */
fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * 6_371_000 * asin(sqrt(h))
}
