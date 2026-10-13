package app.delayd.debug

import app.delayd.Departure
import app.delayd.Fix
import app.delayd.Stop
import app.delayd.distanceM
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Debug-only: turns a train that really ran in the last hours (from Transitous departures with live times) into the
 * location fixes a phone on that train would have produced. The pure part, shared by the emulator broadcast and the JVM test.
 */
object Replay {
    class Pick(val dep: Departure, val origin: Pair<Double, Double>, val stops: List<Stop>) {
        val dest get() = stops.last()
    }

    /**
     * A train that left in the last 2 h and has already reached a stop 4-80 km away after a 12-90 min ride.
     * Prefers the most delayed one, then the latest; null when the board has none.
     */
    fun pick(deps: List<Departure>, origin: Pair<Double, Double>, now: Instant): Pick? = deps
        .filter { it.realtime && !it.cancelled && it.time > now.minus(Duration.ofHours(2)) }
        .mapNotNull { d ->
            val usable = d.next.takeWhile { it.arrival != null && it.scheduledArrival != null }
            val end = usable.indices.lastOrNull { i ->
                val s = usable[i]
                val ride = Duration.between(d.time, s.arrival)
                s.arrival!! <= now.minus(Duration.ofMinutes(10)) && ride >= Duration.ofMinutes(12) && ride <= Duration.ofMinutes(90) &&
                    distanceM(origin.first, origin.second, s.lat, s.lon) in 4_000.0..80_000.0
            } ?: return@mapNotNull null
            Pick(d, origin, usable.take(end + 1))
        }
        .maxWithOrNull(compareBy<Pick> { Duration.between(it.dest.scheduledArrival, it.dest.arrival).toMinutes() }.thenBy { it.dep.time })

    /** Line speed in m/s by train type: a train runs at it between stops and waits at the station, it does not crawl. */
    private fun cruise(train: String) = when {
        Regex("^(ICE|IC|EC|FLX|NJ|TGV|RJX?)\b").containsMatchIn(train) -> 50.0
        train.startsWith("S") -> 17.0
        train.startsWith("RE") -> 33.0
        else -> 27.0
    }

    /** Seconds from standstill to standstill over [d] meters at line speed [v] with acceleration [ACCEL]. */
    private fun physSeconds(d: Double, v: Double) = if (d > v * v / ACCEL) d / v + v / ACCEL else 2 * sqrt(d / ACCEL)

    /** Share of a leg of [d] meters covered [t] seconds into its [total] seconds: accelerate, cruise, brake (trapezoid speed). */
    private fun covered(t: Double, total: Double, d: Double): Double {
        if (d <= 0 || total <= 0) return 1.0
        val a = max(ACCEL, 4 * d / (total * total))
        val v = a * (total - sqrt(max(0.0, total * total - 4 * d / a))) / 2
        val ta = v / a
        val s = when {
            t <= ta -> 0.5 * a * t * t
            t <= total - ta -> 0.5 * a * ta * ta + v * (t - ta)
            else -> d - 0.5 * a * (total - t) * (total - t)
        }
        return (s / d).coerceIn(0.0, 1.0)
    }

    private const val ACCEL = 0.7

    /**
     * Fixes for [pick]: three on the platform, one every 10 s while it runs (accelerating at 0.7 m/s2 and braking, 40 s standing at the
     * intermediate stops), then one a minute at the destination for 8 minutes so the ride closes.
     */
    fun fixes(pick: Pick): List<Fix> {
        val out = mutableListOf<Fix>()
        val (oLat, oLon) = pick.origin
        repeat(3) { out += Fix(pick.dep.time.minusSeconds(240L - it * 60), oLat, oLon) }
        var from = Triple(pick.dep.time, oLat, oLon)
        for (s in pick.stops) {
            val arrive = s.arrival!!
            val d = distanceM(from.second, from.third, s.lat, s.lon)
            // stand at the last stop until the train can just make it at line speed, then run
            val leave = maxOf(from.first, arrive.minusSeconds(physSeconds(d, cruise(pick.dep.train)).toLong()))
            var w = from.first.plusSeconds(10)
            while (w < leave) { out += Fix(w, from.second, from.third); w = w.plusSeconds(10) }
            var t = leave.plusSeconds(10)
            while (t < arrive) {
                val e = covered(Duration.between(leave, t).seconds.toDouble(), Duration.between(leave, arrive).seconds.toDouble(), d)
                out += Fix(t, from.second + (s.lat - from.second) * e, from.third + (s.lon - from.third) * e)
                t = t.plusSeconds(10)
            }
            out += Fix(arrive, s.lat, s.lon)
            from = Triple(arrive.plusSeconds(40), s.lat, s.lon)
        }
        val end = pick.dest
        repeat(8) { out += Fix(end.arrival!!.plusSeconds((it + 1) * 60L), end.lat, end.lon) }
        return out
    }
}
