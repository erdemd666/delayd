package app.delayd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.delayd.ui.DelaydApp
import app.delayd.ui.DelaydTheme
import app.delayd.ui.Prefs

class MainActivity : ComponentActivity() {
    /** Trip id from a delay notification, consumed once by the UI. */
    private var deepLinkTripId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TripStore.init(this)
        val prefs = Prefs(this)
        // After rotation the same intent is delivered again; only a fresh launch may navigate.
        if (savedInstanceState == null) deepLinkTripId = intent.getStringExtra(TrackingService.EXTRA_TRIP_ID)
        addOnNewIntentListener { it.getStringExtra(TrackingService.EXTRA_TRIP_ID)?.let { id -> deepLinkTripId = id } }
        setContent { DelaydTheme { DelaydApp(prefs, deepLinkTripId) { deepLinkTripId = null } } }
    }
}
