package app.delayd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.delayd.ClaimStatus
import app.delayd.Compensation
import app.delayd.R
import app.delayd.TrackingService
import app.delayd.Trip
import java.time.Instant
import java.time.YearMonth

const val TRANSITOUS_SOURCES = "https://transitous.org/sources/"

val MaterialTheme.extra @Composable get() = LocalExtra.current

@Composable
fun HomeScreen(prefs: Prefs, trips: List<Trip>, onTrip: (Trip) -> Unit, onSettings: () -> Unit, onClaimMonth: (YearMonth) -> Unit, onAdd: () -> Unit) {
    val running by TrackingService.running.collectAsState()
    val riding by TrackingService.riding.collectAsState()
    val health by rememberHealth(prefs)
    val toggle = rememberDetectionToggle(prefs)
    HomeContent(trips, prefs.dTicket, running, riding, health, toggle, onTrip, onSettings, onClaimMonth, onAdd)
}

@Composable
fun HomeContent(
    trips: List<Trip>, dTicket: Boolean, running: Boolean, riding: Boolean, health: Health,
    onToggle: (Boolean) -> Unit, onTrip: (Trip) -> Unit, onSettings: () -> Unit, onClaimMonth: (YearMonth) -> Unit, onAdd: () -> Unit,
) {
    val uri = LocalUriHandler.current
    Screen(stringResource(R.string.app_name), onBack = null, actions = {
        IconButton(onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
    }, fab = {
        ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_fab)) })
    }) {
        item { DetectionCard(running, riding, health, onToggle) }
        item { MoneySummary(trips, dTicket) }
        if (dTicket) item { DTicketProgress(trips, onClaimMonth) }
        if (trips.isEmpty()) item { EmptyState(running) }
        trips.sortedByDescending { it.scheduledArrival }.groupBy { it.month() }.forEach { (month, list) ->
            item(key = "h$month") { SectionTitle(monthTitle(month), Modifier.padding(top = Space.s)) }
            items(list, key = { it.id }) { TripRow(it, dTicket) { onTrip(it) } }
        }
        item {
            TextButton({ uri.openUri(TRANSITOUS_SOURCES) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.attribution_short), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DetectionCard(running: Boolean, riding: Boolean, health: Health, onToggle: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when {
        running && riding -> c.tertiaryContainer to c.onTertiaryContainer
        running -> c.primaryContainer to c.onPrimaryContainer
        else -> c.surface to c.onSurface
    }
    val title = when {
        running && riding -> R.string.detect_riding
        running -> R.string.detect_on
        else -> R.string.detect_off
    }
    val body = when {
        running && riding -> R.string.detect_riding_body
        running -> R.string.detect_on_body
        health.locationBlocked -> R.string.detect_blocked_body
        health.coarseOnly -> R.string.detect_coarse_body
        else -> R.string.detect_off_body
    }
    Surface(
        Modifier.fillMaxWidth().toggleable(running, role = Role.Switch, onValueChange = onToggle),
        MaterialTheme.shapes.medium, bg, fg,
        border = if (running) null else BorderStroke(1.dp, c.outlineVariant),
    ) {
        Row(Modifier.padding(Space.l).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.detect_title), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(Space.m))
            Switch(running, null)
        }
    }
}

@Composable
private fun MoneySummary(trips: List<Trip>, dTicket: Boolean) {
    val t = remember(trips, dTicket) { totals(trips, dTicket) }
    // single-ticket mode can't value a delayed trip without a fare
    val needFare = if (dTicket) 0 else trips.count { it.status == ClaimStatus.OPEN && it.late && it.fareCt == null }
    Panel {
        Text(stringResource(R.string.owed_now), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigMoney(money(t.owed), color = if (t.owed > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (needFare > 0) Text(pluralStringResource(R.plurals.needs_fare, needFare, needFare), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Figure(stringResource(R.string.claimed), money(t.claimed), Modifier.weight(1f))
            Figure(stringResource(R.string.recovered), money(t.recovered), Modifier.weight(1f), MaterialTheme.extra.success)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = color)
    }
}

@Composable
private fun DTicketProgress(trips: List<Trip>, onClaim: (YearMonth) -> Unit) {
    val month = remember { YearMonth.now(ZONE) }
    val cases = openCases(trips, month)
    val ct = Compensation.dTicketMonthCt(cases.map { it.delayMin })
    val min = Compensation.MIN_PAYOUT_CT
    val payable = Compensation.payable(ct)
    Panel {
        SectionTitle(stringResource(R.string.dt_title, monthTitle(month)))
        Text(pluralStringResource(R.plurals.dt_cases, cases.size, cases.size, money(ct)), style = MaterialTheme.typography.bodyMedium)
        val desc = stringResource(R.string.dt_progress_desc, money(minOf(ct, min)), money(min))
        LinearProgressIndicator(
            { (ct.toFloat() / min).coerceIn(0f, 1f) },
            Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = desc },
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round,
        )
        Text(
            if (payable) stringResource(R.string.dt_ready, money(ct)) else stringResource(R.string.dt_more, money(min - ct)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (payable) Button({ onClaim(month) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.dt_claim)) }
    }
}

@Composable
private fun TripRow(trip: Trip, dTicket: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val amount = tripCt(trip, dTicket)?.takeIf { trip.late && it > 0 }
    Surface(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = c.surface, border = BorderStroke(1.dp, c.outlineVariant)) {
        Row(Modifier.padding(Space.l).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.route, trip.from, trip.to), style = MaterialTheme.typography.titleMedium)
                Text("${trip.train} · ${date(trip.scheduledArrival)}, ${time(trip.scheduledArrival)}", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.width(Space.m))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                DelayBadge(trip.delayMin)
                if (trip.status != ClaimStatus.OPEN) StatusChip(trip.status)
                else if (amount != null) Text(money(amount), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = c.primary)
            }
        }
    }
}

@Composable
private fun EmptyState(running: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        TicketGlyph(Modifier.size(56.dp), MaterialTheme.colorScheme.outline)
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (running) R.string.empty_on else R.string.empty_off), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

internal val sampleTrips = listOf(
    Trip("ICE 1601", "Hamburg Hbf", "München Hbf", Instant.parse("2026-10-12T12:05:00Z"), Instant.parse("2026-10-12T13:20:00Z"), true),
    Trip("RE 4", "Köln Hbf", "Düsseldorf Hbf", Instant.parse("2026-10-09T08:00:00Z"), Instant.parse("2026-10-09T08:25:00Z"), false),
    Trip("IC 2012", "Berlin Hbf", "Leipzig Hbf", Instant.parse("2026-09-30T17:00:00Z"), Instant.parse("2026-09-30T19:10:00Z"), true, 3_500, ClaimStatus.PAID),
)
internal val okHealth = Health(fine = true, coarseOnly = false, notifications = true, batteryUnrestricted = true, locationBlocked = false, notificationsBlocked = false)

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() = DelaydTheme(dark = false) { HomeContent(sampleTrips, true, true, false, okHealth, {}, {}, {}, {}, {}) }

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomeDarkPreview() = DelaydTheme(dark = true) { HomeContent(sampleTrips, false, true, true, okHealth, {}, {}, {}, {}, {}) }

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HomeEmptyPreview() = DelaydTheme(dark = false) { HomeContent(emptyList(), true, false, false, okHealth.copy(fine = false), {}, {}, {}, {}, {}) }
