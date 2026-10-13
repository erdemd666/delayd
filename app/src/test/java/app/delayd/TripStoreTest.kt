package app.delayd

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TripStoreTest {
    @Test
    fun `new fields survive a save and load`() {
        val trip = tripOf("S7 + RE1", "C", 30, 100).copy(replaced = "ICE 9", legs = 2, fareCt = 1_999)
        assertEquals(listOf(trip), TripStore.fromJson(TripStore.toJson(listOf(trip))))
    }

    @Test
    fun `files written before replaced and legs existed still load`() {
        val old = """[{"train":"S7","from":"A","to":"B","scheduledArrival":"2026-10-13T11:22:00Z","arrival":"2026-10-13T11:28:00Z","realtime":false}]"""
        val trip = TripStore.fromJson(old).single()
        assertEquals(1, trip.legs)
        assertEquals(null, trip.replaced)
        assertEquals(true, trip.measured)
    }

    @Test
    fun `a damaged row is skipped, the rest is kept`() {
        val good = TripStore.toJson(listOf(tripOf("S7", "B", 30, 40))).trim('[', ']')
        val json = """[{"train":"X"},{"train":"Y","from":"a","to":"b","scheduledArrival":"nope","arrival":"x","realtime":true},$good]"""
        assertEquals(listOf("S7"), TripStore.fromJson(json).map { it.train })
    }

    @Test
    fun `an unknown status falls back to open`() {
        val json = """[{"train":"S7","from":"A","to":"B","scheduledArrival":"2026-10-13T11:22:00Z","arrival":"2026-10-13T11:28:00Z","realtime":true,"status":"WAT"}]"""
        assertEquals(ClaimStatus.OPEN, TripStore.fromJson(json).single().status)
    }

    @Test
    fun `text that is no array is an error the caller handles`() {
        assertThrows(JSONException::class.java) { TripStore.fromJson("<html>") }
    }
}
