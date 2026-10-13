package app.delayd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class MonthlyClaimTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-02T08:00:00Z")
    private fun sept(day: Int, delayMin: Long, status: ClaimStatus = ClaimStatus.OPEN): Trip {
        val sched = Instant.parse("2026-09-%02dT10:00:00Z".format(day))
        return Trip("RE1", "A", "B", sched, sched.plusSeconds(delayMin * 60), true, status = status)
    }

    @Test
    fun `three late trains last month reach the 4 euro minimum`() {
        val (month, ct) = Compensation.monthlyClaim(listOf(sept(1, 70), sept(2, 90), sept(3, 61)), now, zone, null)!!
        assertEquals(YearMonth.of(2026, 9), month)
        assertEquals(450, ct)
    }

    @Test
    fun `claimed and paid trips of the month use up the cap`() {
        val claimed = List(6) { sept(it + 1, 70, ClaimStatus.CLAIMED) }          // 900 ct, 675 ct of the 1575 cap left
        val open = List(5) { sept(it + 10, 70) }                                  // would be 750 ct on its own
        assertEquals(675, Compensation.monthlyClaim(claimed + open, now, zone, null)!!.second)
        assertNull(Compensation.monthlyClaim(claimed + open.take(2), now, zone, null)) // 300 ct: below the minimum
        assertNull(Compensation.monthlyClaim(List(11) { sept(it + 1, 70, ClaimStatus.PAID) } + open, now, zone, null))
    }

    @Test
    fun `two late trains stay below the minimum`() =
        assertNull(Compensation.monthlyClaim(listOf(sept(1, 70), sept(2, 90)), now, zone, null))

    @Test
    fun `claimed trips and short delays do not count`() =
        assertNull(Compensation.monthlyClaim(listOf(sept(1, 70), sept(2, 90, ClaimStatus.CLAIMED), sept(3, 30), sept(4, 70)), now, zone, null))

    @Test
    fun `notifies once per month`() =
        assertNull(Compensation.monthlyClaim(listOf(sept(1, 70), sept(2, 90), sept(3, 61)), now, zone, "2026-09"))

    @Test
    fun `this month is not due yet`() =
        assertNull(Compensation.monthlyClaim(listOf(sept(1, 70), sept(2, 90), sept(3, 61)), Instant.parse("2026-09-20T08:00:00Z"), zone, null))
}
