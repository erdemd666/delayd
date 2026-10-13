package app.delayd

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class TransitousCancelledTest {
    private val departures = Transitous.parse(javaClass.getResource("/departures-berlin-hbf.json")!!.readText())
    private val hbf = Fix(Instant.parse("2026-10-13T10:54:00Z"), 52.5251, 13.3694)
    private val potsdam = Fix(Instant.parse("2026-10-13T11:28:30Z"), 52.3918, 13.0668)
    private fun cancelled(depart: String, arriveAtPotsdam: String) = Departure("S7", "S+U Berlin Hauptbahnhof",
        Instant.parse(depart), realtime = true, cancelled = true, next = listOf(Stop("S Potsdam Hauptbahnhof", 52.3918, 13.0668,
            Instant.parse(arriveAtPotsdam), Instant.parse(arriveAtPotsdam))))

    @Test
    fun `a replacement train counts against the cancelled train plan`() {
        val trip = Transitous.match(Ride(hbf, potsdam), departures + cancelled("2026-10-13T10:40:00Z", "2026-10-13T11:07:00Z"))!!
        assertEquals(Instant.parse("2026-10-13T11:07:00Z"), trip.scheduledArrival)
        assertEquals("S7", trip.replaced)
        assertEquals(21, trip.delayMin)
    }

    @Test
    fun `a cancelled train that was planned later is no replacement`() {
        val trip = Transitous.match(Ride(hbf, potsdam), departures + cancelled("2026-10-13T11:05:00Z", "2026-10-13T11:40:00Z"))!!
        assertNull(trip.replaced)
        assertEquals(6, trip.delayMin)
    }

    @Test
    fun `cancelled trains are never matched as the ride itself`() {
        assertNull(Transitous.match(Ride(hbf, potsdam), listOf(cancelled("2026-10-13T10:55:00Z", "2026-10-13T11:28:00Z"))))
    }

    @Test
    fun `parse keeps cancelled trains flagged and skips malformed entries`() {
        val json = """{"stopTimes":[
            {"place":{"name":"A","departure":"garbage"},"displayName":"X"},
            {"displayName":"no place"},
            {"place":{"name":"A","departure":"2026-10-13T10:00:00Z"},"displayName":"RE1","tripCancelled":true,"nextStops":[]}]}"""
        val parsed = Transitous.parse(json)
        assertEquals(listOf("RE1"), parsed.map { it.train })
        assertEquals(true, parsed.single().cancelled)
    }

    @Test
    fun `a response that is not a stop list is an error the caller handles`() {
        assertThrows(JSONException::class.java) { Transitous.parse("<html>502</html>") }
    }
}
