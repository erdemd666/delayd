package app.delayd.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import app.delayd.Departure
import app.delayd.Place
import app.delayd.R
import app.delayd.Stop
import app.delayd.Transitous
import app.delayd.Trip
import app.delayd.TripStore
import app.delayd.manualTrip
import app.delayd.nearestInstant
import app.delayd.reachedStops
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Busy : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    /** [offline]: no connection at all, as opposed to the service answering badly. */
    data class Fail(val offline: Boolean) : Load<Nothing>
}

private const val DEBOUNCE_MS = 300L
private const val MIN_CHARS = 2
private val LEAD: Duration = Duration.ofMinutes(10)

private fun nowMinute() = LocalTime.now(ZONE).truncatedTo(ChronoUnit.MINUTES)

/** Steps: 0 station, 1 date + time, 2 train, 3 destination, 4 arrival + confirm. Lives across rotation. */
class AddRideModel : ViewModel() {
    var step by mutableIntStateOf(0)
    var query by mutableStateOf("")
    var places by mutableStateOf<Load<List<Place>>>(Load.Idle)
    var station by mutableStateOf<Place?>(null)
    var day by mutableStateOf(LocalDate.now(ZONE))
    var at by mutableStateOf(nowMinute())
    var departures by mutableStateOf<Load<List<Departure>>>(Load.Idle)
    var train by mutableStateOf<Departure?>(null)
    var stop by mutableStateOf<Stop?>(null)
    /** The arrival the user typed; null = keep what the data says. */
    var typed by mutableStateOf<Instant?>(null)
    var existingId by mutableStateOf<String?>(null)
    private var job: Job? = null

    val departure: Instant get() = day.atTime(at).atZone(ZONE).toInstant()
    val inFuture: Boolean get() = departure.isAfter(Instant.now())

    fun reset() {
        job?.cancel(); step = 0; query = ""; places = Load.Idle; station = null; departures = Load.Idle
        train = null; stop = null; typed = null; existingId = null; day = LocalDate.now(ZONE); at = nowMinute()
    }

    /** One request per pause in typing; shorter than [MIN_CHARS] sends nothing. */
    fun onQuery(q: String) {
        query = q
        job?.cancel()
        if (q.trim().length < MIN_CHARS) { places = Load.Idle; return }
        places = Load.Busy
        job = viewModelScope.launch { delay(DEBOUNCE_MS); places = io { Transitous.searchStations(q.trim()) } }
    }

    fun retrySearch() = onQuery(query)

    fun loadTrains() {
        val s = station ?: return
        job?.cancel()
        departures = Load.Busy
        job = viewModelScope.launch { departures = io { Transitous.departuresAt(s.id, departure - LEAD).filter { !it.cancelled } } }
    }

