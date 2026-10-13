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
    fun `a ride without a train is dropped quietly`() {
        val q = queue().apply { add(ride) }
        assertEquals(emptyList<Trip>(), q.run(find = { null }).first)
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
}
