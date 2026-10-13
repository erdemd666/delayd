package app.delayd.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.delayd.TrackingService

const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
const val NOTIF = "android.permission.POST_NOTIFICATIONS"

/** Everything the health check and onboarding show. [blocked] = "don't ask again" was chosen. */
data class Health(
    val fine: Boolean, val coarseOnly: Boolean, val notifications: Boolean, val batteryUnrestricted: Boolean,
    val locationBlocked: Boolean, val notificationsBlocked: Boolean,
) {
    val ok get() = fine && notifications && batteryUnrestricted
}

fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

private fun Context.granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

fun readHealth(context: Context, prefs: Prefs): Health {
    val act = context.activity()
    val fine = context.granted(FINE)
    val notif = Build.VERSION.SDK_INT < 33 || context.granted(NOTIF)
    fun blocked(p: String) = prefs.asked(p) && act?.shouldShowRequestPermissionRationale(p) == false
    return Health(
        fine = fine, coarseOnly = !fine && context.granted(COARSE), notifications = notif,
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        locationBlocked = !fine && blocked(FINE), notificationsBlocked = !notif && blocked(NOTIF),
    )
}

/** Health that refreshes whenever the user returns from system settings. */
@Composable
fun rememberHealth(prefs: Prefs, refreshKey: Int = 0): State<Health> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readHealth(context, prefs)) }
    androidx.compose.runtime.LaunchedEffect(refreshKey) { state.value = readHealth(context, prefs) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) state.value = readHealth(context, prefs) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return state
}

fun openAppSettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

/** The list screen, not the one-tap prompt: that needs a manifest permission Play restricts. */
fun openBatterySettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

fun startDetection(context: Context) = context.startForegroundService(Intent(context, TrackingService::class.java))
fun stopDetection(context: Context) { context.stopService(Intent(context, TrackingService::class.java)) }

/**
 * Asks for [permissions] and calls [onResult] afterwards. When a permission is permanently denied the system
 * dialog never shows, so the caller should send the user to app settings instead (see [Health]).
 */
@Composable
fun rememberPermissionRequest(prefs: Prefs, permissions: Array<String>, onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { onResult() }
    return { prefs.markAsked(permissions); launcher.launch(permissions) }
}

/** Turning detection on needs precise location; turning it off never needs anything. */
@Composable
fun rememberDetectionToggle(prefs: Prefs): (Boolean) -> Unit {
    val context = LocalContext.current
    val perms = remember { buildList { add(FINE); add(COARSE); if (Build.VERSION.SDK_INT >= 33) add(NOTIF) }.toTypedArray() }
    val request = rememberPermissionRequest(prefs, perms) { if (context.granted(FINE)) startDetection(context) }
    return { on ->
        when {
            !on -> stopDetection(context)
            context.granted(FINE) -> startDetection(context)
            readHealth(context, prefs).locationBlocked -> openAppSettings(context)
            else -> request()
        }
    }
}
