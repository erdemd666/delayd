package app.delayd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Android 14+ refuses a location service started without a visible app, so after boot or update we ask for one tap. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!TrackingService.wanted(context)) return
        Notifier.channels(context)
        Notifier.resume(context)
    }
}
