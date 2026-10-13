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
}
