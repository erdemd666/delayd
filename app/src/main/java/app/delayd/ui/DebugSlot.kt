package app.delayd.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import app.delayd.DebugHooks
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext

fun isDebuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** Appended to the end of Settings in debug builds only; the simulator itself lives in src/debug. */
fun LazyListScope.debugSlot(context: Context) {
    if (!DebugHooks.available(context)) return
    item {
        Panel {
            SectionTitle("Debug")
            Button({
                DebugHooks.simulateRide(context)
                Toast.makeText(context, "Simulating a 75 min late ride…", Toast.LENGTH_SHORT).show()
            }) {
                Text("Simulate ride")
            }
        }
    }
}
