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

    private fun hbfToOstbahnhof(via: List<Fix>) = Ride(berlinHbf("2026-10-13T10:48:00Z"),
        Fix(Instant.parse("2026-10-13T10:59:30Z"), 52.51052, 13.435342), via)
    private fun at(time: String, lat: Double, lon: Double) = Fix(Instant.parse(time), lat, lon)

    @Test
    fun `checkpoints on the train's timeline keep the match`() {
        val onTrain = listOf(at("2026-10-13T10:51:00Z", 52.5204, 13.3872), at("2026-10-13T10:54:00Z", 52.5215, 13.4112), at("2026-10-13T10:56:00Z", 52.5142, 13.4195))
        assertEquals("S Ostbahnhof (Berlin)", Transitous.match(hbfToOstbahnhof(onTrain), departures)!!.to)
    }

    @Test
    fun `a bus along the same line five minutes behind the train is not the train`() {
        val bus = listOf(at("2026-10-13T10:56:00Z", 52.5204, 13.3872), at("2026-10-13T10:59:00Z", 52.5215, 13.4112), at("2026-10-13T11:01:00Z", 52.5142, 13.4195))
        assertNull(Transitous.match(hbfToOstbahnhof(bus).copy(end = Fix(Instant.parse("2026-10-13T11:04:00Z"), 52.51052, 13.435342)), departures))
    }

    @Test
    fun `a car on a road kilometres from the rails is not the train`() {
        val car = listOf(at("2026-10-13T10:51:00Z", 52.535, 13.30), at("2026-10-13T10:54:00Z", 52.50, 13.33), at("2026-10-13T10:56:00Z", 52.49, 13.38))
        assertNull(Transitous.match(hbfToOstbahnhof(car), departures))
    }

    @Test
    fun `a stop in the open that made the train late is allowed for`() {
        // the train stood 20 min between two stops: checkpoints are 20 min "late" against a straight timeline
        val slow = Departure("RE9", "Berlin Hbf", Instant.parse("2026-10-13T10:48:00Z"), realtime = true, next = listOf(
            Stop("A", 52.50, 13.40, Instant.parse("2026-10-13T10:52:00Z"), Instant.parse("2026-10-13T10:52:00Z")),
            Stop("Ostbahnhof", 52.51052, 13.435342, Instant.parse("2026-10-13T10:58:00Z"), Instant.parse("2026-10-13T11:18:00Z"))))
        val ride = Ride(berlinHbf("2026-10-13T10:48:00Z"), Fix(Instant.parse("2026-10-13T11:18:30Z"), 52.51052, 13.435342),
            listOf(at("2026-10-13T10:53:00Z", 52.502, 13.405), at("2026-10-13T11:01:00Z", 52.505, 13.42), at("2026-10-13T11:12:00Z", 52.508, 13.43)))
        assertEquals(20, Transitous.match(ride, listOf(slow))!!.delayMin - 0)
    }
}
