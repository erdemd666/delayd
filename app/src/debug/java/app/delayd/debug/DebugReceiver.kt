package app.delayd.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.delayd.DebugHooks
import kotlin.concurrent.thread

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                DebugTools.simulateRide(app, intent.getIntExtra(DebugHooks.EXTRA_DELAY_MIN, 75))
            } finally {
                pending.finish()
            }
        }
    }
}
