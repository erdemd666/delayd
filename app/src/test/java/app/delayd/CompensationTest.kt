package app.delayd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompensationTest {
    @Test
    fun `single ticket thresholds`() {
        assertEquals(0, Compensation.singleTicketCt(59, 8_000))
        assertEquals(2_000, Compensation.singleTicketCt(60, 8_000))
        assertEquals(2_000, Compensation.singleTicketCt(119, 8_000))
        assertEquals(4_000, Compensation.singleTicketCt(120, 8_000))
        assertEquals(499, Compensation.singleTicketCt(60, 1_999)) // rounds down, never overpromises
    }

    @Test
    fun `deutschlandticket collects cases up to a quarter of the price`() {
        assertEquals(300, Compensation.dTicketMonthCt(listOf(60, 59, 130)))
        assertEquals(1_575, Compensation.dTicketMonthCt(List(11) { 75L }))
    }

    @Test
    fun `payout minimum`() {
        assertFalse(Compensation.payable(399))
        assertTrue(Compensation.payable(400))
    }
}
