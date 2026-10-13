package app.delayd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.delayd.ClaimStatus
import app.delayd.Compensation
import app.delayd.R
import app.delayd.Trip
import app.delayd.TripStore
import java.text.DecimalFormatSymbols
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DB_CLAIM_URL = "https://www.bahn.de/service/informationen-buchung/fahrgastrechte"

private val DE = Locale.GERMANY
private val DE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZONE)

/** The claim text is always German: it goes to DB. What the user sees is exactly what gets copied. */
fun claimTextSingle(trip: Trip, fareCt: Int): String = buildString {
    val owed = Compensation.singleTicketCt(trip.delayMin, fareCt)
    appendLine("Fahrgastrechte – Antrag auf Entschädigung")
    appendLine("Zug: ${trip.train}")
    appendLine("Datum: ${DE_DATE.format(trip.scheduledArrival)}")
    appendLine("Strecke: ${trip.from} → ${trip.to}")
    appendLine("Planmäßige Ankunft: ${time(trip.scheduledArrival)} Uhr")
    appendLine("Tatsächliche Ankunft: ${time(trip.arrival)} Uhr (+${trip.delayMin} Min.)")
    appendLine("Fahrkarte: Einzelfahrkarte, Fahrpreis ${money(fareCt, DE)}")
    append("Entschädigung: ${if (trip.delayMin >= 120) 50 else 25} % = ${money(owed, DE)}")
}

fun claimTextMonth(month: YearMonth, cases: List<Trip>): String = buildString {
    val total = Compensation.dTicketMonthCt(cases.map { it.delayMin })
    appendLine("Fahrgastrechte – Sammelantrag Deutschlandticket, ${monthTitle(month, DE)}")
    appendLine("Fahrkarte: Deutschlandticket")
    appendLine("Verspätungen von mindestens 60 Minuten am Ziel:")
    cases.forEachIndexed { i, t ->
        appendLine("${i + 1}. ${DE_DATE.format(t.scheduledArrival)}, ${t.train}, ${t.from} → ${t.to}, " +
            "planmäßig ${time(t.scheduledArrival)} Uhr, tatsächlich ${time(t.arrival)} Uhr (+${t.delayMin} Min.)")
    }
    append("Pauschale: ${cases.size} × ${money(Compensation.D_TICKET_CASE_CT, DE)}, höchstens 25 % des Monatspreises = ${money(total, DE)}")
}

/** "35.00" or "35,00" depending on the language, so the field matches what the user types. */
fun fareText(ct: Int): String = "%d%s%02d".format(ct / 100, DecimalFormatSymbols.getInstance().decimalSeparator, ct % 100)

@Composable
fun ClaimScreen(id: String, trips: List<Trip>, onBack: () -> Unit, onDone: () -> Unit) {
    ClaimContent(trips.firstOrNull { it.id == id }, onBack) { fareCt ->
        TripStore.update(id) { it.copy(fareCt = fareCt, status = ClaimStatus.CLAIMED) }
        onDone()
    }
}

