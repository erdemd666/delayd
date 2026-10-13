package app.delayd

import java.time.Duration
import java.time.Instant
import kotlin.math.*

data class Fix(val time: Instant, val lat: Double, val lon: Double)

/** A fast movement between two standstills, e.g. a train ride from station to station. */
data class Ride(val start: Fix, val end: Fix)

/**
 * Turns a stream of location fixes into rides.
 * A ride starts at the last slow fix before the phone moves faster than any walker or cyclist,
 * and ends at the first slow fix of a standstill that lasts [END_AFTER].
 * Short standstills (intermediate stations, signals) don't end the ride.
 * Cars match too; the train lookup filters them out.
 */
class TripDetector(private val onRide: (Ride) -> Unit) {
    private var last: Fix? = null
    private var still: Fix? = null
    @Volatile private var start: Fix? = null
    private var slowSince: Fix? = null

    val moving get() = start != null
    /** Where the ride in progress began. */
    val current: Fix? get() = start

    fun add(fix: Fix) {
        val prev = last
        last = fix
        if (prev == null) { still = fix; return }
        val fast = speedMps(prev, fix) >= FAST_MPS

        val s = start
        if (s == null) {
            // fixes after a long silence (idle without polling) say nothing about speed in between
            if (Duration.between(prev.time, fix.time) > MAX_SILENCE) { still = fix; return }
            if (fast) start = still ?: prev else still = fix
            return
        }
        if (fast) { slowSince = null; return }
        val slow = slowSince ?: fix.also { slowSince = it }
        // a stop longer than END_AFTER splits the ride; Journeys joins the pieces later
        if (Duration.between(slow.time, fix.time) >= END_AFTER) {
            if (distanceM(s, slow) >= MIN_RIDE_M) onRide(Ride(s, slow))
            start = null
            slowSince = null
            still = fix
        }
    }

    companion object {
        const val FAST_MPS = 12.0 // ~43 km/h
        const val MIN_RIDE_M = 3_000.0
        val MAX_SILENCE: Duration = Duration.ofMinutes(10)
        val END_AFTER: Duration = Duration.ofMinutes(10)
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
