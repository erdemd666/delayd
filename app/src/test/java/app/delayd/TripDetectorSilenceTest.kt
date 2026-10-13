package app.delayd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TripDetectorSilenceTest {
    private val t0 = Instant.parse("2026-10-13T08:00:00Z")
    private fun fix(sec: Long, northM: Double) = Fix(t0.plusSeconds(sec), 52.0 + northM / 111_195.0, 13.0)

    @Test
    fun `a fix after hours of silence is not a speed reading`() {
        val d = TripDetector { }
        d.add(fix(0, 0.0))
        d.add(fix(4 * 3_600, 30_000.0))            // asleep for 4 h, 30 km away: 2 m/s on paper
        d.add(fix(4 * 3_600 + 10, 30_000.0 + 300)) // now clearly moving
        d.add(fix(4 * 3_600 + 20, 30_000.0 + 600)) // the next fix confirms it
        assertTrue(d.moving)
        assertEquals(t0.plusSeconds(4 * 3_600), d.current!!.time) // the ride starts after the silence, not 4 h earlier
    }
}