@Composable
fun ClaimContent(trip: Trip?, onBack: () -> Unit, onSubmitted: (fareCt: Int) -> Unit) {
    var fare by rememberSaveable { mutableStateOf(trip?.fareCt?.let(::fareText).orEmpty()) }
    val fareCt = parseFareCt(fare)
    val owed = fareCt?.let { Compensation.singleTicketCt(trip?.delayMin ?: 0, it) }
    val payable = owed != null && Compensation.payable(owed)
    Screen(stringResource(R.string.claim_title), onBack) {
        if (trip == null) { item { Text(stringResource(R.string.trip_missing), Modifier.padding(Space.xl)) }; return@Screen }

        item { StepTitle(1, stringResource(R.string.claim_step_facts)) }
        item {
            Panel {
                Fact(stringResource(R.string.fact_train), trip.train)
                Fact(stringResource(R.string.fact_date), date(trip.scheduledArrival))
                Fact(stringResource(R.string.fact_route), stringResource(R.string.route, trip.from, trip.to))
                Fact(stringResource(R.string.fact_scheduled), time(trip.scheduledArrival))
                Fact(stringResource(R.string.fact_actual), stringResource(R.string.actual_short, time(trip.arrival), trip.delayMin))
                if (!trip.realtime) Text(stringResource(if (trip.userReported) R.string.source_reported_body else R.string.source_phone_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { StepTitle(2, stringResource(R.string.claim_step_fare)) }
        item {
            Panel {
                OutlinedTextField(fare, { fare = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.fare_label)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(when {
                            fare.isBlank() -> stringResource(R.string.fare_hint)
                            owed == null -> stringResource(R.string.fare_invalid)
                            payable -> stringResource(R.string.fare_owed, money(owed))
                            else -> stringResource(R.string.fare_below, money(owed), money(Compensation.MIN_PAYOUT_CT))
                        })
                    })
            }
        }
        item { StepTitle(3, stringResource(R.string.claim_step_send)) }
        item {
            ClaimSend(
                text = fareCt?.let { claimTextSingle(trip, it) }, canSubmit = payable,
                submitLabel = R.string.claim_submitted, onSubmit = { onSubmitted(fareCt!!) },
            )
        }
    }
}

@Composable
fun MonthClaimScreen(month: YearMonth, trips: List<Trip>, onBack: () -> Unit, onDone: () -> Unit) {
    val cases = openCases(trips, month)
    MonthClaimContent(month, cases, onBack) {
        cases.forEach { c -> TripStore.update(c.id) { it.copy(status = ClaimStatus.CLAIMED) } }
        onDone()
    }
}

@Composable
fun MonthClaimContent(month: YearMonth, cases: List<Trip>, onBack: () -> Unit, onSubmitted: () -> Unit) {
    val total = Compensation.dTicketMonthCt(cases.map { it.delayMin })
    val payable = Compensation.payable(total)
    Screen(stringResource(R.string.claim_month_title, monthTitle(month)), onBack) {
        if (cases.isEmpty()) { item { Text(stringResource(R.string.month_empty), Modifier.padding(Space.xl)) }; return@Screen }
        item { StepTitle(1, stringResource(R.string.claim_step_cases)) }
        item {
            Panel {
                cases.forEach { t ->
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        Text(stringResource(R.string.route, t.from, t.to), style = MaterialTheme.typography.titleMedium)
                        Text("${t.train} · ${date(t.scheduledArrival)} · " + stringResource(R.string.delay_badge, t.delayMin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { StepTitle(2, stringResource(R.string.claim_step_total)) }
        item {
            Panel {
                BigMoney(money(total), color = MaterialTheme.colorScheme.primary)
                Text(pluralStringResource(R.plurals.month_total_body, cases.size, cases.size, money(Compensation.D_TICKET_CASE_CT), money(Compensation.D_TICKET_PRICE_CT / 4)), style = MaterialTheme.typography.bodyMedium)
                if (!payable) Text(stringResource(R.string.rule_below_min, money(Compensation.MIN_PAYOUT_CT)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { StepTitle(3, stringResource(R.string.claim_step_send)) }
        item { ClaimSend(claimTextMonth(month, cases), payable, R.string.claim_month_submitted, onSubmitted) }
    }
}

/** Preview of the exact text, copy, open the official page, and the explicit "I did it" step that changes status. */
@Composable
private fun ClaimSend(text: String?, canSubmit: Boolean, submitLabel: Int, onSubmit: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    var copied by remember(text) { mutableStateOf(false) }
    Panel {
        Text(stringResource(R.string.claim_exact), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(), MaterialTheme.shapes.small, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface) {
            SelectionContainer {
                Text(text ?: stringResource(R.string.claim_text_pending), Modifier.padding(Space.m), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Actions {
            OutlinedButton({ clipboard.setText(AnnotatedString(text!!)); copied = true }, Modifier.heightIn(min = 48.dp), enabled = text != null) {
                Text(stringResource(if (copied) R.string.copied else R.string.copy_text))
            }
            OutlinedButton({ uri.openUri(DB_CLAIM_URL) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.open_db)) }
        }
        Text(stringResource(R.string.claim_login_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onSubmit, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = canSubmit) { Text(stringResource(submitLabel)) }
    }
}

@Composable
private fun StepTitle(n: Int, title: String) =
    SectionTitle(stringResource(R.string.step_format, n, title), Modifier.padding(top = Space.s))

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1200)
@Composable
private fun ClaimPreview() = DelaydTheme(dark = false) { ClaimContent(sampleTrips[2].copy(status = ClaimStatus.OPEN), {}, {}) }

@Preview(showBackground = true, widthDp = 360, heightDp = 1200)
@Composable
private fun MonthClaimPreview() = DelaydTheme(dark = true) {
    val m = YearMonth.of(2026, 10)
    MonthClaimContent(m, List(3) { sampleTrips[0].copy(train = "ICE ${1600 + it}") }, {}, {})
}
