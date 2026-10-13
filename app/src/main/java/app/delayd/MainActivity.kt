package app.delayd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ZONE = ZoneId.of("Europe/Berlin")
private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private const val DB_CLAIM_URL = "https://www.bahn.de/service/informationen-buchung/fahrgastrechte"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TripStore.init(this)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(prefs) }
            }
        }
    }
}

@Composable
private fun App(prefs: SharedPreferences) {
    val context = LocalContext.current
    val trips by TripStore.trips.collectAsState()
    val running by TrackingService.running.collectAsState()
    var dTicket by remember { mutableStateOf(prefs.getBoolean("dTicket", true)) }
    val service = Intent(context, TrackingService::class.java)
    val askPermissions = rememberLauncherForActivityResult(RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) context.startForegroundService(service)
    }

    Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
        Text("Delayd", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
        SettingRow("Detect train rides automatically", running) { on ->
            if (!on) context.stopService(service)
            else askPermissions.launch(buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray())
        }
        SettingRow("I travel with a Deutschlandticket", dTicket) {
            dTicket = it
            prefs.edit().putBoolean("dTicket", it).apply()
        }
        if (dTicket) DTicketSummary(trips)

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (trips.isEmpty()) item {
                Text("No rides yet. Turn on detection and take a train, Delayd does the rest.",
                    Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(trips) { TripCard(it, dTicket) }
        }
        Attribution()
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun DTicketSummary(trips: List<Trip>) {
    val month = YearMonth.now(ZONE)
    val delays = trips.filter { YearMonth.from(it.scheduledArrival.atZone(ZONE)) == month }.map { it.delayMin }
    val ct = Compensation.dTicketMonthCt(delays)
    val cases = delays.count { it >= 60 }
    val text = if (Compensation.payable(ct)) "$cases delays this month: ${Compensation.format(ct)}, ready to claim"
    else "$cases delays this month: ${Compensation.format(ct)}. Claims open at ${Compensation.format(Compensation.MIN_PAYOUT_CT)}."
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(text, Modifier.padding(16.dp)) }
}

@Composable
private fun TripCard(trip: Trip, dTicket: Boolean) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var fare by remember(trip) { mutableStateOf("") }
    val late = trip.delayMin >= 60
    val fareCt = fare.replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.toInt()?.takeIf { it > 0 }

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(trip.train, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(trip.scheduledArrival.atZone(ZONE).format(DATE))
            }
            Text("${trip.from} → ${trip.to}")
            Text("Due ${trip.scheduledArrival.atZone(ZONE).format(TIME)} · arrived " +
                "${trip.arrival.atZone(ZONE).format(TIME)} (+${trip.delayMin} min)",
                color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!trip.realtime) Text("Arrival measured by your phone", style = MaterialTheme.typography.bodySmall)
            if (late) {
                if (dTicket) Text("Counts ${Compensation.format(Compensation.D_TICKET_CASE_CT)} toward your monthly claim")
                else {
                    OutlinedTextField(fare, { fare = it }, label = { Text("Ticket price (€)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    fareCt?.let {
                        val owed = Compensation.singleTicketCt(trip.delayMin, it)
                        Text(if (Compensation.payable(owed)) "You're owed ${Compensation.format(owed)}"
                        else "${Compensation.format(owed)} is below the 4 € payout minimum")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button({ shareClaim(context, trip, fare.takeIf { !dTicket }) }) { Text("Copy claim details") }
                    OutlinedButton({ uri.openUri(DB_CLAIM_URL) }) { Text("Open DB form") }
                }
            }
        }
    }
}

/** DB's form needs a login, so we hand over the facts in German, ready to paste. */
private fun shareClaim(context: Context, trip: Trip, fare: String?) {
    val text = buildString {
        appendLine("Fahrgastrechte – Entschädigungsantrag")
        appendLine("Zug: ${trip.train}")
        appendLine("Datum: ${trip.scheduledArrival.atZone(ZONE).format(DATE)}")
        appendLine("Von: ${trip.from}")
        appendLine("Nach: ${trip.to}")
        appendLine("Planmäßige Ankunft: ${trip.scheduledArrival.atZone(ZONE).format(TIME)}")
        appendLine("Tatsächliche Ankunft: ${trip.arrival.atZone(ZONE).format(TIME)} (+${trip.delayMin} Min.)")
        appendLine(if (fare.isNullOrBlank()) "Fahrkarte: Deutschlandticket" else "Fahrpreis: $fare €")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Claim details"))
}

@Composable
private fun Attribution() {
    val uri = LocalUriHandler.current
    TextButton({ uri.openUri("https://transitous.org/sources/") }, Modifier.fillMaxWidth()) {
        Text("Train data: Transitous · © OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall)
    }
}
