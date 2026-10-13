package app.delayd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Fixture: real Transitous departures from Berlin Hbf on 2026-10-13, trimmed. */
class TransitousTest {
    private val departures = Transitous.parse(
        javaClass.getResource("/departures-berlin-hbf.json")!!.readText()
    )
    private fun berlinHbf(at: String) = Fix(Instant.parse(at), 52.5251, 13.3694)
    private fun potsdamHbf(at: String) = Fix(Instant.parse(at), 52.3918, 13.0668)

    @Test
    fun `parses real response`() {
        assertEquals(7, departures.size)
        assertEquals("ICE 375", departures.last { it.train.startsWith("ICE") }.train)
    }

    @Test
    fun `matches the S7 that arrived when the phone stopped`() {
        val trip = Transitous.match(Ride(berlinHbf("2026-10-13T10:54:00Z"), potsdamHbf("2026-10-13T11:28:30Z")), departures)!!
        assertEquals("S7", trip.train)
        assertEquals("S Potsdam Hauptbahnhof", trip.to)
        assertEquals(Instant.parse("2026-10-13T11:22:00Z"), trip.scheduledArrival)
        assertEquals(6, trip.delayMin)
    }

    @Test
    fun `tells apart two S7 departures minutes apart by arrival`() {
        val trip = Transitous.match(Ride(berlinHbf("2026-10-13T10:54:00Z"), potsdamHbf("2026-10-13T11:34:20Z")), departures)!!
        assertEquals(Instant.parse("2026-10-13T11:32:00Z"), trip.scheduledArrival)
        assertEquals(2, trip.delayMin)
    }

    @Test
    fun `no train goes where the ride ended`() {
        val hamburg = Fix(Instant.parse("2026-10-13T12:40:00Z"), 53.5530, 10.0069)
        assertNull(Transitous.match(Ride(berlinHbf("2026-10-13T10:54:00Z"), hamburg), departures))
    }

    @Test
    fun `without live data the phone's arrival counts`() {
        val dep = Departure("RE1", "Berlin Hbf", Instant.parse("2026-10-13T10:55:00Z"), realtime = false,
            next = listOf(Stop("Potsdam Hbf", 52.3918, 13.0668,
                Instant.parse("2026-10-13T11:22:00Z"), Instant.parse("2026-10-13T11:22:00Z"))))
        val trip = Transitous.match(Ride(berlinHbf("2026-10-13T10:54:00Z"), potsdamHbf("2026-10-13T12:30:00Z")), listOf(dep))!!
        assertEquals(68, trip.delayMin)
    }

    @Test
    fun `trips survive a save and load`() {
        val trip = Transitous.match(Ride(berlinHbf("2026-10-13T10:54:00Z"), potsdamHbf("2026-10-13T11:28:30Z")), departures)!!
        val claimed = trip.copy(fareCt = 1_999, status = ClaimStatus.CLAIMED)
        assertEquals(listOf(trip, claimed), TripStore.fromJson(TripStore.toJson(listOf(trip, claimed))))
        // files written before fareCt/status existed still load
        val old = """[{"train":"S7","from":"A","to":"B","scheduledArrival":"2026-10-13T11:22:00Z","arrival":"2026-10-13T11:28:00Z","realtime":true}]"""
        assertEquals(ClaimStatus.OPEN, TripStore.fromJson(old).single().status)
    }
}
