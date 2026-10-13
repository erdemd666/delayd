package app.delayd.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext

/**
 * Debug-only entry points the coordinator wires up. Release builds never show the section: it checks
 * FLAG_DEBUGGABLE at runtime so no BuildConfig (Gradle belongs to Workstream C) is needed.
 *
 * Wiring: in a debug-only place that runs at start-up, e.g. `DebugHooks.simulateRide = { DebugTools.simulateRide(it) }`.
 */
object DebugHooks {
    var simulateRide: ((Context) -> Unit)? = null
}

fun isDebuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** DEBUG SLOT: appended to the end of Settings in debuggable builds only. */
fun LazyListScope.debugSlot(context: Context) {
    if (!isDebuggable(context)) return
    item {
        Panel {
            SectionTitle("Debug")
            Button({ DebugHooks.simulateRide?.invoke(context) ?: Toast.makeText(context, "Ride simulator not wired yet", Toast.LENGTH_SHORT).show() }) {
                Text("Simulate ride")
            }
        }
    }
}