    private suspend fun <T> io(block: () -> T): Load<T> = try {
        Load.Ok(withContext(Dispatchers.IO) { block() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) { // IOException or bad JSON: both end in "try again"
        Load.Fail(e is UnknownHostException || e is ConnectException)
    }

    fun trip(): Trip? = train?.let { t -> stop?.let { manualTrip(t, it, typed) } }

    /** Returns the new trip's id, or null when it already existed (then [existingId] is set). */
    fun save(): String? {
        val trip = trip() ?: return null
        if (TripStore.add(trip)) return trip.id
        existingId = trip.id
        return null
    }
}

@Composable
fun AddRideScreen(onBack: () -> Unit, onOpenTrip: (String) -> Unit) {
    val activity = LocalActivity.current as ComponentActivity
    // activity-scoped so the flow survives rotation; the viewmodel-compose artifact would be a new dependency for one call
    val m = remember { ViewModelProvider(activity)[AddRideModel::class.java] }
    DisposableEffect(Unit) { onDispose { if (!activity.isChangingConfigurations) m.reset() } }
    BackHandler(m.step > 0) { m.step -= 1 }
    Screen(stringResource(R.string.add_title), onBack = { if (m.step > 0) m.step -= 1 else onBack() }) {
        when (m.step) {
            0 -> stationStep(m)
            1 -> whenStep(m)
            2 -> trainStep(m)
            3 -> stopStep(m)
            else -> confirmStep(m, onOpenTrip)
        }
    }
}

private fun LazyListScope.stationStep(m: AddRideModel) {
    item { SectionTitle(stringResource(R.string.add_step_station)) }
    item {
        OutlinedTextField(
            m.query, m::onQuery, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.add_station_label)) },
            placeholder = { Text(stringResource(R.string.add_station_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
    when (val p = m.places) {
        Load.Idle -> item { Hint(stringResource(R.string.add_type_more)) }
        Load.Busy -> item { Busy() }
        is Load.Fail -> item { Failure(p.offline, m::retrySearch) }
        is Load.Ok -> if (p.value.isEmpty()) item { Hint(stringResource(R.string.add_no_stations, m.query.trim())) }
        else items(p.value, key = { it.id }) { place ->
            PickRow(place.name, place.area.takeIf { it.isNotEmpty() }) { m.station = place; m.step = 1 }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now(ZONE).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today },
    )
    DatePickerDialog(
        onDismiss,
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_set_time)) },
        text = { TimeInput(state) },
        confirmButton = { TextButton({ onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun LazyListScope.whenStep(m: AddRideModel) {
    item { SectionTitle(stringResource(R.string.add_step_when)) }
    item {
        var dateOpen by remember { mutableStateOf(false) }
        var timeOpen by remember { mutableStateOf(false) }
        Panel {
            Text(m.station?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.add_when_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Actions {
                val d = date(m.departure)
                val t = time(m.departure)
                val dLabel = stringResource(R.string.add_date) + ": " + d
                val tLabel = stringResource(R.string.add_time) + ": " + t
                OutlinedButton({ dateOpen = true }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = dLabel }) { Text(d) }
                OutlinedButton({ timeOpen = true }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = tLabel }) { Text(t) }
            }
            if (m.inFuture) Text(stringResource(R.string.add_future), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (dateOpen) DateDialog(m.day, { dateOpen = false }) { m.day = it }
        if (timeOpen) TimeDialog(m.at, { timeOpen = false }) { m.at = it }
    }
    item {
        Actions {
            Button({ m.loadTrains(); m.step = 2 }, Modifier.heightIn(min = 48.dp), enabled = !m.inFuture) { Text(stringResource(R.string.add_show_trains)) }
        }
    }
}

private fun LazyListScope.trainStep(m: AddRideModel) {
    item { SectionTitle(stringResource(R.string.add_step_train)) }
    item { Hint(stringResource(R.string.add_trains_from, m.station?.name.orEmpty(), "${date(m.departure)}, ${time(m.departure)}")) }
    when (val d = m.departures) {
        Load.Idle, Load.Busy -> item { Busy() }
        is Load.Fail -> item { Failure(d.offline, m::loadTrains) }
        is Load.Ok -> if (d.value.isEmpty()) item { Hint(stringResource(R.string.add_no_trains, m.station?.name.orEmpty())) }
        else items(d.value) { dep ->
            PickRow(
                "${time(dep.time)}  ${dep.train}",
                dep.headsign.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.add_train_to, it) },
            ) { m.train = dep; m.step = 3 }
        }
    }
}

private fun LazyListScope.stopStep(m: AddRideModel) {
    val dep = m.train ?: return
    val stops = reachedStops(dep, Instant.now())
    item { SectionTitle(stringResource(R.string.add_step_dest)) }
    item { Hint("${dep.train} · ${time(dep.time)}") }
    if (stops.isEmpty()) item { Hint(stringResource(R.string.add_no_stops)) }
    else items(stops) { s ->
        PickRow(s.name, stringResource(R.string.scheduled_time, time(s.scheduledArrival!!))) {
            m.stop = s; m.typed = null; m.existingId = null; m.step = 4
        }
    }
}

private fun LazyListScope.confirmStep(m: AddRideModel, onOpenTrip: (String) -> Unit) {
    val dep = m.train ?: return
    val stop = m.stop ?: return
    val trip = m.trip() ?: return
    item { SectionTitle(stringResource(R.string.add_step_arrival)) }
    item {
        var open by remember { mutableStateOf(false) }
        Panel {
            Text(stringResource(R.string.route, dep.from, stop.name), style = MaterialTheme.typography.titleMedium)
            Text("${dep.train} · ${date(trip.scheduledArrival)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.scheduled_time, time(trip.scheduledArrival)), style = MaterialTheme.typography.bodyLarge)
            Actions {
                val label = stringResource(R.string.add_actual) + ": " + time(trip.arrival)
                OutlinedButton({ open = true }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = label }) { Text(label) }
                DelayBadge(trip.delayMin)
            }
            Text(stringResource(if (trip.userReported) R.string.add_arrival_manual else R.string.add_arrival_live),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open) TimeDialog(trip.arrival.atZone(ZONE).toLocalTime(), { open = false }) {
                m.typed = nearestInstant(trip.scheduledArrival, it, ZONE)
            }
        }
    }
    item {
        Actions {
            Button({ m.save()?.let(onOpenTrip) }, Modifier.heightIn(min = 48.dp), enabled = m.existingId == null) { Text(stringResource(R.string.add_button)) }
        }
    }
    m.existingId?.let { id ->
        item {
            Panel(container = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Text(stringResource(R.string.add_dupe), style = MaterialTheme.typography.bodyMedium)
                TextButton({ onOpenTrip(id) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_open)) }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = c.surface, border = BorderStroke(1.dp, c.outlineVariant)) {
        Column(Modifier.padding(Space.l).heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Busy() {
    val d = stringResource(R.string.add_loading)
    Box(Modifier.fillMaxWidth().padding(Space.l), Alignment.Center) { CircularProgressIndicator(Modifier.semantics { contentDescription = d }) }
}

@Composable
private fun Failure(offline: Boolean, retry: () -> Unit) = Panel {
    Text(stringResource(if (offline) R.string.add_offline else R.string.add_error), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(retry, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_retry)) }
}
