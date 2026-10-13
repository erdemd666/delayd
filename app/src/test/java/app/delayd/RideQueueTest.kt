package app.delayd

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

class RideQueueTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = T0.plus(Duration.ofHours(2))
    private val ride = rideAt(0, 30)
    private val trip = tripOf("ICE 1", "B", 30, 95)
    private fun file() = File(tmp.root, "pending.json")
    private fun queue() = RideQueue(file()) { now }
    private fun RideQueue.run(inProgress: Fix? = null, find: (Ride) -> Trip? = { trip }): Pair<List<Trip>, Instant?> {
        val out = mutableListOf<Trip>()
        return out to drain(inProgress, find) { out += it }
    }

    @Test
    fun `a pending ride survives a restart`() {
        queue().add(ride)
        assertEquals(1, queue().size)
    }

    @Test
    fun `waits for a connecting ride before looking up`() {
        now = T0.plus(Duration.ofMinutes(40)) // 10 min after the ride ended
        val q = queue().apply { add(ride) }
        var calls = 0
        val (trips, wake) = q.run(find = { calls++; trip })
        assertEquals(0, calls)
        assertTrue(trips.isEmpty())
        assertEquals(ride.end.time.plus(RideQueue.SETTLE), wake)
    }

    @Test
    fun `a found trip is delivered and the ride leaves the queue`() {
        val q = queue().apply { add(ride) }
        val (trips, wake) = q.run()
        assertEquals(listOf(trip), trips)
        assertNull(wake)
        assertEquals(0, queue().size)
    }

    @Test
    fun `a ride without a train waits as an orphan, then is dropped without another lookup`() {
        val q = queue().apply { add(ride) }
        assertEquals(emptyList<Trip>(), q.run(find = { null }).first)
        assertEquals(1, q.size)
        var calls = 0
        now = ride.end.time.plus(Journeys.LONG_GAP).plusSeconds(1)
        q.run(find = { calls++; null })
        assertEquals(0, calls)
        assertEquals(0, q.size)
    }

    @Test
    fun `an orphan is completed by the second half of a ride split by a long stop`() {
        val first = rideAt(0, 20, 13.4, 13.6)
        val second = rideAt(110, 130, 13.6, 13.8) // stood in the open for 90 min
        val q = queue().apply { add(first) }
        now = T0.plus(Duration.ofMinutes(60))
        q.run(find = { null }) // the first half alone has no train
        q.add(second)
        now = T0.plus(Duration.ofMinutes(170))
        var asked: Ride? = null
        val (trips, _) = q.run(find = { asked = asked ?: it; trip })
        assertEquals(listOf(trip), trips)
        assertEquals(Ride(first.start, second.end), asked) // the whole ride is tried first
        assertEquals(0, q.size)
    }

    @Test
    fun `network failure backs off and retries later`() {
        val q = queue().apply { add(ride) }
        var calls = 0
        val fail: (Ride) -> Trip? = { calls++; throw IOException("offline") }
        assertEquals(now.plus(Duration.ofMinutes(1)), q.run(find = fail).second)
        now = now.plus(Duration.ofSeconds(30))
        q.run(find = fail)
        assertEquals(1, calls) // not due yet
        now = now.plus(Duration.ofSeconds(31))
        assertEquals(now.plus(Duration.ofMinutes(2)), q.run(find = fail).second)
        assertEquals(2, calls)
        now = now.plus(Duration.ofMinutes(3))
        assertEquals(listOf(trip), queue().run().first) // reloaded from disk
    }

    @Test
    fun `a rate limit Retry-After wins over the short backoff`() {
        val q = queue().apply { add(ride) }
        val wake = q.run(find = { throw LookupException("HTTP 429", 600) }).second
        assertEquals(now.plus(Duration.ofMinutes(10)), wake)
    }

    @Test
    fun `one failing journey does not make the next one hammer the server`() {
        val other = rideAt(500, 530)
        now = T0.plus(Duration.ofHours(12))
        val q = queue().apply { add(ride); add(other) }
        var calls = 0
        q.run(find = { calls++; throw IOException("down") })
        assertEquals(1, calls)
    }

    @Test
    fun `gives up after too many failures`() {
        val q = queue().apply { add(ride) }
        repeat(RideQueue.MAX_ATTEMPTS) {
            q.run(find = { throw IOException("down") })
            now = now.plus(Duration.ofHours(2))
        }
        assertEquals(0, q.size)
    }

    @Test
    fun `gives up on a ride older than two days`() {
        val q = queue().apply { add(ride) }
        now = T0.plus(Duration.ofHours(60))
        q.run(find = { throw IOException("down") })
        assertEquals(0, q.size)
    }

    @Test
    fun `unreadable responses drop the ride after three tries`() {
        val q = queue().apply { add(ride) }
        repeat(3) {
            q.run(find = { throw JSONException("garbage") })
            now = now.plus(Duration.ofHours(1))
        }
        assertEquals(0, q.size)
    }

    @Test
    fun `holds a journey while its next ride is in progress`() {
        val q = queue().apply { add(ride) }
        var calls = 0
        q.run(inProgress = fixAt(40, lon = 13.7), find = { calls++; trip })
        assertEquals(0, calls)
        assertEquals(1, q.size)
    }

    @Test
    fun `a damaged queue file is ignored`() {
        file().writeText("{not json")
        assertEquals(0, queue().size)
        file().writeText("""[{"junk":1}]""")
        assertEquals(0, queue().size)
    }

    @Test
    fun `backoff doubles up to an hour`() {
        assertEquals(Duration.ofMinutes(1), RideQueue.backoff(1))
        assertEquals(Duration.ofMinutes(4), RideQueue.backoff(3))
        assertEquals(Duration.ofHours(1), RideQueue.backoff(9))
    }

    @Test
    fun `clear empties the queue and the file`() {
        val q = queue().apply { add(ride) }
        q.clear()
        assertEquals(0, q.size)
        assertEquals(0, queue().size)
    }

    @Test
    fun `an unexpected error on one journey does not block the next one`() {
        val other = rideAt(100, 130, from = 14.0, to = 14.3)
        now = T0.plus(Duration.ofHours(5))
        val q = queue().apply { add(ride); add(other) }
        val (trips, _) = q.run(find = { if (it == ride) throw IllegalStateException("bug") else trip })
        assertEquals(listOf(trip), trips)
        assertEquals(1, q.size) // the bad ride waits for a retry, it is not lost at once
    }

    @Test
    fun `a ride queued as detection stops is looked up later by a fresh queue and the job is armed for its due time`() {
        now = T0
        val fixes = (0..60).map { Fix(T0.plusSeconds(it * 10L), 52.0 + it * 300 / 111_195.0, 13.0) }
        val d = TripDetector { queue().add(it) }
        fixes.forEach(d::add)
        d.flush() // switching detection off mid-ride
        assertEquals(1, queue().size)
        val due = queue().next()!!
        assertEquals(fixes[59].time.plus(RideQueue.SETTLE), due) // the newest fix was still unconfirmed
        now = due // the service is gone; the job fires and drains
        assertEquals(listOf(trip), queue().run().first)
        assertEquals(0, queue().size)
    }

    @Test
    fun `the wake-up is never sooner than 30 seconds`() {
        assertEquals(Duration.ofSeconds(30), Pipeline.wait(T0, T0.plusSeconds(100)))
        assertEquals(Duration.ofMinutes(5), Pipeline.wait(T0.plusSeconds(300), T0))
    }
}
