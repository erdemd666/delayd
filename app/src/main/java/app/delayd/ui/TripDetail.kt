package app.delayd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.delayd.ClaimStatus
import app.delayd.Compensation
import app.delayd.R
import app.delayd.Trip
import app.delayd.TripStore

@Composable
fun TripDetailScreen(id: String, trips: List<Trip>, dTicket: Boolean, onBack: () -> Unit, onClaim: () -> Unit) {
    TripDetailContent(trips.firstOrNull { it.id == id }, trips, dTicket, onBack, onClaim,
        setStatus = { s -> TripStore.update(id) { it.copy(status = s) } },
        delete = { TripStore.delete(id); onBack() })
}

@Composable
fun TripDetailContent(
    trip: Trip?, trips: List<Trip>, dTicket: Boolean, onBack: () -> Unit, onClaim: () -> Unit,
    setStatus: (ClaimStatus) -> Unit, delete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Screen(stringResource(R.string.trip_title), onBack) {
        if (trip == null) {
            item { Text(stringResource(R.string.trip_missing), Modifier.padding(vertical = Space.xl), style = MaterialTheme.typography.bodyLarge) }
            return@Screen
        }
        item { Header(trip) }
        item { Timeline(trip) }
        item { Source(trip) }
        item { CompensationCard(trip, trips, dTicket) }
        item {
            Actions {
                when (trip.status) {
                    ClaimStatus.OPEN -> {
                        if (trip.late) Button(onClaim, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_claim)) }
                        OutlinedButton({ setStatus(ClaimStatus.DISMISSED) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_dismiss)) }
                    }
                    ClaimStatus.CLAIMED -> {
                        Button({ setStatus(ClaimStatus.PAID) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_mark_paid)) }
                        OutlinedButton({ setStatus(ClaimStatus.OPEN) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_reopen)) }
                    }
                    ClaimStatus.PAID -> OutlinedButton({ setStatus(ClaimStatus.CLAIMED) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_unpay)) }
                    ClaimStatus.DISMISSED -> OutlinedButton({ setStatus(ClaimStatus.OPEN) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_reopen)) }
                }
            }
        }
        item {
            TextButton({ confirmDelete = true }, Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_trip_title)) },
        text = { Text(stringResource(R.string.delete_trip_body)) },
        confirmButton = { TextButton({ confirmDelete = false; delete() }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Header(trip: Trip) = Panel {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
        DelayBadge(trip.delayMin)
        StatusChip(trip.status)
    }
    Text(trip.train, style = MaterialTheme.typography.headlineSmall)
    Text(date(trip.scheduledArrival), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** From/to nodes joined by a line. The two rows draw their own halves so text of any height stays aligned. */
@Composable
private fun Timeline(trip: Trip) {
    val c = MaterialTheme.colorScheme
    val line = c.outline
    val accent = if (trip.late) c.tertiary else c.primary
    Panel {
        SectionTitle(stringResource(R.string.route_title))
        Row(Modifier.height(IntrinsicSize.Min).semantics(mergeDescendants = true) {}) {
            Canvas(Modifier.width(24.dp).fillMaxHeight()) {
                val x = size.width / 2; val y = 10.dp.toPx()
                drawLine(line, Offset(x, y), Offset(x, size.height), 2.dp.toPx())
                drawCircle(line, 5.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
            }
            Column(Modifier.padding(bottom = Space.l)) {
                Text(stringResource(R.string.departed_from), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Text(trip.from, style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.height(IntrinsicSize.Min).semantics(mergeDescendants = true) {}) {
            Canvas(Modifier.width(24.dp).fillMaxHeight()) {
                val x = size.width / 2; val y = 10.dp.toPx()
                drawLine(line, Offset(x, 0f), Offset(x, y), 2.dp.toPx())
                drawCircle(accent, 6.dp.toPx(), Offset(x, y))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.arrived_at), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Text(trip.to, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.scheduled_time, time(trip.scheduledArrival)),
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                    textDecoration = if (trip.late) TextDecoration.LineThrough else null)
                Text(stringResource(R.string.actual_time, time(trip.arrival), trip.delayMin),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (trip.late) c.tertiary else c.onSurface)
            }
        }
    }
}

@Composable
private fun Source(trip: Trip) = Panel {
    SectionTitle(stringResource(R.string.source_title))
    Text(stringResource(if (trip.realtime) R.string.source_live else R.string.source_phone), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    Text(stringResource(if (trip.realtime) R.string.source_live_body else R.string.source_phone_body),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CompensationCard(trip: Trip, trips: List<Trip>, dTicket: Boolean) {
    val c = MaterialTheme.colorScheme
    Panel {
        SectionTitle(stringResource(R.string.comp_title))
        when {
            !trip.late -> Text(stringResource(R.string.rule_none), style = MaterialTheme.typography.bodyLarge)
            dTicket -> {
                val month = trip.month()
                val cases = trips.filter { it.late && it.month() == month && it.status == ClaimStatus.OPEN }
                val ct = Compensation.dTicketMonthCt(cases.map { it.delayMin })
                BigMoney(money(Compensation.D_TICKET_CASE_CT), color = c.primary)
                Text(stringResource(R.string.rule_dt, money(Compensation.D_TICKET_CASE_CT), money(Compensation.MIN_PAYOUT_CT)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.rule_dt_month, monthTitle(month), cases.size, money(ct)), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
            trip.fareCt == null -> Text(stringResource(R.string.rule_single_nofare), style = MaterialTheme.typography.bodyLarge)
            else -> {
                val owed = Compensation.singleTicketCt(trip.delayMin, trip.fareCt)
                BigMoney(money(owed), color = if (Compensation.payable(owed)) c.primary else c.onSurface)
                Text(stringResource(if (trip.delayMin >= 120) R.string.rule_single_50 else R.string.rule_single_25, money(trip.fareCt)), style = MaterialTheme.typography.bodyMedium)
                if (!Compensation.payable(owed)) Text(stringResource(R.string.rule_below_min, money(Compensation.MIN_PAYOUT_CT)), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
        if (trip.late) Text(stringResource(R.string.rule_exceptions), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun TripDetailPreview() = DelaydTheme(dark = false) {
    TripDetailContent(sampleTrips[0], sampleTrips, true, {}, {}, {}, {})
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun TripDetailDarkPreview() = DelaydTheme(dark = true) {
    TripDetailContent(sampleTrips[2].copy(status = ClaimStatus.CLAIMED), sampleTrips, false, {}, {}, {}, {})
}
