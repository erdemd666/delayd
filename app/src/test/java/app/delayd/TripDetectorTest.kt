package app.delayd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TripDetectorTest {
    private val t0 = Instant.parse("2026-10-13T08:00:00Z")
    private val mPerDegLat = 111_195.0

    private fun fix(sec: Long, northM: Double) = Fix(t0.plusSeconds(sec), 52.0 + northM / mPerDegLat, 13.0)

    @Test
    fun `detects one ride across a short intermediate stop`() {
        val rides = mutableListOf<Ride>()
        val d = TripDetector { rides += it }
        var t = 0L
        var pos = 0.0
        repeat(5) { t += 60; d.add(fix(t, pos)) }          // waiting on the platform, network fixes
        val lastStill = t
        repeat(60) { t += 10; pos += 300; d.add(fix(t, pos)) } // 30 m/s for 10 min
        repeat(18) { t += 10; d.add(fix(t, pos)) }             // 3 min at a station
        repeat(60) { t += 10; pos += 300; d.add(fix(t, pos)) }
        val arrived = t
        repeat(70) { t += 10; d.add(fix(t, pos)) }             // standing at the destination

        assertEquals(1, rides.size)
        assertEquals(t0.plusSeconds(lastStill), rides[0].start.time)
        assertTrue(Duration.between(t0.plusSeconds(arrived), rides[0].end.time) <= Duration.ofSeconds(20))
        assertEquals(36_000.0, distanceM(rides[0].start, rides[0].end), 50.0)
    }

    @Test
    fun `a network location jump is not a ride`() {
        val rides = mutableListOf<Ride>()
        val d = TripDetector { rides += it }
        listOf(0.0, 0.0, 1_200.0, 0.0, 0.0).forEachIndexed { i, m -> d.add(fix(i * 60L, m)) }
        (5..20).forEach { d.add(fix(it * 60L, 0.0)) }
        assertTrue(rides.isEmpty())
    }

    @Test
    fun `a jump out and back at watch rate does not even start a ride`() {
        val d = TripDetector { }
        listOf(0.0, 0.0, 1_200.0).forEachIndexed { i, m -> d.add(fix(i * 15L, m)) }
        assertTrue(d.moving) // provisional: the service goes to GPS to find out
        d.add(fix(45, 20.0))
        assertTrue(!d.moving)
        assertEquals(null, d.current)
    }

    @Test
    fun `a data gap on the platform does not hide the departure`() {
        val d = TripDetector { }
        listOf(0L, 60, 120).forEach { d.add(fix(it, 0.0)) }
        d.add(fix(120 + 360, 4_000.0)) // underground for 6 min, then 4 km away: 11 m/s on average, still a train
        d.add(fix(120 + 370, 4_300.0))
        assertEquals(t0.plusSeconds(120), d.current!!.time)
    }

    @Test
    fun `a gps spike after arrival does not extend the ride`() {
        val rides = mutableListOf<Ride>()
        val d = TripDetector { rides += it }
        var t = 0L
        var pos = 0.0
        repeat(5) { t += 10; d.add(fix(t, pos)) }
        repeat(60) { t += 10; pos += 300; d.add(fix(t, pos)) }
        val arrived = t
        repeat(12) { t += 10; d.add(fix(t, pos)) }
        t += 10; d.add(fix(t, pos + 140)) // 14 m/s on paper, but never left
        repeat(40) { t += 10; d.add(fix(t, pos)) }
        assertEquals(1, rides.size)
        assertTrue(Duration.between(t0.plusSeconds(arrived), rides[0].end.time) <= Duration.ofSeconds(20))
    }

    @Test
    fun `a stop of five minutes splits the ride, and a short last piece is still sent`() {
        val rides = mutableListOf<Ride>()
        val d = TripDetector { rides += it }
        var t = 0L
        var pos = 0.0
        repeat(3) { t += 10; d.add(fix(t, pos)) }
        repeat(60) { t += 10; pos += 300; d.add(fix(t, pos)) }   // 18 km
        repeat(32) { t += 10; d.add(fix(t, pos)) }               // held in the open for 5 min
        repeat(40) { t += 10; pos += 300; d.add(fix(t, pos)) }   // 12 km more
        repeat(30) { t += 10; d.add(fix(t, pos)) }
        assertEquals(2, rides.size)
        assertTrue(Journeys.connects(rides[0].end, rides[1].start))
        assertEquals(3, rides[0].via.size)
    }

    @Test
    fun `a last piece under 3 km after a long stop is sent`() {
        val rides = mutableListOf<Ride>()
        val d = TripDetector { rides += it }
        var t = 0L
        var pos = 0.0
        repeat(3) { t += 10; d.add(fix(t, pos)) }
        repeat(60) { t += 10; pos += 300; d.add(fix(t, pos)) }
        repeat(80) { t += 10; d.add(fix(t, pos)) }               // 13 min held outside the station
        repeat(8) { t += 10; pos += 200; d.add(fix(t, pos)) }    // 1.6 km in
        repeat(60) { t += 10; d.add(fix(t, pos)) }
        assertEquals(2, rides.size)
        assertEquals(1_600.0, distanceM(rides[1].start, rides[1].end), 300.0)
    }
}
