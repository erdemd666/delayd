package app.delayd.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.delayd.R

private const val STEPS = 4

@Composable
fun OnboardingScreen(prefs: Prefs, onFinish: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val health by rememberHealth(prefs, tick)
    val askLocation = rememberPermissionRequest(prefs, arrayOf(FINE, COARSE)) { tick++ }
    val askNotifications = rememberPermissionRequest(prefs, arrayOf(NOTIF)) { tick++ }
    OnboardingContent(
        health = health,
        onAskLocation = { if (health.locationBlocked) openAppSettings(context) else askLocation() },
        onAskNotifications = { if (health.notificationsBlocked) openAppSettings(context) else askNotifications() },
        onBattery = { openBatterySettings(context) },
        onFinish = { if (health.fine) startDetection(context); onFinish() },
    )
}

@Composable
fun OnboardingContent(health: Health, onAskLocation: () -> Unit, onAskNotifications: () -> Unit, onBattery: () -> Unit, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxHeight().padding(horizontal = Space.l)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                val progress = stringResource(R.string.onb_progress, step + 1, STEPS)
                Row(Modifier.padding(top = Space.xl).semantics { contentDescription = progress }, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    repeat(STEPS) { Box(Modifier.size(width = 28.dp, height = 6.dp).background(if (it <= step) c.primary else c.outlineVariant, CircleShape)) }
                }
                Box(Modifier.size(72.dp).background(c.primaryContainer, CircleShape), Alignment.Center) { TicketGlyph(Modifier.size(40.dp), c.onPrimaryContainer) }
                when (step) {
                    0 -> Page(R.string.onb1_title, R.string.onb1_body) {
                        Panel { Text(stringResource(R.string.onb1_rules), style = MaterialTheme.typography.bodyLarge) }
                    }
                    1 -> Page(R.string.onb2_title, R.string.onb2_body) {
                        Panel {
                            Bullet(R.string.onb2_p1); Bullet(R.string.onb2_p2); Bullet(R.string.onb2_p3)
                        }
                    }
                    2 -> Page(R.string.onb3_title, R.string.onb3_body) {
                        PermissionRow(R.string.perm_location, R.string.perm_location_why, health.fine, health.locationBlocked, onAskLocation,
                            note = if (health.coarseOnly) R.string.perm_location_coarse else null)
                        if (Build.VERSION.SDK_INT >= 33) PermissionRow(R.string.perm_notifications, R.string.perm_notifications_why, health.notifications, health.notificationsBlocked, onAskNotifications)
                        Text(stringResource(R.string.onb3_skip_note), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    }
                    else -> Page(R.string.onb4_title, R.string.onb4_body) {
                        Panel {
                            if (health.batteryUnrestricted) StatusLine(R.string.battery_ok)
                            else OutlinedButton(onBattery, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.open_battery)) }
                            Text(stringResource(R.string.onb4_hint), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Space.l), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton({ step-- }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                Button({ if (step < STEPS - 1) step++ else onFinish() }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (step < STEPS - 1) R.string.next else R.string.get_started))
                }
            }
        }
    }
}

@Composable
private fun Page(title: Int, body: Int, extra: @Composable ColumnScope.() -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m), content = extra)
}

@Composable
private fun Bullet(text: Int) = Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
    Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun StatusLine(text: Int) = Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), MaterialTheme.extra.success)
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.extra.success)
}

@Composable
private fun PermissionRow(title: Int, why: Int, granted: Boolean, blocked: Boolean, onAsk: () -> Unit, note: Int? = null) {
    Panel {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (note != null) Text(stringResource(note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        if (granted) StatusLine(R.string.perm_allowed)
        else {
            if (blocked) Text(stringResource(R.string.perm_blocked), style = MaterialTheme.typography.bodyMedium)
            // two rows say "Allow": the spoken label names what is allowed
            val action = stringResource(if (blocked) R.string.open_app_settings else R.string.allow)
            val spoken = action + ": " + stringResource(title)
            Button(onAsk, Modifier.heightIn(min = 48.dp).semantics { contentDescription = spoken }) { Text(action) }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingPreview() = DelaydTheme(dark = false) {
    OnboardingContent(okHealth.copy(fine = false, locationBlocked = true), {}, {}, {}, {})
}
