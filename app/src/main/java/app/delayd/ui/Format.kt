package app.delayd.ui

import app.delayd.ClaimStatus
import app.delayd.Compensation
import app.delayd.Trip
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

/** The locale of what the user reads: German or English as resolved by Android, anything else shows the German default resources. */
fun appLocale(): Locale = Locale.getDefault().takeIf { it.language == "en" || it.language == "de" } ?: Locale.GERMANY

/** EN "€4.50", DE "4,50 €". Compensation.format is German-only, so the UI formats money itself. */
fun money(ct: Int, locale: Locale = appLocale()): String =
    NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") }.format(ct / 100.0)

/** DE "13.10.2026" (with [weekday] "Di., 13.10.2026"), EN "Oct 13, 2026". */
fun date(i: Instant, locale: Locale = appLocale(), weekday: Boolean = false): String {
    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZONE).format(i)
    return if (weekday) DateTimeFormatter.ofPattern("EEE", locale).withZone(ZONE).format(i) + ", " + day else day
}

fun time(i: Instant): String = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE).format(i)

fun monthTitle(m: YearMonth, locale: Locale = appLocale()): String =
    m.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

fun Trip.month(): YearMonth = YearMonth.from(scheduledArrival.atZone(ZONE))
val Trip.late get() = delayMin >= 60

/** Parses "4,50" / "4.5" into cents; null when empty or not a positive amount. */
fun parseFareCt(text: String): Int? =
    text.trim().replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.toLong()?.takeIf { it in 1..99_999 }?.toInt()

/** What a single trip is worth: single ticket from its fare, Deutschlandticket per-case share. Null = unknown. */
fun tripCt(t: Trip, dTicket: Boolean): Int? = when {
    !t.late -> 0
    dTicket -> Compensation.D_TICKET_CASE_CT
    else -> t.fareCt?.let { Compensation.singleTicketCt(t.delayMin, it) }
}

data class Totals(val owed: Int, val claimed: Int, val recovered: Int)

/**
 * Open / claimed / paid amounts. Open amounts under the 4 EUR minimum count as nothing (they can't be claimed yet).
 * Deutschlandticket: the 25 % cap holds per month across all three, filled paid first, then claimed, then open.
 */
fun totals(trips: List<Trip>, dTicket: Boolean): Totals {
    fun sum(status: ClaimStatus, needPayable: Boolean = false) = trips.filter { it.status == status }
        .sumOf { tripCt(it, false)?.takeIf { ct -> !needPayable || Compensation.payable(ct) } ?: 0 }
    if (!dTicket) return Totals(sum(ClaimStatus.OPEN, true), sum(ClaimStatus.CLAIMED), sum(ClaimStatus.PAID))
    var t = Totals(0, 0, 0)
    for (m in trips.groupBy { it.month() }.values) {
        fun delays(s: ClaimStatus) = m.filter { it.status == s }.map { it.delayMin }
        val paid = Compensation.dTicketMonthCt(delays(ClaimStatus.PAID))
        val claimed = Compensation.dTicketMonthCt(delays(ClaimStatus.CLAIMED), paid)
        val open = Compensation.dTicketMonthCt(delays(ClaimStatus.OPEN), paid + claimed)
        t = Totals(t.owed + open.takeIf(Compensation::payable).let { it ?: 0 }, t.claimed + claimed, t.recovered + paid)
    }
    return t
}

/** What is still claimable for [month]'s open cases after claimed and paid ones took their share of the cap. */
fun openCt(trips: List<Trip>, month: YearMonth) =
    Compensation.dTicketMonthCt(openCases(trips, month).map { it.delayMin }, Compensation.dTicketTakenCt(trips, month, ZONE))

/** Open delayed Deutschlandticket cases of [month], the pool a monthly claim is built from. */
fun openCases(trips: List<Trip>, month: YearMonth) =
    trips.filter { it.status == ClaimStatus.OPEN && it.late && it.month() == month }.sortedBy { it.scheduledArrival }

/** The month Home offers to claim: last month while it still has a payable pool (the notification points there), else this one. */
fun claimMonth(trips: List<Trip>, now: YearMonth): YearMonth {
    val last = now.minusMonths(1)
    return if (Compensation.payable(openCt(trips, last))) last else now
}

/** Where "claim" leads for [t]: the monthly pool with a Deutschlandticket, else the single-ticket claim. */
fun claimRoute(t: Trip, dTicket: Boolean) = if (dTicket) "month/${t.month()}" else "claim/${t.id}"
