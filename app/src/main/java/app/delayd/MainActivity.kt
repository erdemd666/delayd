package app.delayd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Intent
import app.delayd.ui.DeepLink
import app.delayd.ui.DelaydApp
import app.delayd.ui.DelaydTheme
import app.delayd.ui.Prefs
import app.delayd.ui.readHealth
import app.delayd.ui.startDetection

class MainActivity : ComponentActivity() {
    /** Trip (and whether to open its claim) from a delay notification, consumed once by the UI. */
    private var deepLink by mutableStateOf<DeepLink?>(null)

    private fun linkOf(i: Intent) = i.getStringExtra(TrackingService.EXTRA_TRIP_ID)?.let { DeepLink(it, i.getBooleanExtra(Notifier.EXTRA_CLAIM, false)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TripStore.init(this)
        val prefs = Prefs(this)
        // Quick Settings tile "on": started from here because this activity is visible, which Android 14+ requires for a location service.
        if (savedInstanceState == null && intent.action == ACTION_START_DETECTION && readHealth(this, prefs).fine) {
            startDetection(this); finish(); return
        }
        enableEdgeToEdge()
        Notifier.channels(this) // channel names follow the app language
        // After rotation the same intent is delivered again; only a fresh launch may navigate.
        if (savedInstanceState == null) deepLink = linkOf(intent)
        addOnNewIntentListener { linkOf(it)?.let { l -> deepLink = l } }
        setContent { DelaydTheme { DelaydApp(prefs, deepLink) { deepLink = null } } }
    }

    companion object {
        const val ACTION_START_DETECTION = "app.delayd.START_DETECTION"
    }
}
