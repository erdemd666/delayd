package app.delayd

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

    fun format(ct: Int) = "%d,%02d €".format(ct / 100, ct % 100)
}
