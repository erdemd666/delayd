package app.delayd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FareMemoryTest {
    @Test fun remembersPerRouteAndOverwrites() {
        var s: String? = null
        assertNull(FareMemory.lookup(s, "Berlin Hbf", "Potsdam Hbf"))
        s = FareMemory.remember(s, "Berlin Hbf", "Potsdam Hbf", 450)
        s = FareMemory.remember(s, "Köln Hbf", "Bonn Hbf", 800)
        assertEquals(450, FareMemory.lookup(s, "Berlin Hbf", "Potsdam Hbf"))
        assertNull(FareMemory.lookup(s, "Potsdam Hbf", "Berlin Hbf")) // direction matters
        s = FareMemory.remember(s, "Berlin Hbf", "Potsdam Hbf", 500)
        assertEquals(500, FareMemory.lookup(s, "Berlin Hbf", "Potsdam Hbf"))
        assertEquals(2, s.lines().size)
    }

    @Test fun keepsOnlyTheNewestRoutesAndSurvivesGarbage() {
        var s: String? = "garbage\nx\tnotanumber"
        repeat(FareMemory.MAX + 5) { s = FareMemory.remember(s, "A$it", "B", 100 + it) }
        assertEquals(FareMemory.MAX, s!!.lines().size)
        assertNull(FareMemory.lookup(s, "A0", "B"))
        assertEquals(100 + FareMemory.MAX + 4, FareMemory.lookup(s, "A${FareMemory.MAX + 4}", "B"))
    }
}
