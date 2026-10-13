package app.delayd

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ManualAddTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText(Charsets.UTF_8)
    private val berlin = ZoneId.of("Europe/Berlin")
    private val deps get() = Transitous.parse(res("stoptimes-berlin-hbf-past.json"))

    @Test
    fun `geocode response gives stations with city`() {
        val places = Transitous.parsePlaces(res("geocode-berlin-hbf.json"))
        assertEquals(3, places.size)
        assertEquals(Place("Berlin Hbf", "at-Railway-Current-Reference-Data-2026_de:11000:900003200:1:51", "Berlin"), places.first())
    }

    @Test
    fun `geocode entries without an id are skipped and a non-array is an error`() {
        assertEquals(listOf("A"), Transitous.parsePlaces("""[{"name":"B"},{"name":"A","id":"x"}]""").map { it.name })
        assertThrows(JSONException::class.java) { Transitous.parsePlaces("<html>") }
    }

    @Test
    fun `stoptimes response keeps headsign, realtime flag and following stops`() {
        assertEquals(listOf("RE20", "RE4 (3166)", "S5"), deps.map { it.train })
        assertEquals("Lübbenau, Bahnhof", deps[0].headsign)
        assertEquals(listOf(true, true, false), deps.map { it.realtime })
        assertEquals(5, deps[0].next.size)
    }

    @Test
    fun `live ride keeps the live arrival and is not user reported`() {
        val re4 = deps[1]
        val trip = manualTrip(re4, re4.next[0])
        assertTrue(trip.realtime)
        assertFalse(trip.userReported)
        assertEquals(Instant.parse("2026-10-13T06:07:00Z"), trip.arrival)
        assertEquals(Instant.parse("2026-10-13T06:09:00Z"), trip.scheduledArrival)
        assertFalse(trip.measured)
    }

    @Test
    fun `ride without live data uses the typed arrival and is marked user reported`() {
        val s5 = deps[2]
        val trip = manualTrip(s5, s5.next[0], Instant.parse("2026-10-13T06:50:00Z"))
        assertEquals(Instant.parse("2026-10-13T06:50:00Z"), trip.arrival)
        assertEquals(Instant.parse("2026-10-13T06:03:00Z"), trip.scheduledArrival)
        assertEquals(47, trip.delayMin)
        assertTrue(trip.userReported)
        assertFalse(trip.realtime)
        assertFalse(trip.measured)
        assertEquals("S5@2026-10-13T06:03:00Z", trip.id)
    }

    @Test
    fun `nothing typed and no live data falls back to the schedule`() {
        val s5 = deps[2]
        val trip = manualTrip(s5, s5.next[0])
        assertEquals(trip.scheduledArrival, trip.arrival)
        assertTrue(trip.userReported)
    }

    @Test
    fun `editing a live arrival turns it into a user report`() {
        val trip = manualTrip(deps[0], deps[0].next[0], Instant.parse("2026-10-13T07:00:00Z"))
        assertTrue(trip.userReported)
        assertFalse(trip.realtime)
    }

    @Test
    fun `only stops already reached are offered`() {
        val re20 = deps[0]
        assertEquals(listOf("S+U Potsdamer Platz Bhf (Berlin)"), reachedStops(re20, Instant.parse("2026-10-13T06:10:00Z")).map { it.name })
        assertTrue(reachedStops(re20, Instant.parse("2026-10-13T05:00:00Z")).isEmpty())
    }

    @Test
    fun `typed clock time lands on the day nearest the schedule`() {
        val planned = Instant.parse("2026-10-13T21:50:00Z") // 23:50 in Berlin (CEST)
        assertEquals(Instant.parse("2026-10-13T22:10:00Z"), nearestInstant(planned, LocalTime.of(0, 10), berlin))
        assertEquals(Instant.parse("2026-10-13T21:55:00Z"), nearestInstant(planned, LocalTime.of(23, 55), berlin))
    }

    @Test
    fun `user reported flag survives storage and old files load without it`() {
        val trip = manualTrip(deps[2], deps[2].next[0], Instant.parse("2026-10-13T06:50:00Z"))
        assertEquals(listOf(trip), TripStore.fromJson(TripStore.toJson(listOf(trip))))
        val old = """[{"train":"S7","from":"A","to":"B","scheduledArrival":"2026-10-13T11:22:00Z","arrival":"2026-10-13T11:28:00Z","realtime":true}]"""
        assertFalse(TripStore.fromJson(old).single().userReported)
    }
}
