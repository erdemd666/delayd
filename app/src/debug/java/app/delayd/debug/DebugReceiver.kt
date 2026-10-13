package app.delayd.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.delayd.DebugHooks
import kotlin.concurrent.thread

/** adb shell am broadcast -a app.delayd.debug.REPLAY_REAL -p app.delayd --es station "Berlin Hbf" */
class DebugReceiver : BroadcastReceiver() {
    private companion object { const val REPLAY_REAL = "app.delayd.debug.REPLAY_REAL" }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                if (intent.action == REPLAY_REAL) DebugTools.replayReal(app, intent.getStringExtra("station") ?: "Berlin Hbf")
                else DebugTools.simulateRide(app, intent.getIntExtra(DebugHooks.EXTRA_DELAY_MIN, 75))
            } catch (e: Exception) {
                android.util.Log.e("DelaydReplay", "failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
