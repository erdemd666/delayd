package app.delayd.ui

import app.delayd.ClaimStatus
import app.delayd.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.util.Locale

class FormatTest {
    private fun trip(delayMin: Long, status: ClaimStatus = ClaimStatus.OPEN, fareCt: Int? = null, day: Int = 5) =
        Trip("ICE $day$delayMin", "A", "B", Instant.parse("2026-10-%02dT10:00:00Z".format(day)),
            Instant.parse("2026-10-%02dT10:00:00Z".format(day)).plusSeconds(delayMin * 60), true, fareCt, status)

    @Test fun moneyFollowsLocale() {
        assertEquals("€4.50", money(450, Locale.US))
        assertEquals("4,50\u00a0€", money(450, Locale.GERMANY))
    }

    @Test fun fareParsing() {
        assertEquals(3550, parseFareCt("35,50"))
        assertEquals(3500, parseFareCt(" 35 "))
        assertNull(parseFareCt("abc"))
        assertNull(parseFareCt("0"))
        assertNull(parseFareCt(""))
    }

    @Test fun deutschlandticketTotalsUseMonthlyPoolAndMinimum() {
        val two = listOf(trip(70, day = 1), trip(80, day = 2))        // 300 ct: below the 4 EUR minimum
        assertEquals(0, totals(two, true).owed)
        val three = two + trip(90, day = 3)                           // 450 ct: payable
        assertEquals(450, totals(three, true).owed)
        assertEquals(450, totals(three.map { it.copy(status = ClaimStatus.PAID) }, true).recovered)
        assertEquals(3, openCases(three, YearMonth.of(2026, 10)).size)
    }

    @Test fun singleTicketTotalsNeedAFareAndSkipDismissed() {
        val trips = listOf(trip(130, fareCt = 4000), trip(70, fareCt = 4000), trip(70), trip(130, ClaimStatus.DISMISSED, 4000))
        assertEquals(2000 + 1000, totals(trips, false).owed)
        assertEquals(1000, tripCt(trips[1], false))
        assertNull(tripCt(trips[2], false))
    }

    @Test fun claimTextIsGermanAndExact() {
        val text = claimTextSingle(trip(75, fareCt = 4000), 4000)
        assertTrue(text.contains("Entschädigung: 25 % = 10,00\u00a0€"))
        assertTrue(text.contains("Tatsächliche Ankunft: 13:15 Uhr (+75 Min.)"))
    }
}
