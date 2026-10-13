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

/** EN "€4.50", DE "4,50 €". Compensation.format is German-only, so the UI formats money itself. */
fun money(ct: Int, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") }.format(ct / 100.0)

fun date(i: Instant, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZONE).format(i)

fun time(i: Instant): String = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE).format(i)

fun monthTitle(m: YearMonth, locale: Locale = Locale.getDefault()): String =
    m.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

fun Trip.month(): YearMonth = YearMonth.from(scheduledArrival.atZone(ZONE))
val Trip.late get() = delayMin >= 60

/** Parses "4,50" / "4.5" into cents; null when empty or not a positive amount. */
fun parseFareCt(text: String): Int? =
    text.trim().replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.toInt()?.takeIf { it in 1..99_999 }

/** What a single trip is worth: single ticket from its fare, Deutschlandticket per-case share. Null = unknown. */
fun tripCt(t: Trip, dTicket: Boolean): Int? = when {
    !t.late -> 0
    dTicket -> Compensation.D_TICKET_CASE_CT
    else -> t.fareCt?.let { Compensation.singleTicketCt(t.delayMin, it) }
}

/** Total for [trips]. With [needPayable] amounts under the 4 EUR minimum count as nothing (they can't be claimed yet). */
fun totalCt(trips: List<Trip>, dTicket: Boolean, needPayable: Boolean): Int {
    fun ok(ct: Int) = !needPayable || Compensation.payable(ct)
    return if (dTicket) trips.groupBy { it.month() }.values.sumOf { Compensation.dTicketMonthCt(it.map { t -> t.delayMin }).takeIf(::ok) ?: 0 }
    else trips.sumOf { tripCt(it, false)?.takeIf(::ok) ?: 0 }
}

data class Totals(val owed: Int, val claimed: Int, val recovered: Int)

fun totals(trips: List<Trip>, dTicket: Boolean) = Totals(
    owed = totalCt(trips.filter { it.status == ClaimStatus.OPEN }, dTicket, needPayable = true),
    claimed = totalCt(trips.filter { it.status == ClaimStatus.CLAIMED }, dTicket, needPayable = false),
    recovered = totalCt(trips.filter { it.status == ClaimStatus.PAID }, dTicket, needPayable = false),
)

/** Open delayed Deutschlandticket cases of [month], the pool a monthly claim is built from. */
fun openCases(trips: List<Trip>, month: YearMonth) =
    trips.filter { it.status == ClaimStatus.OPEN && it.late && it.month() == month }.sortedBy { it.scheduledArrival }
