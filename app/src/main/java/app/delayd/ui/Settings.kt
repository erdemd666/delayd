package app.delayd.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.delayd.Pipeline
import app.delayd.R
import app.delayd.TrackingService
import app.delayd.Trip
import app.delayd.TripStore

const val PRIVACY_URL = "https://github.com/erdem61/delayd/blob/main/docs/privacy-policy.md"
const val SOURCE_URL = "https://github.com/erdem61/delayd"

@Composable
fun SettingsScreen(prefs: Prefs, trips: List<Trip>, onBack: () -> Unit) {
    val context = LocalContext.current
    val running by TrackingService.running.collectAsState()
    val health by rememberHealth(prefs)
    val toggle = rememberDetectionToggle(prefs)
    SettingsContent(
        dTicket = prefs.dTicket, onDTicket = prefs::chooseDTicket, running = running, onToggle = toggle, health = health,
        onAskLocation = { if (health.locationBlocked) openAppSettings(context) else toggle(true) },
        onOpenAppSettings = { openAppSettings(context) }, onBattery = { openBatterySettings(context) },
        version = versionName(context), onExport = { exportTrips(context, trips) },
        onDeleteAll = { TripStore.clear(); Pipeline.queue(context).clear() }, onBack = onBack,
    )
}

private fun versionName(context: Context) =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

private fun exportTrips(context: Context, trips: List<Trip>) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, TripStore.toJson(trips))
        .putExtra(Intent.EXTRA_SUBJECT, "delayd-trips.json")
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
fun SettingsContent(
    dTicket: Boolean, onDTicket: (Boolean) -> Unit, running: Boolean, onToggle: (Boolean) -> Unit, health: Health,
    onAskLocation: () -> Unit, onOpenAppSettings: () -> Unit, onBattery: () -> Unit,
    version: String, onExport: () -> Unit, onDeleteAll: () -> Unit, onBack: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }

    Screen(stringResource(R.string.settings), onBack) {
        item { SectionTitle(stringResource(R.string.set_ticket)) }
        item {
            Panel(padding = Space.s) {
                Column(Modifier.selectableGroup()) {
                    TicketOption(R.string.ticket_single, R.string.ticket_single_body, !dTicket) { onDTicket(false) }
                    TicketOption(R.string.ticket_dt, R.string.ticket_dt_body, dTicket) { onDTicket(true) }
                }
            }
        }
        if (AppLanguage.supported) {
            item { SectionTitle(stringResource(R.string.set_language)) }
            item {
                var lang by remember { mutableStateOf(AppLanguage.current(context)) }
                Panel(padding = Space.s) {
                    Column(Modifier.selectableGroup()) {
                        listOf(null to stringResource(R.string.lang_system), "de" to stringResource(R.string.lang_de), "en" to stringResource(R.string.lang_en)).forEach { (tag, label) ->
                            Row(Modifier.fillMaxWidth().selectable(lang == tag, role = Role.RadioButton) { lang = tag; AppLanguage.set(context, tag) }.heightIn(min = 48.dp).padding(Space.s),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(lang == tag, null)
                                Spacer(Modifier.width(Space.m))
                                // TalkBack pronounces "Deutsch" / "English" with the app's voice unless the span says which language it is
                                Text(if (tag == null) AnnotatedString(label) else AnnotatedString(label, SpanStyle(localeList = LocaleList(tag))), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.set_detection)) }
        item {
            Panel(padding = Space.s) {
                Row(Modifier.fillMaxWidth().toggleable(running, role = Role.Switch, onValueChange = onToggle).heightIn(min = 56.dp).padding(Space.s),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.detect_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (running) R.string.detect_on_body else R.string.detect_off_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(Space.m))
                    Switch(running, null)
                }
            }
        }
        item { SectionTitle(stringResource(R.string.set_health)) }
        item {
            Panel {
                HealthRow(R.string.perm_location, if (health.fine) R.string.health_ok else if (health.coarseOnly) R.string.perm_location_coarse else R.string.health_location_missing,
                    health.fine, if (health.locationBlocked) R.string.open_app_settings else R.string.allow, onAskLocation)
                HealthRow(R.string.perm_notifications, if (health.notifications) R.string.health_ok else R.string.health_notifications_missing,
                    health.notifications, R.string.open_app_settings, onOpenAppSettings)
                HealthRow(R.string.battery, if (health.batteryUnrestricted) R.string.health_ok else R.string.health_battery_restricted,
                    health.batteryUnrestricted, R.string.open_battery, onBattery)
            }
        }
        item { SectionTitle(stringResource(R.string.set_data)) }
        item {
            Panel {
                Text(stringResource(R.string.data_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Actions {
                    OutlinedButton(onExport, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.export_data)) }
                    OutlinedButton({ confirmDelete = true }, Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.delete_all))
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.set_about)) }
        item {
            Panel(padding = Space.s) {
                LinkRow(R.string.privacy_policy) { uri.openUri(PRIVACY_URL) }
                LinkRow(R.string.licenses) { licenses = true }
                LinkRow(R.string.source_code) { uri.openUri(SOURCE_URL) }
                LinkRow(R.string.attribution) { uri.openUri(TRANSITOUS_SOURCES) }
                Text(stringResource(R.string.version, version), Modifier.padding(Space.s), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { BrandLine(Modifier.padding(top = Space.s)) }
        debugSlot(context)
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_all_title)) },
        text = { Text(stringResource(R.string.delete_all_body)) },
        confirmButton = { TextButton({ confirmDelete = false; onDeleteAll() }) { Text(stringResource(R.string.delete_all_confirm)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (licenses) AlertDialog(
        onDismissRequest = { licenses = false },
        title = { Text(stringResource(R.string.licenses)) },
        text = { Text(stringResource(R.string.licenses_body)) },
        confirmButton = { TextButton({ licenses = false }) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun TicketOption(title: Int, body: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).heightIn(min = 56.dp).padding(Space.s),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null)
        Spacer(Modifier.width(Space.m))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthRow(title: Int, status: Int, ok: Boolean, actionLabel: Int, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            if (ok) Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), MaterialTheme.extra.success)
            else Icon(Icons.Filled.Warning, null, Modifier.size(20.dp), MaterialTheme.colorScheme.tertiary)
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ok) TextButton(onAction, Modifier.heightIn(min = 48.dp)) { Text(stringResource(actionLabel)) }
    }
}

@Composable
private fun LinkRow(label: Int, onClick: () -> Unit) {
    Surface(onClick, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = Space.s), Alignment.CenterStart) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1400)
@Composable
private fun SettingsPreview() = DelaydTheme(dark = false) {
    SettingsContent(true, {}, true, {}, okHealth.copy(batteryUnrestricted = false), {}, {}, {}, "1.0", {}, {}, {})
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1400)
@Composable
private fun SettingsDarkPreview() = DelaydTheme(dark = true) {
    SettingsContent(false, {}, false, {}, okHealth.copy(fine = false, locationBlocked = true), {}, {}, {}, "1.0", {}, {}, {})
}
