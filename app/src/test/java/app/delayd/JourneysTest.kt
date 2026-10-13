package app.delayd

import org.junit.Assert.assertEquals
import org.junit.Test

class JourneysTest {
    private val leg1 = rideAt(0, 30, 13.4, 13.7)
    private val leg2 = rideAt(40, 70, 13.7, 14.0)   // 10 min later, same place
    private val later = rideAt(400, 430, 13.7, 14.0)
    private val afterBlockage = rideAt(150, 180, 13.7, 14.0) // 80 min after leg2 ended: same train after a long stop

    @Test
    fun `rides at the same place within three hours chain`() {
        assertEquals(listOf(listOf(leg1, leg2), listOf(later)), Journeys.chains(listOf(later, leg2, leg1)))
    }

    @Test
    fun `a long stop in the open joins the pieces of one ride but not as a connection`() {
        val whole = tripOf("ICE 1", "C", 190, 200)
        assertEquals(listOf(whole), Journeys.resolve(listOf(leg1, afterBlockage)) { if (it.start == leg1.start && it.end == afterBlockage.end) whole else null })
        val a = tripOf("ICE 1", "B", 30, 45)
        val b = tripOf("RE 2", "C", 190, 200)
        // two trains 80 min apart are two trips, not one journey
        assertEquals(listOf(a, b), Journeys.resolve(listOf(leg1, afterBlockage)) { if (it == leg1) a else if (it == afterBlockage) b else null })
    }

    @Test
    fun `a ride starting elsewhere does not chain`() {
        assertEquals(2, Journeys.chains(listOf(leg1, rideAt(40, 70, 13.9, 14.2))).size)
    }

    @Test
    fun `one ride split by a long stop is found as a whole`() {
        val whole = tripOf("ICE 1", "C", 68, 80)
        val trips = Journeys.resolve(listOf(leg1, leg2)) { if (it.start == leg1.start && it.end == leg2.end) whole else null }
        assertEquals(listOf(whole), trips)
    }

    @Test
    fun `a connection is one trip counted at the final destination`() {
        val a = tripOf("ICE 1", "B", 30, 45)
        val b = tripOf("RE 2", "C", 70, 140)
        val trip = Journeys.resolve(listOf(leg1, leg2)) { if (it == leg1) a else if (it == leg2) b else null }.single()
        assertEquals("ICE 1 + RE 2", trip.train)
        assertEquals("A", trip.from)
        assertEquals("C", trip.to)
        assertEquals(70, trip.delayMin)
        assertEquals(2, trip.legs)
    }

    @Test
    fun `a connection with a phone-measured leg is measured`() {
        val a = tripOf("ICE 1", "B", 30, 45, realtime = false)
        val b = tripOf("RE 2", "C", 70, 80)
        assertEquals(true, Journeys.resolve(listOf(leg1, leg2)) { if (it == leg1) a else if (it == leg2) b else null }.single().measured)
    }

    @Test
    fun `a leg without a train keeps only the legs that have one`() {
        val b = tripOf("RE 2", "C", 70, 80)
        assertEquals(listOf(b), Journeys.resolve(listOf(leg1, leg2)) { if (it == leg2) b else null })
    }

    @Test
    fun `single ride without a train gives nothing`() {
        assertEquals(emptyList<Trip>(), Journeys.resolve(listOf(leg1)) { null })
    }
}
