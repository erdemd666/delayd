package app.delayd

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * German rail passenger rights (bahn.de Fahrgastrechte), amounts in euro cents.
 * Single ticket: 25 % of the fare from 60 min delay at the destination, 50 % from 120 min.
 * Deutschlandticket: 150 ct per case from 60 min, capped at 25 % of the monthly price.
 * Nothing is paid out below 400 ct, so Deutschlandticket cases are collected first.
 */
object Compensation {
    const val MIN_PAYOUT_CT = 400
    const val D_TICKET_CASE_CT = 150
    const val D_TICKET_PRICE_CT = 6_300 // 63 € since Jan 2026

    fun singleTicketCt(delayMin: Long, fareCt: Int): Int = when {
        delayMin >= 120 -> fareCt / 2
        delayMin >= 60 -> fareCt / 4
        else -> 0
    }

    /** Total for one month of Deutschlandticket trips. */
    fun dTicketMonthCt(delaysMin: List<Long>): Int =
        minOf(delaysMin.count { it >= 60 } * D_TICKET_CASE_CT, D_TICKET_PRICE_CT / 4)

    fun payable(ct: Int) = ct >= MIN_PAYOUT_CT


    /** Last month's open Deutschlandticket cases once they reach the payout minimum, unless [notified] (a YearMonth string) already covers it. */
    fun monthlyClaim(trips: List<Trip>, now: Instant, zone: ZoneId, notified: String?): Pair<YearMonth, Int>? {
        val month = YearMonth.from(now.atZone(zone)).minusMonths(1)
        if (month.toString() == notified) return null
        val ct = dTicketMonthCt(trips.filter {
            it.status == ClaimStatus.OPEN && YearMonth.from(it.scheduledArrival.atZone(zone)) == month
        }.map { it.delayMin })
        return if (payable(ct)) month to ct else null
    }
}
