package app.delayd

import java.time.Instant

val T0: Instant = Instant.parse("2026-10-13T10:00:00Z")
fun fixAt(min: Long, lat: Double = 52.5, lon: Double = 13.4) = Fix(T0.plusSeconds(min * 60), lat, lon)
/** A ride from minute [a] to [b]; [from]/[to] are the longitudes of the two places (0.3 deg is about 20 km). */
fun rideAt(a: Long, b: Long, from: Double = 13.4, to: Double = 13.7) = Ride(fixAt(a, lon = from), fixAt(b, lon = to))
fun tripOf(train: String, to: String, sched: Long, actual: Long, realtime: Boolean = true) =
    Trip(train, "A", to, T0.plusSeconds(sched * 60), T0.plusSeconds(actual * 60), realtime)
