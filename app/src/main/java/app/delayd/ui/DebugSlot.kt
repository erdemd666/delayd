package app.delayd.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import app.delayd.DebugHooks
import app.delayd.R
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource

fun isDebuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** Appended to the end of Settings in debug builds only; the simulator itself lives in src/debug. */
fun LazyListScope.debugSlot(context: Context) {
    if (!DebugHooks.available(context)) return
    item {
        Panel {
            SectionTitle(stringResource(R.string.debug_title))
            Button({
                DebugHooks.simulateRide(context)
                Toast.makeText(context, context.getString(R.string.debug_simulating), Toast.LENGTH_SHORT).show()
            }) {
                Text(stringResource(R.string.debug_simulate))
            }
        }
    }
}
